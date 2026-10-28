package com.aishop.service;

import com.aishop.config.CacheConfig;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.dto.RecommendationDtos.Recommendation;
import com.aishop.dto.RecommendationDtos.RecommendationResponse;
import com.aishop.entity.CartItem;
import com.aishop.entity.Product;
import com.aishop.repository.CartItemRepository;
import com.aishop.repository.OrderRepository;
import com.aishop.repository.ProductRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Personalized recommendations. The user's purchase history and cart are summarized into a shopper profile,
 * and OpenAI picks and explains the best matches from the in-stock catalog. Every id the model returns is
 * checked against the candidate list, so it can only recommend real, purchasable products. Without an API key,
 * or if the call fails, a content-based scorer (category, tags, rating, price band) is used instead.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final int LIMIT = 8;
    private static final int MAX_CANDIDATES = 80;
    private static final int MAX_PER_CATEGORY = 3;

    /** Categories that go together, used by the local scorer to suggest complements rather than duplicates. */
    private static final Map<String, Set<String>> COMPLEMENTS = Map.of(
            "Laptops", Set.of("Accessories", "Tablets"),
            "Smartphones", Set.of("Accessories", "Watches"),
            "Tablets", Set.of("Accessories", "Laptops"),
            "Accessories", Set.of("Smartphones", "Laptops"),
            "Fashion", Set.of("Footwear", "Watches", "Beauty"),
            "Footwear", Set.of("Fashion", "Sports"),
            "Watches", Set.of("Fashion", "Accessories"),
            "Beauty", Set.of("Fashion"),
            "Kitchen", Set.of("Home"),
            "Home", Set.of("Kitchen"));

    private static final String SYSTEM_PROMPT = """
            You are the recommendation engine of an online store. Given a shopper profile and a catalog of
            candidate products, choose the products this shopper is most likely to buy next. Prefer items that
            complement what they own (accessories, related categories) over near-duplicates, and keep a sensible
            price range. Only use product ids from the catalog.
            Respond with JSON: {"recommendations":[{"id":<number>,"reason":"<max 15 words, addressed to the shopper>"}]}
            """;

    private final ProductRepository products;
    private final OrderRepository orders;
    private final CartItemRepository cartItems;
    private final OpenAiClient openAi;

    public RecommendationService(ProductRepository products, OrderRepository orders, CartItemRepository cartItems,
                                 OpenAiClient openAi) {
        this.products = products;
        this.orders = orders;
        this.cartItems = cartItems;
        this.openAi = openAi;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.RECOMMENDATIONS, key = "#userId")
    public RecommendationResponse forUser(Long userId) {
        List<Product> catalog = products.findByActiveTrueAndStockGreaterThan(0);
        Map<Long, Product> byId = catalog.stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Product> purchased = orders.findPurchasedProductIds(userId, OrderService.PAID_STATUSES).stream()
                .distinct().map(byId::get).filter(Objects::nonNull).limit(15).toList();
        List<Product> inCart = cartItems.findByUserIdOrderByIdAsc(userId).stream().map(CartItem::getProduct).toList();

        if (purchased.isEmpty() && inCart.isEmpty()) {
            return new RecommendationResponse(trending(catalog, Set.of()), "trending");
        }

        Set<Long> exclude = new HashSet<>();
        purchased.forEach(p -> exclude.add(p.getId()));
        inCart.forEach(p -> exclude.add(p.getId()));

        String profile = "Recently purchased:\n" + describe(purchased) + "\nCurrently in cart:\n" + describe(inCart);
        List<Product> signals = new ArrayList<>(inCart);
        signals.addAll(purchased);
        return recommend(profile, signals, catalog, exclude, exclude);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.SIMILAR, key = "#productId")
    public RecommendationResponse similarTo(Long productId) {
        List<Product> catalog = products.findByActiveTrueAndStockGreaterThan(0);
        Product anchor = products.findById(productId).orElseThrow(() -> com.aishop.exception.ApiException.notFound("Product"));
        String profile = "Shopper is viewing this product and wants related items or alternatives:\n"
                + describe(List.of(anchor));
        return recommend(profile, List.of(anchor), catalog, Set.of(anchor.getId()), Set.of());
    }

    /** A purchase changes the profile, so drop the user's cached recommendations. */
    @TransactionalEventListener
    @CacheEvict(cacheNames = CacheConfig.RECOMMENDATIONS, key = "#event.userId()")
    public void onOrderPaid(OrderPaidEvent event) {
    }

    /** {@code owned} are signals the shopper already has, so more of the same category is less useful. */
    private RecommendationResponse recommend(String profile, List<Product> signals, List<Product> catalog,
                                             Set<Long> exclude, Set<Long> owned) {
        // Pre-rank with the local scorer so the prompt holds the most relevant candidates and stays small.
        List<Product> candidates = catalog.stream()
                .filter(p -> !exclude.contains(p.getId()))
                .sorted(Comparator.comparingDouble((Product p) -> score(p, signals, owned)).reversed())
                .limit(MAX_CANDIDATES)
                .toList();

        if (openAi.enabled()) {
            try {
                List<Recommendation> ai = askModel(profile, candidates);
                if (ai.size() >= 3) {
                    return new RecommendationResponse(ai, "openai");
                }
                log.warn("OpenAI returned only {} usable recommendations; using fallback", ai.size());
            } catch (Exception e) {
                log.warn("OpenAI recommendation call failed; using fallback: {}", e.getMessage());
            }
        }
        // Keep the list varied: at most a few picks from any one category.
        Map<String, Integer> perCategory = new HashMap<>();
        return new RecommendationResponse(candidates.stream()
                .filter(p -> perCategory.merge(p.getCategory(), 1, Integer::sum) <= MAX_PER_CATEGORY)
                .limit(LIMIT)
                .map(p -> new Recommendation(ProductResponse.from(p), fallbackReason(p, signals)))
                .toList(), "fallback");
    }

    private List<Recommendation> askModel(String profile, List<Product> candidates) {
        String userPrompt = profile + "\nCatalog (id | name | category | price INR | rating | tags):\n"
                + candidates.stream().map(RecommendationService::line).collect(Collectors.joining("\n"))
                + "\n\nReturn the best " + LIMIT + " picks, best first.";
        String json = openAi.completeJson(SYSTEM_PROMPT, userPrompt).orElse("{}");

        Map<Long, Product> allowed = candidates.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        JSONArray picks = new JSONObject(json).optJSONArray("recommendations", new JSONArray());
        Map<Long, Recommendation> result = new LinkedHashMap<>();
        for (int i = 0; i < picks.length() && result.size() < LIMIT; i++) {
            JSONObject pick = picks.optJSONObject(i);
            if (pick == null) continue;
            Product p = allowed.get(pick.optLong("id", -1));
            if (p != null) {
                result.putIfAbsent(p.getId(), new Recommendation(ProductResponse.from(p),
                        truncate(pick.optString("reason", "Picked for you"), 120)));
            }
        }
        return List.copyOf(result.values());
    }

    private List<Recommendation> trending(List<Product> catalog, Set<Long> exclude) {
        return catalog.stream()
                .filter(p -> !exclude.contains(p.getId()))
                .sorted(Comparator.comparingDouble(Product::getRating).reversed())
                .limit(LIMIT)
                .map(p -> new Recommendation(ProductResponse.from(p), "Top rated in " + p.getCategory()))
                .toList();
    }

    /** Content-based similarity between a candidate and the shopper's signal products. */
    static double score(Product candidate, List<Product> signals, Set<Long> owned) {
        Set<String> candidateTags = tags(candidate);
        double best = 0;
        for (Product s : signals) {
            double sc = 0;
            if (s.getCategory().equalsIgnoreCase(candidate.getCategory())) {
                sc += owned.contains(s.getId()) ? 0.5 : 3;
            } else if (COMPLEMENTS.getOrDefault(s.getCategory(), Set.of()).contains(candidate.getCategory())) {
                sc += owned.contains(s.getId()) ? 3 : 2;
            }
            Set<String> overlap = new HashSet<>(tags(s));
            overlap.retainAll(candidateTags);
            sc += overlap.size() * 1.5;
            double ratio = candidate.getPrice().doubleValue() / Math.max(1, s.getPrice().doubleValue());
            if (ratio > 0.3 && ratio < 3) sc += 1;
            best = Math.max(best, sc);
        }
        return best + candidate.getRating() / 5.0;
    }

    private static String fallbackReason(Product p, List<Product> signals) {
        return signals.stream()
                .filter(s -> COMPLEMENTS.getOrDefault(s.getCategory(), Set.of()).contains(p.getCategory()))
                .findFirst()
                .map(s -> "Pairs well with " + s.getName())
                .or(() -> signals.stream()
                        .filter(s -> s.getCategory().equalsIgnoreCase(p.getCategory()))
                        .findFirst()
                        .map(s -> "Similar to " + s.getName()))
                .orElse("Popular in " + p.getCategory());
    }

    private static Set<String> tags(Product p) {
        if (p.getTags() == null || p.getTags().isBlank()) return Set.of();
        return Arrays.stream(p.getTags().toLowerCase().split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).collect(Collectors.toSet());
    }

    private static String describe(List<Product> list) {
        if (list.isEmpty()) return "- (none)";
        return list.stream()
                .map(p -> "- " + p.getName() + " (" + p.getCategory() + ", INR " + p.getPrice().toPlainString() + ")")
                .collect(Collectors.joining("\n"));
    }

    private static String line(Product p) {
        return p.getId() + " | " + p.getName() + " | " + p.getCategory() + " | " + p.getPrice().toPlainString()
                + " | " + p.getRating() + " | " + Objects.requireNonNullElse(p.getTags(), "");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

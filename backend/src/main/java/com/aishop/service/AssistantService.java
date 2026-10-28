package com.aishop.service;

import com.aishop.dto.AssistantDtos.AssistantResponse;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.dto.RecommendationDtos.Recommendation;
import com.aishop.entity.Product;
import com.aishop.repository.ProductRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Conversational shopping assistant behind the voice orb on the home page. The shopper describes what they
 * want in their own words ("a gift for my dad under 5000"); OpenAI answers and picks matching products from
 * the in-stock catalog. As with recommendations, every id the model returns is checked against the catalog.
 * Without an API key a keyword matcher (synonyms, categories, tags, budget) answers instead.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final int LIMIT = 4;
    private static final int MAX_CANDIDATES = 40;

    private static final Pattern BUDGET = Pattern.compile(
            "(?:under|below|less than|within|upto|up to|max(?:imum)?|budget(?: of)?)\\s*(?:₹|rs\\.?|inr)?\\s*([\\d,]+(?:\\.\\d+)?)\\s*(k(?![a-z])|lakhs?|lacs?)?",
            Pattern.CASE_INSENSITIVE);

    /** Everyday words mapped to catalog categories. */
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("phone", "Smartphones"), Map.entry("mobile", "Smartphones"), Map.entry("iphone", "Smartphones"),
            Map.entry("laptop", "Laptops"), Map.entry("notebook", "Laptops"), Map.entry("computer", "Laptops"),
            Map.entry("tablet", "Tablets"), Map.entry("ipad", "Tablets"),
            Map.entry("headphone", "Accessories"), Map.entry("earbud", "Accessories"), Map.entry("earphone", "Accessories"),
            Map.entry("speaker", "Accessories"), Map.entry("charger", "Accessories"), Map.entry("audio", "Accessories"),
            Map.entry("music", "Accessories"), Map.entry("gadget", "Accessories"),
            Map.entry("watch", "Watches"), Map.entry("shoe", "Footwear"), Map.entry("sneaker", "Footwear"),
            Map.entry("heel", "Footwear"), Map.entry("slipper", "Footwear"), Map.entry("footwear", "Footwear"),
            Map.entry("dress", "Fashion"), Map.entry("shirt", "Fashion"), Map.entry("clothe", "Fashion"),
            Map.entry("bag", "Fashion"), Map.entry("handbag", "Fashion"), Map.entry("jewellery", "Fashion"),
            Map.entry("jewelry", "Fashion"), Map.entry("earring", "Fashion"), Map.entry("sunglass", "Fashion"),
            Map.entry("outfit", "Fashion"), Map.entry("makeup", "Beauty"), Map.entry("perfume", "Beauty"),
            Map.entry("fragrance", "Beauty"), Map.entry("skincare", "Beauty"), Map.entry("skin", "Beauty"),
            Map.entry("cook", "Kitchen"), Map.entry("cooking", "Kitchen"), Map.entry("kitchen", "Kitchen"),
            Map.entry("furniture", "Home"), Map.entry("decor", "Home"), Map.entry("home", "Home"),
            Map.entry("sofa", "Home"), Map.entry("bed", "Home"), Map.entry("sport", "Sports"),
            Map.entry("sports", "Sports"), Map.entry("fitness", "Sports"), Map.entry("ball", "Sports"),
            Map.entry("game", "Sports"));

    private static final Set<String> MALE = Set.of("dad", "father", "husband", "brother", "him", "men", "man", "boyfriend");
    private static final Set<String> FEMALE = Set.of("mom", "mother", "wife", "sister", "her", "women", "woman",
            "girlfriend");
    private static final Set<String> GIFT_FOR_MALE = Set.of("Watches", "Accessories");
    private static final Set<String> GIFT_FOR_FEMALE = Set.of("Beauty", "Fashion");
    private static final Set<String> GIFT = Set.of("Watches", "Accessories", "Beauty");

    private static final Set<String> STOP_WORDS = Set.of("a", "an", "the", "i", "me", "my", "for", "to", "of", "and",
            "or", "in", "on", "with", "want", "need", "looking", "show", "find", "some", "something", "please", "can",
            "you", "get", "buy", "suggest", "recommend", "good", "best", "nice", "under", "below", "budget", "rs",
            "inr", "is", "it", "that", "this", "what", "which", "like", "would", "should", "give", "any");

    private static final String SYSTEM_PROMPT = """
            You are Curato's friendly voice shopping assistant. The shopper tells you what they are looking for.
            Pick the products from the catalog that best match their request and budget, and reply briefly.
            Only use product ids from the catalog. If nothing fits, say so kindly and suggest the closest options.
            Your reply is read aloud, so keep it to one or two short sentences with no lists, markdown or prices.
            Respond with JSON: {"reply":"<text>","recommendations":[{"id":<number>,"reason":"<max 12 words>"}]}
            """;

    private final ProductRepository products;
    private final OpenAiClient openAi;

    public AssistantService(ProductRepository products, OpenAiClient openAi) {
        this.products = products;
        this.openAi = openAi;
    }

    @Transactional(readOnly = true)
    public AssistantResponse ask(String message) {
        String query = message.strip();
        BigDecimal budget = budget(query);
        List<Product> catalog = products.findByActiveTrueAndStockGreaterThan(0).stream()
                .filter(p -> budget == null || p.getPrice().compareTo(budget) <= 0)
                .toList();

        Set<String> words = words(query);
        Set<String> categories = words.stream().map(AssistantService::category).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // A named product type wins; otherwise go by who the gift is for.
        boolean male = words.stream().anyMatch(MALE::contains);
        boolean female = words.stream().anyMatch(FEMALE::contains);
        if (categories.isEmpty()) {
            if (male) categories.addAll(GIFT_FOR_MALE);
            if (female) categories.addAll(GIFT_FOR_FEMALE);
            if (categories.isEmpty() && words.contains("gift")) categories.addAll(GIFT);
        }
        if (male && !female) words.add("men");
        if (female && !male) words.add("women");
        List<Product> ranked = catalog.stream()
                .sorted(Comparator.comparingDouble((Product p) -> score(p, words, categories)).reversed())
                .toList();

        if (openAi.enabled()) {
            try {
                AssistantResponse ai = askModel(query, ranked.stream().limit(MAX_CANDIDATES).toList());
                if (ai != null) return ai;
            } catch (Exception e) {
                log.warn("OpenAI assistant call failed; using fallback: {}", e.getMessage());
            }
        }
        return fallback(ranked, words, categories, budget);
    }

    private AssistantResponse askModel(String query, List<Product> candidates) {
        String userPrompt = "Shopper said: \"" + query + "\"\n\nCatalog (id | name | category | price INR | rating | tags):\n"
                + candidates.stream().map(AssistantService::line).collect(Collectors.joining("\n"))
                + "\n\nReturn up to " + LIMIT + " picks, best first.";
        JSONObject json = new JSONObject(openAi.completeJson(SYSTEM_PROMPT, userPrompt).orElse("{}"));

        Map<Long, Product> allowed = candidates.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        JSONArray picks = json.optJSONArray("recommendations", new JSONArray());
        Map<Long, Recommendation> result = new LinkedHashMap<>();
        for (int i = 0; i < picks.length() && result.size() < LIMIT; i++) {
            JSONObject pick = picks.optJSONObject(i);
            if (pick == null) continue;
            Product p = allowed.get(pick.optLong("id", -1));
            if (p != null) {
                result.putIfAbsent(p.getId(), new Recommendation(ProductResponse.from(p),
                        truncate(pick.optString("reason", "A good match"), 100)));
            }
        }
        String reply = json.optString("reply", "").strip();
        if (reply.isEmpty()) return null;
        return new AssistantResponse(truncate(reply, 300), List.copyOf(result.values()), "openai");
    }

    private AssistantResponse fallback(List<Product> ranked, Set<String> words, Set<String> categories,
                                       BigDecimal budget) {
        Map<String, Integer> perCategory = new HashMap<>();
        List<Product> matches = ranked.stream()
                .filter(p -> score(p, words, categories) >= 1)
                .filter(p -> categories.size() < 2 || perCategory.merge(p.getCategory(), 1, Integer::sum) <= 2)
                .limit(LIMIT)
                .toList();
        String budgetText = budget == null ? "" : " within your budget";
        if (!matches.isEmpty()) {
            String what = String.join(", ", matches.stream().map(Product::getCategory).distinct().toList())
                    .toLowerCase().replaceFirst(", ([^,]+)$", " and $1");
            return new AssistantResponse("Here are my top picks in " + what + budgetText + ".",
                    matches.stream().map(p -> new Recommendation(ProductResponse.from(p), reason(p, words))).toList(),
                    "fallback");
        }
        List<Product> popular = ranked.stream().sorted(Comparator.comparingDouble(Product::getRating).reversed())
                .limit(LIMIT).toList();
        String reply = popular.isEmpty()
                ? "I couldn't find anything" + budgetText + ". Try a higher budget or another category."
                : "I couldn't find an exact match, but these top-rated picks" + budgetText + " are popular right now.";
        return new AssistantResponse(reply,
                popular.stream().map(p -> new Recommendation(ProductResponse.from(p), "Top rated in " + p.getCategory()))
                        .toList(),
                "fallback");
    }

    static double score(Product p, Set<String> words, Set<String> categories) {
        double sc = categories.contains(p.getCategory()) ? 3 : 0;
        String name = p.getName().toLowerCase();
        Set<String> nameWords = new HashSet<>(Arrays.asList(name.split("[^a-z0-9']+")));
        String tags = Objects.requireNonNullElse(p.getTags(), "").toLowerCase();
        String description = Objects.requireNonNullElse(p.getDescription(), "").toLowerCase();
        for (String w : words) {
            if (w.equals("men") || w.equals("women")) {
                if (nameWords.contains(w) || nameWords.contains(w.replace("men", "man"))) sc += 1.5;
            } else if (name.contains(w)) sc += 2;
            else if (tags.contains(w) || p.getCategory().toLowerCase().contains(w)) sc += 1.5;
            else if (description.contains(w)) sc += 0.5;
        }
        return sc + p.getRating() / 10.0;
    }

    static BigDecimal budget(String query) {
        Matcher m = BUDGET.matcher(query);
        if (!m.find()) return null;
        BigDecimal amount = new BigDecimal(m.group(1).replace(",", ""));
        if (m.group(2) == null) return amount;
        return amount.multiply(BigDecimal.valueOf(m.group(2).equalsIgnoreCase("k") ? 1_000 : 100_000));
    }

    private static Set<String> words(String query) {
        return Arrays.stream(query.toLowerCase().split("[^a-z0-9]+"))
                .filter(w -> w.length() > 1 && !STOP_WORDS.contains(w) && !w.chars().allMatch(Character::isDigit))
                .map(AssistantService::singular)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Rough singular form so "watches", "dresses" and "shoes" match "watch", "dress" and "shoe". */
    private static String singular(String w) {
        if (w.length() <= 3 || !w.endsWith("s") || w.endsWith("ss")) return w;
        if (w.endsWith("ches") || w.endsWith("shes") || w.endsWith("sses")) return w.substring(0, w.length() - 2);
        return w.substring(0, w.length() - 1);
    }

    private static String category(String word) {
        return SYNONYMS.get(word);
    }

    private static String reason(Product p, Set<String> words) {
        String name = p.getName().toLowerCase();
        return words.stream().filter(w -> !w.equals("men") && !w.equals("women")).anyMatch(name::contains)
                ? "Matches what you asked for"
                : "Great pick in " + p.getCategory();
    }

    private static String line(Product p) {
        return p.getId() + " | " + p.getName() + " | " + p.getCategory() + " | " + p.getPrice().toPlainString()
                + " | " + p.getRating() + " | " + Objects.requireNonNullElse(p.getTags(), "");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

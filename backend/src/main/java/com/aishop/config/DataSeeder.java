package com.aishop.config;

import com.aishop.entity.Product;
import com.aishop.entity.Role;
import com.aishop.entity.User;
import com.aishop.repository.ProductRepository;
import com.aishop.repository.UserRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/** Creates the admin account and a starter catalog on first run, so the store is usable immediately. */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final ProductRepository products;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public DataSeeder(UserRepository users, ProductRepository products, PasswordEncoder passwordEncoder,
                      AppProperties props) {
        this.users = users;
        this.products = products;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        var admin = props.admin();
        if (!users.existsByEmailIgnoreCase(admin.email())) {
            users.save(new User(admin.name(), admin.email().toLowerCase(), passwordEncoder.encode(admin.password()), Role.ADMIN));
            log.info("Created admin account {}", admin.email());
        }
        if (products.count() == 0) {
            String json = new ClassPathResource("seed/products.json").getContentAsString(StandardCharsets.UTF_8);
            JSONArray items = new JSONArray(json);
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                Product p = new Product();
                p.setName(o.getString("name"));
                p.setDescription(o.getString("description"));
                p.setCategory(o.getString("category"));
                p.setPrice(new BigDecimal(o.get("price").toString()));
                p.setRating(o.getDouble("rating"));
                p.setStock(o.getInt("stock"));
                p.setImageUrl(o.getString("imageUrl"));
                p.setTags(o.getString("tags"));
                products.save(p);
            }
            log.info("Seeded {} products", items.length());
        }
    }
}

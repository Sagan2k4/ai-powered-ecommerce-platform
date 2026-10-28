package com.aishop;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end API flow against an in-memory database with simulated Razorpay payments. */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.razorpay.mock=true",
        "app.openai.api-key="})
class BackendApplicationTests {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void catalogIsPublic() throws Exception {
        mvc.perform(get("/api/products").param("category", "Laptops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").value("Laptops"));
        mvc.perform(get("/api/products/categories")).andExpect(status().isOk());
    }

    @Test
    void assistantMatchesCategoryAndBudget() throws Exception {
        String body = mvc.perform(post("/api/assistant").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Show me watches under 20k\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("fallback"))
                .andReturn().getResponse().getContentAsString();
        var items = new JSONObject(body).getJSONArray("items");
        assertThat(items.length()).isPositive();
        for (int i = 0; i < items.length(); i++) {
            JSONObject product = items.getJSONObject(i).getJSONObject("product");
            assertThat(product.getString("category")).isEqualTo("Watches");
            assertThat(product.getDouble("price")).isLessThanOrEqualTo(20000);
        }
        mvc.perform(post("/api/assistant").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointsRequireAuthAndAdminRole() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());

        String customer = register();
        mvc.perform(get("/api/admin/stats").header("Authorization", customer)).andExpect(status().isForbidden());

        String admin = login("admin@aishop.dev", "Admin@12345");
        mvc.perform(get("/api/admin/stats").header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    void checkoutPaymentInvoiceAndRecommendations() throws Exception {
        String token = register();

        mvc.perform(post("/api/cart/items").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(2));

        String checkout = mvc.perform(post("/api/orders/checkout").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingName\":\"Test User\",\"shippingPhone\":\"9876543210\",\"shippingAddress\":\"1 Test St, Pune\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JSONObject co = new JSONObject(checkout);
        long orderId = co.getLong("orderId");
        assertThat(co.getBoolean("mockPayment")).isTrue();

        // A forged signature is rejected.
        mvc.perform(post("/api/payments/verify").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(orderId, co.getString("razorpayOrderId"), "forged")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/payments/verify").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(orderId, co.getString("razorpayOrderId"), "mock_signature")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/orders/" + orderId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.invoiceAvailable").value(true));

        byte[] pdf = mvc.perform(get("/api/orders/" + orderId + "/invoice").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        mvc.perform(get("/api/cart").header("Authorization", token)).andExpect(jsonPath("$.itemCount").value(0));

        // Another customer cannot see this order.
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", register())).andExpect(status().isNotFound());

        mvc.perform(get("/api/recommendations").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("fallback"))
                .andExpect(jsonPath("$.items.length()").value(8));
    }

    private String register() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + new JSONObject(body).getString("token");
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + new JSONObject(body).getString("token");
    }

    private static String verifyBody(long orderId, String rzpOrderId, String signature) {
        return new JSONObject().put("orderId", orderId).put("razorpayOrderId", rzpOrderId)
                .put("razorpayPaymentId", "pay_test123").put("razorpaySignature", signature).toString();
    }
}

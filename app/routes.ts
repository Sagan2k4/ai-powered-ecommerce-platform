import { type RouteConfig, index, route } from "@react-router/dev/routes";

export default [
  index("routes/home.tsx"),
  route("products", "routes/products.tsx"),
  route("products/:id", "routes/product.tsx"),
  route("cart", "routes/cart.tsx"),
  route("checkout", "routes/checkout.tsx"),
  route("orders", "routes/orders.tsx"),
  route("orders/:id", "routes/order.tsx"),
  route("login", "routes/login.tsx"),
  route("register", "routes/register.tsx"),
  route("admin", "routes/admin.tsx"),
] satisfies RouteConfig;

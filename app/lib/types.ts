export type Role = "CUSTOMER" | "ADMIN";

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
}

export interface AuthResponse {
  token: string;
  expiresInSeconds: number;
  user: User;
}

export interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string | null;
  tags: string | null;
  rating: number;
  stock: number;
  active: boolean;
}

export interface ProductPage {
  content: Product[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Recommendation {
  product: Product;
  reason: string;
}

export interface RecommendationResponse {
  items: Recommendation[];
  source: "openai" | "fallback" | "trending";
}

export interface CartItem {
  id: number;
  product: Product;
  quantity: number;
  lineTotal: number;
}

export interface Cart {
  items: CartItem[];
  itemCount: number;
  total: number;
}

export type OrderStatus = "PENDING_PAYMENT" | "PAID" | "SHIPPED" | "DELIVERED" | "CANCELLED" | "PAYMENT_FAILED";

export interface OrderItem {
  productId: number | null;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface Order {
  id: number;
  status: OrderStatus;
  totalAmount: number;
  shippingName: string;
  shippingPhone: string;
  shippingAddress: string;
  razorpayOrderId: string | null;
  razorpayPaymentId: string | null;
  createdAt: string;
  paidAt: string | null;
  invoiceAvailable: boolean;
  customerEmail: string;
  items: OrderItem[];
}

export interface CheckoutResponse {
  orderId: number;
  razorpayOrderId: string;
  amountInPaise: number;
  currency: string;
  razorpayKeyId: string;
  mockPayment: boolean;
  customerName: string;
  customerEmail: string;
  customerPhone: string;
}

export interface DashboardStats {
  products: number;
  users: number;
  paidOrders: number;
  pendingOrders: number;
  revenue: number;
}

export interface Paged<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface AssistantResponse {
  reply: string;
  items: Recommendation[];
  source: "openai" | "fallback";
}

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api } from "~/lib/api";
import type { Cart } from "~/lib/types";
import { useAuth } from "./auth";

const EMPTY: Cart = { items: [], itemCount: 0, total: 0 };

interface CartState {
  cart: Cart;
  loading: boolean;
  /** True once the cart for the current user has been fetched at least once. */
  loaded: boolean;
  refresh: () => Promise<void>;
  add: (productId: number, quantity?: number) => Promise<void>;
  setQuantity: (itemId: number, quantity: number) => Promise<void>;
  remove: (itemId: number) => Promise<void>;
}

const CartContext = createContext<CartState | null>(null);

/** Server-side cart: every mutation returns the full cart, which becomes the new state. */
export function CartProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [cart, setCart] = useState<Cart>(EMPTY);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);

  const refresh = useCallback(async () => {
    if (!user) {
      setCart(EMPTY);
      setLoaded(false);
      return;
    }
    setLoading(true);
    try {
      setCart(await api<Cart>("/cart"));
    } finally {
      setLoading(false);
      setLoaded(true);
    }
  }, [user]);

  useEffect(() => {
    refresh().catch(() => setCart(EMPTY));
  }, [refresh]);

  const value = useMemo<CartState>(
    () => ({
      cart,
      loading,
      loaded,
      refresh,
      add: async (productId, quantity = 1) =>
        setCart(await api<Cart>("/cart/items", { method: "POST", body: { productId, quantity } })),
      setQuantity: async (itemId, quantity) =>
        setCart(await api<Cart>(`/cart/items/${itemId}`, { method: "PATCH", body: { quantity } })),
      remove: async (itemId) => setCart(await api<Cart>(`/cart/items/${itemId}`, { method: "DELETE" })),
    }),
    [cart, loading, loaded, refresh],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error("useCart must be used inside CartProvider");
  return ctx;
}

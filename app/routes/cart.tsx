import { Minus, Plus, ShoppingBag, Trash2 } from "lucide-react";
import { Link } from "react-router";
import { ProductImage } from "~/components/ProductCard";
import { useToast } from "~/components/Toast";
import { EmptyState, PageTitle, RequireAuth, Spinner, primaryButton } from "~/components/ui";
import { useCart } from "~/context/cart";
import { errorMessage } from "~/lib/api";
import { formatPrice } from "~/lib/format";
import type { Route } from "./+types/cart";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Cart — Curato" }];
}

export default function CartPage() {
  return (
    <RequireAuth>
      <CartView />
    </RequireAuth>
  );
}

function CartView() {
  const { cart, loaded, setQuantity, remove } = useCart();
  const toast = useToast();

  const run = async (action: () => Promise<void>) => {
    try {
      await action();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  if (!loaded) return <Spinner />;
  if (cart.items.length === 0) {
    return (
      <EmptyState
        icon={<ShoppingBag className="size-6" />}
        title="Your cart is empty"
        text="Browse the store and add something you like."
        action={{ to: "/products", label: "Start shopping" }}
      />
    );
  }

  return (
    <div>
      <PageTitle subtitle={`${cart.itemCount} item${cart.itemCount === 1 ? "" : "s"}`}>Your cart</PageTitle>
      <div className="grid gap-8 lg:grid-cols-[1fr_340px]">
        <ul className="divide-y divide-slate-200 rounded-2xl border border-slate-200 bg-white">
          {cart.items.map((item) => (
            <li key={item.id} className="flex gap-4 p-4">
              <Link to={`/products/${item.product.id}`} className="size-24 shrink-0 overflow-hidden rounded-xl bg-slate-50">
                <ProductImage product={item.product} className="size-full p-2" />
              </Link>
              <div className="flex min-w-0 flex-1 flex-col">
                <div className="flex justify-between gap-4">
                  <Link to={`/products/${item.product.id}`} className="truncate font-medium hover:text-indigo-600">
                    {item.product.name}
                  </Link>
                  <span className="shrink-0 font-semibold">{formatPrice(item.lineTotal)}</span>
                </div>
                <span className="text-sm text-slate-500">{formatPrice(item.product.price)} each</span>
                <div className="mt-auto flex items-center justify-between pt-2">
                  <div className="flex items-center rounded-full border border-slate-200">
                    <button
                      disabled={item.quantity <= 1}
                      onClick={() => run(() => setQuantity(item.id, item.quantity - 1))}
                      className="p-2 text-slate-600 disabled:opacity-30"
                      aria-label="Decrease quantity"
                    >
                      <Minus className="size-3.5" />
                    </button>
                    <span className="w-7 text-center text-sm font-semibold">{item.quantity}</span>
                    <button
                      onClick={() => run(() => setQuantity(item.id, item.quantity + 1))}
                      className="p-2 text-slate-600"
                      aria-label="Increase quantity"
                    >
                      <Plus className="size-3.5" />
                    </button>
                  </div>
                  <button
                    onClick={() => run(() => remove(item.id))}
                    className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-rose-600"
                  >
                    <Trash2 className="size-4" /> Remove
                  </button>
                </div>
              </div>
            </li>
          ))}
        </ul>

        <aside className="h-fit rounded-2xl border border-slate-200 bg-white p-6">
          <h2 className="font-semibold">Order summary</h2>
          <dl className="mt-4 space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500">Subtotal</dt>
              <dd>{formatPrice(cart.total)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Shipping</dt>
              <dd className="text-emerald-600">Free</dd>
            </div>
            <div className="flex justify-between border-t border-slate-200 pt-3 text-base font-semibold">
              <dt>Total</dt>
              <dd>{formatPrice(cart.total)}</dd>
            </div>
          </dl>
          <Link to="/checkout" className={`${primaryButton} mt-6 w-full py-3`}>
            Proceed to checkout
          </Link>
        </aside>
      </div>
    </div>
  );
}

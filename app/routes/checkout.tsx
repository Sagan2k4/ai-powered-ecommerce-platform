import { Lock, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { Navigate, useNavigate } from "react-router";
import { ProductImage } from "~/components/ProductCard";
import { ErrorBox, PageTitle, RequireAuth, Spinner, inputClass, primaryButton } from "~/components/ui";
import { useAuth } from "~/context/auth";
import { useCart } from "~/context/cart";
import { api, errorMessage } from "~/lib/api";
import { formatPrice } from "~/lib/format";
import type { CheckoutResponse } from "~/lib/types";
import type { Route } from "./+types/checkout";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Checkout — Curato" }];
}

// Minimal typing for the Razorpay Checkout global loaded from checkout.razorpay.com.
type RazorpaySuccess = { razorpay_payment_id: string; razorpay_order_id: string; razorpay_signature: string };
type RazorpayInstance = { open: () => void; on: (event: string, cb: (res: unknown) => void) => void };
declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => RazorpayInstance;
  }
}

function loadRazorpay(): Promise<void> {
  if (window.Razorpay) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Could not load Razorpay. Check your connection."));
    document.body.appendChild(script);
  });
}

export default function CheckoutPage() {
  return (
    <RequireAuth>
      <Checkout />
    </RequireAuth>
  );
}

function Checkout() {
  const { user } = useAuth();
  const { cart, refresh, loaded } = useCart();
  const navigate = useNavigate();
  const [form, setForm] = useState({ shippingName: user?.name ?? "", shippingPhone: "", shippingAddress: "" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!loaded) return <Spinner />;
  if (cart.items.length === 0 && !busy) return <Navigate to="/cart" replace />;

  const verify = async (orderId: number, res: RazorpaySuccess) => {
    await api(`/payments/verify`, {
      method: "POST",
      body: {
        orderId,
        razorpayOrderId: res.razorpay_order_id,
        razorpayPaymentId: res.razorpay_payment_id,
        razorpaySignature: res.razorpay_signature,
      },
    });
    await refresh();
    navigate(`/orders/${orderId}?paid=1`, { replace: true });
  };

  const pay = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const order = await api<CheckoutResponse>("/orders/checkout", { method: "POST", body: form });

      if (order.mockPayment) {
        // Demo mode (no Razorpay keys configured): the backend accepts a simulated payment.
        await verify(order.orderId, {
          razorpay_order_id: order.razorpayOrderId,
          razorpay_payment_id: `pay_mock_${Date.now()}`,
          razorpay_signature: "mock_signature",
        });
        return;
      }

      await loadRazorpay();
      const rzp = new window.Razorpay!({
        key: order.razorpayKeyId,
        order_id: order.razorpayOrderId,
        amount: order.amountInPaise,
        currency: order.currency,
        name: "Curato",
        description: `Order #${order.orderId}`,
        prefill: { name: order.customerName, email: order.customerEmail, contact: order.customerPhone },
        theme: { color: "#4f46e5" },
        handler: (res: RazorpaySuccess) => {
          verify(order.orderId, res).catch((err) => {
            setError(errorMessage(err));
            setBusy(false);
          });
        },
        modal: {
          ondismiss: () => {
            setBusy(false);
            setError("Payment was cancelled. Your cart is saved, you can try again.");
          },
        },
      });
      rzp.on("payment.failed", () => {
        api("/payments/failed", { method: "POST", body: { orderId: order.orderId } }).catch(() => {});
      });
      rzp.open();
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  const field = (key: keyof typeof form) => ({
    value: form[key],
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setForm({ ...form, [key]: e.target.value }),
  });

  return (
    <div>
      <PageTitle>Checkout</PageTitle>
      <form onSubmit={pay} className="grid gap-8 lg:grid-cols-[1fr_380px]">
        <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6">
          <h2 className="font-semibold">Shipping details</h2>
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-slate-700">Full name</span>
            <input required maxLength={100} className={inputClass} {...field("shippingName")} />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-slate-700">Phone</span>
            <input
              required
              type="tel"
              pattern="[0-9+\- ]{7,20}"
              placeholder="9876543210"
              className={inputClass}
              {...field("shippingPhone")}
            />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-slate-700">Address</span>
            <textarea
              required
              rows={3}
              maxLength={500}
              placeholder="House no, street, city, state, PIN"
              className={inputClass}
              {...field("shippingAddress")}
            />
          </label>
          {error && <ErrorBox message={error} />}
        </section>

        <aside className="h-fit rounded-2xl border border-slate-200 bg-white p-6">
          <h2 className="font-semibold">Order summary</h2>
          <ul className="mt-4 space-y-3">
            {cart.items.map((item) => (
              <li key={item.id} className="flex items-center gap-3 text-sm">
                <div className="size-12 shrink-0 overflow-hidden rounded-lg bg-slate-50">
                  <ProductImage product={item.product} className="size-full p-1" />
                </div>
                <span className="flex-1 truncate">
                  {item.product.name} <span className="text-slate-400">× {item.quantity}</span>
                </span>
                <span className="font-medium">{formatPrice(item.lineTotal)}</span>
              </li>
            ))}
          </ul>
          <div className="mt-4 flex justify-between border-t border-slate-200 pt-4 text-base font-semibold">
            <span>Total</span>
            <span>{formatPrice(cart.total)}</span>
          </div>
          <button type="submit" disabled={busy} className={`${primaryButton} mt-6 w-full py-3`}>
            <Lock className="size-4" /> {busy ? "Processing…" : `Pay ${formatPrice(cart.total)}`}
          </button>
          <p className="mt-3 flex items-center justify-center gap-1.5 text-xs text-slate-500">
            <ShieldCheck className="size-3.5" /> Secured by Razorpay · UPI, cards, netbanking
          </p>
        </aside>
      </form>
    </div>
  );
}

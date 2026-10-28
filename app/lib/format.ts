import type { OrderStatus } from "./types";

const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });

export function formatPrice(amount: number): string {
  return inr.format(amount);
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" });
}

export const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING_PAYMENT: "Awaiting payment",
  PAID: "Paid",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
  PAYMENT_FAILED: "Payment failed",
};

export const STATUS_STYLE: Record<OrderStatus, string> = {
  PENDING_PAYMENT: "bg-amber-50 text-amber-700 ring-amber-200",
  PAID: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  SHIPPED: "bg-sky-50 text-sky-700 ring-sky-200",
  DELIVERED: "bg-indigo-50 text-indigo-700 ring-indigo-200",
  CANCELLED: "bg-slate-100 text-slate-600 ring-slate-200",
  PAYMENT_FAILED: "bg-rose-50 text-rose-700 ring-rose-200",
};

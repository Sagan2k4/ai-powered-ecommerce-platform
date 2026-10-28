import { ShoppingCart, Sparkles, Star } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "~/context/auth";
import { useCart } from "~/context/cart";
import { errorMessage } from "~/lib/api";
import { formatPrice } from "~/lib/format";
import type { Product } from "~/lib/types";
import { useToast } from "./Toast";

export function ProductImage({ product, className = "" }: { product: Product; className?: string }) {
  return product.imageUrl ? (
    <img src={product.imageUrl} alt={product.name} loading="lazy" className={`object-contain ${className}`} />
  ) : (
    <div className={`grid place-items-center bg-gradient-to-br from-indigo-100 to-sky-100 text-3xl font-bold text-indigo-400 ${className}`}>
      {product.name.slice(0, 2).toUpperCase()}
    </div>
  );
}

export function Rating({ value }: { value: number }) {
  return (
    <span className="inline-flex items-center gap-1 text-xs font-medium text-slate-600">
      <Star className="size-3.5 fill-amber-400 text-amber-400" />
      {value.toFixed(1)}
    </span>
  );
}

export function useAddToCart() {
  const { user } = useAuth();
  const { add } = useCart();
  const navigate = useNavigate();
  const toast = useToast();
  return async (product: Product, quantity = 1) => {
    if (!user) {
      navigate(`/login?next=${encodeURIComponent(`/products/${product.id}`)}`);
      return;
    }
    try {
      await add(product.id, quantity);
      toast(`Added ${product.name} to cart`);
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };
}

export function ProductCard({ product, reason }: { product: Product; reason?: string }) {
  const addToCart = useAddToCart();
  const [busy, setBusy] = useState(false);
  const soldOut = product.stock <= 0;

  return (
    <div className="group flex flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white transition hover:-translate-y-0.5 hover:shadow-lg hover:shadow-slate-200/60">
      <Link to={`/products/${product.id}`} className="relative block aspect-square bg-slate-50">
        <ProductImage product={product} className="size-full p-4 transition group-hover:scale-105" />
        <span className="absolute left-3 top-3 rounded-full bg-white/90 px-2 py-0.5 text-[11px] font-medium text-slate-600 ring-1 ring-slate-200">
          {product.category}
        </span>
      </Link>
      <div className="flex flex-1 flex-col gap-2 p-4">
        <Link to={`/products/${product.id}`} className="line-clamp-2 text-sm font-semibold text-slate-900 hover:text-indigo-600">
          {product.name}
        </Link>
        {reason && (
          <p className="flex items-start gap-1.5 rounded-lg bg-indigo-50 px-2 py-1.5 text-xs text-indigo-700">
            <Sparkles className="mt-0.5 size-3 shrink-0" />
            <span className="line-clamp-2">{reason}</span>
          </p>
        )}
        <div className="mt-auto flex items-center justify-between pt-1">
          <div>
            <div className="text-base font-bold text-slate-900">{formatPrice(product.price)}</div>
            <Rating value={product.rating} />
          </div>
          <button
            disabled={busy || soldOut}
            onClick={async () => {
              setBusy(true);
              await addToCart(product);
              setBusy(false);
            }}
            className="grid size-9 place-items-center rounded-full bg-slate-900 text-white transition hover:bg-indigo-600 disabled:cursor-not-allowed disabled:bg-slate-300"
            aria-label={soldOut ? "Sold out" : `Add ${product.name} to cart`}
            title={soldOut ? "Sold out" : "Add to cart"}
          >
            <ShoppingCart className="size-4" />
          </button>
        </div>
      </div>
    </div>
  );
}

export function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="animate-pulse overflow-hidden rounded-2xl border border-slate-200 bg-white">
          <div className="aspect-square bg-slate-100" />
          <div className="space-y-2 p-4">
            <div className="h-4 w-3/4 rounded bg-slate-100" />
            <div className="h-4 w-1/3 rounded bg-slate-100" />
          </div>
        </div>
      ))}
    </div>
  );
}

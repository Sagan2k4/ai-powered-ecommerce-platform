import { ChevronLeft, Minus, Plus, ShieldCheck, Sparkles, Truck } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { ProductCard, ProductGridSkeleton, ProductImage, Rating, useAddToCart } from "~/components/ProductCard";
import { ErrorBox, Spinner, primaryButton } from "~/components/ui";
import { api, errorMessage } from "~/lib/api";
import { formatPrice } from "~/lib/format";
import type { Product, RecommendationResponse } from "~/lib/types";
import type { Route } from "./+types/product";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Product — Curato" }];
}

export default function ProductPage() {
  const { id } = useParams();
  const addToCart = useAddToCart();
  const [product, setProduct] = useState<Product | null>(null);
  const [similar, setSimilar] = useState<RecommendationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setProduct(null);
    setSimilar(null);
    setError(null);
    setQuantity(1);
    api<Product>(`/products/${id}`).then(setProduct).catch((e) => setError(errorMessage(e)));
    api<RecommendationResponse>(`/products/${id}/similar`).then(setSimilar).catch(() => {});
  }, [id]);

  if (error) return <ErrorBox message={error} />;
  if (!product) return <Spinner />;

  const soldOut = product.stock <= 0;
  const maxQty = Math.min(product.stock, 20);

  return (
    <div className="space-y-14">
      <div>
        <Link to="/products" className="mb-6 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-900">
          <ChevronLeft className="size-4" /> Back to shop
        </Link>
        <div className="grid gap-10 lg:grid-cols-2">
          <div className="aspect-square overflow-hidden rounded-3xl border border-slate-200 bg-white">
            <ProductImage product={product} className="size-full p-8" />
          </div>
          <div className="flex flex-col">
            <Link
              to={`/products?category=${encodeURIComponent(product.category)}`}
              className="text-sm font-medium text-indigo-600 hover:underline"
            >
              {product.category}
            </Link>
            <h1 className="mt-2 text-3xl font-bold tracking-tight">{product.name}</h1>
            <div className="mt-3">
              <Rating value={product.rating} />
            </div>
            <div className="mt-6 text-3xl font-bold">{formatPrice(product.price)}</div>
            <p className="text-xs text-slate-500">Inclusive of all taxes</p>
            <p className="mt-6 leading-relaxed text-slate-600">{product.description}</p>

            <div className="mt-6 text-sm">
              {soldOut ? (
                <span className="font-medium text-rose-600">Out of stock</span>
              ) : product.stock < 10 ? (
                <span className="font-medium text-amber-600">Only {product.stock} left</span>
              ) : (
                <span className="font-medium text-emerald-600">In stock</span>
              )}
            </div>

            <div className="mt-6 flex items-center gap-3">
              <div className="flex items-center rounded-full border border-slate-200 bg-white">
                <button
                  onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                  className="p-3 text-slate-600 hover:text-slate-900"
                  aria-label="Decrease quantity"
                >
                  <Minus className="size-4" />
                </button>
                <span className="w-8 text-center text-sm font-semibold">{quantity}</span>
                <button
                  onClick={() => setQuantity((q) => Math.min(maxQty, q + 1))}
                  className="p-3 text-slate-600 hover:text-slate-900"
                  aria-label="Increase quantity"
                >
                  <Plus className="size-4" />
                </button>
              </div>
              <button
                disabled={soldOut || busy}
                onClick={async () => {
                  setBusy(true);
                  await addToCart(product, quantity);
                  setBusy(false);
                }}
                className={`${primaryButton} flex-1 py-3`}
              >
                {soldOut ? "Sold out" : "Add to cart"}
              </button>
            </div>

            <div className="mt-8 grid gap-3 rounded-2xl bg-white p-4 text-sm text-slate-600 ring-1 ring-slate-200">
              <div className="flex items-center gap-2">
                <Truck className="size-4 text-indigo-600" /> Free delivery across India
              </div>
              <div className="flex items-center gap-2">
                <ShieldCheck className="size-4 text-indigo-600" /> Secure payments via Razorpay
              </div>
            </div>
          </div>
        </div>
      </div>

      <section>
        <h2 className="mb-1 flex items-center gap-2 text-xl font-bold">
          <Sparkles className="size-5 text-indigo-600" /> You might also like
        </h2>
        <p className="mb-5 text-sm text-slate-500">
          {similar?.source === "openai" ? "Picked by OpenAI to go with this product" : "Similar and complementary products"}
        </p>
        {similar ? (
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
            {similar.items.slice(0, 4).map((r) => (
              <ProductCard key={r.product.id} product={r.product} reason={r.reason} />
            ))}
          </div>
        ) : (
          <ProductGridSkeleton count={4} />
        )}
      </section>
    </div>
  );
}

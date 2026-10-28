import { ArrowRight, Sparkles, Star } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { ProductCard, ProductGridSkeleton, ProductImage } from "~/components/ProductCard";
import { VoiceAssistant } from "~/components/VoiceAssistant";
import { useAuth } from "~/context/auth";
import { api } from "~/lib/api";
import { formatPrice } from "~/lib/format";
import type { Product, ProductPage, RecommendationResponse } from "~/lib/types";
import type { Route } from "./+types/home";

export function meta({}: Route.MetaArgs) {
  return [
    { title: "Curato — Smart shopping, curated for you" },
    { name: "description", content: "Curato is an AI-powered store that curates products for every shopper." },
  ];
}

const SOURCE_LABEL: Record<RecommendationResponse["source"], string> = {
  openai: "Ranked for you by OpenAI from your orders and cart",
  fallback: "Based on your orders and cart",
  trending: "Top rated right now. Shop a little and these become personal.",
};

/** Categories shown first in the hero showcase, so it reads like a storefront rather than one aisle. */
const SHOWCASE_ORDER = ["Smartphones", "Footwear", "Watches", "Accessories", "Laptops", "Fashion"];
const SHOWCASE_SIZE = 3;

/** Best-rated product from each category, in showcase order. */
function pickShowcase(products: Product[]): Product[] {
  const best = new Map<string, Product>();
  for (const p of products) if (p.imageUrl && !best.has(p.category)) best.set(p.category, p);
  const ordered = [
    ...SHOWCASE_ORDER.flatMap((c) => best.get(c) ?? []),
    ...[...best.values()].filter((p) => !SHOWCASE_ORDER.includes(p.category)),
  ];
  return ordered.slice(0, SHOWCASE_SIZE);
}

function HeroShowcase({ products }: { products: Product[] | null }) {
  return (
    <div className="mt-10">
      <div className="mb-3 text-xs font-semibold uppercase tracking-wider text-indigo-100">Trending now</div>
      <div className="grid grid-cols-3 gap-3">
        {products
          ? products.map((p) => (
              <Link
                key={p.id}
                to={`/products/${p.id}`}
                className="group rounded-2xl bg-white p-2 text-slate-900 shadow-lg shadow-indigo-900/20 transition hover:-translate-y-1"
              >
                <div className="aspect-[4/3] overflow-hidden rounded-xl bg-slate-50">
                  <ProductImage product={p} className="size-full p-2 transition group-hover:scale-105" />
                </div>
                <div className="px-1 pb-0.5 pt-2">
                  <div className="truncate text-xs font-semibold">{p.name}</div>
                  <div className="mt-0.5 flex items-center justify-between gap-1">
                    <span className="truncate text-xs font-bold text-indigo-700 sm:text-sm">{formatPrice(p.price)}</span>
                    <span className="hidden items-center gap-0.5 text-[11px] text-slate-500 sm:inline-flex">
                      <Star className="size-3 fill-amber-400 text-amber-400" />
                      {p.rating.toFixed(1)}
                    </span>
                  </div>
                </div>
              </Link>
            ))
          : Array.from({ length: SHOWCASE_SIZE }, (_, i) => (
              <div key={i} className="rounded-2xl bg-white/15 p-2">
                <div className="aspect-[4/3] animate-pulse rounded-xl bg-white/20" />
                <div className="mt-2 h-4 animate-pulse rounded bg-white/20" />
                <div className="mt-1.5 h-4 w-1/2 animate-pulse rounded bg-white/20" />
              </div>
            ))}
      </div>
    </div>
  );
}

export default function Home() {
  const { user, ready } = useAuth();
  const [recs, setRecs] = useState<RecommendationResponse | null>(null);
  const [featured, setFeatured] = useState<ProductPage | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [showcase, setShowcase] = useState<Product[] | null>(null);

  useEffect(() => {
    api<ProductPage>("/products?sort=rating&size=48")
      .then((page) => setShowcase(pickShowcase(page.content)))
      .catch(() => setShowcase([]));
    api<ProductPage>("/products?sort=rating&size=8").then(setFeatured).catch(() => {});
    api<string[]>("/products/categories").then(setCategories).catch(() => {});
  }, []);

  useEffect(() => {
    if (!ready || !user) return;
    setRecs(null);
    api<RecommendationResponse>("/recommendations").then(setRecs).catch(() => {});
  }, [ready, user]);

  return (
    <div className="space-y-14">
      <section className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-indigo-600 via-indigo-500 to-sky-500 px-6 py-12 text-white sm:px-12">
        <div className="absolute -right-20 -top-20 size-72 rounded-full bg-white/10 blur-2xl" />
        <div className="relative grid items-center gap-10 lg:grid-cols-[1fr_minmax(0,26rem)]">
          <div className="max-w-xl">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-xs font-medium ring-1 ring-white/25">
              <Sparkles className="size-3.5" /> AI-personalized shopping
            </span>
            <h1 className="mt-4 text-4xl font-bold tracking-tight sm:text-5xl">Find what you'll love, faster.</h1>
            <p className="mt-4 text-indigo-50">
              Recommendations that learn from what you buy, secure checkout with Razorpay, and an instant PDF invoice
              for every order.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link
                to="/products"
                className="inline-flex items-center gap-2 rounded-full bg-white px-5 py-2.5 text-sm font-semibold text-indigo-700 hover:bg-indigo-50"
              >
                Start shopping <ArrowRight className="size-4" />
              </Link>
              {ready && !user && (
                <Link to="/register" className="rounded-full px-5 py-2.5 text-sm font-semibold ring-1 ring-white/50 hover:bg-white/10">
                  Create account
                </Link>
              )}
            </div>
            {showcase?.length !== 0 && <HeroShowcase products={showcase} />}
          </div>
          <VoiceAssistant />
        </div>
      </section>

      {user && (
        <section>
          <div className="mb-5 flex items-end justify-between gap-4">
            <div>
              <h2 className="flex items-center gap-2 text-xl font-bold">
                <Sparkles className="size-5 text-indigo-600" /> Recommended for you
              </h2>
              {recs && <p className="mt-1 text-sm text-slate-500">{SOURCE_LABEL[recs.source]}</p>}
            </div>
          </div>
          {recs ? (
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
              {recs.items.map((r) => (
                <ProductCard key={r.product.id} product={r.product} reason={r.reason} />
              ))}
            </div>
          ) : (
            <ProductGridSkeleton count={4} />
          )}
        </section>
      )}

      {categories.length > 0 && (
        <section>
          <h2 className="mb-4 text-xl font-bold">Shop by category</h2>
          <div className="flex flex-wrap gap-2">
            {categories.map((c) => (
              <Link
                key={c}
                to={`/products?category=${encodeURIComponent(c)}`}
                className="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:border-indigo-300 hover:text-indigo-700"
              >
                {c}
              </Link>
            ))}
          </div>
        </section>
      )}

      <section>
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-xl font-bold">Top rated</h2>
          <Link to="/products?sort=rating" className="text-sm font-medium text-indigo-600 hover:underline">
            View all
          </Link>
        </div>
        {featured ? (
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
            {featured.content.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        ) : (
          <ProductGridSkeleton />
        )}
      </section>
    </div>
  );
}

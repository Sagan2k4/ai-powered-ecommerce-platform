import { PackageSearch } from "lucide-react";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { ProductCard, ProductGridSkeleton } from "~/components/ProductCard";
import { EmptyState, ErrorBox } from "~/components/ui";
import { api, errorMessage } from "~/lib/api";
import type { ProductPage } from "~/lib/types";
import type { Route } from "./+types/products";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Shop — Curato" }];
}

const SORTS = [
  { value: "newest", label: "Newest" },
  { value: "rating", label: "Top rated" },
  { value: "price_asc", label: "Price: low to high" },
  { value: "price_desc", label: "Price: high to low" },
];

export default function Products() {
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const category = params.get("category") ?? "";
  const sort = params.get("sort") ?? "newest";
  const page = Number(params.get("page") ?? 0);

  const [data, setData] = useState<ProductPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [categories, setCategories] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<string[]>("/products/categories").then(setCategories).catch(() => {});
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const search = new URLSearchParams({ sort, page: String(page), size: "12" });
    if (q) search.set("q", q);
    if (category) search.set("category", category);
    // Keep the current results on screen while the next page loads, so the layout doesn't jump.
    setLoading(true);
    setError(null);
    api<ProductPage>(`/products?${search}`, { signal: controller.signal })
      .then((page) => {
        setData(page);
        setLoading(false);
      })
      .catch((e) => {
        if (!controller.signal.aborted) {
          setError(errorMessage(e));
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [q, category, sort, page]);

  const update = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (!("page" in changes)) next.delete("page");
    setParams(next);
  };

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">{category || (q ? `Results for "${q}"` : "All products")}</h1>
          <p className="mt-1 h-5 text-sm text-slate-500">{data && !loading ? `${data.totalElements} products` : ""}</p>
        </div>
        <select
          value={sort}
          onChange={(e) => update({ sort: e.target.value })}
          className="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm outline-none focus:border-indigo-400"
          aria-label="Sort products"
        >
          {SORTS.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
      </div>

      <div className="mb-6 flex gap-2 overflow-x-auto pb-1">
        {["", ...categories].map((c) => (
          <button
            key={c || "all"}
            onClick={() => update({ category: c || null })}
            aria-pressed={c === category}
            className={`shrink-0 rounded-full border px-4 py-1.5 text-sm font-medium transition-colors ${
              c === category
                ? "border-slate-900 bg-slate-900 text-white"
                : "border-slate-200 bg-white text-slate-700 hover:border-slate-300"
            }`}
          >
            {c || "All"}
          </button>
        ))}
      </div>

      {error && <ErrorBox message={error} />}
      {!data && !error && <ProductGridSkeleton count={12} />}
      <div className={`transition-opacity duration-150 ${loading && data ? "pointer-events-none opacity-50" : ""}`}>
        {data && data.content.length === 0 && (
          <EmptyState
            icon={<PackageSearch className="size-6" />}
            title="No products found"
            text="Try a different search or category."
            action={{ to: "/products", label: "Clear filters" }}
          />
        )}
        {data && data.content.length > 0 && (
          <>
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
              {data.content.map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
            {data.totalPages > 1 && (
              <div className="mt-8 flex items-center justify-center gap-3 text-sm">
                <button
                  disabled={page === 0}
                  onClick={() => update({ page: String(page - 1) })}
                  className="rounded-full border border-slate-200 bg-white px-4 py-2 disabled:opacity-40"
                >
                  Previous
                </button>
                <span className="text-slate-500">
                  Page {page + 1} of {data.totalPages}
                </span>
                <button
                  disabled={page + 1 >= data.totalPages}
                  onClick={() => update({ page: String(page + 1) })}
                  className="rounded-full border border-slate-200 bg-white px-4 py-2 disabled:opacity-40"
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

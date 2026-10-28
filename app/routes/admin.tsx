import { IndianRupee, Package, Pencil, Plus, ShoppingCart, Trash2, Users, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ProductImage } from "~/components/ProductCard";
import { useToast } from "~/components/Toast";
import { ErrorBox, PageTitle, RequireAuth, Spinner, StatusBadge, inputClass, primaryButton } from "~/components/ui";
import { api, downloadFile, errorMessage } from "~/lib/api";
import { formatDate, formatPrice, STATUS_LABEL } from "~/lib/format";
import type { DashboardStats, Order, OrderStatus, Paged, Product, ProductPage } from "~/lib/types";
import type { Route } from "./+types/admin";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Admin — Curato" }];
}

type Tab = "products" | "orders";

export default function AdminPage() {
  return (
    <RequireAuth role="ADMIN">
      <Admin />
    </RequireAuth>
  );
}

function Admin() {
  const [tab, setTab] = useState<Tab>("orders");
  const [stats, setStats] = useState<DashboardStats | null>(null);

  const loadStats = useCallback(() => {
    api<DashboardStats>("/admin/stats").then(setStats).catch(() => {});
  }, []);
  useEffect(loadStats, [loadStats]);

  return (
    <div>
      <PageTitle subtitle="Manage the catalog and fulfil orders">Admin dashboard</PageTitle>

      <div className="mb-8 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat icon={IndianRupee} label="Revenue" value={stats ? formatPrice(stats.revenue) : "—"} />
        <Stat icon={ShoppingCart} label="Paid orders" value={stats ? `${stats.paidOrders}` : "—"} hint={stats ? `${stats.pendingOrders} awaiting payment` : undefined} />
        <Stat icon={Package} label="Products" value={stats ? `${stats.products}` : "—"} />
        <Stat icon={Users} label="Users" value={stats ? `${stats.users}` : "—"} />
      </div>

      <div className="mb-6 inline-flex rounded-full bg-slate-200/70 p-1">
        {(["orders", "products"] as Tab[]).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`rounded-full px-5 py-1.5 text-sm font-medium capitalize transition ${
              tab === t ? "bg-white text-slate-900 shadow-sm" : "text-slate-600 hover:text-slate-900"
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === "orders" ? <OrdersAdmin onChange={loadStats} /> : <ProductsAdmin onChange={loadStats} />}
    </div>
  );
}

function Stat({ icon: Icon, label, value, hint }: { icon: typeof Package; label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5">
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <Icon className="size-4" /> {label}
      </div>
      <div className="mt-2 text-2xl font-bold">{value}</div>
      {hint && <div className="mt-1 text-xs text-slate-500">{hint}</div>}
    </div>
  );
}

const STATUSES = Object.keys(STATUS_LABEL) as OrderStatus[];

function OrdersAdmin({ onChange }: { onChange: () => void }) {
  const toast = useToast();
  const [data, setData] = useState<Paged<Order> | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Paged<Order>>(`/admin/orders?page=${page}&size=15`).then(setData).catch((e) => setError(errorMessage(e)));
  }, [page]);

  const updateStatus = async (order: Order, status: OrderStatus) => {
    try {
      const updated = await api<Order>(`/admin/orders/${order.id}/status`, { method: "PATCH", body: { status } });
      setData((d) => d && { ...d, content: d.content.map((o) => (o.id === updated.id ? updated : o)) });
      toast(`Order #${order.id} marked ${STATUS_LABEL[status]}`);
      onChange();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  if (error) return <ErrorBox message={error} />;
  if (!data) return <Spinner />;
  if (data.content.length === 0) return <p className="text-sm text-slate-500">No orders yet.</p>;

  return (
    <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
      <table className="w-full min-w-[720px] text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-3">Order</th>
            <th className="px-4 py-3">Customer</th>
            <th className="px-4 py-3">Placed</th>
            <th className="px-4 py-3 text-right">Total</th>
            <th className="px-4 py-3">Status</th>
            <th className="px-4 py-3">Invoice</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {data.content.map((o) => (
            <tr key={o.id}>
              <td className="px-4 py-3 font-medium">#{o.id}</td>
              <td className="px-4 py-3">
                <div>{o.shippingName}</div>
                <div className="text-xs text-slate-500">{o.customerEmail}</div>
              </td>
              <td className="px-4 py-3 text-slate-600">{formatDate(o.createdAt)}</td>
              <td className="px-4 py-3 text-right font-medium">{formatPrice(o.totalAmount)}</td>
              <td className="px-4 py-3">
                <div className="flex items-center gap-2">
                  <StatusBadge status={o.status} />
                  <select
                    value={o.status}
                    onChange={(e) => updateStatus(o, e.target.value as OrderStatus)}
                    className="rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs"
                    aria-label={`Change status of order ${o.id}`}
                  >
                    {STATUSES.map((s) => (
                      <option key={s} value={s}>
                        {STATUS_LABEL[s]}
                      </option>
                    ))}
                  </select>
                </div>
              </td>
              <td className="px-4 py-3">
                {o.invoiceAvailable ? (
                  <button
                    onClick={() => downloadFile(`/orders/${o.id}/invoice`, `invoice-${o.id}.pdf`).catch((e) => toast(errorMessage(e), "error"))}
                    className="text-indigo-600 hover:underline"
                  >
                    Download
                  </button>
                ) : (
                  <span className="text-slate-400">—</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {data.page.totalPages > 1 && (
        <div className="flex items-center justify-end gap-3 border-t border-slate-200 p-3 text-sm">
          <button disabled={page === 0} onClick={() => setPage(page - 1)} className="rounded-full border px-3 py-1 disabled:opacity-40">
            Previous
          </button>
          <span className="text-slate-500">
            {page + 1} / {data.page.totalPages}
          </span>
          <button disabled={page + 1 >= data.page.totalPages} onClick={() => setPage(page + 1)} className="rounded-full border px-3 py-1 disabled:opacity-40">
            Next
          </button>
        </div>
      )}
    </div>
  );
}

type ProductForm = {
  name: string;
  description: string;
  price: string;
  category: string;
  imageUrl: string;
  tags: string;
  rating: string;
  stock: string;
};

const EMPTY_FORM: ProductForm = { name: "", description: "", price: "", category: "", imageUrl: "", tags: "", rating: "4.5", stock: "10" };

function ProductsAdmin({ onChange }: { onChange: () => void }) {
  const toast = useToast();
  const [data, setData] = useState<ProductPage | null>(null);
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<{ id: number | null; form: ProductForm } | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const params = new URLSearchParams({ page: String(page), size: "15", sort: "newest" });
    if (q) params.set("q", q);
    api<ProductPage>(`/products?${params}`).then(setData).catch(() => {});
  }, [q, page, reload]);

  const remove = async (p: Product) => {
    if (!window.confirm(`Remove "${p.name}" from the store?`)) return;
    try {
      await api(`/admin/products/${p.id}`, { method: "DELETE" });
      toast("Product removed");
      setReload((r) => r + 1);
      onChange();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const edit = (p: Product) =>
    setEditing({
      id: p.id,
      form: {
        name: p.name,
        description: p.description ?? "",
        price: String(p.price),
        category: p.category,
        imageUrl: p.imageUrl ?? "",
        tags: p.tags ?? "",
        rating: String(p.rating),
        stock: String(p.stock),
      },
    });

  return (
    <div>
      <div className="mb-4 flex flex-wrap gap-3">
        <input
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
          placeholder="Search products"
          className={`${inputClass} max-w-xs`}
        />
        <button onClick={() => setEditing({ id: null, form: EMPTY_FORM })} className={`${primaryButton} ml-auto`}>
          <Plus className="size-4" /> Add product
        </button>
      </div>

      {!data ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Product</th>
                <th className="px-4 py-3">Category</th>
                <th className="px-4 py-3 text-right">Price</th>
                <th className="px-4 py-3 text-right">Stock</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((p) => (
                <tr key={p.id}>
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-3">
                      <div className="size-10 shrink-0 overflow-hidden rounded-lg bg-slate-50">
                        <ProductImage product={p} className="size-full p-1" />
                      </div>
                      <span className="font-medium">{p.name}</span>
                    </div>
                  </td>
                  <td className="px-4 py-2 text-slate-600">{p.category}</td>
                  <td className="px-4 py-2 text-right">{formatPrice(p.price)}</td>
                  <td className={`px-4 py-2 text-right ${p.stock < 10 ? "font-medium text-amber-600" : ""}`}>{p.stock}</td>
                  <td className="px-4 py-2">
                    <div className="flex justify-end gap-1">
                      <button onClick={() => edit(p)} className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-900" aria-label={`Edit ${p.name}`}>
                        <Pencil className="size-4" />
                      </button>
                      <button onClick={() => remove(p)} className="rounded-lg p-2 text-slate-500 hover:bg-rose-50 hover:text-rose-600" aria-label={`Remove ${p.name}`}>
                        <Trash2 className="size-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {data.totalPages > 1 && (
            <div className="flex items-center justify-end gap-3 border-t border-slate-200 p-3 text-sm">
              <button disabled={page === 0} onClick={() => setPage(page - 1)} className="rounded-full border px-3 py-1 disabled:opacity-40">
                Previous
              </button>
              <span className="text-slate-500">
                {page + 1} / {data.totalPages}
              </span>
              <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)} className="rounded-full border px-3 py-1 disabled:opacity-40">
                Next
              </button>
            </div>
          )}
        </div>
      )}

      {editing && (
        <ProductDialog
          id={editing.id}
          initial={editing.form}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            setReload((r) => r + 1);
            onChange();
          }}
        />
      )}
    </div>
  );
}

function ProductDialog({ id, initial, onClose, onSaved }: { id: number | null; initial: ProductForm; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  const [form, setForm] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const body = {
      ...form,
      price: Number(form.price),
      rating: Number(form.rating),
      stock: Number(form.stock),
      imageUrl: form.imageUrl || null,
    };
    try {
      await api(id ? `/admin/products/${id}` : "/admin/products", { method: id ? "PUT" : "POST", body });
      toast(id ? "Product updated" : "Product created");
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  const field = (key: keyof ProductForm) => ({
    value: form[key],
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setForm({ ...form, [key]: e.target.value }),
  });

  return (
    <div className="fixed inset-0 z-40 grid place-items-center bg-slate-900/40 p-4" role="dialog" aria-modal="true">
      <form onSubmit={save} className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-3xl bg-white p-6 shadow-2xl">
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-lg font-semibold">{id ? "Edit product" : "New product"}</h2>
          <button type="button" onClick={onClose} className="rounded-full p-1.5 text-slate-500 hover:bg-slate-100" aria-label="Close">
            <X className="size-5" />
          </button>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="text-sm sm:col-span-2">
            Name
            <input required maxLength={200} className={`${inputClass} mt-1`} {...field("name")} />
          </label>
          <label className="text-sm sm:col-span-2">
            Description
            <textarea rows={3} maxLength={2000} className={`${inputClass} mt-1`} {...field("description")} />
          </label>
          <label className="text-sm">
            Price (INR)
            <input required type="number" min={1} step="0.01" className={`${inputClass} mt-1`} {...field("price")} />
          </label>
          <label className="text-sm">
            Category
            <input required maxLength={60} className={`${inputClass} mt-1`} {...field("category")} />
          </label>
          <label className="text-sm">
            Stock
            <input required type="number" min={0} className={`${inputClass} mt-1`} {...field("stock")} />
          </label>
          <label className="text-sm">
            Rating
            <input required type="number" min={0} max={5} step="0.1" className={`${inputClass} mt-1`} {...field("rating")} />
          </label>
          <label className="text-sm sm:col-span-2">
            Image URL
            <input type="url" maxLength={500} className={`${inputClass} mt-1`} {...field("imageUrl")} />
          </label>
          <label className="text-sm sm:col-span-2">
            Tags (comma-separated, used by recommendations)
            <input maxLength={300} className={`${inputClass} mt-1`} {...field("tags")} />
          </label>
        </div>
        {error && (
          <div className="mt-4">
            <ErrorBox message={error} />
          </div>
        )}
        <div className="mt-6 flex justify-end gap-3">
          <button type="button" onClick={onClose} className="rounded-full px-5 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-100">
            Cancel
          </button>
          <button disabled={busy} className={primaryButton}>
            {busy ? "Saving…" : "Save"}
          </button>
        </div>
      </form>
    </div>
  );
}

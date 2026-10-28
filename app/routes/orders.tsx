import { ChevronRight, Package } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { EmptyState, ErrorBox, PageTitle, RequireAuth, Spinner, StatusBadge } from "~/components/ui";
import { api, errorMessage } from "~/lib/api";
import { formatDate, formatPrice } from "~/lib/format";
import type { Order } from "~/lib/types";
import type { Route } from "./+types/orders";

export function meta({}: Route.MetaArgs) {
  return [{ title: "My orders — Curato" }];
}

export default function OrdersPage() {
  return (
    <RequireAuth>
      <Orders />
    </RequireAuth>
  );
}

function Orders() {
  const [orders, setOrders] = useState<Order[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Order[]>("/orders").then(setOrders).catch((e) => setError(errorMessage(e)));
  }, []);

  if (error) return <ErrorBox message={error} />;
  if (!orders) return <Spinner />;
  if (orders.length === 0) {
    return (
      <EmptyState
        icon={<Package className="size-6" />}
        title="No orders yet"
        text="When you place an order it will show up here with its invoice."
        action={{ to: "/products", label: "Browse products" }}
      />
    );
  }

  return (
    <div>
      <PageTitle subtitle="Track orders and download invoices">My orders</PageTitle>
      <ul className="space-y-3">
        {orders.map((o) => (
          <li key={o.id}>
            <Link
              to={`/orders/${o.id}`}
              className="flex items-center gap-4 rounded-2xl border border-slate-200 bg-white p-5 transition hover:border-indigo-200 hover:shadow-sm"
            >
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-3">
                  <span className="font-semibold">Order #{o.id}</span>
                  <StatusBadge status={o.status} />
                </div>
                <p className="mt-1 truncate text-sm text-slate-500">
                  {formatDate(o.createdAt)} · {o.items.map((i) => i.productName).join(", ")}
                </p>
              </div>
              <span className="font-semibold">{formatPrice(o.totalAmount)}</span>
              <ChevronRight className="size-4 text-slate-400" />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

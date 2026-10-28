import { CheckCircle2, ChevronLeft, Download, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router";
import { useToast } from "~/components/Toast";
import { ErrorBox, RequireAuth, Spinner, StatusBadge, primaryButton } from "~/components/ui";
import { api, downloadFile, errorMessage } from "~/lib/api";
import { formatDate, formatPrice } from "~/lib/format";
import type { Order } from "~/lib/types";
import type { Route } from "./+types/order";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Order details — Curato" }];
}

export default function OrderPage() {
  return (
    <RequireAuth>
      <OrderDetails />
    </RequireAuth>
  );
}

function OrderDetails() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const toast = useToast();
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    let attempts = 0;
    let timer: ReturnType<typeof setTimeout>;
    const load = () =>
      api<Order>(`/orders/${id}`)
        .then((o) => {
          setOrder(o);
          // The invoice is generated right after payment commits; poll briefly if it is not there yet.
          if (o.status === "PAID" && !o.invoiceAvailable && attempts++ < 5) timer = setTimeout(load, 1000);
        })
        .catch((e) => setError(errorMessage(e)));
    load();
    return () => clearTimeout(timer);
  }, [id]);

  if (error) return <ErrorBox message={error} />;
  if (!order) return <Spinner />;

  const download = async () => {
    setDownloading(true);
    try {
      await downloadFile(`/orders/${order.id}/invoice`, `invoice-${order.id}.pdf`);
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl">
      <Link to="/orders" className="mb-6 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-900">
        <ChevronLeft className="size-4" /> All orders
      </Link>

      {params.get("paid") && order.status === "PAID" && (
        <div className="mb-6 flex items-start gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 p-5">
          <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-emerald-600" />
          <div>
            <p className="font-semibold text-emerald-900">Payment successful. Thank you for your order!</p>
            <p className="text-sm text-emerald-800">Your invoice has been generated and is ready to download.</p>
          </div>
        </div>
      )}

      <div className="rounded-2xl border border-slate-200 bg-white">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 p-6">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold">Order #{order.id}</h1>
              <StatusBadge status={order.status} />
            </div>
            <p className="mt-1 text-sm text-slate-500">Placed {formatDate(order.createdAt)}</p>
          </div>
          {order.invoiceAvailable && (
            <button onClick={download} disabled={downloading} className={primaryButton}>
              {downloading ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
              Invoice PDF
            </button>
          )}
        </div>

        <ul className="divide-y divide-slate-100 px-6">
          {order.items.map((item, i) => (
            <li key={i} className="flex justify-between gap-4 py-4 text-sm">
              <div>
                {item.productId ? (
                  <Link to={`/products/${item.productId}`} className="font-medium hover:text-indigo-600">
                    {item.productName}
                  </Link>
                ) : (
                  <span className="font-medium">{item.productName}</span>
                )}
                <div className="text-slate-500">
                  {item.quantity} × {formatPrice(item.unitPrice)}
                </div>
              </div>
              <span className="font-medium">{formatPrice(item.lineTotal)}</span>
            </li>
          ))}
        </ul>

        <div className="flex justify-between border-t border-slate-200 px-6 py-4 font-semibold">
          <span>Total</span>
          <span>{formatPrice(order.totalAmount)}</span>
        </div>

        <div className="grid gap-6 border-t border-slate-200 p-6 text-sm sm:grid-cols-2">
          <div>
            <h2 className="mb-2 font-semibold">Shipping to</h2>
            <p>{order.shippingName}</p>
            <p className="text-slate-600">{order.shippingAddress}</p>
            <p className="text-slate-600">{order.shippingPhone}</p>
          </div>
          <div>
            <h2 className="mb-2 font-semibold">Payment</h2>
            <p className="text-slate-600">Razorpay</p>
            {order.razorpayPaymentId && <p className="break-all text-slate-600">Payment ID: {order.razorpayPaymentId}</p>}
            {order.paidAt && <p className="text-slate-600">Paid {formatDate(order.paidAt)}</p>}
          </div>
        </div>
      </div>
    </div>
  );
}

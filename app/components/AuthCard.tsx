/** Only follow same-site relative paths after login, never an absolute URL from the query string. */
export function safeNext(next: string | null, fallback = "/") {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : fallback;
}

export function AuthCard({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto mt-6 max-w-md rounded-3xl border border-slate-200 bg-white p-8 shadow-sm">
      <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
      <p className="mb-6 mt-1 text-sm text-slate-500">{subtitle}</p>
      {children}
    </div>
  );
}

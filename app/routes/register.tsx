import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { AuthCard, safeNext } from "~/components/AuthCard";
import { ErrorBox, inputClass, primaryButton } from "~/components/ui";
import { useAuth } from "~/context/auth";
import { errorMessage } from "~/lib/api";
import type { Route } from "./+types/register";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Create account — Curato" }];
}

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [form, setForm] = useState({ name: "", email: "", password: "" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await register(form.name, form.email, form.password);
      navigate(safeNext(params.get("next")), { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <AuthCard title="Create your account" subtitle="Get recommendations tailored to you">
      <form onSubmit={submit} className="space-y-4">
        <input required maxLength={100} placeholder="Full name" autoComplete="name" className={inputClass} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <input type="email" required placeholder="Email" autoComplete="email" className={inputClass} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        <input type="password" required minLength={8} maxLength={72} placeholder="Password (min 8 characters)" autoComplete="new-password" className={inputClass} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
        {error && <ErrorBox message={error} />}
        <button disabled={busy} className={`${primaryButton} w-full py-3`}>
          {busy ? "Creating account…" : "Create account"}
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        Already have an account?{" "}
        <Link to="/login" className="font-medium text-indigo-600 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthCard>
  );
}

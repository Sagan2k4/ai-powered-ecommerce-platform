import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { AuthCard, safeNext } from "~/components/AuthCard";
import { ErrorBox, inputClass, primaryButton } from "~/components/ui";
import { useAuth } from "~/context/auth";
import { errorMessage } from "~/lib/api";
import type { Route } from "./+types/login";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Sign in — Curato" }];
}

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const user = await login(email, password);
      navigate(safeNext(params.get("next"), user.role === "ADMIN" ? "/admin" : "/"), { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <AuthCard title="Welcome back" subtitle="Sign in to your account">
      <form onSubmit={submit} className="space-y-4">
        <input type="email" required placeholder="Email" autoComplete="email" className={inputClass} value={email} onChange={(e) => setEmail(e.target.value)} />
        <input type="password" required placeholder="Password" autoComplete="current-password" className={inputClass} value={password} onChange={(e) => setPassword(e.target.value)} />
        {error && <ErrorBox message={error} />}
        <button disabled={busy} className={`${primaryButton} w-full py-3`}>
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        New here?{" "}
        <Link to={`/register${params.get("next") ? `?next=${encodeURIComponent(params.get("next")!)}` : ""}`} className="font-medium text-indigo-600 hover:underline">
          Create an account
        </Link>
      </p>
    </AuthCard>
  );
}

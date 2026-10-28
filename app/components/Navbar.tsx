import { LayoutDashboard, LogOut, Package, Search, ShoppingBag, Sparkles } from "lucide-react";
import { useState } from "react";
import { Link, NavLink, useNavigate } from "react-router";
import { useAuth } from "~/context/auth";
import { useCart } from "~/context/cart";

export function Navbar() {
  const { user, ready, logout } = useAuth();
  const { cart } = useCart();
  const navigate = useNavigate();
  const [query, setQuery] = useState("");

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    navigate(query.trim() ? `/products?q=${encodeURIComponent(query.trim())}` : "/products");
  };

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `text-sm font-medium transition-colors ${isActive ? "text-indigo-600" : "text-slate-600 hover:text-slate-900"}`;

  return (
    <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/85 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6">
        <Link to="/" className="flex shrink-0 items-center gap-2 text-lg font-bold text-slate-900">
          <span className="grid size-8 place-items-center rounded-lg bg-indigo-600 text-white">
            <Sparkles className="size-4" />
          </span>
          <span className="hidden sm:inline">Curato</span>
        </Link>

        <nav className="hidden items-center gap-6 md:flex">
          <NavLink to="/products" className={linkClass}>
            Shop
          </NavLink>
          {user && (
            <NavLink to="/orders" className={linkClass}>
              Orders
            </NavLink>
          )}
          {user?.role === "ADMIN" && (
            <NavLink to="/admin" className={linkClass}>
              Admin
            </NavLink>
          )}
        </nav>

        <form onSubmit={onSearch} className="relative ml-auto w-full max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search products"
            aria-label="Search products"
            className="h-9 w-full rounded-full border border-slate-200 bg-slate-50 pl-9 pr-3 text-sm outline-none focus:border-indigo-400 focus:bg-white focus:ring-2 focus:ring-indigo-100"
          />
        </form>

        <Link to="/cart" className="relative rounded-full p-2 text-slate-700 hover:bg-slate-100" aria-label="Cart">
          <ShoppingBag className="size-5" />
          {cart.itemCount > 0 && (
            <span className="absolute -right-0.5 -top-0.5 grid min-w-5 place-items-center rounded-full bg-indigo-600 px-1 text-[11px] font-semibold text-white">
              {cart.itemCount}
            </span>
          )}
        </Link>

        {ready &&
          (user ? (
            <div className="flex items-center gap-1">
              <Link to="/orders" className="rounded-full p-2 text-slate-700 hover:bg-slate-100 md:hidden" aria-label="Orders">
                <Package className="size-5" />
              </Link>
              {user.role === "ADMIN" && (
                <Link to="/admin" className="rounded-full p-2 text-slate-700 hover:bg-slate-100 md:hidden" aria-label="Admin">
                  <LayoutDashboard className="size-5" />
                </Link>
              )}
              <span className="hidden text-sm text-slate-600 lg:inline">Hi, {user.name.split(" ")[0]}</span>
              <button
                onClick={() => {
                  logout();
                  navigate("/");
                }}
                className="rounded-full p-2 text-slate-700 hover:bg-slate-100"
                aria-label="Sign out"
                title="Sign out"
              >
                <LogOut className="size-5" />
              </button>
            </div>
          ) : (
            <Link
              to="/login"
              className="shrink-0 rounded-full bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
            >
              Sign in
            </Link>
          ))}
      </div>
    </header>
  );
}

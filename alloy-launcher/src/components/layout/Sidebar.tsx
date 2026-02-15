import { useNavigate, useLocation } from "react-router";
import { useStore } from "../../lib/store";
import alloyLogo from "../../assets/alloy-logo.svg";

const navItems = [
  {
    path: "/",
    label: "Home",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
        <polyline points="9 22 9 12 15 12 15 22" />
      </svg>
    ),
  },
  {
    path: "/settings",
    label: "Settings",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="3" />
        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
      </svg>
    ),
  },
];

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const profile = useStore((s) => s.profile);

  return (
    <div className="flex h-full w-16 flex-col items-center border-r border-obsidian-700 bg-obsidian-900/50 py-4">
      {/* Logo */}
      <div className="mb-6">
        <img src={alloyLogo} alt="Alloy" className="h-8 w-8" />
      </div>

      {/* Nav */}
      <nav className="flex flex-1 flex-col items-center gap-2">
        {navItems.map((item) => {
          const active = location.pathname === item.path;
          return (
            <button
              key={item.path}
              onClick={() => navigate(item.path)}
              title={item.label}
              className={
                "flex h-10 w-10 items-center justify-center rounded-lg transition-all duration-200 " +
                (active
                  ? "bg-ember/15 text-ember shadow-[0_0_12px_rgba(255,107,0,0.15)]"
                  : "text-stone-500 hover:text-stone-300 hover:bg-obsidian-700")
              }
            >
              {item.icon}
            </button>
          );
        })}
      </nav>

      {/* User avatar */}
      {profile && (
        <div className="mt-auto pt-4">
          <img
            src={`https://mc-heads.net/avatar/${profile.uuid.replaceAll("-", "")}/32`}
            alt={profile.username}
            title={profile.username}
            className="h-8 w-8 rounded-lg border border-obsidian-600"
            onError={(e) => { e.currentTarget.style.display = "none"; }}
          />
        </div>
      )}
    </div>
  );
}

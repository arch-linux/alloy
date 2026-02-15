import { useEffect } from "react";
import { useNavigate } from "react-router";
import { useStore } from "../lib/store";
import Button from "../components/ui/Button";
import Spinner from "../components/ui/Spinner";
import ForgeSparks from "../components/effects/ForgeSparks";
import EmberGlow from "../components/effects/EmberGlow";
import GridOverlay from "../components/effects/GridOverlay";
import TitleBar from "../components/layout/TitleBar";
import alloyLogo from "../assets/alloy-logo.svg";

export default function LoginPage() {
  const navigate = useNavigate();
  const isAuthenticated = useStore((s) => s.isAuthenticated);
  const isLoggingIn = useStore((s) => s.isLoggingIn);
  const authError = useStore((s) => s.authError);
  const login = useStore((s) => s.login);

  useEffect(() => {
    if (isAuthenticated) {
      navigate("/", { replace: true });
    }
  }, [isAuthenticated, navigate]);

  return (
    <div className="flex h-screen w-screen flex-col bg-obsidian-950">
      <TitleBar />
      <div className="relative flex flex-1 items-center justify-center">
        <GridOverlay />
        <EmberGlow />
        <ForgeSparks count={24} />

        <div className="relative z-10 flex flex-col items-center gap-8 animate-fade-in-up">
          {/* Logo */}
          <div className="flex flex-col items-center gap-3">
            <img
              src={alloyLogo}
              alt="Alloy"
              className="h-20 w-20 animate-float"
            />
            <h1 className="font-heading text-4xl font-bold text-stone-100 text-glow">
              Alloy
            </h1>
            <p className="text-sm text-stone-400">Forged with Alloy</p>
          </div>

          {/* Login button */}
          <div className="flex flex-col items-center gap-3">
            <Button
              onClick={login}
              disabled={isLoggingIn}
              className="min-w-[240px] text-base py-3"
            >
              {isLoggingIn ? (
                <>
                  <Spinner size={18} />
                  Signing in...
                </>
              ) : (
                <>
                  <svg
                    width="18"
                    height="18"
                    viewBox="0 0 21 21"
                    fill="currentColor"
                  >
                    <rect width="10" height="10" />
                    <rect x="11" width="10" height="10" />
                    <rect y="11" width="10" height="10" />
                    <rect x="11" y="11" width="10" height="10" />
                  </svg>
                  Sign in with Microsoft
                </>
              )}
            </Button>

            {authError && (
              <p className="max-w-xs text-center text-xs text-red-400">
                {authError}
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

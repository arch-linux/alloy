import { useStore } from "../../lib/store";
import Spinner from "../ui/Spinner";
import type { LaunchState } from "../../lib/types";

const stateLabels: Record<LaunchState, string> = {
  ready: "Launch",
  preparing: "Preparing...",
  downloading: "Downloading...",
  launching: "Launching...",
  running: "Running",
};

export default function LaunchButton() {
  const launchState = useStore((s) => s.launchState);
  const launchGame = useStore((s) => s.launchGame);
  const downloadProgress = useStore((s) => s.downloadProgress);

  const isIdle = launchState === "ready";
  const isDownloading = launchState === "downloading";

  return (
    <button
      onClick={launchGame}
      disabled={!isIdle}
      className={
        "relative w-full rounded-xl px-8 py-5 text-lg font-heading font-bold transition-all duration-300 cursor-pointer " +
        "disabled:cursor-default overflow-hidden " +
        (isIdle
          ? "bg-gradient-to-r from-ember to-ember-dark text-obsidian-950 " +
            "hover:from-ember-light hover:to-ember " +
            "shadow-[0_0_40px_rgba(255,107,0,0.3)] hover:shadow-[0_0_60px_rgba(255,107,0,0.5)] " +
            "active:scale-[0.98]"
          : launchState === "running"
            ? "bg-obsidian-700 text-emerald-400 border border-emerald-500/30"
            : "bg-obsidian-700 text-stone-300 border border-obsidian-600")
      }
    >
      {/* Download progress bar */}
      {isDownloading && (
        <div
          className="absolute inset-0 bg-ember/20 transition-all duration-300"
          style={{ width: `${downloadProgress}%` }}
        />
      )}

      <span className="relative flex items-center justify-center gap-3">
        {!isIdle && launchState !== "running" && <Spinner size={20} />}
        {stateLabels[launchState]}
        {isDownloading && (
          <span className="text-sm font-normal opacity-70">
            {Math.round(downloadProgress)}%
          </span>
        )}
      </span>
    </button>
  );
}

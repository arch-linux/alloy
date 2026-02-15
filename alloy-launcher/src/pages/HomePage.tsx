import { useStore } from "../lib/store";
import PlayerCard from "../components/home/PlayerCard";
import LaunchButton from "../components/home/LaunchButton";
import ProgressBar from "../components/home/ProgressBar";
import EmberGlow from "../components/effects/EmberGlow";
import GridOverlay from "../components/effects/GridOverlay";

export default function HomePage() {
  const profile = useStore((s) => s.profile);
  const launchState = useStore((s) => s.launchState);
  const downloadProgress = useStore((s) => s.downloadProgress);
  const downloadMessage = useStore((s) => s.downloadMessage);
  const gameError = useStore((s) => s.gameError);
  const settings = useStore((s) => s.settings);

  return (
    <div className="relative flex h-full flex-col">
      <GridOverlay />
      <EmberGlow />

      <div className="relative z-10 flex flex-1 flex-col items-center justify-center gap-6 p-8">
        {/* Player card */}
        {profile && <PlayerCard profile={profile} />}

        {/* Launch button */}
        <div className="w-full max-w-md">
          <LaunchButton />
        </div>

        {/* Download progress */}
        {launchState === "downloading" && (
          <div className="w-full max-w-md">
            <ProgressBar progress={downloadProgress} message={downloadMessage} />
          </div>
        )}

        {/* Game error */}
        {gameError && (
          <div className="max-w-md rounded-lg border border-red-600/20 bg-red-600/10 p-3 text-center text-sm text-red-400">
            {gameError}
          </div>
        )}

        {/* Version badge */}
        <div className="flex items-center gap-2 text-xs text-stone-500">
          <span>Minecraft {settings.minecraft_version}</span>
          <span className="text-obsidian-600">&middot;</span>
          <span>Alloy 0.1.0</span>
        </div>
      </div>
    </div>
  );
}

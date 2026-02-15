interface ProgressBarProps {
  progress: number;
  message?: string;
}

export default function ProgressBar({ progress, message }: ProgressBarProps) {
  if (progress <= 0) return null;

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-xs text-stone-400">
        <span>{message || "Downloading..."}</span>
        <span>{Math.round(progress)}%</span>
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-obsidian-700">
        <div
          className="h-full rounded-full bg-gradient-to-r from-ember to-forge-gold transition-all duration-300"
          style={{ width: `${Math.min(progress, 100)}%` }}
        />
      </div>
    </div>
  );
}

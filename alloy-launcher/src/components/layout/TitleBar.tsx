import { getCurrentWindow } from "@tauri-apps/api/window";

export default function TitleBar() {
  const appWindow = getCurrentWindow();

  return (
    <div
      data-tauri-drag-region
      className="flex h-8 shrink-0 items-center justify-between bg-obsidian-950 select-none"
    >
      <div data-tauri-drag-region className="flex-1 pl-3">
        <span className="text-xs font-medium text-stone-500 font-heading">
          Alloy Launcher
        </span>
      </div>
      <div className="flex">
        <button
          onClick={() => appWindow.minimize()}
          className="flex h-8 w-11 items-center justify-center text-stone-500 hover:bg-obsidian-800 hover:text-stone-300 transition-colors"
        >
          <svg width="10" height="1" viewBox="0 0 10 1" fill="currentColor">
            <rect width="10" height="1" />
          </svg>
        </button>
        <button
          onClick={() => appWindow.toggleMaximize()}
          className="flex h-8 w-11 items-center justify-center text-stone-500 hover:bg-obsidian-800 hover:text-stone-300 transition-colors"
        >
          <svg
            width="10"
            height="10"
            viewBox="0 0 10 10"
            fill="none"
            stroke="currentColor"
            strokeWidth="1"
          >
            <rect x="0.5" y="0.5" width="9" height="9" />
          </svg>
        </button>
        <button
          onClick={() => appWindow.close()}
          className="flex h-8 w-11 items-center justify-center text-stone-500 hover:bg-red-600 hover:text-white transition-colors"
        >
          <svg
            width="10"
            height="10"
            viewBox="0 0 10 10"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.2"
          >
            <line x1="1" y1="1" x2="9" y2="9" />
            <line x1="9" y1="1" x2="1" y2="9" />
          </svg>
        </button>
      </div>
    </div>
  );
}

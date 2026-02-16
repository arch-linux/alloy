import { getCurrentWindow } from "@tauri-apps/api/window";
import { useStore } from "../../lib/store";

export default function TitleBar() {
  const appWindow = getCurrentWindow();
  const projectName = useStore((s) => s.currentProject?.name);
  const activeFilePath = useStore((s) => s.activeFilePath);
  const openFiles = useStore((s) => s.openFiles);

  const activeFile = openFiles.find((f) => f.path === activeFilePath);

  return (
    <div
      data-tauri-drag-region
      className="flex h-8 shrink-0 items-center justify-between bg-obsidian-950 border-b border-obsidian-700 select-none"
      onDoubleClick={() => appWindow.toggleMaximize()}
    >
      <div data-tauri-drag-region className="flex items-center gap-2 flex-1 pl-3 pointer-events-none">
        <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" className="h-4 w-4">
          <path d="M20 4L6 14v4l14 10 14-10v-4L20 4z" fill="url(#titlebar-g)" />
          <path d="M20 18L6 14v4l14 10V18z" fill="#e06000" />
          <path d="M20 18l14-4v4L20 28V18z" fill="#cc5500" />
          <circle cx="20" cy="12" r="2" fill="#f0b830" />
          <defs>
            <linearGradient id="titlebar-g" x1="6" y1="4" x2="34" y2="28">
              <stop stopColor="#ff9a4a" />
              <stop offset="1" stopColor="#f04800" />
            </linearGradient>
          </defs>
        </svg>
        <span className="text-xs font-medium text-stone-500 font-heading">
          Alloy IDE
        </span>
        {projectName && (
          <>
            <span className="text-xs text-stone-500">/</span>
            <span className="text-xs text-stone-300">{projectName}</span>
          </>
        )}
        {activeFile && (
          <>
            <span className="text-xs text-stone-600">—</span>
            <span className="text-xs text-stone-400">
              {activeFile.dirty && <span className="text-ember mr-0.5">*</span>}
              {activeFile.name}
            </span>
          </>
        )}
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

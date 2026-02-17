import { useState, useEffect, useCallback, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";
import { Bot, GitBranch, Hammer, Bell } from "lucide-react";
import {
  useNotificationHistory,
  clearNotificationHistory,
} from "../ui/Toast";
import type { NotificationEntry } from "../ui/Toast";

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function StatusBar() {
  const currentProject = useStore((s) => s.currentProject);
  const activeFilePath = useStore((s) => s.activeFilePath);
  const openFiles = useStore((s) => s.openFiles);
  const cursorPosition = useStore((s) => s.cursorPosition);
  const editorSettings = useStore((s) => s.editorSettings);
  const aiConfig = useStore((s) => s.aiConfig);
  const aiLoading = useStore((s) => s.aiLoading);
  const lspRunning = useStore((s) => s.lspRunning);
  const buildRunning = useStore((s) => s.buildRunning);
  const buildErrors = useStore((s) => s.buildErrors);
  const [branch, setBranch] = useState<string | null>(null);
  const [fileSize, setFileSize] = useState<number | null>(null);

  const fetchBranch = useCallback(async () => {
    if (!currentProject) {
      setBranch(null);
      return;
    }
    try {
      const status = await invoke<{ branch: string }>("git_status", {
        projectPath: currentProject.path,
      });
      setBranch(status.branch);
    } catch {
      setBranch(null);
    }
  }, [currentProject]);

  useEffect(() => {
    fetchBranch();
    const interval = setInterval(fetchBranch, 15_000);
    return () => clearInterval(interval);
  }, [fetchBranch]);

  // Fetch file size when active file changes
  useEffect(() => {
    if (!activeFilePath) {
      setFileSize(null);
      return;
    }
    invoke<number>("get_file_size", { path: activeFilePath })
      .then(setFileSize)
      .catch(() => setFileSize(null));
  }, [activeFilePath]);

  const activeFile = openFiles.find((f) => f.path === activeFilePath);
  const env = currentProject?.environment;

  const envLabel = env
    ? { client: "Client", server: "Server", both: "Universal" }[env]
    : null;

  const envColor = env
    ? { client: "text-blue-400", server: "text-green-400", both: "text-forge-gold" }[env]
    : "";

  const hasApiKey = !!aiConfig?.api_key;

  return (
    <div className="flex h-6 shrink-0 items-center justify-between bg-obsidian-900 border-t border-obsidian-700 px-3 text-[11px] select-none">
      <div className="flex items-center gap-3">
        {branch && (
          <button
            onClick={() => useStore.getState().setSidebarPanel("git")}
            className="flex items-center gap-1 text-stone-400 hover:text-stone-200 transition-colors"
          >
            <GitBranch size={12} />
            <span>{branch}</span>
          </button>
        )}
        {envLabel && (
          <span className={envColor + " font-medium"}>{envLabel}</span>
        )}
        {currentProject && (
          <span className="text-stone-500">
            {currentProject.project_type === "mod" ? "Mod" : currentProject.project_type === "modpack" ? "Modpack" : "Project"}
          </span>
        )}
        {/* Build status */}
        {buildRunning && (
          <span className="flex items-center gap-1 text-ember">
            <Hammer size={11} className="animate-pulse" />
            <span className="text-[10px]">Building...</span>
          </span>
        )}
        {!buildRunning && buildErrors.length > 0 && (
          <button
            onClick={() => {
              useStore.getState().setBottomPanel("problems");
              if (!useStore.getState().bottomPanelVisible) {
                useStore.getState().toggleBottomPanel();
              }
            }}
            className="flex items-center gap-1 text-red-400 hover:text-red-300 transition-colors"
          >
            <span className="text-[10px]">{buildErrors.length} error(s)</span>
          </button>
        )}
      </div>
      <div className="flex items-center gap-3 text-stone-500">
        {activeFile && (
          <>
            <span>
              Ln {cursorPosition.line}, Col {cursorPosition.column}
              {cursorPosition.selected ? ` (${cursorPosition.selected} selected)` : ""}
              {cursorPosition.cursors ? ` \u00b7 ${cursorPosition.cursors} cursors` : ""}
            </span>
            <button
              onClick={() => {
                const s = useStore.getState();
                s.updateEditorSettings({
                  tabSize: editorSettings.tabSize === 2 ? 4 : 2,
                });
              }}
              className="hover:text-stone-300 transition-colors"
              title="Click to toggle tab size"
            >
              Spaces: {editorSettings.tabSize}
            </button>
            <span>{activeFile.language.toUpperCase()}</span>
            <span>UTF-8</span>
            {fileSize !== null && (
              <span className="text-stone-600">{formatFileSize(fileSize)}</span>
            )}
          </>
        )}
        {/* LSP status */}
        {activeFile?.language === "java" && (
          <span
            className={
              "text-[10px] " +
              (lspRunning ? "text-stone-400" : "text-stone-600")
            }
            title={lspRunning ? "Java Language Server running" : "Java Language Server not running"}
          >
            {lspRunning ? "JDT LS" : "JDT LS \u00d7"}
          </span>
        )}
        {/* Notifications */}
        <NotificationBell />
        {/* AI status indicator */}
        <span className="flex items-center gap-1">
          <Bot size={12} className={hasApiKey ? "text-stone-400" : "text-stone-600"} />
          {aiLoading ? (
            <span className="text-ember text-[10px]">AI...</span>
          ) : (
            <span
              className={
                "inline-block h-1.5 w-1.5 rounded-full " +
                (hasApiKey ? "bg-green-400" : "bg-stone-600")
              }
            />
          )}
        </span>
      </div>
    </div>
  );
}

const TYPE_COLORS: Record<string, string> = {
  success: "text-green-400",
  error: "text-red-400",
  warning: "text-yellow-400",
  info: "text-blue-400",
};

function NotificationBell() {
  const notifications = useNotificationHistory();
  const [open, setOpen] = useState(false);
  const [lastSeen, setLastSeen] = useState(Date.now());
  const panelRef = useRef<HTMLDivElement>(null);

  const unseen = notifications.filter((n) => n.timestamp > lastSeen).length;

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  const handleToggle = () => {
    if (!open) {
      setLastSeen(Date.now());
    }
    setOpen(!open);
  };

  return (
    <div className="relative" ref={panelRef}>
      <button
        onClick={handleToggle}
        className="flex items-center gap-0.5 text-stone-500 hover:text-stone-300 transition-colors relative"
      >
        <Bell size={12} />
        {unseen > 0 && (
          <span className="absolute -top-1 -right-1 h-2.5 w-2.5 rounded-full bg-ember text-obsidian-950 text-[7px] flex items-center justify-center font-bold">
            {unseen > 9 ? "+" : unseen}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute bottom-6 right-0 w-72 max-h-64 rounded-lg border border-obsidian-600 bg-obsidian-800 shadow-2xl overflow-hidden z-50">
          <div className="flex items-center justify-between px-3 py-1.5 border-b border-obsidian-700">
            <span className="text-[10px] font-medium text-stone-400 uppercase tracking-wide">
              Notifications
            </span>
            {notifications.length > 0 && (
              <button
                onClick={() => {
                  clearNotificationHistory();
                  setOpen(false);
                }}
                className="text-[10px] text-stone-600 hover:text-stone-300 transition-colors"
              >
                Clear
              </button>
            )}
          </div>
          <div className="overflow-y-auto max-h-52">
            {notifications.length === 0 ? (
              <div className="px-3 py-4 text-center text-stone-600 text-[11px]">
                No notifications
              </div>
            ) : (
              notifications.slice(0, 20).map((n) => (
                <div
                  key={n.id}
                  className="flex items-start gap-2 px-3 py-1.5 border-b border-obsidian-700/50 hover:bg-obsidian-700/50"
                >
                  <span className={"mt-0.5 h-1.5 w-1.5 rounded-full shrink-0 " + (TYPE_COLORS[n.type]?.replace("text-", "bg-") || "bg-stone-500")} />
                  <div className="flex-1 min-w-0">
                    <span className="text-[11px] text-stone-300 leading-tight block truncate">
                      {n.message}
                    </span>
                    <span className="text-[9px] text-stone-600">
                      {formatTimeAgo(n.timestamp)}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function formatTimeAgo(timestamp: number): string {
  const diff = Date.now() - timestamp;
  if (diff < 60_000) return "just now";
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`;
  return new Date(timestamp).toLocaleDateString();
}

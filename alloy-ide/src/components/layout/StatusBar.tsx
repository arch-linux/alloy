import { useState, useEffect, useCallback } from "react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";
import { Bot, GitBranch, Hammer } from "lucide-react";

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

import { useState, useEffect, useCallback, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import {
  GitBranch, RefreshCw, FileEdit, FilePlus, FileX, FileMinus2,
  Plus, Minus, RotateCcw, Check, ChevronDown, ChevronRight, Send,
} from "lucide-react";
import { useStore } from "../../lib/store";
import { showToast } from "../ui/Toast";
import type { GitStatus, GitFileStatus } from "../../lib/types";

const statusConfig: Record<string, { label: string; color: string; icon: typeof FileEdit }> = {
  modified: { label: "M", color: "text-yellow-400", icon: FileEdit },
  added: { label: "A", color: "text-green-400", icon: FilePlus },
  deleted: { label: "D", color: "text-red-400", icon: FileX },
  untracked: { label: "U", color: "text-stone-400", icon: FilePlus },
  renamed: { label: "R", color: "text-blue-400", icon: FileMinus2 },
  changed: { label: "C", color: "text-yellow-400", icon: FileEdit },
};

export default function GitPanel() {
  const [gitStatus, setGitStatus] = useState<GitStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [commitMessage, setCommitMessage] = useState("");
  const [committing, setCommitting] = useState(false);
  const [diffText, setDiffText] = useState<string | null>(null);
  const [diffFile, setDiffFile] = useState<string | null>(null);
  const [changesOpen, setChangesOpen] = useState(true);
  const commitInputRef = useRef<HTMLTextAreaElement>(null);
  const currentProject = useStore((s) => s.currentProject);

  const refresh = useCallback(async () => {
    if (!currentProject) return;
    setLoading(true);
    setError(null);
    try {
      const status = await invoke<GitStatus>("git_status", {
        projectPath: currentProject.path,
      });
      setGitStatus(status);
    } catch (err) {
      setError(String(err));
      setGitStatus(null);
    } finally {
      setLoading(false);
    }
  }, [currentProject]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    if (!currentProject) return;
    const interval = setInterval(refresh, 10_000);
    return () => clearInterval(interval);
  }, [currentProject, refresh]);

  const openFile = (file: GitFileStatus) => {
    if (!currentProject || file.status === "deleted") return;
    const fullPath = file.path.startsWith("/")
      ? file.path
      : `${currentProject.path}/${file.path}`;
    const name = file.path.split("/").pop() || file.path;
    useStore.getState().openFile(fullPath, name);
  };

  const showDiff = async (file: GitFileStatus) => {
    if (!currentProject) return;
    try {
      const diff = await invoke<string>("git_diff", {
        projectPath: currentProject.path,
        filePath: file.path,
      });
      setDiffText(diff || "(No changes to show)");
      setDiffFile(file.path);
    } catch (err) {
      showToast("error", `Failed to get diff: ${err}`);
    }
  };

  const handleStage = async (filePath: string) => {
    if (!currentProject) return;
    try {
      await invoke("git_stage", { projectPath: currentProject.path, filePath });
      await refresh();
    } catch (err) {
      showToast("error", `Stage failed: ${err}`);
    }
  };

  const handleUnstage = async (filePath: string) => {
    if (!currentProject) return;
    try {
      await invoke("git_unstage", { projectPath: currentProject.path, filePath });
      await refresh();
    } catch (err) {
      showToast("error", `Unstage failed: ${err}`);
    }
  };

  const handleDiscard = async (filePath: string) => {
    if (!currentProject) return;
    const confirmed = window.confirm(`Discard changes to "${filePath}"? This cannot be undone.`);
    if (!confirmed) return;
    try {
      await invoke("git_discard", { projectPath: currentProject.path, filePath });
      showToast("info", "Changes discarded");
      await refresh();
    } catch (err) {
      showToast("error", `Discard failed: ${err}`);
    }
  };

  const handleCommit = async () => {
    if (!currentProject || !commitMessage.trim()) return;
    setCommitting(true);
    try {
      await invoke("git_commit", {
        projectPath: currentProject.path,
        message: commitMessage.trim(),
      });
      setCommitMessage("");
      showToast("success", "Committed successfully");
      await refresh();
    } catch (err) {
      showToast("error", `Commit failed: ${err}`);
    }
    setCommitting(false);
  };

  const handleStageAll = async () => {
    if (!currentProject || !gitStatus) return;
    for (const file of gitStatus.files) {
      try {
        await invoke("git_stage", { projectPath: currentProject.path, filePath: file.path });
      } catch { /* ignore individual failures */ }
    }
    await refresh();
  };

  if (!currentProject) {
    return (
      <div className="flex flex-col items-center justify-center h-32 text-stone-500 text-xs">
        Open a project to see source control
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center gap-2 p-4">
        <div className="text-stone-500 text-xs text-center">
          {error.includes("Not a git") || error.includes("git not found")
            ? "Not a git repository"
            : "Failed to get git status"}
        </div>
        <button
          onClick={refresh}
          className="text-[10px] text-stone-400 hover:text-stone-200 flex items-center gap-1"
        >
          <RefreshCw size={10} /> Retry
        </button>
      </div>
    );
  }

  // If viewing a diff, show it
  if (diffText !== null) {
    return (
      <div className="flex flex-col h-full">
        <div className="flex items-center justify-between px-3 py-2 border-b border-obsidian-700">
          <button
            onClick={() => { setDiffText(null); setDiffFile(null); }}
            className="text-xs text-ember hover:text-ember-light transition-colors"
          >
            Back
          </button>
          <span className="text-[11px] text-stone-300 truncate ml-2">{diffFile}</span>
        </div>
        <div className="flex-1 overflow-auto bg-obsidian-950 p-2">
          <pre className="text-[11px] font-mono leading-relaxed">
            {diffText.split("\n").map((line, i) => {
              let color = "text-stone-400";
              if (line.startsWith("+") && !line.startsWith("+++")) color = "text-green-400";
              else if (line.startsWith("-") && !line.startsWith("---")) color = "text-red-400";
              else if (line.startsWith("@@")) color = "text-blue-400";
              else if (line.startsWith("diff") || line.startsWith("index")) color = "text-stone-500";
              return (
                <div key={i} className={color + (line.startsWith("+") && !line.startsWith("+++") ? " bg-green-400/5" : "") + (line.startsWith("-") && !line.startsWith("---") ? " bg-red-400/5" : "")}>
                  {line || " "}
                </div>
              );
            })}
          </pre>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-obsidian-700">
        <div className="flex items-center gap-2 text-xs">
          <GitBranch size={13} className="text-stone-400" />
          <span className="text-stone-200 font-medium">
            {gitStatus?.branch || "..."}
          </span>
        </div>
        <button
          onClick={refresh}
          disabled={loading}
          className="text-stone-500 hover:text-stone-300 transition-colors disabled:opacity-50"
        >
          <RefreshCw size={13} className={loading ? "animate-spin" : ""} />
        </button>
      </div>

      {/* Commit message */}
      <div className="px-3 py-2 border-b border-obsidian-700">
        <div className="flex gap-1.5">
          <textarea
            ref={commitInputRef}
            value={commitMessage}
            onChange={(e) => setCommitMessage(e.target.value)}
            placeholder="Commit message..."
            rows={2}
            className="flex-1 resize-none rounded-md border border-obsidian-600 bg-obsidian-950 px-2.5 py-1.5 text-xs text-stone-100 placeholder:text-stone-500 outline-none focus:border-ember focus:ring-1 focus:ring-ember/30"
            onKeyDown={(e) => {
              if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
                handleCommit();
              }
            }}
          />
        </div>
        <div className="flex items-center gap-1.5 mt-1.5">
          <button
            onClick={handleCommit}
            disabled={committing || !commitMessage.trim()}
            className={
              "flex-1 flex items-center justify-center gap-1.5 rounded-md py-1.5 text-[11px] font-medium transition-colors " +
              (committing || !commitMessage.trim()
                ? "text-stone-500 bg-obsidian-700 cursor-not-allowed"
                : "text-obsidian-950 bg-ember hover:bg-ember-light")
            }
          >
            <Check size={12} />
            {committing ? "Committing..." : "Commit"}
          </button>
        </div>
      </div>

      {/* Changes list */}
      <div className="flex-1 overflow-y-auto">
        {gitStatus && gitStatus.files.length === 0 && (
          <div className="flex flex-col items-center justify-center h-24 text-stone-500 text-xs">
            No changes
          </div>
        )}

        {gitStatus && gitStatus.files.length > 0 && (
          <div className="py-1">
            <button
              onClick={() => setChangesOpen(!changesOpen)}
              className="flex w-full items-center gap-1.5 px-3 py-1.5 text-[10px] font-semibold text-stone-400 uppercase tracking-wider hover:bg-obsidian-800 transition-colors"
            >
              {changesOpen ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
              Changes ({gitStatus.files.length})
              <button
                onClick={(e) => { e.stopPropagation(); handleStageAll(); }}
                className="ml-auto text-stone-500 hover:text-stone-200 transition-colors"
                title="Stage All"
              >
                <Plus size={12} />
              </button>
            </button>

            {changesOpen &&
              gitStatus.files.map((file) => {
                const config = statusConfig[file.status] || statusConfig.changed;
                const Icon = config.icon;
                const fileName = file.path.split("/").pop() || file.path;

                return (
                  <div
                    key={file.path}
                    className="flex items-center gap-1.5 px-3 py-1 text-xs hover:bg-obsidian-700 transition-colors group"
                  >
                    <Icon size={13} className={config.color + " shrink-0"} />
                    <button
                      onClick={() => openFile(file)}
                      className="flex-1 text-left truncate text-stone-200 hover:text-stone-100"
                    >
                      {fileName}
                    </button>
                    {/* Action buttons — visible on hover */}
                    <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button
                        onClick={() => showDiff(file)}
                        className="p-0.5 rounded text-stone-500 hover:text-stone-200 hover:bg-obsidian-600 transition-colors"
                        title="Show Diff"
                      >
                        <FileEdit size={12} />
                      </button>
                      <button
                        onClick={() => handleStage(file.path)}
                        className="p-0.5 rounded text-stone-500 hover:text-green-400 hover:bg-obsidian-600 transition-colors"
                        title="Stage"
                      >
                        <Plus size={12} />
                      </button>
                      {file.status !== "untracked" && (
                        <button
                          onClick={() => handleDiscard(file.path)}
                          className="p-0.5 rounded text-stone-500 hover:text-red-400 hover:bg-obsidian-600 transition-colors"
                          title="Discard Changes"
                        >
                          <RotateCcw size={12} />
                        </button>
                      )}
                    </div>
                    <span
                      className={
                        "text-[10px] font-mono font-bold shrink-0 w-4 text-center " +
                        config.color
                      }
                    >
                      {config.label}
                    </span>
                  </div>
                );
              })}
          </div>
        )}
      </div>
    </div>
  );
}

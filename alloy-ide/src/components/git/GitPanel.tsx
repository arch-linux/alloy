import { useState, useEffect, useCallback, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import {
  GitBranch, RefreshCw, FileEdit, FilePlus, FileX, FileMinus2,
  Plus, Minus, RotateCcw, Check, ChevronDown, ChevronRight,
  Upload, Download,
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
  copied: { label: "C", color: "text-blue-400", icon: FilePlus },
  changed: { label: "C", color: "text-yellow-400", icon: FileEdit },
};

export default function GitPanel() {
  const [gitStatus, setGitStatus] = useState<GitStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [commitMessage, setCommitMessage] = useState("");
  const [committing, setCommitting] = useState(false);
  const [pushing, setPushing] = useState(false);
  const [pulling, setPulling] = useState(false);
  const [changesOpen, setChangesOpen] = useState(true);
  const [stagedOpen, setStagedOpen] = useState(true);
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
      const fullPath = file.path.startsWith("/")
        ? file.path
        : `${currentProject.path}/${file.path}`;

      // Get the HEAD version of the file
      let original = "";
      if (file.status !== "untracked" && file.status !== "added") {
        try {
          original = await invoke<string>("git_show_file", {
            projectPath: currentProject.path,
            filePath: file.path,
          });
        } catch {
          original = "";
        }
      }

      // Get the current working copy
      let modified = "";
      if (file.status !== "deleted") {
        try {
          modified = await invoke<string>("read_file", { path: fullPath });
        } catch {
          modified = "";
        }
      }

      const fileName = file.path.split("/").pop() || file.path;
      useStore.getState().showDiffView(
        original,
        modified,
        `${fileName} (HEAD)`,
        `${fileName} (Working Tree)`,
      );
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

  const handleUnstageAll = async () => {
    if (!currentProject || !gitStatus) return;
    for (const file of gitStatus.staged) {
      try {
        await invoke("git_unstage", { projectPath: currentProject.path, filePath: file.path });
      } catch { /* ignore individual failures */ }
    }
    await refresh();
  };

  const handlePush = async () => {
    if (!currentProject) return;
    setPushing(true);
    try {
      await invoke<string>("git_push", { projectPath: currentProject.path });
      showToast("success", "Pushed successfully");
      await refresh();
    } catch (err) {
      showToast("error", `Push failed: ${err}`);
    }
    setPushing(false);
  };

  const handlePull = async () => {
    if (!currentProject) return;
    setPulling(true);
    try {
      await invoke<string>("git_pull", { projectPath: currentProject.path });
      showToast("success", "Pulled successfully");
      await refresh();
    } catch (err) {
      showToast("error", `Pull failed: ${err}`);
    }
    setPulling(false);
  };

  if (!currentProject) {
    return (
      <div className="flex flex-col items-center justify-center h-32 text-stone-500 text-xs">
        Open a project to see source control
      </div>
    );
  }

  const handleInit = async () => {
    if (!currentProject) return;
    setLoading(true);
    try {
      await invoke<string>("git_init", { projectPath: currentProject.path });
      showToast("success", "Initialized git repository");
      await refresh();
    } catch (err) {
      showToast("error", `Failed to initialize: ${err}`);
    } finally {
      setLoading(false);
    }
  };

  if (error) {
    const isNotRepo = error.includes("Not a git") || error.includes("git not found") || error.includes("not a git");
    return (
      <div className="flex flex-col items-center gap-3 p-6">
        <GitBranch size={32} className="text-stone-600" />
        <div className="text-stone-400 text-xs text-center font-medium">
          {isNotRepo ? "No Source Control" : "Failed to get git status"}
        </div>
        {isNotRepo ? (
          <>
            <p className="text-[11px] text-stone-500 text-center leading-relaxed max-w-[200px]">
              This project is not tracked by git. Initialize a repository to start tracking changes.
            </p>
            <button
              onClick={handleInit}
              disabled={loading}
              className="flex items-center gap-1.5 px-4 py-1.5 rounded-md text-[11px] font-medium bg-ember text-obsidian-950 hover:bg-ember-light transition-colors disabled:opacity-50"
            >
              {loading ? (
                <RefreshCw size={12} className="animate-spin" />
              ) : (
                <Plus size={12} />
              )}
              Initialize Repository
            </button>
          </>
        ) : (
          <button
            onClick={refresh}
            className="text-[10px] text-stone-400 hover:text-stone-200 flex items-center gap-1"
          >
            <RefreshCw size={10} /> Retry
          </button>
        )}
      </div>
    );
  }

  const hasStaged = gitStatus && gitStatus.staged.length > 0;
  const hasUnstaged = gitStatus && gitStatus.files.length > 0;
  const noChanges = !hasStaged && !hasUnstaged;

  return (
    <div className="flex flex-col h-full">
      {/* Header with branch + sync */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-obsidian-700">
        <div className="flex items-center gap-2 text-xs">
          <GitBranch size={13} className="text-stone-400" />
          <span className="text-stone-200 font-medium">
            {gitStatus?.branch || "..."}
          </span>
          {gitStatus && (gitStatus.ahead > 0 || gitStatus.behind > 0) && (
            <span className="text-[10px] text-stone-500">
              {gitStatus.ahead > 0 && `↑${gitStatus.ahead}`}
              {gitStatus.ahead > 0 && gitStatus.behind > 0 && " "}
              {gitStatus.behind > 0 && `↓${gitStatus.behind}`}
            </span>
          )}
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={handlePull}
            disabled={pulling}
            className="p-1 rounded text-stone-500 hover:text-stone-300 hover:bg-obsidian-700 transition-colors disabled:opacity-50"
            title="Pull"
          >
            <Download size={13} className={pulling ? "animate-pulse" : ""} />
          </button>
          <button
            onClick={handlePush}
            disabled={pushing}
            className="p-1 rounded text-stone-500 hover:text-stone-300 hover:bg-obsidian-700 transition-colors disabled:opacity-50"
            title="Push"
          >
            <Upload size={13} className={pushing ? "animate-pulse" : ""} />
          </button>
          <button
            onClick={refresh}
            disabled={loading}
            className="p-1 rounded text-stone-500 hover:text-stone-300 hover:bg-obsidian-700 transition-colors disabled:opacity-50"
            title="Refresh"
          >
            <RefreshCw size={13} className={loading ? "animate-spin" : ""} />
          </button>
        </div>
      </div>

      {/* Commit message */}
      <div className="px-3 py-2 border-b border-obsidian-700">
        <textarea
          ref={commitInputRef}
          value={commitMessage}
          onChange={(e) => setCommitMessage(e.target.value)}
          placeholder="Commit message..."
          rows={2}
          className="w-full resize-none rounded-md border border-obsidian-600 bg-obsidian-950 px-2.5 py-1.5 text-xs text-stone-100 placeholder:text-stone-500 outline-none focus:border-ember focus:ring-1 focus:ring-ember/30"
          onKeyDown={(e) => {
            if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
              handleCommit();
            }
          }}
        />
        <button
          onClick={handleCommit}
          disabled={committing || !commitMessage.trim() || !hasStaged}
          className={
            "w-full flex items-center justify-center gap-1.5 rounded-md py-1.5 mt-1.5 text-[11px] font-medium transition-colors " +
            (committing || !commitMessage.trim() || !hasStaged
              ? "text-stone-500 bg-obsidian-700 cursor-not-allowed"
              : "text-obsidian-950 bg-ember hover:bg-ember-light")
          }
          title={!hasStaged ? "Stage changes first" : undefined}
        >
          <Check size={12} />
          {committing ? "Committing..." : "Commit"}
        </button>
      </div>

      {/* Changes lists */}
      <div className="flex-1 overflow-y-auto">
        {noChanges && (
          <div className="flex flex-col items-center justify-center h-24 text-stone-500 text-xs">
            No changes
          </div>
        )}

        {/* Staged Changes */}
        {hasStaged && (
          <div className="py-0.5">
            <button
              onClick={() => setStagedOpen(!stagedOpen)}
              className="flex w-full items-center gap-1.5 px-3 py-1.5 text-[10px] font-semibold text-stone-400 uppercase tracking-wider hover:bg-obsidian-800 transition-colors"
            >
              {stagedOpen ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
              Staged ({gitStatus!.staged.length})
              <button
                onClick={(e) => { e.stopPropagation(); handleUnstageAll(); }}
                className="ml-auto text-stone-500 hover:text-stone-200 transition-colors"
                title="Unstage All"
              >
                <Minus size={12} />
              </button>
            </button>

            {stagedOpen &&
              gitStatus!.staged.map((file) => (
                <FileRow
                  key={"staged-" + file.path}
                  file={file}
                  onOpen={() => openFile(file)}
                  onDiff={() => showDiff(file)}
                  actions={
                    <button
                      onClick={() => handleUnstage(file.path)}
                      className="p-0.5 rounded text-stone-500 hover:text-yellow-400 hover:bg-obsidian-600 transition-colors"
                      title="Unstage"
                    >
                      <Minus size={12} />
                    </button>
                  }
                />
              ))}
          </div>
        )}

        {/* Unstaged Changes */}
        {hasUnstaged && (
          <div className="py-0.5">
            <button
              onClick={() => setChangesOpen(!changesOpen)}
              className="flex w-full items-center gap-1.5 px-3 py-1.5 text-[10px] font-semibold text-stone-400 uppercase tracking-wider hover:bg-obsidian-800 transition-colors"
            >
              {changesOpen ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
              Changes ({gitStatus!.files.length})
              <button
                onClick={(e) => { e.stopPropagation(); handleStageAll(); }}
                className="ml-auto text-stone-500 hover:text-stone-200 transition-colors"
                title="Stage All"
              >
                <Plus size={12} />
              </button>
            </button>

            {changesOpen &&
              gitStatus!.files.map((file) => (
                <FileRow
                  key={"unstaged-" + file.path}
                  file={file}
                  onOpen={() => openFile(file)}
                  onDiff={() => showDiff(file)}
                  actions={
                    <>
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
                    </>
                  }
                />
              ))}
          </div>
        )}
      </div>
    </div>
  );
}

/** Single file row in the git changes list */
function FileRow({
  file,
  onOpen,
  onDiff,
  actions,
}: {
  file: GitFileStatus;
  onOpen: () => void;
  onDiff: () => void;
  actions: React.ReactNode;
}) {
  const config = statusConfig[file.status] || statusConfig.changed;
  const Icon = config.icon;
  const fileName = file.path.split("/").pop() || file.path;
  const dirPath = file.path.includes("/")
    ? file.path.substring(0, file.path.lastIndexOf("/"))
    : "";

  return (
    <div className="flex items-center gap-1.5 px-3 py-1 text-xs hover:bg-obsidian-700 transition-colors group">
      <Icon size={13} className={config.color + " shrink-0"} />
      <button
        onClick={onOpen}
        className="flex-1 text-left truncate text-stone-200 hover:text-stone-100 flex items-baseline gap-1 min-w-0"
      >
        <span className="truncate">{fileName}</span>
        {dirPath && (
          <span className="text-[10px] text-stone-600 truncate shrink">{dirPath}</span>
        )}
      </button>
      <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          onClick={onDiff}
          className="p-0.5 rounded text-stone-500 hover:text-stone-200 hover:bg-obsidian-600 transition-colors"
          title="Show Diff"
        >
          <FileEdit size={12} />
        </button>
        {actions}
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
}


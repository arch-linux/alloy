import { useState, useCallback, useMemo } from "react";
import { FolderOpen, RefreshCw, Upload, Search, X } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";
import { showToast } from "../ui/Toast";
import FileTreeNode from "./FileTreeNode";
import type { FileEntry } from "../../lib/types";

/** Recursively filter the tree to entries matching the query. */
function filterTree(entries: FileEntry[], query: string): FileEntry[] {
  const q = query.toLowerCase();
  const result: FileEntry[] = [];

  for (const entry of entries) {
    if (entry.is_dir && entry.children) {
      const filteredChildren = filterTree(entry.children, query);
      if (filteredChildren.length > 0) {
        result.push({ ...entry, children: filteredChildren, expanded: true });
      }
    } else if (entry.name.toLowerCase().includes(q)) {
      result.push(entry);
    }
  }

  return result;
}

export default function FileTree() {
  const fileTree = useStore((s) => s.fileTree);
  const currentProject = useStore((s) => s.currentProject);
  const openFolderDialog = useStore((s) => s.openFolderDialog);
  const openProject = useStore((s) => s.openProject);
  const [dragOver, setDragOver] = useState(false);
  const [filter, setFilter] = useState("");
  const [filterVisible, setFilterVisible] = useState(false);

  const handleRefresh = () => {
    if (currentProject) {
      openProject(currentProject.path);
    }
  };

  const IMAGE_EXTENSIONS = new Set(["png", "jpg", "jpeg", "gif", "bmp", "webp", "tga"]);

  const handleExternalDrop = useCallback(
    async (e: React.DragEvent) => {
      e.preventDefault();
      setDragOver(false);
      if (!currentProject) return;

      const files = e.dataTransfer.files;
      if (files.length === 0) return;

      // Check if first file is an image — open asset import wizard
      const first = files[0];
      const ext = first.name.split(".").pop()?.toLowerCase() || "";
      const filePath = (first as File & { path?: string }).path;

      if (filePath && IMAGE_EXTENSIONS.has(ext)) {
        useStore.getState().showAssetImport(filePath);
        return;
      }

      // Non-image files: plain copy
      let importedCount = 0;
      for (let i = 0; i < files.length; i++) {
        const file = files[i];
        const fp = (file as File & { path?: string }).path;
        if (!fp) continue;

        try {
          await invoke("copy_file_to", {
            source: fp,
            destDir: currentProject.path,
          });
          importedCount++;
        } catch (err) {
          showToast("error", `Failed to import ${file.name}: ${err}`);
        }
      }

      if (importedCount > 0) {
        showToast("success", `Imported ${importedCount} file(s)`);
        handleRefresh();
      }
    },
    [currentProject],
  );

  const displayTree = useMemo(() => {
    if (!filter.trim()) return fileTree;
    return filterTree(fileTree, filter.trim());
  }, [fileTree, filter]);

  if (fileTree.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-32 gap-3 text-stone-500 text-xs px-4 text-center">
        <span>No folder open</span>
        <button
          onClick={openFolderDialog}
          className="flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium bg-ember text-obsidian-950 hover:bg-ember-light transition-colors"
        >
          <FolderOpen size={13} />
          Open Folder
        </button>
      </div>
    );
  }

  return (
    <div
      className={"flex flex-col h-full" + (dragOver ? " ring-2 ring-inset ring-ember/40" : "")}
      onDragOver={(e) => {
        e.preventDefault();
        if (e.dataTransfer.types.includes("Files")) {
          setDragOver(true);
        }
      }}
      onDragLeave={() => setDragOver(false)}
      onDrop={handleExternalDrop}
    >
      {/* Action buttons */}
      <div className="flex items-center justify-end gap-0.5 px-2 py-1 shrink-0">
        <button
          onClick={() => {
            setFilterVisible((v) => !v);
            if (filterVisible) setFilter("");
          }}
          className={
            "p-1 rounded transition-colors " +
            (filterVisible
              ? "text-ember bg-ember/10"
              : "text-stone-500 hover:text-stone-300 hover:bg-obsidian-800")
          }
          title="Filter files"
        >
          <Search size={13} />
        </button>
        <button
          onClick={handleRefresh}
          className="p-1 rounded text-stone-500 hover:text-stone-300 hover:bg-obsidian-800 transition-colors"
          title="Refresh"
        >
          <RefreshCw size={13} />
        </button>
      </div>

      {/* Filter input */}
      {filterVisible && (
        <div className="flex items-center gap-1.5 px-2 pb-1.5 shrink-0">
          <div className="flex items-center flex-1 gap-1 bg-obsidian-950 border border-obsidian-600 rounded px-2 py-0.5">
            <Search size={11} className="text-stone-600 shrink-0" />
            <input
              autoFocus
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              placeholder="Filter files..."
              className="flex-1 bg-transparent text-[11px] text-stone-200 placeholder:text-stone-600 outline-none"
              onKeyDown={(e) => {
                if (e.key === "Escape") {
                  setFilter("");
                  setFilterVisible(false);
                }
              }}
            />
            {filter && (
              <button
                onClick={() => setFilter("")}
                className="text-stone-600 hover:text-stone-300"
              >
                <X size={10} />
              </button>
            )}
          </div>
        </div>
      )}

      {/* Tree */}
      <div className="flex-1 overflow-y-auto scrollbar-thin py-0.5">
        {displayTree.length === 0 && filter ? (
          <div className="px-3 py-4 text-center text-[11px] text-stone-600">
            No matches for "{filter}"
          </div>
        ) : (
          displayTree.map((entry) => (
            <FileTreeNode key={entry.path} entry={entry} depth={0} />
          ))
        )}
      </div>

      {/* Drop indicator */}
      {dragOver && (
        <div className="flex items-center justify-center gap-1.5 py-2 text-[11px] text-ember border-t border-ember/30 bg-ember/5">
          <Upload size={13} />
          Drop files to import
        </div>
      )}
    </div>
  );
}

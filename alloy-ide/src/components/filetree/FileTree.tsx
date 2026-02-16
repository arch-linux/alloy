import { useState, useCallback } from "react";
import { FolderOpen, RefreshCw, Upload } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";
import { showToast } from "../ui/Toast";
import FileTreeNode from "./FileTreeNode";

export default function FileTree() {
  const fileTree = useStore((s) => s.fileTree);
  const currentProject = useStore((s) => s.currentProject);
  const openFolderDialog = useStore((s) => s.openFolderDialog);
  const openProject = useStore((s) => s.openProject);
  const [dragOver, setDragOver] = useState(false);

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
          onClick={handleRefresh}
          className="p-1 rounded text-stone-500 hover:text-stone-300 hover:bg-obsidian-800 transition-colors"
          title="Refresh"
        >
          <RefreshCw size={13} />
        </button>
      </div>
      {/* Tree */}
      <div className="flex-1 overflow-y-auto scrollbar-thin py-0.5">
        {fileTree.map((entry) => (
          <FileTreeNode key={entry.path} entry={entry} depth={0} />
        ))}
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

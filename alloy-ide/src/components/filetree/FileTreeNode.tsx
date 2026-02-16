import { useState, useCallback, useEffect, useRef } from "react";
import { ChevronRight, ChevronDown, FilePlus, FolderPlus, Trash2, Pencil, Copy } from "lucide-react";
import { useStore } from "../../lib/store";
import { showToast } from "../ui/Toast";
import FileIcon from "./FileIcon";
import type { FileEntry } from "../../lib/types";

interface FileTreeNodeProps {
  entry: FileEntry;
  depth: number;
}

export default function FileTreeNode({ entry, depth }: FileTreeNodeProps) {
  const activeFilePath = useStore((s) => s.activeFilePath);
  const toggleDirectory = useStore((s) => s.toggleDirectory);
  const openFile = useStore((s) => s.openFile);
  const pinFile = useStore((s) => s.pinFile);
  const createFile = useStore((s) => s.createFile);
  const createDirectory = useStore((s) => s.createDirectory);
  const deletePath = useStore((s) => s.deletePath);

  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null);
  const [newItemMode, setNewItemMode] = useState<"file" | "folder" | null>(null);
  const [newItemName, setNewItemName] = useState("");
  const [renaming, setRenaming] = useState(false);
  const [renameName, setRenameName] = useState("");
  const menuRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const isActive = entry.path === activeFilePath;
  const paddingLeft = 12 + depth * 16;

  const handleClick = () => {
    if (entry.is_dir) {
      toggleDirectory(entry.path);
    } else {
      openFile(entry.path, entry.name);
    }
  };

  const handleDoubleClick = () => {
    if (!entry.is_dir) {
      pinFile(entry.path);
    }
  };

  const handleContextMenu = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setContextMenu({ x: e.clientX, y: e.clientY });
  }, []);

  const handleNewFile = useCallback(() => {
    setContextMenu(null);
    setNewItemMode("file");
    setNewItemName("");
    // Expand directory if collapsed
    if (entry.is_dir && !entry.expanded) {
      toggleDirectory(entry.path);
    }
  }, [entry, toggleDirectory]);

  const handleNewFolder = useCallback(() => {
    setContextMenu(null);
    setNewItemMode("folder");
    setNewItemName("");
    if (entry.is_dir && !entry.expanded) {
      toggleDirectory(entry.path);
    }
  }, [entry, toggleDirectory]);

  const handleDelete = useCallback(() => {
    setContextMenu(null);
    // Simple confirm
    const confirmed = window.confirm(`Delete "${entry.name}"?`);
    if (confirmed) {
      deletePath(entry.path);
    }
  }, [entry, deletePath]);

  const handleRename = useCallback(() => {
    setContextMenu(null);
    setRenaming(true);
    setRenameName(entry.name);
  }, [entry.name]);

  const submitNewItem = useCallback(async () => {
    if (!newItemName.trim()) {
      setNewItemMode(null);
      return;
    }
    const parentPath = entry.is_dir ? entry.path : entry.path.substring(0, entry.path.lastIndexOf("/"));
    const fullPath = `${parentPath}/${newItemName.trim()}`;
    try {
      if (newItemMode === "file") {
        await createFile(fullPath);
        openFile(fullPath, newItemName.trim());
      } else {
        await createDirectory(fullPath);
      }
    } catch (e) {
      console.error("Failed to create:", e);
    }
    setNewItemMode(null);
    setNewItemName("");
  }, [newItemName, newItemMode, entry, createFile, createDirectory, openFile]);

  const submitRename = useCallback(async () => {
    if (!renameName.trim() || renameName === entry.name) {
      setRenaming(false);
      return;
    }
    const parentPath = entry.path.substring(0, entry.path.lastIndexOf("/"));
    const newPath = `${parentPath}/${renameName.trim()}`;
    try {
      await useStore.getState().renamePath(entry.path, newPath);
    } catch (e) {
      console.error("Failed to rename:", e);
    }
    setRenaming(false);
  }, [renameName, entry]);

  // Focus input when new item mode activates
  useEffect(() => {
    if ((newItemMode || renaming) && inputRef.current) {
      inputRef.current.focus();
    }
  }, [newItemMode, renaming]);

  // Close context menu on outside click
  useEffect(() => {
    if (!contextMenu) return;
    const handleClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setContextMenu(null);
      }
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [contextMenu]);

  return (
    <>
      <div
        onClick={handleClick}
        onDoubleClick={handleDoubleClick}
        onContextMenu={handleContextMenu}
        className={
          "flex items-center h-[26px] cursor-pointer text-xs transition-colors " +
          (isActive
            ? "bg-ember/10 text-stone-100"
            : "text-stone-300 hover:bg-obsidian-800")
        }
        style={{ paddingLeft }}
      >
        {entry.is_dir ? (
          <span className="mr-1 text-stone-500">
            {entry.expanded ? (
              <ChevronDown size={14} />
            ) : (
              <ChevronRight size={14} />
            )}
          </span>
        ) : (
          <span className="mr-1 w-[14px]" />
        )}
        <FileIcon isDir={entry.is_dir} extension={entry.extension} expanded={entry.expanded} name={entry.name} />
        {renaming ? (
          <input
            ref={inputRef}
            value={renameName}
            onChange={(e) => setRenameName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") submitRename();
              if (e.key === "Escape") setRenaming(false);
            }}
            onBlur={submitRename}
            className="ml-1.5 flex-1 bg-obsidian-950 border border-ember/50 rounded px-1 py-0 text-xs text-stone-100 outline-none"
          />
        ) : (
          <span className="ml-1.5 truncate">{entry.name}</span>
        )}
      </div>

      {/* New item input row */}
      {newItemMode && entry.is_dir && (
        <div
          className="flex items-center h-[26px] bg-obsidian-800"
          style={{ paddingLeft: paddingLeft + 16 }}
        >
          {newItemMode === "folder" ? (
            <FolderPlus size={13} className="text-ember mr-1.5 shrink-0" />
          ) : (
            <FilePlus size={13} className="text-ember mr-1.5 shrink-0" />
          )}
          <input
            ref={inputRef}
            value={newItemName}
            onChange={(e) => setNewItemName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") submitNewItem();
              if (e.key === "Escape") { setNewItemMode(null); setNewItemName(""); }
            }}
            onBlur={submitNewItem}
            placeholder={newItemMode === "file" ? "filename.java" : "folder-name"}
            className="flex-1 bg-obsidian-950 border border-ember/50 rounded px-1.5 py-0 text-xs text-stone-100 placeholder:text-stone-500 outline-none mr-2"
          />
        </div>
      )}

      {entry.expanded && entry.children && (
        <>
          {entry.children.map((child) => (
            <FileTreeNode key={child.path} entry={child} depth={depth + 1} />
          ))}
        </>
      )}

      {/* Context menu */}
      {contextMenu && (
        <div
          ref={menuRef}
          className="fixed z-50 min-w-[160px] rounded-md border border-obsidian-600 bg-obsidian-800 py-1 shadow-xl"
          style={{
            left: Math.min(contextMenu.x, window.innerWidth - 180),
            top: Math.min(contextMenu.y, window.innerHeight - 180),
          }}
        >
          {entry.is_dir && (
            <>
              <button
                onClick={handleNewFile}
                className="flex w-full items-center gap-2 px-3 py-1.5 text-[12px] text-stone-200 hover:bg-obsidian-700 hover:text-stone-100 transition-colors"
              >
                <FilePlus size={13} className="text-stone-400" />
                New File
              </button>
              <button
                onClick={handleNewFolder}
                className="flex w-full items-center gap-2 px-3 py-1.5 text-[12px] text-stone-200 hover:bg-obsidian-700 hover:text-stone-100 transition-colors"
              >
                <FolderPlus size={13} className="text-stone-400" />
                New Folder
              </button>
              <div className="my-1 border-t border-obsidian-600" />
            </>
          )}
          <button
            onClick={handleRename}
            className="flex w-full items-center gap-2 px-3 py-1.5 text-[12px] text-stone-200 hover:bg-obsidian-700 hover:text-stone-100 transition-colors"
          >
            <Pencil size={13} className="text-stone-400" />
            Rename
          </button>
          <button
            onClick={() => {
              navigator.clipboard.writeText(entry.path);
              showToast("info", "Copied path to clipboard");
              setContextMenu(null);
            }}
            className="flex w-full items-center gap-2 px-3 py-1.5 text-[12px] text-stone-200 hover:bg-obsidian-700 hover:text-stone-100 transition-colors"
          >
            <Copy size={13} className="text-stone-400" />
            Copy Path
          </button>
          <div className="my-1 border-t border-obsidian-600" />
          <button
            onClick={handleDelete}
            className="flex w-full items-center gap-2 px-3 py-1.5 text-[12px] text-red-400 hover:bg-red-500/10 transition-colors"
          >
            <Trash2 size={13} />
            Delete
          </button>
        </div>
      )}
    </>
  );
}

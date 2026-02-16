import { useState, useRef } from "react";
import { X } from "lucide-react";
import { useStore } from "../../lib/store";
import TabContextMenu from "./TabContextMenu";

export default function EditorTabs() {
  const openFiles = useStore((s) => s.openFiles);
  const activeFilePath = useStore((s) => s.activeFilePath);
  const setActiveFile = useStore((s) => s.setActiveFile);
  const closeFile = useStore((s) => s.closeFile);
  const pinFile = useStore((s) => s.pinFile);
  const reorderFiles = useStore((s) => s.reorderFiles);

  const [dragIdx, setDragIdx] = useState<number | null>(null);
  const [dropIdx, setDropIdx] = useState<number | null>(null);
  const dragStartIdx = useRef<number | null>(null);
  const [ctxMenu, setCtxMenu] = useState<{ x: number; y: number; path: string } | null>(null);

  if (openFiles.length === 0) return null;

  const handleDragStart = (e: React.DragEvent, index: number) => {
    dragStartIdx.current = index;
    setDragIdx(index);
    e.dataTransfer.effectAllowed = "move";
    e.dataTransfer.setData("text/plain", String(index));
  };

  const handleDragOver = (e: React.DragEvent, index: number) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = "move";
    setDropIdx(index);
  };

  const handleDrop = (e: React.DragEvent, toIndex: number) => {
    e.preventDefault();
    const fromIndex = dragStartIdx.current;
    if (fromIndex !== null && fromIndex !== toIndex) {
      reorderFiles(fromIndex, toIndex);
    }
    setDragIdx(null);
    setDropIdx(null);
    dragStartIdx.current = null;
  };

  const handleDragEnd = () => {
    setDragIdx(null);
    setDropIdx(null);
    dragStartIdx.current = null;
  };

  return (
    <div className="flex h-9 shrink-0 items-end bg-obsidian-900 overflow-x-auto border-b border-obsidian-700">
      {openFiles.map((file, i) => {
        const active = file.path === activeFilePath;
        const isDragging = dragIdx === i;
        const isDropTarget = dropIdx === i && dragIdx !== i;

        return (
          <div
            key={file.path}
            draggable
            onDragStart={(e) => handleDragStart(e, i)}
            onDragOver={(e) => handleDragOver(e, i)}
            onDrop={(e) => handleDrop(e, i)}
            onDragEnd={handleDragEnd}
            onClick={() => setActiveFile(file.path)}
            onDoubleClick={() => pinFile(file.path)}
            onContextMenu={(e) => {
              e.preventDefault();
              setCtxMenu({ x: e.clientX, y: e.clientY, path: file.path });
            }}
            className={
              "group flex h-full items-center gap-1.5 px-3 border-r border-obsidian-700 cursor-pointer " +
              "text-xs transition-colors shrink-0 " +
              (active
                ? "bg-obsidian-950 text-stone-100 border-b-2 border-b-ember"
                : "bg-obsidian-900 text-stone-400 hover:text-stone-200 border-b-2 border-b-transparent") +
              (isDragging ? " opacity-40" : "") +
              (isDropTarget ? " border-l-2 border-l-ember" : "")
            }
          >
            {file.dirty && (
              <span className="h-2 w-2 rounded-full bg-ember shrink-0" />
            )}
            <span className={
              "truncate max-w-[140px]" +
              (file.preview ? " italic opacity-70" : "")
            }>
              {file.name}
            </span>
            <button
              onClick={(e) => {
                e.stopPropagation();
                closeFile(file.path);
              }}
              className="ml-1 rounded p-0.5 opacity-0 group-hover:opacity-100 hover:bg-obsidian-700 transition-opacity text-stone-500 hover:text-stone-200"
            >
              <X size={12} />
            </button>
          </div>
        );
      })}
      {ctxMenu && (
        <TabContextMenu
          x={ctxMenu.x}
          y={ctxMenu.y}
          filePath={ctxMenu.path}
          onClose={() => setCtxMenu(null)}
        />
      )}
    </div>
  );
}

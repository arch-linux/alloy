import { useState, useRef, useEffect, useCallback } from "react";
import { X, Pin, ChevronLeft, ChevronRight, AlertTriangle } from "lucide-react";
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
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const [showScrollLeft, setShowScrollLeft] = useState(false);
  const [showScrollRight, setShowScrollRight] = useState(false);

  const checkOverflow = useCallback(() => {
    const el = scrollContainerRef.current;
    if (!el) return;
    setShowScrollLeft(el.scrollLeft > 0);
    setShowScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 1);
  }, []);

  useEffect(() => {
    checkOverflow();
    const el = scrollContainerRef.current;
    if (!el) return;
    el.addEventListener("scroll", checkOverflow);
    const observer = new ResizeObserver(checkOverflow);
    observer.observe(el);
    return () => {
      el.removeEventListener("scroll", checkOverflow);
      observer.disconnect();
    };
  }, [checkOverflow, openFiles.length]);

  // Scroll active tab into view
  useEffect(() => {
    if (!activeFilePath || !scrollContainerRef.current) return;
    const idx = openFiles.findIndex((f) => f.path === activeFilePath);
    if (idx === -1) return;
    const container = scrollContainerRef.current;
    const tab = container.children[idx] as HTMLElement | undefined;
    if (tab) {
      tab.scrollIntoView({ block: "nearest", inline: "nearest" });
    }
  }, [activeFilePath, openFiles]);

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

  const scrollBy = (dx: number) => {
    scrollContainerRef.current?.scrollBy({ left: dx, behavior: "smooth" });
  };

  return (
    <div className="flex h-9 shrink-0 items-end bg-obsidian-900 border-b border-obsidian-700 relative">
      {showScrollLeft && (
        <button
          onClick={() => scrollBy(-120)}
          className="absolute left-0 top-0 z-10 flex h-full w-6 items-center justify-center bg-obsidian-900 border-r border-obsidian-700 text-stone-500 hover:text-stone-300 transition-colors"
        >
          <ChevronLeft size={14} />
        </button>
      )}
      <div
        ref={scrollContainerRef}
        className={"flex h-full items-end overflow-x-auto scrollbar-none flex-1 " + (showScrollLeft ? "ml-6 " : "") + (showScrollRight ? "mr-6" : "")}
      >
      {openFiles.map((file, i) => {
        const active = file.path === activeFilePath;
        const isDragging = dragIdx === i;
        const isDropTarget = dropIdx === i && dragIdx !== i;
        const isPinned = file.pinned;

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
              "group flex h-full items-center gap-1.5 border-r border-obsidian-700 cursor-pointer " +
              "text-xs transition-colors shrink-0 " +
              (isPinned ? "px-2 " : "px-3 ") +
              (active
                ? "bg-obsidian-950 text-stone-100 border-b-2 border-b-ember"
                : "bg-obsidian-900 text-stone-400 hover:text-stone-200 border-b-2 border-b-transparent") +
              (isDragging ? " opacity-40" : "") +
              (isDropTarget ? " border-l-2 border-l-ember" : "")
            }
          >
            {/* Pin icon for pinned tabs */}
            {isPinned && (
              <Pin size={11} className="text-ember shrink-0 -rotate-45" />
            )}
            {/* Externally modified indicator */}
            {file.externallyModified && (
              <AlertTriangle size={11} className="text-yellow-400 shrink-0" />
            )}
            {/* Dirty indicator */}
            {file.dirty && !file.externallyModified && !isPinned && (
              <span className="h-2 w-2 rounded-full bg-ember shrink-0" />
            )}
            {/* File name */}
            <span className={
              "truncate " +
              (isPinned ? "max-w-[80px]" : "max-w-[140px]") +
              (file.preview ? " italic opacity-70" : "")
            }>
              {isPinned && file.dirty ? (
                <span className="flex items-center gap-1">
                  <span className="h-1.5 w-1.5 rounded-full bg-ember inline-block shrink-0" />
                  {file.name}
                </span>
              ) : (
                file.name
              )}
            </span>
            {/* Close button (hidden for pinned tabs unless hovered) */}
            {isPinned ? (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  pinFile(file.path);
                }}
                className="ml-0.5 rounded p-0.5 opacity-0 group-hover:opacity-100 hover:bg-obsidian-700 transition-opacity text-stone-500 hover:text-stone-200"
                title="Unpin"
              >
                <Pin size={10} className="-rotate-45" />
              </button>
            ) : (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  closeFile(file.path);
                }}
                className="ml-1 rounded p-0.5 opacity-0 group-hover:opacity-100 hover:bg-obsidian-700 transition-opacity text-stone-500 hover:text-stone-200"
              >
                <X size={12} />
              </button>
            )}
          </div>
        );
      })}
      </div>
      {showScrollRight && (
        <button
          onClick={() => scrollBy(120)}
          className="absolute right-0 top-0 z-10 flex h-full w-6 items-center justify-center bg-obsidian-900 border-l border-obsidian-700 text-stone-500 hover:text-stone-300 transition-colors"
        >
          <ChevronRight size={14} />
        </button>
      )}
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

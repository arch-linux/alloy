import { useCallback, useRef } from "react";
import { useStore } from "../../lib/store";

export default function Sidebar({ children }: { children?: React.ReactNode }) {
  const sidebarVisible = useStore((s) => s.sidebarVisible);
  const sidebarWidth = useStore((s) => s.sidebarWidth);
  const sidebarPanel = useStore((s) => s.sidebarPanel);
  const setSidebarWidth = useStore((s) => s.setSidebarWidth);
  const dragRef = useRef(false);
  const startXRef = useRef(0);
  const startWidthRef = useRef(0);

  const panelLabels: Record<string, string> = {
    files: "EXPLORER",
    search: "SEARCH",
    git: "SOURCE CONTROL",
    extensions: "EXTENSIONS",
    tasks: "TASKS",
    ai: "AI ASSISTANT",
  };

  const onMouseDown = useCallback(
    (e: React.MouseEvent) => {
      dragRef.current = true;
      startXRef.current = e.clientX;
      startWidthRef.current = sidebarWidth;

      const onMouseMove = (e: MouseEvent) => {
        if (!dragRef.current) return;
        const delta = e.clientX - startXRef.current;
        setSidebarWidth(startWidthRef.current + delta);
      };

      const onMouseUp = () => {
        dragRef.current = false;
        document.removeEventListener("mousemove", onMouseMove);
        document.removeEventListener("mouseup", onMouseUp);
      };

      document.addEventListener("mousemove", onMouseMove);
      document.addEventListener("mouseup", onMouseUp);
    },
    [sidebarWidth, setSidebarWidth],
  );

  if (!sidebarVisible) return null;

  return (
    <div
      className="relative flex h-full shrink-0 flex-col bg-obsidian-900 border-r border-obsidian-700"
      style={{ width: sidebarWidth }}
    >
      <div className="flex h-9 items-center px-4 shrink-0">
        <span className="text-[11px] font-semibold tracking-wider text-stone-400">
          {panelLabels[sidebarPanel] ?? sidebarPanel.toUpperCase()}
        </span>
      </div>
      <div className="flex-1 overflow-y-auto overflow-x-hidden">
        {children}
      </div>
      {/* Resize handle */}
      <div
        onMouseDown={onMouseDown}
        className="absolute right-0 top-0 h-full w-1 cursor-col-resize hover:bg-ember/30 transition-colors z-10"
      />
    </div>
  );
}

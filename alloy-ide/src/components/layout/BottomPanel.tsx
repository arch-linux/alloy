import { useCallback, useRef } from "react";
import { Terminal, AlertTriangle, FileText } from "lucide-react";
import { useStore } from "../../lib/store";
import type { BottomPanelTab } from "../../lib/types";

const tabs: { id: BottomPanelTab; icon: typeof Terminal; label: string }[] = [
  { id: "terminal", icon: Terminal, label: "Terminal" },
  { id: "problems", icon: AlertTriangle, label: "Problems" },
  { id: "output", icon: FileText, label: "Output" },
];

export default function BottomPanel({ children }: { children?: React.ReactNode }) {
  const bottomPanelVisible = useStore((s) => s.bottomPanelVisible);
  const bottomPanel = useStore((s) => s.bottomPanel);
  const bottomPanelHeight = useStore((s) => s.bottomPanelHeight);
  const setBottomPanel = useStore((s) => s.setBottomPanel);
  const setBottomPanelHeight = useStore((s) => s.setBottomPanelHeight);
  const toggleBottomPanel = useStore((s) => s.toggleBottomPanel);
  const dragRef = useRef(false);
  const startYRef = useRef(0);
  const startHeightRef = useRef(0);

  const onMouseDown = useCallback(
    (e: React.MouseEvent) => {
      dragRef.current = true;
      startYRef.current = e.clientY;
      startHeightRef.current = bottomPanelHeight;

      const onMouseMove = (e: MouseEvent) => {
        if (!dragRef.current) return;
        const delta = startYRef.current - e.clientY;
        setBottomPanelHeight(startHeightRef.current + delta);
      };

      const onMouseUp = () => {
        dragRef.current = false;
        document.removeEventListener("mousemove", onMouseMove);
        document.removeEventListener("mouseup", onMouseUp);
      };

      document.addEventListener("mousemove", onMouseMove);
      document.addEventListener("mouseup", onMouseUp);
    },
    [bottomPanelHeight, setBottomPanelHeight],
  );

  if (!bottomPanelVisible) return null;

  return (
    <div
      className="relative flex shrink-0 flex-col border-t border-obsidian-700 bg-obsidian-900"
      style={{ height: bottomPanelHeight }}
    >
      {/* Resize handle */}
      <div
        onMouseDown={onMouseDown}
        className="absolute top-0 left-0 right-0 h-1 cursor-row-resize hover:bg-ember/30 transition-colors z-10"
      />
      {/* Tab bar */}
      <div className="flex h-8 items-center border-b border-obsidian-700 px-2 gap-1 shrink-0">
        {tabs.map(({ id, icon: Icon, label }) => {
          const active = bottomPanel === id;
          return (
            <button
              key={id}
              onClick={() => setBottomPanel(id)}
              className={
                "flex items-center gap-1.5 px-2.5 py-1 rounded text-xs transition-colors " +
                (active
                  ? "text-stone-100 bg-obsidian-800"
                  : "text-stone-500 hover:text-stone-300")
              }
            >
              <Icon size={13} />
              {label}
            </button>
          );
        })}
        <div className="flex-1" />
        <button
          onClick={toggleBottomPanel}
          className="text-stone-500 hover:text-stone-300 p-1 rounded hover:bg-obsidian-800 transition-colors"
        >
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.5">
            <line x1="2" y1="2" x2="10" y2="10" />
            <line x1="10" y1="2" x2="2" y2="10" />
          </svg>
        </button>
      </div>
      {/* Content */}
      <div className="flex-1 overflow-auto min-h-0">
        {children}
      </div>
    </div>
  );
}

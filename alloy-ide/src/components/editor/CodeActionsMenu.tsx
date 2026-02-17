import { useEffect, useRef } from "react";
import { Lightbulb, Wrench, FileCode } from "lucide-react";
import type { LspCodeAction } from "../../lib/lsp";

interface CodeActionsMenuProps {
  actions: LspCodeAction[];
  x: number;
  y: number;
  onSelect: (action: LspCodeAction) => void;
  onClose: () => void;
}

function actionIcon(kind: string | null) {
  if (kind?.includes("quickfix")) return <Wrench size={11} />;
  if (kind?.includes("refactor")) return <FileCode size={11} />;
  return <Lightbulb size={11} />;
}

export default function CodeActionsMenu({
  actions,
  x,
  y,
  onSelect,
  onClose,
}: CodeActionsMenuProps) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        onClose();
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [onClose]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [onClose]);

  return (
    <div
      ref={ref}
      className="fixed z-[200] min-w-[220px] max-w-[400px] max-h-[280px] overflow-y-auto rounded-md border border-obsidian-600 bg-obsidian-800 py-1 shadow-2xl"
      style={{ left: x, top: y }}
    >
      {actions.map((action, i) => (
        <button
          key={i}
          onClick={() => {
            onSelect(action);
            onClose();
          }}
          className="flex w-full items-center gap-2 px-3 py-1.5 text-xs text-stone-300 hover:bg-obsidian-700 hover:text-stone-100 transition-colors text-left"
        >
          <span className="text-ember shrink-0">
            {actionIcon(action.kind)}
          </span>
          <span className="truncate">{action.title}</span>
        </button>
      ))}
    </div>
  );
}

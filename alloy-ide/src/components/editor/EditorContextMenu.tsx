import { useEffect, useRef } from "react";
import { Bot, MessageSquare, RefreshCw, TestTube, FileCode } from "lucide-react";

interface EditorContextMenuProps {
  x: number;
  y: number;
  selectedText: string;
  onClose: () => void;
  onAiAction: (action: string, text: string) => void;
}

const aiActions = [
  { id: "explain", label: "Explain Code", icon: MessageSquare },
  { id: "refactor", label: "Refactor", icon: RefreshCw },
  { id: "tests", label: "Generate Tests", icon: TestTube },
  { id: "document", label: "Add Documentation", icon: FileCode },
];

export default function EditorContextMenu({
  x,
  y,
  selectedText,
  onClose,
  onAiAction,
}: EditorContextMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("mousedown", handleClick);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleClick);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [onClose]);

  // Adjust position to stay within viewport
  const adjustedX = Math.min(x, window.innerWidth - 200);
  const adjustedY = Math.min(y, window.innerHeight - 180);

  return (
    <div
      ref={menuRef}
      className="fixed z-50 min-w-[180px] rounded-md border border-obsidian-600 bg-obsidian-800 py-1 shadow-xl"
      style={{ left: adjustedX, top: adjustedY }}
    >
      <div className="px-3 py-1.5 text-[10px] text-stone-500 uppercase tracking-wider flex items-center gap-1.5">
        <Bot size={10} />
        AI Actions
      </div>
      {aiActions.map(({ id, label, icon: Icon }) => (
        <button
          key={id}
          onClick={() => {
            onAiAction(id, selectedText);
            onClose();
          }}
          disabled={!selectedText}
          className={
            "flex w-full items-center gap-2 px-3 py-1.5 text-[12px] transition-colors " +
            (selectedText
              ? "text-stone-200 hover:bg-obsidian-700 hover:text-stone-100"
              : "text-stone-600 cursor-not-allowed")
          }
        >
          <Icon size={13} className={selectedText ? "text-ember" : "text-stone-600"} />
          {label}
        </button>
      ))}
      {!selectedText && (
        <div className="px-3 py-1 text-[10px] text-stone-600 italic">
          Select code first
        </div>
      )}
    </div>
  );
}

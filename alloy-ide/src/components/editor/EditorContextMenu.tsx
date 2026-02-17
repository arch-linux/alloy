import { useEffect, useRef, useState } from "react";
import {
  Bot, MessageSquare, RefreshCw, TestTube, FileCode,
  Copy, Scissors, ClipboardPaste, TextSelect,
  Compass, BookOpen, PenLine, ChevronRight,
  Columns2, WrapText,
} from "lucide-react";

interface EditorContextMenuProps {
  x: number;
  y: number;
  selectedText: string;
  onClose: () => void;
  onAiAction: (action: string, text: string) => void;
  onSplitRight?: () => void;
  onToggleWordWrap?: () => void;
}

const isMac = typeof navigator !== "undefined" && navigator.platform.includes("Mac");
const mod = isMac ? "\u2318" : "Ctrl";

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
  onSplitRight,
  onToggleWordWrap,
}: EditorContextMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);
  const [aiSubOpen, setAiSubOpen] = useState(false);

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
  const menuWidth = 220;
  const menuHeight = 360;
  const adjustedX = Math.min(x, window.innerWidth - menuWidth);
  const adjustedY = Math.min(y, window.innerHeight - menuHeight);

  const execCommand = (cmd: string) => {
    document.execCommand(cmd);
    onClose();
  };

  return (
    <div
      ref={menuRef}
      className="fixed z-50 min-w-[210px] rounded-md border border-obsidian-600 bg-obsidian-800 py-1 shadow-xl"
      style={{ left: adjustedX, top: adjustedY }}
    >
      {/* Standard edit actions */}
      <MenuItem
        icon={<Scissors size={13} />}
        label="Cut"
        shortcut={`${mod}+X`}
        disabled={!selectedText}
        onClick={() => execCommand("cut")}
      />
      <MenuItem
        icon={<Copy size={13} />}
        label="Copy"
        shortcut={`${mod}+C`}
        disabled={!selectedText}
        onClick={() => execCommand("copy")}
      />
      <MenuItem
        icon={<ClipboardPaste size={13} />}
        label="Paste"
        shortcut={`${mod}+V`}
        onClick={() => execCommand("paste")}
      />
      <MenuItem
        icon={<TextSelect size={13} />}
        label="Select All"
        shortcut={`${mod}+A`}
        onClick={() => execCommand("selectAll")}
      />

      <Separator />

      {/* Code navigation (LSP stubs) */}
      <MenuItem
        icon={<Compass size={13} />}
        label="Go to Definition"
        shortcut="F12"
        disabled
        onClick={() => {}}
      />
      <MenuItem
        icon={<BookOpen size={13} />}
        label="Find References"
        shortcut={`${mod}+Shift+F12`}
        disabled
        onClick={() => {}}
      />
      <MenuItem
        icon={<PenLine size={13} />}
        label="Rename Symbol"
        shortcut="F2"
        disabled
        onClick={() => {}}
      />

      <Separator />

      {/* Editor actions */}
      <MenuItem
        icon={<Columns2 size={13} />}
        label="Split Editor Right"
        shortcut={`${mod}+\\`}
        onClick={() => {
          onSplitRight?.();
          onClose();
        }}
      />
      <MenuItem
        icon={<WrapText size={13} />}
        label="Toggle Word Wrap"
        onClick={() => {
          onToggleWordWrap?.();
          onClose();
        }}
      />

      <Separator />

      {/* AI Actions submenu */}
      <div
        className="relative"
        onMouseEnter={() => setAiSubOpen(true)}
        onMouseLeave={() => setAiSubOpen(false)}
      >
        <div
          className={
            "flex w-full items-center gap-2 px-3 py-1.5 text-[12px] transition-colors cursor-pointer " +
            (selectedText
              ? "text-stone-200 hover:bg-obsidian-700"
              : "text-stone-500")
          }
        >
          <Bot size={13} className={selectedText ? "text-ember" : "text-stone-600"} />
          <span className="flex-1">AI Actions</span>
          <ChevronRight size={11} className="text-stone-500" />
        </div>

        {/* Submenu */}
        {aiSubOpen && (
          <div
            className="absolute left-full top-0 ml-0.5 min-w-[180px] rounded-md border border-obsidian-600 bg-obsidian-800 py-1 shadow-xl"
            style={{
              // Flip to left if near right edge
              ...(adjustedX + menuWidth + 180 > window.innerWidth
                ? { left: "auto", right: "100%", marginLeft: 0, marginRight: 2 }
                : {}),
            }}
          >
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
        )}
      </div>
    </div>
  );
}

function MenuItem({
  icon,
  label,
  shortcut,
  disabled,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  shortcut?: string;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={
        "flex w-full items-center gap-2 px-3 py-1.5 text-[12px] transition-colors " +
        (disabled
          ? "text-stone-600 cursor-not-allowed"
          : "text-stone-200 hover:bg-obsidian-700 hover:text-stone-100")
      }
    >
      <span className={disabled ? "text-stone-600" : "text-stone-400"}>{icon}</span>
      <span className="flex-1 text-left">{label}</span>
      {shortcut && (
        <span className="text-[10px] text-stone-600 ml-4">{shortcut}</span>
      )}
    </button>
  );
}

function Separator() {
  return <div className="my-1 h-px bg-obsidian-600" />;
}

import { X, Keyboard } from "lucide-react";

const isMac = navigator.platform.toUpperCase().includes("MAC");
const mod = isMac ? "\u2318" : "Ctrl";
const shift = isMac ? "\u21E7" : "Shift";

interface ShortcutEntry {
  label: string;
  keys: string;
}

interface ShortcutGroup {
  category: string;
  shortcuts: ShortcutEntry[];
}

const SHORTCUTS: ShortcutGroup[] = [
  {
    category: "General",
    shortcuts: [
      { label: "Command Palette", keys: `${mod}+${shift}+P` },
      { label: "Quick Open (Go to File)", keys: `${mod}+P` },
      { label: "Settings", keys: `${mod}+,` },
      { label: "Keyboard Shortcuts", keys: `${mod}+K ${mod}+S` },
      { label: "Open Folder", keys: `${mod}+O` },
    ],
  },
  {
    category: "Editor",
    shortcuts: [
      { label: "Save", keys: `${mod}+S` },
      { label: "Find in File", keys: `${mod}+F` },
      { label: "Replace in File", keys: `${mod}+H` },
      { label: "Go to Line", keys: `${mod}+G` },
      { label: "Indent Line", keys: "Tab" },
      { label: "Outdent Line", keys: `${shift}+Tab` },
      { label: "Zoom In", keys: `${mod}+=` },
      { label: "Zoom Out", keys: `${mod}+-` },
      { label: "Reset Zoom", keys: `${mod}+0` },
    ],
  },
  {
    category: "Split Editor",
    shortcuts: [
      { label: "Split Right", keys: `${mod}+\\` },
      { label: "Close Split", keys: `${mod}+${shift}+\\` },
    ],
  },
  {
    category: "Tabs",
    shortcuts: [
      { label: "Close Tab", keys: `${mod}+W` },
      { label: "Reopen Closed Tab", keys: `${mod}+${shift}+T` },
    ],
  },
  {
    category: "Panels",
    shortcuts: [
      { label: "Toggle Sidebar", keys: `${mod}+B` },
      { label: "Toggle Terminal", keys: `${mod}+\`` },
      { label: "Explorer", keys: `${mod}+${shift}+E` },
      { label: "Search", keys: `${mod}+${shift}+F` },
      { label: "Search & Replace", keys: `${mod}+${shift}+H` },
      { label: "Source Control", keys: `${mod}+${shift}+G` },
    ],
  },
  {
    category: "Build",
    shortcuts: [
      { label: "Build Project", keys: `${mod}+${shift}+B` },
    ],
  },
];

interface KeyboardShortcutsProps {
  onClose: () => void;
}

export default function KeyboardShortcuts({ onClose }: KeyboardShortcutsProps) {
  return (
    <div
      className="fixed inset-0 z-[150] flex items-center justify-center"
      onClick={onClose}
    >
      <div className="absolute inset-0 bg-obsidian-950/80" />
      <div
        className="relative z-10 w-[520px] max-h-[70vh] rounded-xl border border-obsidian-600 bg-obsidian-800 shadow-2xl overflow-hidden flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-obsidian-700">
          <div className="flex items-center gap-2">
            <Keyboard size={16} className="text-ember" />
            <span className="text-sm font-semibold text-stone-100">Keyboard Shortcuts</span>
          </div>
          <button
            onClick={onClose}
            className="rounded-md p-1 text-stone-500 hover:text-stone-200 hover:bg-obsidian-700 transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        {/* Shortcuts list */}
        <div className="flex-1 overflow-y-auto px-5 py-3 space-y-4">
          {SHORTCUTS.map((group) => (
            <div key={group.category}>
              <h3 className="text-[10px] font-semibold text-stone-500 uppercase tracking-wider mb-1.5">
                {group.category}
              </h3>
              <div className="space-y-0.5">
                {group.shortcuts.map((sc) => (
                  <div
                    key={sc.label}
                    className="flex items-center justify-between py-1 px-2 rounded hover:bg-obsidian-700/50"
                  >
                    <span className="text-xs text-stone-300">{sc.label}</span>
                    <KeyCombo keys={sc.keys} />
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function KeyCombo({ keys }: { keys: string }) {
  const parts = keys.split("+").map((k) => k.trim());
  return (
    <span className="flex items-center gap-0.5">
      {parts.map((part, i) => (
        <span key={i} className="flex items-center gap-0.5">
          {i > 0 && <span className="text-stone-600 text-[10px] mx-0.5">+</span>}
          <kbd className="inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded bg-obsidian-900 border border-obsidian-600 text-[10px] text-stone-300 font-mono">
            {part}
          </kbd>
        </span>
      ))}
    </span>
  );
}

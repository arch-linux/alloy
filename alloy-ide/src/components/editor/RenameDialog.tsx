import { useState, useEffect, useRef } from "react";

interface RenameDialogProps {
  currentName: string;
  onRename: (newName: string) => void;
  onClose: () => void;
}

export default function RenameDialog({
  currentName,
  onRename,
  onClose,
}: RenameDialogProps) {
  const [value, setValue] = useState(currentName);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const handleSubmit = () => {
    const trimmed = value.trim();
    if (trimmed && trimmed !== currentName) {
      onRename(trimmed);
    }
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center"
      onClick={onClose}
    >
      <div className="absolute inset-0 bg-obsidian-950/60" />
      <div
        className="relative w-80 rounded-lg border border-obsidian-600 bg-obsidian-800 shadow-2xl p-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="text-xs font-medium text-stone-300 mb-2">
          Rename Symbol
        </div>
        <input
          ref={inputRef}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") handleSubmit();
            if (e.key === "Escape") onClose();
          }}
          className="w-full bg-obsidian-900 border border-obsidian-600 rounded px-3 py-1.5 text-sm text-stone-200 outline-none focus:border-ember/50"
          spellCheck={false}
        />
        <div className="flex justify-end gap-2 mt-3">
          <button
            onClick={onClose}
            className="px-3 py-1 text-xs text-stone-400 hover:text-stone-200 rounded hover:bg-obsidian-700 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSubmit}
            disabled={!value.trim() || value.trim() === currentName}
            className="px-3 py-1 text-xs bg-ember text-obsidian-950 rounded hover:bg-ember-light transition-colors disabled:opacity-40 disabled:cursor-not-allowed font-medium"
          >
            Rename
          </button>
        </div>
      </div>
    </div>
  );
}

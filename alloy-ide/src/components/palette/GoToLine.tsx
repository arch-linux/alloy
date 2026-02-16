import { useState, useEffect, useRef } from "react";

interface GoToLineProps {
  onClose: () => void;
  onGo: (line: number) => void;
  maxLine?: number;
}

export default function GoToLine({ onClose, onGo, maxLine }: GoToLineProps) {
  const [value, setValue] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      const num = parseInt(value, 10);
      if (!isNaN(num) && num > 0) {
        onGo(num);
        onClose();
      }
    } else if (e.key === "Escape") {
      onClose();
    }
  };

  return (
    <div
      className="fixed inset-0 z-[100] flex items-start justify-center pt-[15vh]"
      onClick={onClose}
    >
      <div
        className="w-[320px] rounded-lg border border-obsidian-600 bg-obsidian-800 shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-2 px-3 py-2.5">
          <span className="text-xs text-stone-500">Go to Line</span>
          <input
            ref={inputRef}
            value={value}
            onChange={(e) => setValue(e.target.value.replace(/\D/g, ""))}
            onKeyDown={handleKeyDown}
            placeholder={maxLine ? `Line (1-${maxLine})` : "Line number"}
            className="flex-1 bg-obsidian-900 border border-obsidian-600 rounded px-2 py-1 text-sm text-stone-100 placeholder:text-stone-500 outline-none focus:border-ember/50"
          />
        </div>
      </div>
    </div>
  );
}

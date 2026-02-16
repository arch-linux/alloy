import { useState, useRef, useCallback } from "react";
import { Send } from "lucide-react";

interface ChatInputProps {
  onSend: (message: string) => void;
  disabled?: boolean;
}

export default function ChatInput({ onSend, disabled }: ChatInputProps) {
  const [value, setValue] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleSend = useCallback(() => {
    const trimmed = value.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed);
    setValue("");
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
    }
  }, [value, disabled, onSend]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleInput = () => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 120) + "px";
  };

  return (
    <div className="flex items-end gap-2 border-t border-obsidian-600 bg-obsidian-900 p-2">
      <textarea
        ref={textareaRef}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={handleKeyDown}
        onInput={handleInput}
        placeholder={disabled ? "AI is thinking..." : "Ask the AI assistant..."}
        disabled={disabled}
        rows={1}
        className={
          "flex-1 resize-none rounded-md border border-obsidian-600 bg-obsidian-950 px-3 py-2 " +
          "text-[13px] text-stone-100 placeholder:text-stone-500 " +
          "focus:border-ember focus:outline-none focus:ring-1 focus:ring-ember/30 " +
          "disabled:opacity-50 disabled:cursor-not-allowed " +
          "scrollbar-thin"
        }
      />
      <button
        onClick={handleSend}
        disabled={disabled || !value.trim()}
        className={
          "flex h-8 w-8 shrink-0 items-center justify-center rounded-md transition-colors " +
          (disabled || !value.trim()
            ? "bg-obsidian-700 text-stone-500 cursor-not-allowed"
            : "bg-ember text-obsidian-950 hover:bg-ember-light")
        }
      >
        <Send size={14} />
      </button>
    </div>
  );
}

import type { InputHTMLAttributes } from "react";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
}

export default function Input({ label, className = "", ...props }: InputProps) {
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label className="text-xs font-medium text-stone-400">{label}</label>
      )}
      <input
        className={
          "rounded-lg border border-obsidian-600 bg-obsidian-900 px-3 py-2 text-sm text-stone-200 " +
          "placeholder:text-stone-500 outline-none transition-colors " +
          "focus:border-ember/50 focus:ring-1 focus:ring-ember/20 " +
          className
        }
        {...props}
      />
    </div>
  );
}

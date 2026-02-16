import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
}

const variants: Record<Variant, string> = {
  primary:
    "bg-gradient-to-r from-ember to-ember-dark text-obsidian-950 font-semibold " +
    "hover:from-ember-light hover:to-ember shadow-[0_0_24px_rgba(255,107,0,0.3)] " +
    "hover:shadow-[0_0_32px_rgba(255,107,0,0.5)] active:scale-[0.97]",
  secondary:
    "bg-obsidian-700 text-stone-200 border border-obsidian-600 " +
    "hover:bg-obsidian-600 hover:border-obsidian-500 active:scale-[0.97]",
  ghost:
    "bg-transparent text-stone-400 hover:text-stone-200 hover:bg-obsidian-800 active:scale-[0.97]",
  danger:
    "bg-red-600/10 text-red-400 border border-red-600/20 " +
    "hover:bg-red-600/20 hover:border-red-600/30 active:scale-[0.97]",
};

export default function Button({
  variant = "primary",
  className = "",
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      className={
        "inline-flex items-center justify-center gap-2 rounded-lg px-5 py-2.5 text-sm " +
        "transition-all duration-200 cursor-pointer disabled:opacity-50 disabled:pointer-events-none " +
        variants[variant] +
        " " +
        className
      }
      {...props}
    >
      {children}
    </button>
  );
}

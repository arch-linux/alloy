import type { HTMLAttributes } from "react";

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  glow?: boolean;
}

export default function Card({
  glow = false,
  className = "",
  children,
  ...props
}: CardProps) {
  return (
    <div
      className={
        "rounded-xl border border-obsidian-600 bg-obsidian-800/60 backdrop-blur-sm " +
        "transition-all duration-300 " +
        (glow
          ? "hover:border-ember/30 hover:shadow-[0_0_40px_rgba(255,107,0,0.06)] "
          : "hover:border-obsidian-500 ") +
        className
      }
      {...props}
    >
      {children}
    </div>
  );
}

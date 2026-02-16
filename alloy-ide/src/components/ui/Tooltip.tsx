import { useState, useRef, type ReactNode } from "react";

interface TooltipProps {
  content: string;
  side?: "right" | "bottom";
  children: ReactNode;
}

export default function Tooltip({ content, side = "right", children }: TooltipProps) {
  const [visible, setVisible] = useState(false);
  const timeout = useRef<ReturnType<typeof setTimeout>>(undefined);

  const show = () => {
    timeout.current = setTimeout(() => setVisible(true), 400);
  };

  const hide = () => {
    clearTimeout(timeout.current);
    setVisible(false);
  };

  const positionClass =
    side === "right"
      ? "left-full ml-2 top-1/2 -translate-y-1/2"
      : "top-full mt-2 left-1/2 -translate-x-1/2";

  return (
    <div className="relative" onMouseEnter={show} onMouseLeave={hide}>
      {children}
      {visible && (
        <div
          className={
            "absolute z-50 whitespace-nowrap rounded-md bg-obsidian-700 px-2.5 py-1.5 " +
            "text-xs text-stone-200 border border-obsidian-600 shadow-lg pointer-events-none " +
            positionClass
          }
        >
          {content}
        </div>
      )}
    </div>
  );
}

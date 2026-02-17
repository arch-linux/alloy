import { useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";

interface SidebarSectionProps {
  title: string;
  defaultOpen?: boolean;
  badge?: number;
  children: React.ReactNode;
}

export default function SidebarSection({
  title,
  defaultOpen = true,
  badge,
  children,
}: SidebarSectionProps) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div className="flex flex-col">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1 h-[22px] px-2 text-[10px] font-semibold tracking-wider text-stone-500 hover:text-stone-300 bg-obsidian-900 hover:bg-obsidian-800 transition-colors shrink-0 select-none uppercase"
      >
        {open ? <ChevronDown size={10} /> : <ChevronRight size={10} />}
        <span className="flex-1 text-left">{title}</span>
        {badge !== undefined && badge > 0 && (
          <span className="text-[9px] text-stone-600 font-normal">{badge}</span>
        )}
      </button>
      {open && children}
    </div>
  );
}

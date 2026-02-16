import {
  LayoutGrid,
  ArrowRight,
  Zap,
  Droplets,
  MousePointerClick,
  Type,
  Square,
  Image,
} from "lucide-react";
import type { GuiWidgetType } from "../../lib/types";

interface Props {
  onAdd: (type: GuiWidgetType) => void;
}

const widgets: { type: GuiWidgetType; label: string; desc: string; icon: React.ReactNode }[] = [
  { type: "slot", label: "Slot", desc: "Inventory slot (input/output)", icon: <LayoutGrid size={14} /> },
  { type: "progress_bar", label: "Progress", desc: "Machine progress indicator", icon: <ArrowRight size={14} /> },
  { type: "energy_bar", label: "Energy", desc: "Energy/power level bar", icon: <Zap size={14} /> },
  { type: "fluid_tank", label: "Fluid", desc: "Fluid tank display", icon: <Droplets size={14} /> },
  { type: "button", label: "Button", desc: "Clickable button control", icon: <MousePointerClick size={14} /> },
  { type: "label", label: "Label", desc: "Text label or title", icon: <Type size={14} /> },
  { type: "region", label: "Region", desc: "Custom area with tooltip", icon: <Square size={14} /> },
  { type: "image", label: "Image", desc: "Texture region reference", icon: <Image size={14} /> },
];

export default function WidgetPalette({ onAdd }: Props) {
  return (
    <div className="w-[160px] shrink-0 border-r border-obsidian-700 bg-obsidian-900 flex flex-col">
      <div className="px-3 py-2 border-b border-obsidian-700">
        <h3 className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold">Widgets</h3>
      </div>
      <div className="flex-1 overflow-y-auto py-1 space-y-0.5">
        {widgets.map((w) => (
          <button
            key={w.type}
            onClick={() => onAdd(w.type)}
            className="w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-obsidian-800 transition-colors group cursor-pointer"
          >
            <div className="text-stone-500 group-hover:text-ember transition-colors">
              {w.icon}
            </div>
            <div className="min-w-0">
              <div className="text-[11px] text-stone-300 font-medium truncate">{w.label}</div>
              <div className="text-[9px] text-stone-600 truncate">{w.desc}</div>
            </div>
          </button>
        ))}
      </div>
      <div className="px-3 py-2 border-t border-obsidian-700">
        <p className="text-[9px] text-stone-600 leading-relaxed">
          Click to add a widget to the center of the canvas. Drag to reposition.
        </p>
      </div>
    </div>
  );
}

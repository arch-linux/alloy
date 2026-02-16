import { Files, Search, GitBranch, Blocks, Bot, Settings, Play } from "lucide-react";
import { useStore } from "../../lib/store";
import Tooltip from "../ui/Tooltip";
import type { SidebarPanel } from "../../lib/types";

const items: { panel: SidebarPanel; icon: typeof Files; label: string }[] = [
  { panel: "files", icon: Files, label: "Explorer" },
  { panel: "search", icon: Search, label: "Search" },
  { panel: "git", icon: GitBranch, label: "Source Control" },
  { panel: "extensions", icon: Blocks, label: "Extensions" },
  { panel: "tasks", icon: Play, label: "Tasks" },
];

function Badge({ count }: { count: number }) {
  if (count <= 0) return null;
  return (
    <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 flex items-center justify-center rounded-full bg-ember text-obsidian-950 text-[9px] font-bold px-1">
      {count > 99 ? "99+" : count}
    </span>
  );
}

export default function ActivityBar() {
  const sidebarPanel = useStore((s) => s.sidebarPanel);
  const sidebarVisible = useStore((s) => s.sidebarVisible);
  const setSidebarPanel = useStore((s) => s.setSidebarPanel);
  const buildErrors = useStore((s) => s.buildErrors);
  const searchResults = useStore((s) => s.searchResults);

  const aiActive = sidebarPanel === "ai" && sidebarVisible;

  // Badge counts per panel
  const badgeCounts: Record<string, number> = {
    search: searchResults.length,
  };

  return (
    <div className="flex h-full w-12 shrink-0 flex-col items-center border-r border-obsidian-700 bg-obsidian-900 py-2 gap-1">
      {items.map(({ panel, icon: Icon, label }) => {
        const active = sidebarPanel === panel && sidebarVisible;
        return (
          <Tooltip key={panel} content={label} side="right">
            <button
              onClick={() => setSidebarPanel(panel)}
              className={
                "relative flex h-10 w-10 items-center justify-center rounded-lg transition-all duration-200 " +
                (active
                  ? "text-ember"
                  : "text-stone-500 hover:text-stone-300 hover:bg-obsidian-800")
              }
            >
              {active && (
                <div className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 bg-ember rounded-r" />
              )}
              <Icon size={20} strokeWidth={1.5} />
              <Badge count={badgeCounts[panel] || 0} />
            </button>
          </Tooltip>
        );
      })}

      {/* Spacer pushes AI to bottom */}
      <div className="flex-1" />

      {/* AI Assistant */}
      <Tooltip content="AI Assistant" side="right">
        <button
          onClick={() => setSidebarPanel("ai")}
          className={
            "relative flex h-10 w-10 items-center justify-center rounded-lg transition-all duration-200 " +
            (aiActive
              ? "text-ember"
              : "text-stone-500 hover:text-stone-300 hover:bg-obsidian-800")
          }
        >
          {aiActive && (
            <div className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 bg-ember rounded-r" />
          )}
          <Bot size={20} strokeWidth={1.5} />
        </button>
      </Tooltip>

      {/* Settings */}
      <Tooltip content="Settings" side="right">
        <button
          onClick={() => useStore.getState().toggleSettings()}
          className="flex h-10 w-10 items-center justify-center rounded-lg transition-all duration-200 text-stone-500 hover:text-stone-300 hover:bg-obsidian-800"
        >
          <Settings size={20} strokeWidth={1.5} />
        </button>
      </Tooltip>
    </div>
  );
}

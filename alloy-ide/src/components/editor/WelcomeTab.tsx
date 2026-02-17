import { useEffect } from "react";
import { FolderOpen, Clock, Hammer, Shield, FileText } from "lucide-react";
import { useStore } from "../../lib/store";
import alloyLogo from "../../assets/alloy-logo.svg";

export default function WelcomeTab() {
  const openFolderDialog = useStore((s) => s.openFolderDialog);
  const recentProjects = useStore((s) => s.recentProjects);
  const openProject = useStore((s) => s.openProject);
  const loadRecentProjects = useStore((s) => s.loadRecentProjects);
  const recentlyClosed = useStore((s) => s.recentlyClosed);
  const openFile = useStore((s) => s.openFile);

  const isMac = navigator.platform.includes("Mac");
  const mod = isMac ? "\u2318" : "Ctrl";

  useEffect(() => {
    loadRecentProjects();
  }, [loadRecentProjects]);

  const formatTime = (timestamp: number) => {
    const date = new Date(timestamp * 1000);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins < 1) return "just now";
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  };

  const projectTypeBadge = (type: string) => {
    switch (type) {
      case "mod":
        return (
          <span className="flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[9px] font-medium bg-ember/15 text-ember border border-ember/30">
            <Shield size={8} />
            MOD
          </span>
        );
      case "modpack":
        return (
          <span className="flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[9px] font-medium bg-forge-gold/15 text-forge-gold border border-forge-gold/30">
            <Hammer size={8} />
            MODPACK
          </span>
        );
      default:
        return (
          <span className="px-1.5 py-0.5 rounded text-[9px] font-medium bg-obsidian-700 text-stone-400 border border-obsidian-600">
            PROJECT
          </span>
        );
    }
  };

  const shortcuts = [
    { keys: `${mod}+O`, desc: "Open Folder" },
    { keys: `${mod}+P`, desc: "Quick Open File" },
    { keys: `${mod}+Shift+P`, desc: "Command Palette" },
    { keys: `${mod}+Shift+F`, desc: "Search in Files" },
    { keys: `${mod}+Shift+B`, desc: "Build Project" },
    { keys: `${mod}+\``, desc: "Toggle Terminal" },
    { keys: `${mod}+G`, desc: "Go to Line" },
    { keys: `${mod}+B`, desc: "Toggle Sidebar" },
  ];

  return (
    <div className="flex h-full flex-col items-center justify-center gap-8 px-8">
      {/* Logo and title */}
      <div className="text-center">
        <img src={alloyLogo} alt="Alloy IDE" className="h-20 w-20 mx-auto mb-4 opacity-30" />
        <h1 className="text-xl font-heading text-stone-300 mb-1">Alloy IDE</h1>
        <p className="text-xs text-stone-500">Where Mods Are Forged</p>
      </div>

      {/* Actions row */}
      <div className="flex gap-3">
        <button
          onClick={openFolderDialog}
          className="flex items-center gap-2 rounded-lg px-5 py-2.5 text-sm font-medium bg-ember text-obsidian-950 hover:bg-ember-light transition-colors shadow-lg shadow-ember/20"
        >
          <FolderOpen size={16} />
          Open Folder
        </button>
      </div>

      {/* Recently closed files */}
      {recentlyClosed.length > 0 && (
        <div className="max-w-[650px] w-full">
          <div className="flex items-center gap-2 mb-2">
            <FileText size={12} className="text-stone-500" />
            <span className="text-[11px] text-stone-500 uppercase tracking-wider font-semibold">
              Recently Closed
            </span>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {recentlyClosed.slice(0, 8).map((f, i) => (
              <button
                key={f.path + i}
                onClick={() => openFile(f.path, f.name)}
                className="px-2.5 py-1 rounded-md text-[11px] text-stone-400 bg-obsidian-800 border border-obsidian-600 hover:bg-obsidian-700 hover:text-stone-200 transition-colors truncate max-w-[150px]"
              >
                {f.name}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Two column layout */}
      <div className="flex gap-12 max-w-[650px] w-full">
        {/* Recent projects */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-3">
            <Clock size={12} className="text-stone-500" />
            <span className="text-[11px] text-stone-500 uppercase tracking-wider font-semibold">
              Recent Projects
            </span>
          </div>
          {recentProjects.length === 0 ? (
            <div className="text-xs text-stone-600 py-4">
              No recent projects yet
            </div>
          ) : (
            <div className="flex flex-col gap-0.5">
              {recentProjects.slice(0, 6).map((p) => (
                <button
                  key={p.path}
                  onClick={() => openProject(p.path)}
                  className="flex items-center gap-2.5 px-3 py-2 rounded-md text-left text-xs hover:bg-obsidian-800 transition-colors group"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-stone-200 font-medium truncate group-hover:text-stone-100">
                        {p.name}
                      </span>
                      {projectTypeBadge(p.project_type)}
                    </div>
                    <div className="text-[10px] text-stone-600 truncate mt-0.5">
                      {p.path}
                    </div>
                  </div>
                  <span className="text-[10px] text-stone-600 shrink-0">
                    {formatTime(p.last_opened)}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Keyboard shortcuts */}
        <div className="w-[200px] shrink-0">
          <span className="text-[11px] text-stone-500 uppercase tracking-wider font-semibold block mb-3">
            Shortcuts
          </span>
          <div className="flex flex-col gap-1.5">
            {shortcuts.map((s) => (
              <div key={s.keys} className="flex items-center justify-between gap-2">
                <span className="text-[11px] text-stone-500">{s.desc}</span>
                <kbd className="px-1.5 py-0.5 rounded bg-obsidian-800 border border-obsidian-600 text-stone-400 font-mono text-[10px] whitespace-nowrap">
                  {s.keys}
                </kbd>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

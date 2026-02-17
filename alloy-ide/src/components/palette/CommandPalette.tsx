import { useState, useEffect, useRef, useMemo } from "react";
import {
  FolderOpen, Search, Terminal, Bot, Files, GitBranch,
  Blocks, PanelBottom, Save, X, RefreshCw, Hammer, Play, Trash2, Settings,
  RotateCcw, ZoomIn, ZoomOut, Replace, Columns2, Rows2, Keyboard,
  ToggleLeft, AlertTriangle, Bug, GitCompareArrows,
} from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";

interface Command {
  id: string;
  label: string;
  icon: React.ReactNode;
  action: () => void;
  shortcut?: string;
}

export default function CommandPalette({ onClose }: { onClose: () => void }) {
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const isMac = navigator.platform.includes("Mac");
  const mod = isMac ? "\u2318" : "Ctrl";

  const commands: Command[] = useMemo(() => [
    {
      id: "open-folder",
      label: "Open Folder",
      icon: <FolderOpen size={14} />,
      action: () => useStore.getState().openFolderDialog(),
      shortcut: `${mod}+O`,
    },
    {
      id: "save-file",
      label: "Save File",
      icon: <Save size={14} />,
      action: () => {
        const s = useStore.getState();
        if (s.activeFilePath) s.saveFile(s.activeFilePath);
      },
      shortcut: `${mod}+S`,
    },
    {
      id: "close-tab",
      label: "Close Tab",
      icon: <X size={14} />,
      action: () => {
        const s = useStore.getState();
        if (s.activeFilePath) s.closeFile(s.activeFilePath);
      },
      shortcut: `${mod}+W`,
    },
    {
      id: "toggle-explorer",
      label: "Toggle Explorer",
      icon: <Files size={14} />,
      action: () => useStore.getState().setSidebarPanel("files"),
      shortcut: `${mod}+Shift+E`,
    },
    {
      id: "search-files",
      label: "Search in Files",
      icon: <Search size={14} />,
      action: () => useStore.getState().setSidebarPanel("search"),
      shortcut: `${mod}+Shift+F`,
    },
    {
      id: "source-control",
      label: "Source Control",
      icon: <GitBranch size={14} />,
      action: () => useStore.getState().setSidebarPanel("git"),
      shortcut: `${mod}+Shift+G`,
    },
    {
      id: "ai-assistant",
      label: "AI Assistant",
      icon: <Bot size={14} />,
      action: () => useStore.getState().setSidebarPanel("ai"),
    },
    {
      id: "extensions",
      label: "Extensions",
      icon: <Blocks size={14} />,
      action: () => useStore.getState().setSidebarPanel("extensions"),
    },
    {
      id: "toggle-terminal",
      label: "Toggle Terminal",
      icon: <Terminal size={14} />,
      action: () => useStore.getState().toggleBottomPanel(),
      shortcut: `${mod}+\``,
    },
    {
      id: "toggle-sidebar",
      label: "Toggle Sidebar",
      icon: <PanelBottom size={14} />,
      action: () => useStore.getState().toggleSidebar(),
      shortcut: `${mod}+B`,
    },
    {
      id: "refresh-tree",
      label: "Refresh File Explorer",
      icon: <RefreshCw size={14} />,
      action: () => {
        const s = useStore.getState();
        if (s.currentProject) s.openProject(s.currentProject.path);
      },
    },
    {
      id: "settings",
      label: "Open Settings",
      icon: <Settings size={14} />,
      action: () => useStore.getState().toggleSettings(),
      shortcut: `${mod}+,`,
    },
    {
      id: "build",
      label: "Build Project",
      icon: <Hammer size={14} />,
      action: () => useStore.getState().runBuild("build"),
      shortcut: `${mod}+Shift+B`,
    },
    {
      id: "clean-build",
      label: "Clean Build",
      icon: <Trash2 size={14} />,
      action: () => useStore.getState().runBuild("clean"),
    },
    {
      id: "launch-client",
      label: "Launch Client",
      icon: <Play size={14} />,
      action: () => useStore.getState().runBuild("launchClient"),
    },
    {
      id: "launch-server",
      label: "Launch Server",
      icon: <Play size={14} />,
      action: () => useStore.getState().runBuild("launchServer"),
    },
    {
      id: "reopen-tab",
      label: "Reopen Closed Tab",
      icon: <RotateCcw size={14} />,
      action: () => useStore.getState().reopenClosedTab(),
      shortcut: `${mod}+Shift+T`,
    },
    {
      id: "zoom-in",
      label: "Zoom In",
      icon: <ZoomIn size={14} />,
      action: () => {
        const s = useStore.getState();
        s.updateEditorSettings({ fontSize: Math.min(s.editorSettings.fontSize + 1, 32) });
      },
      shortcut: `${mod}+=`,
    },
    {
      id: "zoom-out",
      label: "Zoom Out",
      icon: <ZoomOut size={14} />,
      action: () => {
        const s = useStore.getState();
        s.updateEditorSettings({ fontSize: Math.max(s.editorSettings.fontSize - 1, 8) });
      },
      shortcut: `${mod}+-`,
    },
    {
      id: "search-replace",
      label: "Search and Replace",
      icon: <Replace size={14} />,
      action: () => useStore.getState().setSidebarPanel("search"),
      shortcut: `${mod}+Shift+H`,
    },
    {
      id: "split-right",
      label: "Split Editor Right",
      icon: <Columns2 size={14} />,
      action: () => {
        const s = useStore.getState();
        if (s.activeFilePath) s.openToSide(s.activeFilePath, "horizontal");
      },
      shortcut: `${mod}+\\`,
    },
    {
      id: "split-down",
      label: "Split Editor Down",
      icon: <Rows2 size={14} />,
      action: () => {
        const s = useStore.getState();
        if (s.activeFilePath) s.openToSide(s.activeFilePath, "vertical");
      },
    },
    {
      id: "close-split",
      label: "Close Split Editor",
      icon: <X size={14} />,
      action: () => useStore.getState().closeSplit(),
      shortcut: `${mod}+Shift+\\`,
    },
    {
      id: "task-runner",
      label: "Task Runner",
      icon: <Play size={14} />,
      action: () => useStore.getState().setSidebarPanel("tasks"),
    },
    {
      id: "toggle-word-wrap",
      label: "Toggle Word Wrap",
      icon: <ToggleLeft size={14} />,
      action: () => {
        const s = useStore.getState();
        s.updateEditorSettings({ wordWrap: !s.editorSettings.wordWrap });
      },
    },
    {
      id: "toggle-minimap",
      label: "Toggle Minimap",
      icon: <ToggleLeft size={14} />,
      action: () => {
        const s = useStore.getState();
        s.updateEditorSettings({ minimap: !s.editorSettings.minimap });
      },
    },
    {
      id: "show-problems",
      label: "Show Problems",
      icon: <AlertTriangle size={14} />,
      action: () => {
        const s = useStore.getState();
        s.setBottomPanel("problems");
        if (!s.bottomPanelVisible) s.toggleBottomPanel();
      },
    },
    {
      id: "show-output",
      label: "Show Output",
      icon: <Bug size={14} />,
      action: () => {
        const s = useStore.getState();
        s.setBottomPanel("output");
        if (!s.bottomPanelVisible) s.toggleBottomPanel();
      },
    },
    {
      id: "reset-zoom",
      label: "Reset Zoom",
      icon: <ZoomIn size={14} />,
      action: () => useStore.getState().updateEditorSettings({ fontSize: 13 }),
      shortcut: `${mod}+0`,
    },
    {
      id: "compare-head",
      label: "Compare with Git HEAD",
      icon: <GitCompareArrows size={14} />,
      action: async () => {
        const s = useStore.getState();
        if (!s.activeFilePath || !s.currentProject) return;
        try {
          const headContent = await invoke<string>("git_show_file", {
            projectPath: s.currentProject.path,
            filePath: s.activeFilePath,
          });
          const currentFile = s.openFiles.find((f) => f.path === s.activeFilePath);
          if (currentFile) {
            const name = currentFile.name;
            s.showDiffView(headContent, currentFile.content, `${name} (HEAD)`, `${name} (Working)`);
          }
        } catch { /* file not in git */ }
      },
    },
  ], [mod]);

  const filtered = useMemo(() => {
    if (!query.trim()) return commands;
    const q = query.toLowerCase();
    return commands.filter((c) => c.label.toLowerCase().includes(q));
  }, [query, commands]);

  useEffect(() => {
    setSelected(0);
  }, [query]);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  // Scroll selected into view
  useEffect(() => {
    if (!listRef.current) return;
    const el = listRef.current.children[selected] as HTMLElement;
    if (el) el.scrollIntoView({ block: "nearest" });
  }, [selected]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setSelected((s) => Math.min(s + 1, filtered.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSelected((s) => Math.max(s - 1, 0));
    } else if (e.key === "Enter" && filtered[selected]) {
      e.preventDefault();
      filtered[selected].action();
      onClose();
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
        className="w-[500px] max-w-[90vw] rounded-lg border border-obsidian-600 bg-obsidian-800 shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-2 px-3 py-2.5 border-b border-obsidian-600">
          <Search size={14} className="text-stone-500 shrink-0" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type a command..."
            className="flex-1 bg-transparent text-sm text-stone-100 placeholder:text-stone-500 outline-none"
          />
        </div>
        <div ref={listRef} className="max-h-[300px] overflow-y-auto py-1">
          {filtered.length === 0 && (
            <div className="px-3 py-4 text-center text-xs text-stone-500">
              No matching commands
            </div>
          )}
          {filtered.map((cmd, i) => (
            <button
              key={cmd.id}
              onClick={() => {
                cmd.action();
                onClose();
              }}
              className={
                "flex w-full items-center gap-2.5 px-3 py-2 text-sm transition-colors " +
                (i === selected
                  ? "bg-ember/10 text-stone-100"
                  : "text-stone-300 hover:bg-obsidian-700")
              }
            >
              <span className="text-stone-400">{cmd.icon}</span>
              <span className="flex-1 text-left">{cmd.label}</span>
              {cmd.shortcut && (
                <kbd className="text-[10px] text-stone-500 bg-obsidian-900 border border-obsidian-600 rounded px-1.5 py-0.5 font-mono">
                  {cmd.shortcut}
                </kbd>
              )}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

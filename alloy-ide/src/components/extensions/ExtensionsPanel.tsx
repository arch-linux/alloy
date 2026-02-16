import { useState } from "react";
import { Blocks, Search, ExternalLink, Star, Download, CheckCircle } from "lucide-react";

interface ExtensionInfo {
  id: string;
  name: string;
  description: string;
  author: string;
  version: string;
  installed: boolean;
  category: string;
}

const BUILTIN_EXTENSIONS: ExtensionInfo[] = [
  {
    id: "alloy.java-support",
    name: "Java Language Support",
    description: "Syntax highlighting, code completion, and error checking for Java via Eclipse JDT LS",
    author: "Alloy",
    version: "1.0.0",
    installed: true,
    category: "Language",
  },
  {
    id: "alloy.gradle-support",
    name: "Gradle Build Support",
    description: "Run Gradle tasks, parse build errors, and manage dependencies",
    author: "Alloy",
    version: "1.0.0",
    installed: true,
    category: "Build",
  },
  {
    id: "alloy.git-integration",
    name: "Git Integration",
    description: "Source control with branch management, file status, and diff view",
    author: "Alloy",
    version: "1.0.0",
    installed: true,
    category: "Source Control",
  },
  {
    id: "alloy.ai-assistant",
    name: "AI Assistant",
    description: "Claude-powered coding assistant with tool use for code generation, refactoring, and explanation",
    author: "Alloy",
    version: "1.0.0",
    installed: true,
    category: "AI",
  },
  {
    id: "alloy.mod-templates",
    name: "Mod Templates",
    description: "Quick-start templates for common mod patterns: blocks, items, entities, GUIs",
    author: "Alloy",
    version: "1.0.0",
    installed: true,
    category: "Modding",
  },
];

const AVAILABLE_EXTENSIONS: ExtensionInfo[] = [
  {
    id: "alloy.gui-editor",
    name: "Visual GUI Editor",
    description: "WYSIWYG editor for Minecraft GUI screens with drag-and-drop widgets",
    author: "Alloy",
    version: "0.1.0",
    installed: false,
    category: "Visual",
  },
  {
    id: "alloy.animation-editor",
    name: "Animation Timeline",
    description: "Keyframe-based animation editor for block and entity animations at 20 TPS",
    author: "Alloy",
    version: "0.1.0",
    installed: false,
    category: "Visual",
  },
  {
    id: "alloy.texture-editor",
    name: "Texture Editor",
    description: "Pixel art editor optimized for Minecraft textures with palette support",
    author: "Alloy",
    version: "0.1.0",
    installed: false,
    category: "Visual",
  },
  {
    id: "alloy.block-model-editor",
    name: "Block Model Editor",
    description: "3D block model editor with UV mapping and rotation controls",
    author: "Alloy",
    version: "0.1.0",
    installed: false,
    category: "Visual",
  },
];

export default function ExtensionsPanel() {
  const [searchQuery, setSearchQuery] = useState("");
  const [tab, setTab] = useState<"installed" | "available">("installed");

  const extensions = tab === "installed" ? BUILTIN_EXTENSIONS : AVAILABLE_EXTENSIONS;
  const filtered = searchQuery.trim()
    ? extensions.filter(
        (e) =>
          e.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
          e.description.toLowerCase().includes(searchQuery.toLowerCase()),
      )
    : extensions;

  return (
    <div className="flex flex-col h-full">
      {/* Search */}
      <div className="px-3 py-2">
        <div className="flex items-center gap-2 rounded-md border border-obsidian-600 bg-obsidian-950 px-2.5 py-1.5 focus-within:border-ember focus-within:ring-1 focus-within:ring-ember/30">
          <Search size={13} className="text-stone-500 shrink-0" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search extensions..."
            className="w-full bg-transparent text-xs text-stone-100 placeholder:text-stone-500 outline-none"
          />
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-0 px-3 border-b border-obsidian-700">
        <button
          onClick={() => setTab("installed")}
          className={
            "px-3 py-1.5 text-[11px] font-medium border-b-2 transition-colors " +
            (tab === "installed"
              ? "border-ember text-ember"
              : "border-transparent text-stone-500 hover:text-stone-300")
          }
        >
          Installed ({BUILTIN_EXTENSIONS.length})
        </button>
        <button
          onClick={() => setTab("available")}
          className={
            "px-3 py-1.5 text-[11px] font-medium border-b-2 transition-colors " +
            (tab === "available"
              ? "border-ember text-ember"
              : "border-transparent text-stone-500 hover:text-stone-300")
          }
        >
          Available ({AVAILABLE_EXTENSIONS.length})
        </button>
      </div>

      {/* Extension list */}
      <div className="flex-1 overflow-y-auto scrollbar-thin">
        {filtered.length === 0 && (
          <div className="flex flex-col items-center justify-center h-32 text-stone-500 text-xs gap-2">
            <Blocks size={24} />
            <span>No extensions found</span>
          </div>
        )}
        {filtered.map((ext) => (
          <div
            key={ext.id}
            className="flex gap-3 px-3 py-3 border-b border-obsidian-700/50 hover:bg-obsidian-800/50 transition-colors"
          >
            <div className="shrink-0 mt-0.5 h-8 w-8 rounded-lg bg-obsidian-700 flex items-center justify-center">
              <Blocks size={16} className="text-ember" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-medium text-stone-100 truncate">
                  {ext.name}
                </span>
                <span className="text-[10px] text-stone-500">v{ext.version}</span>
              </div>
              <p className="text-[11px] text-stone-400 mt-0.5 line-clamp-2 leading-relaxed">
                {ext.description}
              </p>
              <div className="flex items-center gap-2 mt-1.5">
                <span className="text-[10px] text-stone-500">{ext.author}</span>
                <span className="text-[10px] text-stone-600 bg-obsidian-700 rounded px-1.5 py-0.5">
                  {ext.category}
                </span>
              </div>
            </div>
            <div className="shrink-0 flex items-start">
              {ext.installed ? (
                <span className="flex items-center gap-1 text-[10px] text-green-400">
                  <CheckCircle size={12} />
                  Active
                </span>
              ) : (
                <button className="flex items-center gap-1 text-[10px] text-ember hover:text-ember-light bg-ember/10 border border-ember/20 rounded px-2 py-1 transition-colors">
                  <Download size={11} />
                  Soon
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

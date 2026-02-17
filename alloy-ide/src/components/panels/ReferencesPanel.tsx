import { useState, useEffect } from "react";
import { X, FileCode, MapPin } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";

interface ReferenceLocation {
  path: string;
  line: number;
  character: number;
}

interface ReferencesData {
  symbol: string;
  locations: ReferenceLocation[];
}

export default function ReferencesPanel() {
  const [data, setData] = useState<ReferencesData | null>(null);
  const [contextLines, setContextLines] = useState<Map<string, string>>(
    new Map()
  );
  const currentProject = useStore((s) => s.currentProject);

  // Listen for reference results
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent).detail as ReferencesData;
      setData(detail);

      // Load context lines for each reference
      const loadContext = async () => {
        const map = new Map<string, string>();
        for (const loc of detail.locations) {
          const key = `${loc.path}:${loc.line}`;
          if (map.has(key)) continue;
          try {
            const content = await invoke<string>("read_file", {
              path: loc.path,
            });
            const lines = content.split("\n");
            const lineContent = lines[loc.line - 1] || "";
            map.set(key, lineContent.trim());
          } catch {
            map.set(key, "");
          }
        }
        setContextLines(map);
      };
      loadContext();
    };

    window.addEventListener("lsp:references", handler);
    return () => window.removeEventListener("lsp:references", handler);
  }, []);

  if (!data || data.locations.length === 0) {
    return (
      <div className="flex items-center justify-center h-full text-xs text-stone-500">
        No references found
      </div>
    );
  }

  // Group references by file
  const grouped = new Map<string, ReferenceLocation[]>();
  for (const loc of data.locations) {
    const existing = grouped.get(loc.path) || [];
    existing.push(loc);
    grouped.set(loc.path, existing);
  }

  const getRelativePath = (path: string) => {
    if (currentProject && path.startsWith(currentProject.path)) {
      return path.slice(currentProject.path.length + 1);
    }
    return path.split("/").pop() || path;
  };

  const handleClick = (loc: ReferenceLocation) => {
    const state = useStore.getState();
    const name = loc.path.split("/").pop() || loc.path;
    state.openFile(loc.path, name).then(() => {
      state.goToLine(loc.line);
    });
  };

  return (
    <div className="h-full overflow-y-auto text-xs">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-1.5 border-b border-obsidian-700 bg-obsidian-900/50 sticky top-0">
        <div className="flex items-center gap-2 text-stone-300">
          <MapPin size={12} className="text-ember" />
          <span>
            {data.locations.length} reference{data.locations.length !== 1 ? "s" : ""} to{" "}
            <span className="text-stone-100 font-medium">{data.symbol}</span>
          </span>
        </div>
        <button
          onClick={() => setData(null)}
          className="text-stone-500 hover:text-stone-200 p-0.5 rounded hover:bg-obsidian-700 transition-colors"
        >
          <X size={12} />
        </button>
      </div>

      {/* Grouped results */}
      {Array.from(grouped.entries()).map(([filePath, locations]) => (
        <div key={filePath}>
          <div className="flex items-center gap-1.5 px-3 py-1 bg-obsidian-900/30 text-stone-400 border-b border-obsidian-700/50">
            <FileCode size={11} />
            <span className="truncate">{getRelativePath(filePath)}</span>
            <span className="text-stone-600 ml-auto shrink-0">
              {locations.length}
            </span>
          </div>
          {locations.map((loc, i) => {
            const key = `${loc.path}:${loc.line}`;
            const context = contextLines.get(key) || "";
            return (
              <button
                key={i}
                onClick={() => handleClick(loc)}
                className="flex w-full items-start gap-2 px-3 py-1 hover:bg-obsidian-700/50 text-left transition-colors"
              >
                <span className="text-stone-500 shrink-0 font-mono w-8 text-right">
                  {loc.line}
                </span>
                <span className="text-stone-300 truncate font-mono">
                  {context || `Line ${loc.line}`}
                </span>
              </button>
            );
          })}
        </div>
      ))}
    </div>
  );
}

import { useState, useEffect, useRef, useMemo } from "react";
import { Search } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";
import type { QuickOpenEntry } from "../../lib/types";

export default function QuickOpen({ onClose }: { onClose: () => void }) {
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(0);
  const [allFiles, setAllFiles] = useState<QuickOpenEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const currentProject = useStore((s) => s.currentProject);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  // Load all files on mount
  useEffect(() => {
    if (!currentProject) {
      setLoading(false);
      return;
    }
    invoke<QuickOpenEntry[]>("list_all_files", { rootPath: currentProject.path })
      .then((files) => {
        setAllFiles(files);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, [currentProject]);

  const filtered = useMemo(() => {
    if (!query.trim()) return allFiles.slice(0, 50);
    const q = query.toLowerCase();
    const matches: { entry: QuickOpenEntry; score: number }[] = [];

    for (const entry of allFiles) {
      const name = entry.name.toLowerCase();
      const rel = entry.relative_path.toLowerCase();

      // Exact name match gets highest score
      if (name === q) {
        matches.push({ entry, score: 100 });
      } else if (name.startsWith(q)) {
        matches.push({ entry, score: 80 });
      } else if (name.includes(q)) {
        matches.push({ entry, score: 60 });
      } else if (rel.includes(q)) {
        matches.push({ entry, score: 40 });
      } else {
        // Simple fuzzy: check if all query chars appear in order
        let qi = 0;
        for (let i = 0; i < rel.length && qi < q.length; i++) {
          if (rel[i] === q[qi]) qi++;
        }
        if (qi === q.length) {
          matches.push({ entry, score: 20 });
        }
      }
    }

    matches.sort((a, b) => b.score - a.score);
    return matches.slice(0, 50).map((m) => m.entry);
  }, [query, allFiles]);

  useEffect(() => {
    setSelected(0);
  }, [query]);

  // Scroll selected into view
  useEffect(() => {
    if (!listRef.current) return;
    const el = listRef.current.children[selected] as HTMLElement;
    if (el) el.scrollIntoView({ block: "nearest" });
  }, [selected]);

  const openSelected = () => {
    const entry = filtered[selected];
    if (!entry) return;
    const name = entry.name;
    useStore.getState().openFile(entry.path, name);
    useStore.getState().pinFile(entry.path);
    onClose();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setSelected((s) => Math.min(s + 1, filtered.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSelected((s) => Math.max(s - 1, 0));
    } else if (e.key === "Enter" && filtered[selected]) {
      e.preventDefault();
      openSelected();
    } else if (e.key === "Escape") {
      onClose();
    }
  };

  const getFileIcon = (name: string) => {
    const ext = name.split(".").pop()?.toLowerCase();
    const colors: Record<string, string> = {
      java: "text-orange-400",
      json: "text-yellow-400",
      toml: "text-stone-400",
      xml: "text-orange-300",
      yml: "text-pink-400",
      yaml: "text-pink-400",
      md: "text-blue-400",
      gradle: "text-green-400",
      kts: "text-purple-400",
      ts: "text-blue-400",
      tsx: "text-blue-400",
      js: "text-yellow-300",
      jsx: "text-yellow-300",
      css: "text-blue-300",
      html: "text-orange-300",
      rs: "text-orange-400",
      py: "text-green-400",
    };
    return colors[ext || ""] || "text-stone-500";
  };

  return (
    <div
      className="fixed inset-0 z-[100] flex items-start justify-center pt-[15vh]"
      onClick={onClose}
    >
      <div
        className="w-[540px] max-w-[90vw] rounded-lg border border-obsidian-600 bg-obsidian-800 shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-2 px-3 py-2.5 border-b border-obsidian-600">
          <Search size={14} className="text-stone-500 shrink-0" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Search files by name..."
            className="flex-1 bg-transparent text-sm text-stone-100 placeholder:text-stone-500 outline-none"
          />
        </div>
        <div ref={listRef} className="max-h-[350px] overflow-y-auto py-1">
          {loading && (
            <div className="px-3 py-4 text-center text-xs text-stone-500">
              Indexing files...
            </div>
          )}
          {!loading && filtered.length === 0 && (
            <div className="px-3 py-4 text-center text-xs text-stone-500">
              {currentProject ? "No matching files" : "Open a project first"}
            </div>
          )}
          {filtered.map((entry, i) => (
            <button
              key={entry.path}
              onClick={() => {
                setSelected(i);
                useStore.getState().openFile(entry.path, entry.name);
                useStore.getState().pinFile(entry.path);
                onClose();
              }}
              className={
                "flex w-full items-center gap-2.5 px-3 py-1.5 text-sm transition-colors " +
                (i === selected
                  ? "bg-ember/10 text-stone-100"
                  : "text-stone-300 hover:bg-obsidian-700")
              }
            >
              <span className={"text-xs font-mono " + getFileIcon(entry.name)}>
                {entry.name.split(".").pop()}
              </span>
              <span className="flex-1 text-left truncate">{entry.name}</span>
              <span className="text-[10px] text-stone-500 truncate max-w-[200px]">
                {entry.relative_path}
              </span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

import { useState, useRef, useEffect, useCallback } from "react";
import { ChevronRight } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";
import FileIcon from "../filetree/FileIcon";

interface SiblingEntry {
  name: string;
  path: string;
  is_dir: boolean;
  extension: string | null;
}

export default function Breadcrumbs() {
  const activeFilePath = useStore((s) => s.activeFilePath);
  const currentProject = useStore((s) => s.currentProject);
  const [dropdown, setDropdown] = useState<{
    index: number;
    dirPath: string;
    entries: SiblingEntry[];
    x: number;
  } | null>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Close dropdown on outside click
  useEffect(() => {
    if (!dropdown) return;
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdown(null);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [dropdown]);

  const handleSegmentClick = useCallback(
    async (index: number, dirPath: string, rect: DOMRect) => {
      // If already showing this dropdown, close it
      if (dropdown?.index === index) {
        setDropdown(null);
        return;
      }

      try {
        const entries = await invoke<SiblingEntry[]>("list_directory", { path: dirPath });
        // Sort: directories first, then alphabetically
        entries.sort((a, b) => {
          if (a.is_dir !== b.is_dir) return a.is_dir ? -1 : 1;
          return a.name.localeCompare(b.name);
        });
        setDropdown({ index, dirPath, entries, x: rect.left });
      } catch {
        setDropdown(null);
      }
    },
    [dropdown],
  );

  const handleEntryClick = useCallback(
    (entry: SiblingEntry) => {
      if (entry.is_dir) {
        // Toggle into directory? For now, just switch sidebar to files
        useStore.getState().setSidebarPanel("files");
      } else {
        useStore.getState().openFile(entry.path, entry.name);
      }
      setDropdown(null);
    },
    [],
  );

  if (!activeFilePath || !currentProject) return null;

  // Build relative path segments
  const relativePath = activeFilePath.startsWith(currentProject.path)
    ? activeFilePath.slice(currentProject.path.length + 1)
    : activeFilePath;

  const segments = relativePath.split("/");
  const projectName = currentProject.name;

  // Build absolute path for each segment
  const segmentPaths: string[] = [];
  for (let i = 0; i < segments.length; i++) {
    const dir =
      i === 0
        ? currentProject.path
        : currentProject.path + "/" + segments.slice(0, i).join("/");
    segmentPaths.push(dir);
  }

  return (
    <div className="relative flex items-center h-6 px-3 bg-obsidian-950 border-b border-obsidian-700 text-[11px] text-stone-500 overflow-x-auto shrink-0 select-none">
      {/* Project root */}
      <button
        className="text-stone-400 hover:text-stone-200 transition-colors shrink-0"
        onClick={(e) => {
          const rect = e.currentTarget.getBoundingClientRect();
          handleSegmentClick(-1, currentProject.path, rect);
        }}
      >
        {projectName}
      </button>

      {segments.map((segment, i) => {
        const isLast = i === segments.length - 1;
        const dirPath = segmentPaths[i];

        return (
          <span key={i} className="flex items-center shrink-0">
            <ChevronRight size={11} className="mx-0.5 text-stone-600" />
            {isLast ? (
              <span className="text-stone-200">{segment}</span>
            ) : (
              <button
                className="text-stone-400 hover:text-stone-200 transition-colors"
                onClick={(e) => {
                  const rect = e.currentTarget.getBoundingClientRect();
                  handleSegmentClick(i, dirPath, rect);
                }}
              >
                {segment}
              </button>
            )}
          </span>
        );
      })}

      {/* Dropdown */}
      {dropdown && (
        <div
          ref={dropdownRef}
          className="absolute top-full left-0 mt-0 z-50 min-w-[200px] max-w-[300px] max-h-[280px] overflow-y-auto rounded-md border border-obsidian-600 bg-obsidian-800 py-1 shadow-xl"
          style={{ left: Math.max(0, dropdown.x - 12) }}
        >
          {dropdown.entries.length === 0 ? (
            <div className="px-3 py-2 text-[11px] text-stone-600">
              Empty directory
            </div>
          ) : (
            dropdown.entries.map((entry) => {
              const isCurrent = entry.path === activeFilePath;
              return (
                <button
                  key={entry.path}
                  onClick={() => handleEntryClick(entry)}
                  className={
                    "flex w-full items-center gap-2 px-3 py-1 text-[11px] transition-colors " +
                    (isCurrent
                      ? "bg-ember/10 text-stone-100"
                      : "text-stone-300 hover:bg-obsidian-700 hover:text-stone-100")
                  }
                >
                  <FileIcon
                    isDir={entry.is_dir}
                    extension={entry.extension}
                    expanded={false}
                    name={entry.name}
                  />
                  <span className="truncate">{entry.name}</span>
                </button>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}

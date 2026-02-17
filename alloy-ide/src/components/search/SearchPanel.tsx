import { useState, useRef, useCallback } from "react";
import {
  Search, FileText, Loader2, Replace, ChevronDown, ChevronRight,
  CaseSensitive, Regex, ArrowRightLeft,
} from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";
import { showToast } from "../ui/Toast";
import type { SearchResult } from "../../lib/types";

export default function SearchPanel() {
  const searchResults = useStore((s) => s.searchResults);
  const searchLoading = useStore((s) => s.searchLoading);
  const openFile = useStore((s) => s.openFile);
  const currentProject = useStore((s) => s.currentProject);

  const [searchValue, setSearchValue] = useState("");
  const [replaceValue, setReplaceValue] = useState("");
  const [showReplace, setShowReplace] = useState(false);
  const [caseSensitive, setCaseSensitive] = useState(false);
  const [regexMode, setRegexMode] = useState(false);
  const [replacing, setReplacing] = useState(false);
  const [loading, setLoading] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  const doSearch = useCallback(
    async (value: string) => {
      if (!currentProject || !value.trim() || value.trim().length < 2) {
        useStore.setState({ searchResults: [], searchQuery: value });
        return;
      }
      setLoading(true);
      useStore.setState({ searchQuery: value, searchLoading: true });
      try {
        const results = await invoke<SearchResult[]>("search_files_advanced", {
          query: value.trim(),
          searchPath: currentProject.path,
          caseSensitive,
          regexMode,
        });
        useStore.setState({ searchResults: results, searchLoading: false });
      } catch (err) {
        useStore.setState({ searchResults: [], searchLoading: false });
        if (regexMode) {
          showToast("error", `Invalid regex: ${err}`);
        }
      }
      setLoading(false);
    },
    [currentProject, caseSensitive, regexMode],
  );

  const handleChange = useCallback(
    (value: string) => {
      setSearchValue(value);
      if (debounceRef.current) clearTimeout(debounceRef.current);
      debounceRef.current = setTimeout(() => doSearch(value), 300);
    },
    [doSearch],
  );

  // Re-search when options change
  const toggleCaseSensitive = () => {
    const next = !caseSensitive;
    setCaseSensitive(next);
    if (searchValue.trim().length >= 2) {
      setTimeout(() => doSearch(searchValue), 50);
    }
  };
  const toggleRegex = () => {
    const next = !regexMode;
    setRegexMode(next);
    if (searchValue.trim().length >= 2) {
      setTimeout(() => doSearch(searchValue), 50);
    }
  };

  const handleReplaceAll = async () => {
    if (!currentProject || !searchValue.trim()) return;
    const confirmed = window.confirm(
      `Replace all occurrences of "${searchValue}" with "${replaceValue}" across the project?`,
    );
    if (!confirmed) return;

    setReplacing(true);
    try {
      const result = await invoke<{ files_changed: number; replacements: number }>(
        "replace_in_files",
        {
          query: searchValue.trim(),
          replacement: replaceValue,
          searchPath: currentProject.path,
          caseSensitive,
          regexMode,
        },
      );
      showToast(
        "success",
        `Replaced ${result.replacements} occurrence(s) in ${result.files_changed} file(s)`,
      );
      // Re-search to update results
      await doSearch(searchValue);
      // Reload open files that may have changed
      const state = useStore.getState();
      for (const file of state.openFiles) {
        try {
          const content = await invoke<string>("read_file", { path: file.path });
          if (content !== file.content) {
            useStore.setState({
              openFiles: state.openFiles.map((f) =>
                f.path === file.path ? { ...f, content, dirty: false } : f,
              ),
            });
          }
        } catch {
          // file may have been deleted
        }
      }
    } catch (err) {
      showToast("error", `Replace failed: ${err}`);
    }
    setReplacing(false);
  };

  const goToLine = useStore((s) => s.goToLine);

  const handleResultClick = async (path: string, name: string, lineNumber?: number) => {
    await openFile(path, name);
    if (lineNumber) {
      setTimeout(() => goToLine(lineNumber), 50);
    }
  };

  // Group results by file
  const grouped = searchResults.reduce(
    (acc, r) => {
      if (!acc[r.path]) acc[r.path] = { name: r.name, results: [] };
      acc[r.path].results.push(r);
      return acc;
    },
    {} as Record<string, { name: string; results: typeof searchResults }>,
  );

  const isLoading = searchLoading || loading;

  return (
    <div className="flex flex-col h-full">
      {/* Search input area */}
      <div className="px-3 py-2 space-y-2">
        <div className="flex items-center gap-1.5">
          {/* Toggle replace section */}
          <button
            onClick={() => setShowReplace(!showReplace)}
            className="text-stone-500 hover:text-stone-300 transition-colors shrink-0 p-0.5"
          >
            {showReplace ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </button>

          {/* Search input */}
          <div className="flex-1 flex items-center gap-1.5 rounded-md border border-obsidian-600 bg-obsidian-950 px-2 py-1.5 focus-within:border-ember focus-within:ring-1 focus-within:ring-ember/30">
            <Search size={13} className="text-stone-500 shrink-0" />
            <input
              type="text"
              value={searchValue}
              onChange={(e) => handleChange(e.target.value)}
              placeholder={currentProject ? "Search..." : "Open a folder first"}
              disabled={!currentProject}
              className="w-full bg-transparent text-xs text-stone-100 placeholder:text-stone-500 outline-none disabled:cursor-not-allowed"
              onKeyDown={(e) => {
                if (e.key === "Enter") doSearch(searchValue);
              }}
            />
            {isLoading && (
              <Loader2 size={13} className="text-ember shrink-0 animate-spin" />
            )}
          </div>

          {/* Search options */}
          <button
            onClick={toggleCaseSensitive}
            className={
              "rounded p-1 transition-colors shrink-0 " +
              (caseSensitive
                ? "bg-ember/15 text-ember border border-ember/30"
                : "text-stone-500 hover:text-stone-300 hover:bg-obsidian-800 border border-transparent")
            }
            title="Case Sensitive"
          >
            <CaseSensitive size={14} />
          </button>
          <button
            onClick={toggleRegex}
            className={
              "rounded p-1 transition-colors shrink-0 " +
              (regexMode
                ? "bg-ember/15 text-ember border border-ember/30"
                : "text-stone-500 hover:text-stone-300 hover:bg-obsidian-800 border border-transparent")
            }
            title="Regex"
          >
            <Regex size={14} />
          </button>
        </div>

        {/* Replace input */}
        {showReplace && (
          <div className="flex items-center gap-1.5 ml-5">
            <div className="flex-1 flex items-center gap-1.5 rounded-md border border-obsidian-600 bg-obsidian-950 px-2 py-1.5 focus-within:border-ember focus-within:ring-1 focus-within:ring-ember/30">
              <Replace size={13} className="text-stone-500 shrink-0" />
              <input
                type="text"
                value={replaceValue}
                onChange={(e) => setReplaceValue(e.target.value)}
                placeholder="Replace..."
                disabled={!currentProject}
                className="w-full bg-transparent text-xs text-stone-100 placeholder:text-stone-500 outline-none disabled:cursor-not-allowed"
              />
            </div>
            <button
              onClick={handleReplaceAll}
              disabled={replacing || !searchValue.trim()}
              className={
                "flex items-center gap-1 rounded-md px-2 py-1.5 text-[11px] font-medium transition-colors shrink-0 " +
                (replacing || !searchValue.trim()
                  ? "text-stone-500 bg-obsidian-800 cursor-not-allowed"
                  : "text-obsidian-950 bg-ember hover:bg-ember-light")
              }
              title="Replace All"
            >
              <ArrowRightLeft size={12} />
              {replacing ? "..." : "All"}
            </button>
          </div>
        )}
      </div>

      {/* Results */}
      <div className="flex-1 overflow-y-auto scrollbar-thin">
        {searchResults.length === 0 && searchValue.trim().length >= 2 && !isLoading && (
          <div className="flex items-center justify-center h-20 text-stone-500 text-xs">
            No results found
          </div>
        )}

        {Object.entries(grouped).map(([filePath, { name, results }]) => (
          <FileResultGroup
            key={filePath}
            filePath={filePath}
            name={name}
            results={results}
            onResultClick={(path, name, line) => handleResultClick(path, name, line)}
            searchQuery={searchValue}
          />
        ))}

        {searchResults.length > 0 && (
          <div className="px-3 py-2 text-[10px] text-stone-500 text-center">
            {searchResults.length}{searchResults.length >= 500 ? "+" : ""} results in{" "}
            {Object.keys(grouped).length} files
          </div>
        )}
      </div>
    </div>
  );
}

function FileResultGroup({
  filePath,
  name,
  results,
  onResultClick,
  searchQuery,
}: {
  filePath: string;
  name: string;
  results: SearchResult[];
  onResultClick: (path: string, name: string, lineNumber: number) => void;
  searchQuery: string;
}) {
  const [collapsed, setCollapsed] = useState(false);
  const project = useStore((s) => s.currentProject);
  const relativePath = project ? filePath.replace(project.path + "/", "") : name;

  return (
    <div className="border-b border-obsidian-700/50">
      <button
        onClick={() => setCollapsed(!collapsed)}
        className="flex w-full items-center gap-1.5 px-3 py-1.5 text-[11px] text-stone-400 hover:bg-obsidian-800 transition-colors"
      >
        {collapsed ? <ChevronRight size={12} /> : <ChevronDown size={12} />}
        <FileText size={12} className="shrink-0" />
        <span className="truncate font-medium text-left">{relativePath}</span>
        <span className="text-stone-500 ml-auto shrink-0">{results.length}</span>
      </button>
      {!collapsed &&
        results.map((r, i) => (
          <button
            key={`${r.path}:${r.line_number}:${i}`}
            onClick={() => onResultClick(r.path, r.name, r.line_number)}
            className="flex w-full items-start gap-2 px-3 py-1 pl-7 text-xs hover:bg-obsidian-800 transition-colors text-left"
          >
            <span className="text-stone-500 shrink-0 w-8 text-right tabular-nums">
              {r.line_number}
            </span>
            <HighlightedLine text={r.line_content.trim()} query={searchQuery} />
          </button>
        ))}
    </div>
  );
}

function HighlightedLine({ text, query }: { text: string; query: string }) {
  if (!query.trim()) return <span className="text-stone-300 truncate">{text}</span>;

  const lower = text.toLowerCase();
  const lq = query.toLowerCase();
  const parts: { text: string; match: boolean }[] = [];
  let last = 0;

  let idx = lower.indexOf(lq, last);
  while (idx !== -1) {
    if (idx > last) parts.push({ text: text.slice(last, idx), match: false });
    parts.push({ text: text.slice(idx, idx + query.length), match: true });
    last = idx + query.length;
    idx = lower.indexOf(lq, last);
  }
  if (last < text.length) parts.push({ text: text.slice(last), match: false });

  return (
    <span className="text-stone-300 truncate">
      {parts.map((p, i) =>
        p.match ? (
          <span key={i} className="bg-forge-gold/20 text-forge-gold rounded-sm px-0.5">
            {p.text}
          </span>
        ) : (
          <span key={i}>{p.text}</span>
        ),
      )}
    </span>
  );
}

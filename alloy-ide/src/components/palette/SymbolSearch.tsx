import { useState, useEffect, useRef, useCallback } from "react";
import {
  Box,
  Braces,
  Hash,
  Circle,
  Diamond,
  Type,
} from "lucide-react";
import { lspWorkspaceSymbols } from "../../lib/lsp";
import type { LspWorkspaceSymbol } from "../../lib/lsp";
import { useStore } from "../../lib/store";

interface SymbolSearchProps {
  onClose: () => void;
}

function symbolIcon(kind: string) {
  switch (kind) {
    case "class":
    case "struct":
      return <Box size={13} className="text-ember shrink-0" />;
    case "interface":
      return <Diamond size={13} className="text-blue-400 shrink-0" />;
    case "method":
    case "function":
    case "constructor":
      return <Braces size={13} className="text-ember-light shrink-0" />;
    case "enum":
    case "enum_member":
      return <Hash size={13} className="text-green-400 shrink-0" />;
    case "field":
    case "property":
    case "constant":
      return <Circle size={13} className="text-forge-gold shrink-0" />;
    case "type_parameter":
      return <Type size={13} className="text-blue-300 shrink-0" />;
    default:
      return <Circle size={13} className="text-stone-500 shrink-0" />;
  }
}

export default function SymbolSearch({ onClose }: SymbolSearchProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<LspWorkspaceSymbol[]>([]);
  const [selected, setSelected] = useState(0);
  const [loading, setLoading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const resultsRef = useRef<HTMLDivElement>(null);
  const currentProject = useStore((s) => s.currentProject);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  // Debounced search
  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (query.length < 2) {
      setResults([]);
      return;
    }

    if (searchTimer.current) clearTimeout(searchTimer.current);
    searchTimer.current = setTimeout(async () => {
      setLoading(true);
      try {
        const symbols = await lspWorkspaceSymbols(query);
        setResults(symbols.slice(0, 50));
        setSelected(0);
      } catch {
        setResults([]);
      }
      setLoading(false);
    }, 200);

    return () => {
      if (searchTimer.current) clearTimeout(searchTimer.current);
    };
  }, [query]);

  const handleSelect = useCallback(
    (symbol: LspWorkspaceSymbol) => {
      const state = useStore.getState();
      const name = symbol.path.split("/").pop() || symbol.path;
      state.openFile(symbol.path, name).then(() => {
        state.goToLine(symbol.line + 1);
      });
      onClose();
    },
    [onClose]
  );

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      onClose();
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      setSelected((s) => Math.min(s + 1, results.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSelected((s) => Math.max(s - 1, 0));
    } else if (e.key === "Enter" && results[selected]) {
      handleSelect(results[selected]);
    }
  };

  // Scroll selected into view
  useEffect(() => {
    const container = resultsRef.current;
    if (!container) return;
    const el = container.children[selected] as HTMLElement | undefined;
    if (el) {
      el.scrollIntoView({ block: "nearest" });
    }
  }, [selected]);

  const getRelativePath = (path: string) => {
    if (currentProject && path.startsWith(currentProject.path)) {
      return path.slice(currentProject.path.length + 1);
    }
    return path.split("/").pop() || path;
  };

  return (
    <div
      className="fixed inset-0 z-[100] flex justify-center pt-[15vh]"
      onClick={onClose}
    >
      <div className="absolute inset-0 bg-obsidian-950/60" />
      <div
        className="relative w-[520px] max-w-[90vw] rounded-lg border border-obsidian-600 bg-obsidian-800 shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
        style={{ maxHeight: "60vh" }}
      >
        <div className="flex items-center gap-2 border-b border-obsidian-700 px-3 py-2">
          <span className="text-stone-500 text-xs">#</span>
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Search symbols..."
            className="flex-1 bg-transparent text-sm text-stone-200 outline-none placeholder:text-stone-600"
            spellCheck={false}
          />
          {loading && (
            <span className="text-[10px] text-stone-500">Searching...</span>
          )}
        </div>

        <div ref={resultsRef} className="overflow-y-auto max-h-[50vh]">
          {results.length === 0 && query.length >= 2 && !loading && (
            <div className="px-4 py-6 text-center text-xs text-stone-600">
              No symbols found
            </div>
          )}
          {results.map((symbol, i) => (
            <button
              key={`${symbol.path}:${symbol.name}:${i}`}
              onClick={() => handleSelect(symbol)}
              className={
                "flex w-full items-center gap-2.5 px-3 py-1.5 text-left transition-colors " +
                (i === selected
                  ? "bg-ember/10 text-stone-100"
                  : "text-stone-300 hover:bg-obsidian-700")
              }
            >
              {symbolIcon(symbol.kind)}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium truncate">
                    {symbol.name}
                  </span>
                  {symbol.container_name && (
                    <span className="text-[10px] text-stone-500 truncate">
                      {symbol.container_name}
                    </span>
                  )}
                </div>
                <div className="text-[10px] text-stone-600 truncate">
                  {getRelativePath(symbol.path)}:{symbol.line + 1}
                </div>
              </div>
              <span className="text-[10px] text-stone-600 capitalize shrink-0">
                {symbol.kind}
              </span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

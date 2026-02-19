import { useState, useEffect, useRef, useCallback, useMemo } from "react";
import {
  Box,
  Braces,
  Hash,
  Circle,
  Diamond,
  Type,
} from "lucide-react";
import { lspDocumentSymbols } from "../../lib/lsp";
import type { LspDocumentSymbol } from "../../lib/lsp";
import { useStore } from "../../lib/store";

interface FileSymbolSearchProps {
  onClose: () => void;
}

interface FlatSymbol {
  name: string;
  kind: string;
  line: number;
  depth: number;
  container: string;
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

function flattenSymbols(
  symbols: LspDocumentSymbol[],
  depth: number,
  container: string,
): FlatSymbol[] {
  const result: FlatSymbol[] = [];
  for (const sym of symbols) {
    result.push({
      name: sym.name,
      kind: sym.kind,
      line: sym.line,
      depth,
      container,
    });
    if (sym.children && sym.children.length > 0) {
      result.push(...flattenSymbols(sym.children, depth + 1, sym.name));
    }
  }
  return result;
}

export default function FileSymbolSearch({ onClose }: FileSymbolSearchProps) {
  const [query, setQuery] = useState("");
  const [symbols, setSymbols] = useState<FlatSymbol[]>([]);
  const [selected, setSelected] = useState(0);
  const [loading, setLoading] = useState(true);
  const inputRef = useRef<HTMLInputElement>(null);
  const resultsRef = useRef<HTMLDivElement>(null);
  const activeFilePath = useStore((s) => s.activeFilePath);
  const goToLine = useStore((s) => s.goToLine);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  // Load symbols for current file
  useEffect(() => {
    if (!activeFilePath) {
      setLoading(false);
      return;
    }
    setLoading(true);
    lspDocumentSymbols(activeFilePath)
      .then((result) => {
        setSymbols(flattenSymbols(result, 0, ""));
        setLoading(false);
      })
      .catch(() => {
        setSymbols([]);
        setLoading(false);
      });
  }, [activeFilePath]);

  const filtered = useMemo(() => {
    if (!query.trim()) return symbols;
    const q = query.toLowerCase();
    return symbols.filter(
      (s) =>
        s.name.toLowerCase().includes(q) ||
        s.kind.toLowerCase().includes(q) ||
        s.container.toLowerCase().includes(q),
    );
  }, [symbols, query]);

  useEffect(() => {
    setSelected(0);
  }, [query]);

  const handleSelect = useCallback(
    (sym: FlatSymbol) => {
      goToLine(sym.line + 1);
      onClose();
    },
    [goToLine, onClose],
  );

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      onClose();
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      setSelected((s) => Math.min(s + 1, filtered.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSelected((s) => Math.max(s - 1, 0));
    } else if (e.key === "Enter" && filtered[selected]) {
      handleSelect(filtered[selected]);
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

  return (
    <div
      className="fixed inset-0 z-[100] flex justify-center pt-[15vh]"
      onClick={onClose}
    >
      <div className="absolute inset-0 bg-obsidian-950/60" />
      <div
        className="relative w-[480px] max-w-[90vw] rounded-lg border border-obsidian-600 bg-obsidian-800 shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
        style={{ maxHeight: "60vh" }}
      >
        <div className="flex items-center gap-2 border-b border-obsidian-700 px-3 py-2">
          <span className="text-stone-500 text-xs">@</span>
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Go to symbol in file..."
            className="flex-1 bg-transparent text-sm text-stone-200 outline-none placeholder:text-stone-600"
            spellCheck={false}
          />
          {loading && (
            <span className="text-[10px] text-stone-500">Loading...</span>
          )}
        </div>

        <div ref={resultsRef} className="overflow-y-auto max-h-[50vh]">
          {!loading && filtered.length === 0 && (
            <div className="px-4 py-6 text-center text-xs text-stone-600">
              {symbols.length === 0
                ? "No symbols found (LSP may not be running)"
                : "No matching symbols"}
            </div>
          )}
          {filtered.map((sym, i) => (
            <button
              key={`${sym.name}:${sym.line}:${i}`}
              onClick={() => handleSelect(sym)}
              className={
                "flex w-full items-center gap-2.5 px-3 py-1.5 text-left transition-colors " +
                (i === selected
                  ? "bg-ember/10 text-stone-100"
                  : "text-stone-300 hover:bg-obsidian-700")
              }
              style={{ paddingLeft: `${12 + sym.depth * 16}px` }}
            >
              {symbolIcon(sym.kind)}
              <span className="text-sm font-medium truncate">{sym.name}</span>
              {sym.container && (
                <span className="text-[10px] text-stone-500 truncate">
                  {sym.container}
                </span>
              )}
              <span className="text-[10px] text-stone-600 capitalize shrink-0 ml-auto">
                {sym.kind}
              </span>
              <span className="text-[10px] text-stone-600 shrink-0 font-mono w-8 text-right">
                :{sym.line + 1}
              </span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

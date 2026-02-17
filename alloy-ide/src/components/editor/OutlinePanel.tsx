import { useState, useEffect, useCallback } from "react";
import {
  ChevronRight,
  ChevronDown,
  Box,
  Braces,
  Hash,
  Circle,
  Diamond,
  Type,
  Variable,
} from "lucide-react";
import { useStore } from "../../lib/store";
import { lspDocumentSymbols } from "../../lib/lsp";
import type { LspDocumentSymbol } from "../../lib/lsp";

function symbolIcon(kind: string) {
  switch (kind) {
    case "class":
    case "struct":
      return <Box size={12} className="text-ember" />;
    case "interface":
      return <Diamond size={12} className="text-blue-400" />;
    case "method":
    case "function":
    case "constructor":
      return <Braces size={12} className="text-ember-light" />;
    case "field":
    case "property":
      return <Circle size={12} className="text-forge-gold" />;
    case "enum":
    case "enum_member":
      return <Hash size={12} className="text-green-400" />;
    case "constant":
      return <Hash size={12} className="text-purple-400" />;
    case "variable":
      return <Variable size={12} className="text-stone-400" />;
    case "type_parameter":
      return <Type size={12} className="text-blue-300" />;
    default:
      return <Circle size={12} className="text-stone-500" />;
  }
}

function SymbolNode({
  symbol,
  depth,
  onNavigate,
}: {
  symbol: LspDocumentSymbol;
  depth: number;
  onNavigate: (line: number) => void;
}) {
  const [expanded, setExpanded] = useState(true);
  const hasChildren = symbol.children.length > 0;

  return (
    <div>
      <button
        onClick={() => {
          if (hasChildren) setExpanded(!expanded);
          onNavigate(symbol.line + 1);
        }}
        className="flex w-full items-center gap-1 py-0.5 text-left hover:bg-obsidian-700/50 transition-colors group"
        style={{ paddingLeft: depth * 12 + 4 }}
      >
        {hasChildren ? (
          <span className="shrink-0 text-stone-600">
            {expanded ? (
              <ChevronDown size={10} />
            ) : (
              <ChevronRight size={10} />
            )}
          </span>
        ) : (
          <span className="w-[10px] shrink-0" />
        )}
        <span className="shrink-0">{symbolIcon(symbol.kind)}</span>
        <span className="text-[11px] text-stone-300 truncate group-hover:text-stone-100">
          {symbol.name}
        </span>
        <span className="text-[9px] text-stone-600 ml-auto shrink-0 pr-2">
          {symbol.line + 1}
        </span>
      </button>
      {expanded &&
        hasChildren &&
        symbol.children.map((child, i) => (
          <SymbolNode
            key={`${child.name}-${i}`}
            symbol={child}
            depth={depth + 1}
            onNavigate={onNavigate}
          />
        ))}
    </div>
  );
}

export default function OutlinePanel() {
  const activeFilePath = useStore((s) => s.activeFilePath);
  const lspRunning = useStore((s) => s.lspRunning);
  const openFiles = useStore((s) => s.openFiles);
  const goToLine = useStore((s) => s.goToLine);
  const [symbols, setSymbols] = useState<LspDocumentSymbol[]>([]);
  const [loading, setLoading] = useState(false);

  const activeFile = openFiles.find((f) => f.path === activeFilePath);
  const isJava = activeFile?.language === "java";

  const loadSymbols = useCallback(async () => {
    if (!activeFilePath || !isJava || !lspRunning) {
      setSymbols([]);
      return;
    }

    setLoading(true);
    try {
      const result = await lspDocumentSymbols(activeFilePath);
      setSymbols(result);
    } catch {
      setSymbols([]);
    }
    setLoading(false);
  }, [activeFilePath, isJava, lspRunning]);

  // Load symbols when file changes
  useEffect(() => {
    loadSymbols();
  }, [loadSymbols]);

  // Refresh on save (file content changes)
  useEffect(() => {
    if (!activeFile || activeFile.dirty) return;
    // File just became clean (was saved) — reload symbols
    const timer = setTimeout(loadSymbols, 500);
    return () => clearTimeout(timer);
  }, [activeFile?.dirty, loadSymbols]);

  if (!isJava) {
    return (
      <div className="px-3 py-4 text-center text-[11px] text-stone-600">
        Outline available for Java files
      </div>
    );
  }

  if (!lspRunning) {
    return (
      <div className="px-3 py-4 text-center text-[11px] text-stone-600">
        Language server not running
      </div>
    );
  }

  if (loading && symbols.length === 0) {
    return (
      <div className="px-3 py-4 text-center text-[11px] text-stone-500">
        Loading symbols...
      </div>
    );
  }

  if (symbols.length === 0) {
    return (
      <div className="px-3 py-4 text-center text-[11px] text-stone-600">
        No symbols found
      </div>
    );
  }

  return (
    <div className="py-1 overflow-y-auto">
      {symbols.map((symbol, i) => (
        <SymbolNode
          key={`${symbol.name}-${i}`}
          symbol={symbol}
          depth={0}
          onNavigate={goToLine}
        />
      ))}
    </div>
  );
}

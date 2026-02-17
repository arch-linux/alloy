import type React from "react";
import { useRef, useMemo, useState } from "react";
import { Columns2, Rows2 } from "lucide-react";

interface DiffLine {
  type: "same" | "added" | "removed" | "empty";
  content: string;
  lineNum: number | null;
}

interface UnifiedDiffLine {
  type: "same" | "added" | "removed";
  content: string;
  oldLineNum: number | null;
  newLineNum: number | null;
}

interface DiffEditorProps {
  originalContent: string;
  modifiedContent: string;
  originalTitle: string;
  modifiedTitle: string;
  language?: string;
}

/** Compute a simple line-level diff between two texts. */
function computeDiff(original: string, modified: string): {
  left: DiffLine[];
  right: DiffLine[];
  unified: UnifiedDiffLine[];
} {
  const origLines = original.split("\n");
  const modLines = modified.split("\n");

  // Simple LCS-based diff
  const m = origLines.length;
  const n = modLines.length;

  // Build LCS table
  const dp: number[][] = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0));
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (origLines[i - 1] === modLines[j - 1]) {
        dp[i][j] = dp[i - 1][j - 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
      }
    }
  }

  // Backtrack to build diff
  const left: DiffLine[] = [];
  const right: DiffLine[] = [];
  const unified: UnifiedDiffLine[] = [];
  let i = m;
  let j = n;

  const result: { type: "same" | "removed" | "added"; origIdx?: number; modIdx?: number }[] = [];

  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && origLines[i - 1] === modLines[j - 1]) {
      result.push({ type: "same", origIdx: i - 1, modIdx: j - 1 });
      i--;
      j--;
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      result.push({ type: "added", modIdx: j - 1 });
      j--;
    } else {
      result.push({ type: "removed", origIdx: i - 1 });
      i--;
    }
  }

  result.reverse();

  for (const entry of result) {
    if (entry.type === "same") {
      left.push({ type: "same", content: origLines[entry.origIdx!], lineNum: entry.origIdx! + 1 });
      right.push({ type: "same", content: modLines[entry.modIdx!], lineNum: entry.modIdx! + 1 });
      unified.push({ type: "same", content: origLines[entry.origIdx!], oldLineNum: entry.origIdx! + 1, newLineNum: entry.modIdx! + 1 });
    } else if (entry.type === "removed") {
      left.push({ type: "removed", content: origLines[entry.origIdx!], lineNum: entry.origIdx! + 1 });
      right.push({ type: "empty", content: "", lineNum: null });
      unified.push({ type: "removed", content: origLines[entry.origIdx!], oldLineNum: entry.origIdx! + 1, newLineNum: null });
    } else {
      left.push({ type: "empty", content: "", lineNum: null });
      right.push({ type: "added", content: modLines[entry.modIdx!], lineNum: entry.modIdx! + 1 });
      unified.push({ type: "added", content: modLines[entry.modIdx!], oldLineNum: null, newLineNum: entry.modIdx! + 1 });
    }
  }

  return { left, right, unified };
}

const LINE_HEIGHT = 20;

const TYPE_STYLES: Record<string, React.CSSProperties> = {
  same: {},
  removed: { background: "rgba(239, 68, 68, 0.1)" },
  added: { background: "rgba(34, 197, 94, 0.1)" },
  empty: { background: "rgba(42, 42, 54, 0.5)" },
};

const GUTTER_STYLES: Record<string, React.CSSProperties> = {
  same: { color: "#6b7280" },
  removed: { color: "#ef4444", background: "rgba(239, 68, 68, 0.15)" },
  added: { color: "#22c55e", background: "rgba(34, 197, 94, 0.15)" },
  empty: { background: "rgba(42, 42, 54, 0.5)" },
};

export default function DiffEditor({
  originalContent,
  modifiedContent,
  originalTitle,
  modifiedTitle,
}: DiffEditorProps) {
  const [mode, setMode] = useState<"side-by-side" | "unified">("side-by-side");
  const containerRef = useRef<HTMLDivElement>(null);
  const diff = useMemo(() => computeDiff(originalContent, modifiedContent), [originalContent, modifiedContent]);

  // Sync scroll between two panels (side-by-side mode)
  const leftScrollRef = useRef<HTMLDivElement>(null);
  const rightScrollRef = useRef<HTMLDivElement>(null);
  const scrollingRef = useRef(false);

  const handleScroll = (source: "left" | "right") => {
    if (scrollingRef.current) return;
    scrollingRef.current = true;
    const src = source === "left" ? leftScrollRef.current : rightScrollRef.current;
    const dst = source === "left" ? rightScrollRef.current : leftScrollRef.current;
    if (src && dst) {
      dst.scrollTop = src.scrollTop;
    }
    requestAnimationFrame(() => {
      scrollingRef.current = false;
    });
  };

  const stats = useMemo(() => {
    let added = 0;
    let removed = 0;
    for (const line of diff.left) if (line.type === "removed") removed++;
    for (const line of diff.right) if (line.type === "added") added++;
    return { added, removed };
  }, [diff]);

  return (
    <div className="flex flex-col h-full bg-obsidian-950">
      {/* Header */}
      <div className="flex border-b border-obsidian-700 shrink-0">
        {mode === "side-by-side" ? (
          <>
            <div className="flex-1 px-3 py-1.5 text-xs text-stone-400 border-r border-obsidian-700 flex items-center justify-between">
              <span className="truncate">{originalTitle}</span>
              <span className="text-red-400 text-[10px]">-{stats.removed}</span>
            </div>
            <div className="flex-1 px-3 py-1.5 text-xs text-stone-400 flex items-center justify-between">
              <span className="truncate">{modifiedTitle}</span>
              <span className="text-green-400 text-[10px]">+{stats.added}</span>
            </div>
          </>
        ) : (
          <div className="flex-1 px-3 py-1.5 text-xs text-stone-400 flex items-center justify-between">
            <span className="truncate">{originalTitle} &rarr; {modifiedTitle}</span>
            <div className="flex items-center gap-2">
              <span className="text-red-400 text-[10px]">-{stats.removed}</span>
              <span className="text-green-400 text-[10px]">+{stats.added}</span>
            </div>
          </div>
        )}
        <div className="flex items-center px-2 border-l border-obsidian-700">
          <button
            onClick={() => setMode(mode === "side-by-side" ? "unified" : "side-by-side")}
            className="p-1 rounded text-stone-500 hover:text-stone-200 hover:bg-obsidian-700 transition-colors"
            title={mode === "side-by-side" ? "Switch to unified view" : "Switch to side-by-side view"}
          >
            {mode === "side-by-side" ? <Rows2 size={14} /> : <Columns2 size={14} />}
          </button>
        </div>
      </div>

      {/* Diff content */}
      {mode === "side-by-side" ? (
        <div className="flex flex-1 min-h-0 overflow-hidden" ref={containerRef}>
          {/* Left (original) */}
          <div
            ref={leftScrollRef}
            className="flex-1 overflow-y-auto overflow-x-auto border-r border-obsidian-700 font-mono text-[12px] leading-[20px]"
            onScroll={() => handleScroll("left")}
          >
            {diff.left.map((line, i) => (
              <div
                key={i}
                className="flex"
                style={{ height: LINE_HEIGHT }}
              >
                <span
                  className="inline-block w-12 text-right pr-2 shrink-0 select-none text-[10px] leading-[20px]"
                  style={GUTTER_STYLES[line.type]}
                >
                  {line.lineNum ?? ""}
                </span>
                <span
                  className="flex-1 px-2 whitespace-pre"
                  style={TYPE_STYLES[line.type]}
                >
                  {line.type === "removed" && (
                    <span className="text-red-400/50 mr-1">-</span>
                  )}
                  <span className={line.type === "removed" ? "text-red-300" : "text-stone-300"}>
                    {line.content}
                  </span>
                </span>
              </div>
            ))}
          </div>

          {/* Right (modified) */}
          <div
            ref={rightScrollRef}
            className="flex-1 overflow-y-auto overflow-x-auto font-mono text-[12px] leading-[20px]"
            onScroll={() => handleScroll("right")}
          >
            {diff.right.map((line, i) => (
              <div
                key={i}
                className="flex"
                style={{ height: LINE_HEIGHT }}
              >
                <span
                  className="inline-block w-12 text-right pr-2 shrink-0 select-none text-[10px] leading-[20px]"
                  style={GUTTER_STYLES[line.type]}
                >
                  {line.lineNum ?? ""}
                </span>
                <span
                  className="flex-1 px-2 whitespace-pre"
                  style={TYPE_STYLES[line.type]}
                >
                  {line.type === "added" && (
                    <span className="text-green-400/50 mr-1">+</span>
                  )}
                  <span className={line.type === "added" ? "text-green-300" : "text-stone-300"}>
                    {line.content}
                  </span>
                </span>
              </div>
            ))}
          </div>
        </div>
      ) : (
        /* Unified view */
        <div className="flex-1 min-h-0 overflow-y-auto overflow-x-auto font-mono text-[12px] leading-[20px]">
          {diff.unified.map((line, i) => (
            <div
              key={i}
              className="flex"
              style={{ height: LINE_HEIGHT }}
            >
              <span
                className="inline-block w-12 text-right pr-1 shrink-0 select-none text-[10px] leading-[20px]"
                style={GUTTER_STYLES[line.type]}
              >
                {line.oldLineNum ?? ""}
              </span>
              <span
                className="inline-block w-12 text-right pr-2 shrink-0 select-none text-[10px] leading-[20px] border-r border-obsidian-700"
                style={GUTTER_STYLES[line.type]}
              >
                {line.newLineNum ?? ""}
              </span>
              <span
                className="flex-1 px-2 whitespace-pre"
                style={TYPE_STYLES[line.type]}
              >
                {line.type === "removed" && (
                  <span className="text-red-400/50 mr-1">-</span>
                )}
                {line.type === "added" && (
                  <span className="text-green-400/50 mr-1">+</span>
                )}
                {line.type === "same" && (
                  <span className="text-stone-600 mr-1">&nbsp;</span>
                )}
                <span className={
                  line.type === "removed" ? "text-red-300" :
                  line.type === "added" ? "text-green-300" :
                  "text-stone-300"
                }>
                  {line.content}
                </span>
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

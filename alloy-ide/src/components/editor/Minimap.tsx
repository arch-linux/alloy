import { useRef, useEffect, useCallback } from "react";

interface MinimapProps {
  content: string;
  visibleStart: number;
  visibleEnd: number;
  totalLines: number;
  onScrollTo: (line: number) => void;
}

const MINIMAP_WIDTH = 60;
const LINE_HEIGHT = 2;
const CHAR_WIDTH = 1;
const MAX_CHARS = 50;

// Simple syntax color mapping
function getCharColor(ch: string, inString: boolean, inComment: boolean): string {
  if (inComment) return "#4a4a58";
  if (inString) return "#7a6820";
  if (/[{}()\[\]]/.test(ch)) return "#6b6b7a";
  if (/[a-zA-Z]/.test(ch)) return "#888898";
  if (/[0-9]/.test(ch)) return "#884420";
  return "#555565";
}

export default function Minimap({
  content,
  visibleStart,
  visibleEnd,
  totalLines,
  onScrollTo,
}: MinimapProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const renderMinimap = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const lines = content.split("\n");
    const lineCount = lines.length;
    const height = lineCount * LINE_HEIGHT;

    // Set canvas size
    const dpr = window.devicePixelRatio || 1;
    canvas.width = MINIMAP_WIDTH * dpr;
    canvas.height = Math.max(height, 100) * dpr;
    canvas.style.width = MINIMAP_WIDTH + "px";
    canvas.style.height = Math.max(height, 100) + "px";
    ctx.scale(dpr, dpr);

    // Clear
    ctx.fillStyle = "#06060a";
    ctx.fillRect(0, 0, MINIMAP_WIDTH, Math.max(height, 100));

    // Draw visible region highlight
    const viewTop = (visibleStart - 1) * LINE_HEIGHT;
    const viewHeight = Math.max((visibleEnd - visibleStart + 1) * LINE_HEIGHT, 10);
    ctx.fillStyle = "rgba(255, 107, 0, 0.08)";
    ctx.fillRect(0, viewTop, MINIMAP_WIDTH, viewHeight);
    ctx.strokeStyle = "rgba(255, 107, 0, 0.2)";
    ctx.lineWidth = 0.5;
    ctx.strokeRect(0.5, viewTop + 0.5, MINIMAP_WIDTH - 1, viewHeight - 1);

    // Draw code lines
    let inBlockComment = false;

    for (let i = 0; i < lineCount; i++) {
      const line = lines[i];
      const y = i * LINE_HEIGHT;
      let inString = false;
      let inLineComment = false;
      let stringChar = "";

      for (let j = 0; j < Math.min(line.length, MAX_CHARS); j++) {
        const ch = line[j];
        const next = line[j + 1];

        // Track comment state
        if (!inString && !inLineComment && !inBlockComment && ch === "/" && next === "/") {
          inLineComment = true;
        }
        if (!inString && !inLineComment && !inBlockComment && ch === "/" && next === "*") {
          inBlockComment = true;
        }
        if (inBlockComment && ch === "*" && next === "/") {
          inBlockComment = false;
          continue;
        }

        // Track string state
        if (!inLineComment && !inBlockComment && (ch === '"' || ch === "'")) {
          if (!inString) {
            inString = true;
            stringChar = ch;
          } else if (ch === stringChar) {
            inString = false;
          }
        }

        if (ch === " " || ch === "\t") continue;

        ctx.fillStyle = getCharColor(ch, inString, inLineComment || inBlockComment);
        ctx.fillRect(j * CHAR_WIDTH + 4, y, CHAR_WIDTH, LINE_HEIGHT - 0.5);
      }
    }
  }, [content, visibleStart, visibleEnd]);

  useEffect(() => {
    renderMinimap();
  }, [renderMinimap]);

  const handleClick = (e: React.MouseEvent) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const y = e.clientY - rect.top;
    const line = Math.floor(y / LINE_HEIGHT) + 1;
    onScrollTo(Math.max(1, Math.min(line, totalLines)));
  };

  const handleMouseDown = (e: React.MouseEvent) => {
    handleClick(e);

    const onMove = (ev: MouseEvent) => {
      const canvas = canvasRef.current;
      if (!canvas) return;
      const rect = canvas.getBoundingClientRect();
      const y = ev.clientY - rect.top;
      const line = Math.floor(y / LINE_HEIGHT) + 1;
      onScrollTo(Math.max(1, Math.min(line, totalLines)));
    };

    const onUp = () => {
      document.removeEventListener("mousemove", onMove);
      document.removeEventListener("mouseup", onUp);
    };

    document.addEventListener("mousemove", onMove);
    document.addEventListener("mouseup", onUp);
  };

  return (
    <div
      ref={containerRef}
      className="shrink-0 overflow-y-auto overflow-x-hidden cursor-pointer border-l border-obsidian-700/50"
      style={{ width: MINIMAP_WIDTH }}
      onMouseDown={handleMouseDown}
    >
      <canvas ref={canvasRef} />
    </div>
  );
}

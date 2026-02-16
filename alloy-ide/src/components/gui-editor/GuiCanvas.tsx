import { useState, useCallback, useRef, useEffect } from "react";
import type { GuiProject, GuiElement } from "../../lib/types";

interface Props {
  project: GuiProject;
  selectedId: string | null;
  zoom: number;
  showGrid: boolean;
  gridSize: number;
  onSelect: (id: string | null) => void;
  onMoveElement: (id: string, x: number, y: number) => void;
  onResizeElement: (id: string, width: number, height: number) => void;
}

const WIDGET_COLORS: Record<string, { bg: string; border: string; label: string }> = {
  slot: { bg: "rgba(139,119,101,0.3)", border: "#8b7765", label: "S" },
  progress_bar: { bg: "rgba(255,107,0,0.2)", border: "#ff6b00", label: "P" },
  energy_bar: { bg: "rgba(255,68,0,0.2)", border: "#ff4400", label: "E" },
  fluid_tank: { bg: "rgba(59,130,246,0.2)", border: "#3b82f6", label: "F" },
  button: { bg: "rgba(160,160,160,0.2)", border: "#a0a0a0", label: "B" },
  label: { bg: "rgba(240,240,244,0.1)", border: "#6b7280", label: "L" },
  region: { bg: "rgba(240,184,48,0.1)", border: "#f0b830", label: "R" },
  image: { bg: "rgba(139,92,246,0.15)", border: "#8b5cf6", label: "I" },
};

export default function GuiCanvas({
  project,
  selectedId,
  zoom,
  showGrid,
  gridSize,
  onSelect,
  onMoveElement,
  onResizeElement,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [dragging, setDragging] = useState<{
    id: string;
    mode: "move" | "resize";
    startX: number;
    startY: number;
    origX: number;
    origY: number;
    origW: number;
    origH: number;
  } | null>(null);

  const snap = useCallback(
    (val: number) => (showGrid ? Math.round(val / gridSize) * gridSize : val),
    [showGrid, gridSize],
  );

  const handleMouseDown = useCallback(
    (e: React.MouseEvent, el: GuiElement, mode: "move" | "resize") => {
      e.stopPropagation();
      e.preventDefault();
      onSelect(el.id);
      setDragging({
        id: el.id,
        mode,
        startX: e.clientX,
        startY: e.clientY,
        origX: el.x,
        origY: el.y,
        origW: el.width,
        origH: el.height,
      });
    },
    [onSelect],
  );

  useEffect(() => {
    if (!dragging) return;

    const handleMouseMove = (e: MouseEvent) => {
      const dx = (e.clientX - dragging.startX) / zoom;
      const dy = (e.clientY - dragging.startY) / zoom;

      if (dragging.mode === "move") {
        const newX = snap(Math.max(0, Math.min(project.width - 1, dragging.origX + dx)));
        const newY = snap(Math.max(0, Math.min(project.height - 1, dragging.origY + dy)));
        onMoveElement(dragging.id, Math.round(newX), Math.round(newY));
      } else {
        const newW = snap(Math.max(4, dragging.origW + dx));
        const newH = snap(Math.max(4, dragging.origH + dy));
        onResizeElement(dragging.id, Math.round(newW), Math.round(newH));
      }
    };

    const handleMouseUp = () => setDragging(null);

    window.addEventListener("mousemove", handleMouseMove);
    window.addEventListener("mouseup", handleMouseUp);
    return () => {
      window.removeEventListener("mousemove", handleMouseMove);
      window.removeEventListener("mouseup", handleMouseUp);
    };
  }, [dragging, zoom, snap, project.width, project.height, onMoveElement, onResizeElement]);

  const canvasW = project.width * zoom;
  const canvasH = project.height * zoom;

  return (
    <div
      ref={containerRef}
      className="flex-1 overflow-auto bg-obsidian-950"
      onClick={() => onSelect(null)}
      style={{ imageRendering: "pixelated" }}
    >
      <div className="flex items-center justify-center min-h-full min-w-full p-8">
        <div
          className="relative border border-obsidian-600 shadow-lg"
          style={{
            width: canvasW,
            height: canvasH,
            background: "#1a1a24",
          }}
        >
          {/* Minecraft-style dark GUI background */}
          <div
            className="absolute inset-0"
            style={{
              background: `linear-gradient(135deg, #2a2a36 0%, #1e1e28 50%, #14141c 100%)`,
              border: `${zoom}px solid`,
              borderColor: "#555 #2a2a2a #2a2a2a #555",
              boxSizing: "border-box",
            }}
          />

          {/* Grid overlay */}
          {showGrid && (
            <svg
              className="absolute inset-0 pointer-events-none"
              width={canvasW}
              height={canvasH}
              style={{ opacity: 0.12 }}
            >
              {Array.from({ length: Math.ceil(project.width / gridSize) - 1 }, (_, i) => (
                <line
                  key={`v${i}`}
                  x1={(i + 1) * gridSize * zoom}
                  y1={0}
                  x2={(i + 1) * gridSize * zoom}
                  y2={canvasH}
                  stroke="#ff6b00"
                  strokeWidth={0.5}
                />
              ))}
              {Array.from({ length: Math.ceil(project.height / gridSize) - 1 }, (_, i) => (
                <line
                  key={`h${i}`}
                  x1={0}
                  y1={(i + 1) * gridSize * zoom}
                  x2={canvasW}
                  y2={(i + 1) * gridSize * zoom}
                  stroke="#ff6b00"
                  strokeWidth={0.5}
                />
              ))}
            </svg>
          )}

          {/* Elements */}
          {project.elements.map((el) => {
            const colors = WIDGET_COLORS[el.type] || WIDGET_COLORS.region;
            const isSelected = el.id === selectedId;

            return (
              <div
                key={el.id}
                className="absolute group"
                style={{
                  left: el.x * zoom,
                  top: el.y * zoom,
                  width: el.width * zoom,
                  height: el.height * zoom,
                }}
              >
                {/* Element body */}
                <div
                  className="absolute inset-0 cursor-move transition-shadow"
                  style={{
                    background: colors.bg,
                    border: `${Math.max(1, zoom * 0.5)}px solid ${isSelected ? "#ff6b00" : colors.border}`,
                    boxShadow: isSelected ? "0 0 0 1px #ff6b00, 0 0 8px rgba(255,107,0,0.3)" : "none",
                  }}
                  onMouseDown={(e) => handleMouseDown(e, el, "move")}
                >
                  {/* Type label */}
                  <div
                    className="absolute inset-0 flex items-center justify-center pointer-events-none select-none"
                    style={{ fontSize: Math.max(8, zoom * 5) }}
                  >
                    <span
                      className="font-mono font-bold"
                      style={{ color: colors.border, opacity: 0.8 }}
                    >
                      {renderWidgetContent(el, zoom)}
                    </span>
                  </div>
                </div>

                {/* Resize handle (bottom-right) */}
                {isSelected && (
                  <div
                    className="absolute -bottom-1 -right-1 w-2.5 h-2.5 bg-ember border border-obsidian-950 cursor-se-resize z-10"
                    onMouseDown={(e) => handleMouseDown(e, el, "resize")}
                  />
                )}

                {/* Size tooltip on hover */}
                {isSelected && (
                  <div
                    className="absolute -top-5 left-0 text-[9px] font-mono text-ember bg-obsidian-900/90 px-1 rounded whitespace-nowrap pointer-events-none"
                    style={{ fontSize: Math.max(8, zoom * 4.5) }}
                  >
                    {el.x},{el.y} {el.width}x{el.height}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function renderWidgetContent(el: GuiElement, zoom: number): string {
  switch (el.type) {
    case "slot":
      return el.properties.slot_id !== undefined ? `${el.properties.slot_id}` : "S";
    case "progress_bar":
      return "\u25B6"; // right arrow
    case "energy_bar":
      return "\u26A1"; // lightning
    case "fluid_tank":
      return "\u2248"; // wavy
    case "button":
      return el.label || "B";
    case "label":
      return el.label || "L";
    case "region":
      return "";
    case "image":
      return "\u25A3"; // square with fill
    default:
      return "?";
  }
}

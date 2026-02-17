/**
 * Interactive bezier curve easing editor.
 * Allows dragging two control points to define a cubic-bezier easing curve.
 * Outputs [x1, y1, x2, y2] values for the CSS cubic-bezier() function.
 */

import { useState, useRef, useCallback, useEffect } from "react";

interface Props {
  handles: [number, number, number, number];
  onChange: (handles: [number, number, number, number]) => void;
}

const SIZE = 160;
const PAD = 16;
const INNER = SIZE - PAD * 2;

// Common easing presets
const PRESETS: { label: string; handles: [number, number, number, number] }[] = [
  { label: "Ease In", handles: [0.42, 0, 1, 1] },
  { label: "Ease Out", handles: [0, 0, 0.58, 1] },
  { label: "Ease In/Out", handles: [0.42, 0, 0.58, 1] },
  { label: "Fast Start", handles: [0.12, 0, 0.39, 0] },
  { label: "Bounce", handles: [0.68, -0.55, 0.27, 1.55] },
];

export default function EasingEditor({ handles, onChange }: Props) {
  const [x1, y1, x2, y2] = handles;
  const svgRef = useRef<SVGSVGElement>(null);
  const [dragging, setDragging] = useState<1 | 2 | null>(null);

  // Convert from [0,1] to SVG coords (y is flipped)
  const toSvg = (x: number, y: number): [number, number] => [
    PAD + x * INNER,
    PAD + (1 - y) * INNER,
  ];

  // Convert from SVG coords to [0,1]
  const fromSvg = (sx: number, sy: number): [number, number] => [
    Math.max(-0.5, Math.min(1.5, (sx - PAD) / INNER)),
    Math.max(-0.5, Math.min(1.5, 1 - (sy - PAD) / INNER)),
  ];

  const [cp1x, cp1y] = toSvg(x1, y1);
  const [cp2x, cp2y] = toSvg(x2, y2);
  const [startX, startY] = toSvg(0, 0);
  const [endX, endY] = toSvg(1, 1);

  const handlePointerDown = useCallback((e: React.PointerEvent, point: 1 | 2) => {
    e.preventDefault();
    e.stopPropagation();
    setDragging(point);
    (e.target as Element).setPointerCapture(e.pointerId);
  }, []);

  const handlePointerMove = useCallback(
    (e: React.PointerEvent) => {
      if (!dragging || !svgRef.current) return;
      const rect = svgRef.current.getBoundingClientRect();
      const sx = e.clientX - rect.left;
      const sy = e.clientY - rect.top;
      const [nx, ny] = fromSvg(sx, sy);

      if (dragging === 1) {
        onChange([nx, ny, x2, y2]);
      } else {
        onChange([x1, y1, nx, ny]);
      }
    },
    [dragging, x1, y1, x2, y2, onChange],
  );

  const handlePointerUp = useCallback(() => {
    setDragging(null);
  }, []);

  // Build the bezier curve path
  const path = `M ${startX} ${startY} C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${endX} ${endY}`;

  // Build the linear reference path
  const linearPath = `M ${startX} ${startY} L ${endX} ${endY}`;

  return (
    <div className="flex flex-col gap-2">
      <svg
        ref={svgRef}
        width={SIZE}
        height={SIZE}
        className="bg-obsidian-900 rounded-lg border border-obsidian-600 cursor-crosshair select-none"
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
      >
        {/* Grid lines */}
        {[0.25, 0.5, 0.75].map((v) => {
          const [gx, gy] = toSvg(v, 0);
          const [, gy2] = toSvg(v, 1);
          return (
            <g key={v}>
              <line x1={gx} y1={gy} x2={gx} y2={gy2} stroke="#1e1e28" strokeWidth={0.5} />
              <line x1={PAD} y1={gy} x2={PAD + INNER} y2={gy2} stroke="#1e1e28" strokeWidth={0.5} />
            </g>
          );
        })}

        {/* Linear reference */}
        <path d={linearPath} fill="none" stroke="#2a2a36" strokeWidth={1} strokeDasharray="4,3" />

        {/* Curve */}
        <path d={path} fill="none" stroke="#ff6b00" strokeWidth={2} />

        {/* Control point lines */}
        <line x1={startX} y1={startY} x2={cp1x} y2={cp1y} stroke="#ff6b00" strokeWidth={1} strokeOpacity={0.4} />
        <line x1={endX} y1={endY} x2={cp2x} y2={cp2y} stroke="#ff6b00" strokeWidth={1} strokeOpacity={0.4} />

        {/* Start/end points */}
        <circle cx={startX} cy={startY} r={3} fill="#6b7280" />
        <circle cx={endX} cy={endY} r={3} fill="#6b7280" />

        {/* Control point 1 */}
        <circle
          cx={cp1x}
          cy={cp1y}
          r={6}
          fill={dragging === 1 ? "#ff8a33" : "#ff6b00"}
          stroke="#fff"
          strokeWidth={1.5}
          className="cursor-grab active:cursor-grabbing"
          onPointerDown={(e) => handlePointerDown(e, 1)}
        />

        {/* Control point 2 */}
        <circle
          cx={cp2x}
          cy={cp2y}
          r={6}
          fill={dragging === 2 ? "#ff8a33" : "#f0b830"}
          stroke="#fff"
          strokeWidth={1.5}
          className="cursor-grab active:cursor-grabbing"
          onPointerDown={(e) => handlePointerDown(e, 2)}
        />
      </svg>

      {/* Values display */}
      <div className="flex items-center gap-1 text-[9px] font-mono text-stone-500">
        <span>cubic-bezier(</span>
        <span className="text-ember">{x1.toFixed(2)}</span>,
        <span className="text-ember">{y1.toFixed(2)}</span>,
        <span className="text-forge-gold">{x2.toFixed(2)}</span>,
        <span className="text-forge-gold">{y2.toFixed(2)}</span>
        <span>)</span>
      </div>

      {/* Presets */}
      <div className="flex flex-wrap gap-1">
        {PRESETS.map((preset) => (
          <button
            key={preset.label}
            onClick={() => onChange(preset.handles)}
            className="px-1.5 py-0.5 rounded text-[9px] text-stone-400 bg-obsidian-800 border border-obsidian-600 hover:bg-obsidian-700 hover:text-stone-200 transition-colors"
          >
            {preset.label}
          </button>
        ))}
      </div>
    </div>
  );
}

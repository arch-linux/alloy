/**
 * Minecraft-scale GUI preview.
 * Renders the GUI at actual Minecraft resolution (1x, 2x, 3x scale) to show
 * how it would appear in-game. Read-only — editing happens in GuiCanvas.
 */

import { useRef, useEffect, useState } from "react";
import type { GuiProject, GuiElement } from "../../lib/types";

interface Props {
  project: GuiProject;
}

const SCALES = [1, 2, 3] as const;

// Minecraft vanilla GUI slot appearance
const MC_SLOT_BG = "#8b8b8b";
const MC_SLOT_BORDER_TOP = "#373737";
const MC_SLOT_BORDER_BOT = "#ffffff";
const MC_GUI_BG = "#c6c6c6";
const MC_GUI_BORDER_TOP = "#ffffff";
const MC_GUI_BORDER_BOT = "#555555";
const MC_GUI_DARK = "#373737";

export default function GuiPreview({ project }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [scale, setScale] = useState<1 | 2 | 3>(2);

  const canvasW = project.width * scale;
  const canvasH = project.height * scale;

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = canvasW * dpr;
    canvas.height = canvasH * dpr;
    ctx.scale(dpr, dpr);
    ctx.imageSmoothingEnabled = false;

    // Draw MC-style GUI background
    drawMcGuiBackground(ctx, project.width, project.height, scale);

    // Draw each element
    for (const el of project.elements) {
      drawElement(ctx, el, scale);
    }
  }, [project, scale, canvasW, canvasH]);

  return (
    <div className="flex-1 overflow-auto bg-obsidian-950">
      <div className="flex flex-col items-center justify-center min-h-full min-w-full p-4 gap-3">
        {/* Scale selector */}
        <div className="flex items-center gap-1">
          {SCALES.map((s) => (
            <button
              key={s}
              onClick={() => setScale(s)}
              className={
                "px-2 py-0.5 rounded text-[10px] font-mono transition-colors " +
                (s === scale
                  ? "bg-ember text-obsidian-950 font-bold"
                  : "text-stone-400 bg-obsidian-800 border border-obsidian-600 hover:bg-obsidian-700")
              }
            >
              {s}x
            </button>
          ))}
          <span className="text-[9px] text-stone-600 ml-2">Minecraft GUI Scale</span>
        </div>

        {/* Canvas */}
        <div
          className="border border-obsidian-600 shadow-xl"
          style={{ imageRendering: "pixelated" }}
        >
          <canvas
            ref={canvasRef}
            style={{ width: canvasW, height: canvasH }}
          />
        </div>

        {/* Info */}
        <div className="text-[9px] text-stone-600 text-center">
          {project.width}x{project.height} pixels · {project.elements.length} elements
        </div>
      </div>
    </div>
  );
}

function drawMcGuiBackground(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  scale: number,
) {
  const s = scale;

  // Main background (MC light gray)
  ctx.fillStyle = MC_GUI_BG;
  ctx.fillRect(0, 0, w * s, h * s);

  // Top-left highlight border
  ctx.fillStyle = MC_GUI_BORDER_TOP;
  ctx.fillRect(0, 0, w * s, s); // top
  ctx.fillRect(0, 0, s, h * s); // left

  // Inner top-left shadow
  ctx.fillStyle = MC_GUI_DARK;
  ctx.fillRect(s, s, (w - 2) * s, s); // inner top
  ctx.fillRect(s, s, s, (h - 2) * s); // inner left

  // Bottom-right shadow border
  ctx.fillStyle = MC_GUI_BORDER_BOT;
  ctx.fillRect(0, (h - 1) * s, w * s, s); // bottom
  ctx.fillRect((w - 1) * s, 0, s, h * s); // right

  // Inner bottom-right highlight
  ctx.fillStyle = MC_GUI_BG;
  ctx.fillRect(s * 2, s * 2, (w - 4) * s, (h - 4) * s);
}

function drawElement(
  ctx: CanvasRenderingContext2D,
  el: GuiElement,
  scale: number,
) {
  const s = scale;
  const x = el.x * s;
  const y = el.y * s;
  const w = el.width * s;
  const h = el.height * s;

  switch (el.type) {
    case "slot":
      drawSlot(ctx, x, y, w, h, s);
      break;
    case "progress_bar":
      drawProgressBar(ctx, x, y, w, h, s, el);
      break;
    case "energy_bar":
      drawEnergyBar(ctx, x, y, w, h, s);
      break;
    case "fluid_tank":
      drawFluidTank(ctx, x, y, w, h, s);
      break;
    case "button":
      drawButton(ctx, x, y, w, h, s, el);
      break;
    case "label":
      drawLabel(ctx, x, y, w, h, s, el);
      break;
    case "region":
      // Regions are invisible in preview (they're just hit areas)
      break;
    case "image":
      drawImagePlaceholder(ctx, x, y, w, h, s);
      break;
  }
}

function drawSlot(
  ctx: CanvasRenderingContext2D,
  x: number, y: number, w: number, h: number, s: number,
) {
  // MC slot: dark border inset
  ctx.fillStyle = MC_SLOT_BORDER_TOP;
  ctx.fillRect(x, y, w, s);
  ctx.fillRect(x, y, s, h);

  ctx.fillStyle = MC_SLOT_BORDER_BOT;
  ctx.fillRect(x, y + h - s, w, s);
  ctx.fillRect(x + w - s, y, s, h);

  ctx.fillStyle = MC_SLOT_BG;
  ctx.fillRect(x + s, y + s, w - 2 * s, h - 2 * s);
}

function drawProgressBar(
  ctx: CanvasRenderingContext2D,
  x: number, y: number, w: number, h: number, s: number,
  el: GuiElement,
) {
  // Background
  ctx.fillStyle = "#555555";
  ctx.fillRect(x, y, w, h);

  // Fill (50% for preview)
  const dir = (el.properties.direction as string) || "right";
  ctx.fillStyle = "#ff6b00";
  if (dir === "right") {
    ctx.fillRect(x, y, w * 0.5, h);
  } else if (dir === "left") {
    ctx.fillRect(x + w * 0.5, y, w * 0.5, h);
  } else if (dir === "up") {
    ctx.fillRect(x, y + h * 0.5, w, h * 0.5);
  } else {
    ctx.fillRect(x, y, w, h * 0.5);
  }

  // Border
  ctx.strokeStyle = "#373737";
  ctx.lineWidth = s * 0.5;
  ctx.strokeRect(x, y, w, h);
}

function drawEnergyBar(
  ctx: CanvasRenderingContext2D,
  x: number, y: number, w: number, h: number, s: number,
) {
  // Background
  ctx.fillStyle = "#373737";
  ctx.fillRect(x, y, w, h);

  // Energy fill (65% for preview, from bottom)
  const fillH = h * 0.65;
  ctx.fillStyle = "#ff4400";
  ctx.fillRect(x + s, y + h - fillH, w - 2 * s, fillH - s);

  // Gradient glow overlay
  const grad = ctx.createLinearGradient(x, y + h - fillH, x, y + h);
  grad.addColorStop(0, "rgba(255, 200, 0, 0.3)");
  grad.addColorStop(1, "rgba(255, 68, 0, 0.1)");
  ctx.fillStyle = grad;
  ctx.fillRect(x + s, y + h - fillH, w - 2 * s, fillH - s);

  // Border
  ctx.strokeStyle = "#555555";
  ctx.lineWidth = s;
  ctx.strokeRect(x, y, w, h);
}

function drawFluidTank(
  ctx: CanvasRenderingContext2D,
  x: number, y: number, w: number, h: number, s: number,
) {
  // Tank background
  ctx.fillStyle = "#1a1a24";
  ctx.fillRect(x, y, w, h);

  // Fluid fill (40% for preview, from bottom)
  const fillH = h * 0.4;
  ctx.fillStyle = "rgba(59, 130, 246, 0.6)";
  ctx.fillRect(x + s, y + h - fillH, w - 2 * s, fillH - s);

  // Glass border
  ctx.strokeStyle = "#6b7280";
  ctx.lineWidth = s;
  ctx.strokeRect(x, y, w, h);
}

function drawButton(
  ctx: CanvasRenderingContext2D,
  x: number, y: number, w: number, h: number, s: number,
  el: GuiElement,
) {
  // MC button style
  ctx.fillStyle = "#a0a0a0";
  ctx.fillRect(x, y, w, h);

  // Highlight
  ctx.fillStyle = "#e0e0e0";
  ctx.fillRect(x, y, w, s);
  ctx.fillRect(x, y, s, h);

  // Shadow
  ctx.fillStyle = "#555555";
  ctx.fillRect(x, y + h - s, w, s);
  ctx.fillRect(x + w - s, y, s, h);

  // Label
  if (el.label) {
    ctx.fillStyle = "#ffffff";
    ctx.font = `${Math.max(6, s * 4)}px monospace`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(el.label, x + w / 2, y + h / 2);
  }
}

function drawLabel(
  ctx: CanvasRenderingContext2D,
  x: number, y: number, w: number, h: number, s: number,
  el: GuiElement,
) {
  const color = (el.properties.color as string) || "#f0f0f4";
  const align = (el.properties.align as string) || "left";
  ctx.fillStyle = color;
  ctx.font = `${Math.max(6, s * 4)}px monospace`;
  ctx.textAlign = align === "center" ? "center" : align === "right" ? "right" : "left";
  ctx.textBaseline = "middle";

  const tx = align === "center" ? x + w / 2 : align === "right" ? x + w : x;
  ctx.fillText(el.label || "", tx, y + h / 2);
}

function drawImagePlaceholder(
  ctx: CanvasRenderingContext2D,
  x: number, y: number, w: number, h: number, s: number,
) {
  // Checkerboard pattern for image placeholder
  const checkSize = Math.max(s * 2, 4);
  for (let cy = 0; cy < h; cy += checkSize) {
    for (let cx = 0; cx < w; cx += checkSize) {
      const isOdd = (Math.floor(cx / checkSize) + Math.floor(cy / checkSize)) % 2 === 0;
      ctx.fillStyle = isOdd ? "#8b5cf6" : "#6d28d9";
      ctx.fillRect(
        x + cx,
        y + cy,
        Math.min(checkSize, w - cx),
        Math.min(checkSize, h - cy),
      );
    }
  }

  ctx.strokeStyle = "#8b5cf6";
  ctx.lineWidth = s * 0.5;
  ctx.strokeRect(x, y, w, h);
}

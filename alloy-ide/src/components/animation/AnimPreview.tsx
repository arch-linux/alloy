/**
 * Real-time animation preview canvas.
 * Renders a sample element being transformed by the current animation tracks.
 * Shows visual effects of UV offset, scale, rotation, opacity, and color changes.
 */

import { useRef, useEffect } from "react";
import type { AnimationProject, AnimationTrack, EasingType } from "../../lib/types";

interface Props {
  project: AnimationProject;
  playbackTick: number;
}

const CANVAS_SIZE = 200;
const ELEMENT_SIZE = 64;

export default function AnimPreview({ project, playbackTick }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = CANVAS_SIZE * dpr;
    canvas.height = CANVAS_SIZE * dpr;
    ctx.scale(dpr, dpr);

    // Get interpolated values for all tracks
    const values: Record<string, number> = {};
    for (const track of project.tracks) {
      values[track.property] = interpolateAtTick(track, playbackTick);
    }

    const cx = CANVAS_SIZE / 2;
    const cy = CANVAS_SIZE / 2;

    // Clear
    ctx.clearRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

    // Draw background grid
    ctx.strokeStyle = "rgba(42, 42, 54, 0.5)";
    ctx.lineWidth = 0.5;
    for (let i = 0; i <= CANVAS_SIZE; i += 16) {
      ctx.beginPath();
      ctx.moveTo(i, 0);
      ctx.lineTo(i, CANVAS_SIZE);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(0, i);
      ctx.lineTo(CANVAS_SIZE, i);
      ctx.stroke();
    }

    // Draw crosshair
    ctx.strokeStyle = "rgba(107, 114, 128, 0.3)";
    ctx.lineWidth = 1;
    ctx.setLineDash([4, 4]);
    ctx.beginPath();
    ctx.moveTo(cx, 0);
    ctx.lineTo(cx, CANVAS_SIZE);
    ctx.moveTo(0, cy);
    ctx.lineTo(CANVAS_SIZE, cy);
    ctx.stroke();
    ctx.setLineDash([]);

    // Apply transforms
    ctx.save();
    ctx.translate(cx, cy);

    // Rotation
    const rotation = (values.rotation || 0) * Math.PI * 2; // 0-1 maps to full rotation
    ctx.rotate(rotation);

    // Scale
    const scaleX = values.scale_x !== undefined ? values.scale_x : 1;
    const scaleY = values.scale_y !== undefined ? values.scale_y : 1;
    ctx.scale(scaleX || 0.01, scaleY || 0.01); // avoid zero scale

    // Opacity
    const opacity = values.opacity !== undefined ? Math.max(0, Math.min(1, values.opacity)) : 1;
    ctx.globalAlpha = opacity;

    // Color
    const r = values.color_r !== undefined ? Math.round(values.color_r * 255) : 255;
    const g = values.color_g !== undefined ? Math.round(values.color_g * 255) : 107;
    const b = values.color_b !== undefined ? Math.round(values.color_b * 255) : 0;

    // UV offset (shifts texture position visually)
    const uvX = (values.uv_offset_x || 0) * ELEMENT_SIZE;
    const uvY = (values.uv_offset_y || 0) * ELEMENT_SIZE;

    // Sprite frame (shown as a frame number indicator)
    const spriteFrame = values.sprite_frame !== undefined ? Math.floor(values.sprite_frame) : 0;

    // Draw the sample element
    const halfSize = ELEMENT_SIZE / 2;

    // Main block with color
    ctx.fillStyle = `rgb(${r}, ${g}, ${b})`;
    ctx.fillRect(-halfSize + uvX, -halfSize + uvY, ELEMENT_SIZE, ELEMENT_SIZE);

    // Inner detail (simulates a machine/texture)
    ctx.fillStyle = `rgba(0, 0, 0, 0.3)`;
    ctx.fillRect(-halfSize + 4 + uvX, -halfSize + 4 + uvY, ELEMENT_SIZE - 8, ELEMENT_SIZE - 8);

    // Animated detail pattern
    const patternOffset = (playbackTick * 2) % 16;
    ctx.fillStyle = `rgba(255, 255, 255, 0.15)`;
    for (let i = 0; i < 4; i++) {
      const py = -halfSize + 8 + i * 12 + patternOffset % 12 + uvY;
      ctx.fillRect(-halfSize + 8 + uvX, py, ELEMENT_SIZE - 16, 2);
    }

    // Sprite frame indicator
    if (values.sprite_frame !== undefined) {
      ctx.fillStyle = "rgba(240, 184, 48, 0.8)";
      ctx.font = "bold 10px monospace";
      ctx.textAlign = "center";
      ctx.fillText(`F${spriteFrame}`, uvX, halfSize - 6 + uvY);
    }

    // Border
    ctx.strokeStyle = `rgba(${r}, ${g}, ${b}, 0.6)`;
    ctx.lineWidth = 2;
    ctx.strokeRect(-halfSize + uvX, -halfSize + uvY, ELEMENT_SIZE, ELEMENT_SIZE);

    ctx.restore();

    // Draw value readouts at bottom
    ctx.globalAlpha = 1;
    ctx.fillStyle = "#6b7280";
    ctx.font = "9px monospace";
    ctx.textAlign = "left";
    let textY = CANVAS_SIZE - 4;
    const readouts: string[] = [];
    if (values.rotation !== undefined) readouts.push(`rot: ${(values.rotation * 360).toFixed(0)}°`);
    if (values.scale_x !== undefined) readouts.push(`sx: ${scaleX.toFixed(2)}`);
    if (values.scale_y !== undefined) readouts.push(`sy: ${scaleY.toFixed(2)}`);
    if (values.opacity !== undefined) readouts.push(`α: ${opacity.toFixed(2)}`);
    if (readouts.length > 0) {
      ctx.fillText(readouts.join("  "), 4, textY);
    }
  }, [project, playbackTick]);

  return (
    <div className="flex items-center justify-center py-3 border-b border-obsidian-700 bg-obsidian-900/30 shrink-0">
      <canvas
        ref={canvasRef}
        style={{ width: CANVAS_SIZE, height: CANVAS_SIZE }}
        className="rounded-lg border border-obsidian-600"
      />
    </div>
  );
}

function interpolateAtTick(track: AnimationTrack, tick: number): number {
  const kfs = track.keyframes;
  if (kfs.length === 0) return 0;
  if (kfs.length === 1) return kfs[0].value;
  if (tick <= kfs[0].tick) return kfs[0].value;
  if (tick >= kfs[kfs.length - 1].tick) return kfs[kfs.length - 1].value;

  for (let i = 0; i < kfs.length - 1; i++) {
    if (tick >= kfs[i].tick && tick <= kfs[i + 1].tick) {
      const t = (tick - kfs[i].tick) / (kfs[i + 1].tick - kfs[i].tick);
      const eased = applyEasing(t, kfs[i].easing, kfs[i].bezier_handles);
      return kfs[i].value + (kfs[i + 1].value - kfs[i].value) * eased;
    }
  }
  return kfs[kfs.length - 1].value;
}

function applyEasing(
  t: number,
  easing: EasingType,
  bezierHandles?: [number, number, number, number],
): number {
  switch (easing) {
    case "linear":
      return t;
    case "ease-in":
      return t * t;
    case "ease-out":
      return 1 - (1 - t) * (1 - t);
    case "ease-in-out":
      return t < 0.5 ? 2 * t * t : 1 - (-2 * t + 2) ** 2 / 2;
    case "cubic-bezier": {
      const [x1, y1, x2, y2] = bezierHandles || [0.42, 0, 0.58, 1];
      // Simple cubic bezier evaluation
      const cb = (u: number, p1: number, p2: number) =>
        3 * (1 - u) * (1 - u) * u * p1 + 3 * (1 - u) * u * u * p2 + u * u * u;
      // Newton-Raphson to find u for t
      let u = t;
      for (let i = 0; i < 8; i++) {
        const bx = cb(u, x1, x2) - t;
        if (Math.abs(bx) < 1e-6) break;
        const dx = 3 * (1 - u) * (1 - u) * x1 + 6 * (1 - u) * u * (x2 - x1) + 3 * u * u * (1 - x2);
        if (Math.abs(dx) < 1e-6) break;
        u -= bx / dx;
      }
      u = Math.max(0, Math.min(1, u));
      return cb(u, y1, y2);
    }
    default:
      return t;
  }
}

import { useRef, useEffect, useState, useCallback } from "react";
import { RotateCcw, ZoomIn, ZoomOut } from "lucide-react";
import type { BlockProject } from "../../lib/types";

interface Props {
  project: BlockProject;
}

// Face colors when no texture is assigned (neutral gray)
const FACE_COLORS: Record<string, string> = {
  top: "#6B6B6B",
  front: "#555555",
  side: "#444444",
};

// Face colors when texture IS assigned (green tint = "has content")
const TEXTURED_COLORS: Record<string, string> = {
  top: "#8B9467",
  front: "#6B7A4F",
  side: "#556340",
};

const FACE_LABELS: Record<number, [string, string, string]> = {
  0: ["Top", "South", "East"],
  1: ["Top", "West", "South"],
  2: ["Top", "North", "West"],
  3: ["Top", "East", "North"],
};

const FACE_MAPPING: Record<number, [string, string, string]> = {
  0: ["top", "south", "east"],
  1: ["top", "west", "south"],
  2: ["top", "north", "west"],
  3: ["top", "east", "north"],
};

export default function IsometricPreview({ project }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [rotation, setRotation] = useState(0);
  const [scale, setScale] = useState(2);

  const rotate = useCallback(() => {
    setRotation((r) => (r + 1) % 4);
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    const baseSize = 64 * scale;
    const canvasWidth = baseSize * 3;
    const canvasHeight = baseSize * 3;

    canvas.width = canvasWidth * dpr;
    canvas.height = canvasHeight * dpr;
    canvas.style.width = `${canvasWidth}px`;
    canvas.style.height = `${canvasHeight}px`;
    ctx.scale(dpr, dpr);

    ctx.imageSmoothingEnabled = false;

    // Clear
    ctx.clearRect(0, 0, canvasWidth, canvasHeight);

    // Center point
    const cx = canvasWidth / 2;
    const cy = canvasHeight / 2;

    const s = baseSize; // Side length in pixels

    // Isometric projection constants
    const isoX = s * Math.cos(Math.PI / 6); // ~0.866 * s
    const isoY = s * Math.sin(Math.PI / 6); // ~0.5 * s

    // Get the face keys for current rotation
    const [topFace, frontFace, sideFace] = FACE_MAPPING[rotation];
    const [topLabel, frontLabel, sideLabel] = FACE_LABELS[rotation];

    // Resolve texture names
    const getTextureName = (face: string): string | null => {
      if (project.texture_mode === "all") {
        return project.textures.all;
      }
      return (project.textures as unknown as Record<string, string | null>)[face] || null;
    };

    const topTex = getTextureName(topFace);
    const frontTex = getTextureName(frontFace);
    const sideTex = getTextureName(sideFace);

    // Draw top face (parallelogram)
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(cx, cy - isoY);               // top center
    ctx.lineTo(cx + isoX, cy);                // right
    ctx.lineTo(cx, cy + isoY);                // bottom center
    ctx.lineTo(cx - isoX, cy);                // left
    ctx.closePath();

    ctx.fillStyle = topTex ? TEXTURED_COLORS.top : FACE_COLORS.top;
    ctx.fill();
    if (topTex) {
      // Crosshatch pattern to indicate texture assigned
      ctx.save();
      ctx.clip();
      ctx.strokeStyle = "rgba(255,255,255,0.12)";
      ctx.lineWidth = 1;
      for (let i = -canvasWidth; i < canvasWidth; i += 8) {
        ctx.beginPath();
        ctx.moveTo(cx + i, cy - isoY - s);
        ctx.lineTo(cx + i + s, cy + isoY + s);
        ctx.stroke();
      }
      ctx.restore();
      // Redraw the face path for stroke
      ctx.beginPath();
      ctx.moveTo(cx, cy - isoY);
      ctx.lineTo(cx + isoX, cy);
      ctx.lineTo(cx, cy + isoY);
      ctx.lineTo(cx - isoX, cy);
      ctx.closePath();
    }
    ctx.strokeStyle = "rgba(0,0,0,0.3)";
    ctx.lineWidth = 1;
    ctx.stroke();

    // Label
    ctx.fillStyle = "rgba(255,255,255,0.6)";
    ctx.font = `${Math.max(9, scale * 5)}px monospace`;
    ctx.textAlign = "center";
    ctx.fillText(topTex || topLabel, cx, cy + 4);
    ctx.restore();

    // Draw front face (left parallelogram)
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(cx, cy + isoY);                // top
    ctx.lineTo(cx + isoX, cy);                // top right
    ctx.lineTo(cx + isoX, cy + s);            // bottom right
    ctx.lineTo(cx, cy + isoY + s);            // bottom
    ctx.closePath();

    ctx.fillStyle = frontTex ? TEXTURED_COLORS.front : FACE_COLORS.front;
    ctx.fill();
    if (frontTex) {
      ctx.save();
      ctx.clip();
      ctx.strokeStyle = "rgba(255,255,255,0.08)";
      ctx.lineWidth = 1;
      for (let i = -canvasWidth; i < canvasWidth; i += 8) {
        ctx.beginPath();
        ctx.moveTo(cx + i, cy - s);
        ctx.lineTo(cx + i + s, cy + s * 2);
        ctx.stroke();
      }
      ctx.restore();
      ctx.beginPath();
      ctx.moveTo(cx, cy + isoY);
      ctx.lineTo(cx + isoX, cy);
      ctx.lineTo(cx + isoX, cy + s);
      ctx.lineTo(cx, cy + isoY + s);
      ctx.closePath();
    }
    ctx.strokeStyle = "rgba(0,0,0,0.3)";
    ctx.lineWidth = 1;
    ctx.stroke();

    // Label
    ctx.fillStyle = "rgba(255,255,255,0.5)";
    ctx.font = `${Math.max(9, scale * 5)}px monospace`;
    ctx.textAlign = "center";
    ctx.fillText(
      frontTex || frontLabel,
      cx + isoX / 2,
      cy + isoY / 2 + s / 2 + 4,
    );
    ctx.restore();

    // Draw side face (right parallelogram)
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(cx, cy + isoY);                // top
    ctx.lineTo(cx - isoX, cy);                // top left
    ctx.lineTo(cx - isoX, cy + s);            // bottom left
    ctx.lineTo(cx, cy + isoY + s);            // bottom
    ctx.closePath();

    ctx.fillStyle = sideTex ? TEXTURED_COLORS.side : FACE_COLORS.side;
    ctx.fill();
    if (sideTex) {
      ctx.save();
      ctx.clip();
      ctx.strokeStyle = "rgba(255,255,255,0.06)";
      ctx.lineWidth = 1;
      for (let i = -canvasWidth; i < canvasWidth; i += 8) {
        ctx.beginPath();
        ctx.moveTo(cx + i, cy - s);
        ctx.lineTo(cx + i + s, cy + s * 2);
        ctx.stroke();
      }
      ctx.restore();
      ctx.beginPath();
      ctx.moveTo(cx, cy + isoY);
      ctx.lineTo(cx - isoX, cy);
      ctx.lineTo(cx - isoX, cy + s);
      ctx.lineTo(cx, cy + isoY + s);
      ctx.closePath();
    }
    ctx.strokeStyle = "rgba(0,0,0,0.3)";
    ctx.lineWidth = 1;
    ctx.stroke();

    // Label
    ctx.fillStyle = "rgba(255,255,255,0.4)";
    ctx.font = `${Math.max(9, scale * 5)}px monospace`;
    ctx.textAlign = "center";
    ctx.fillText(
      sideTex || sideLabel,
      cx - isoX / 2,
      cy + isoY / 2 + s / 2 + 4,
    );
    ctx.restore();

    // Draw edge highlights
    ctx.save();
    ctx.strokeStyle = "rgba(255,255,255,0.1)";
    ctx.lineWidth = 1;
    // Top-left edge
    ctx.beginPath();
    ctx.moveTo(cx, cy - isoY);
    ctx.lineTo(cx - isoX, cy);
    ctx.stroke();
    // Top-right edge
    ctx.beginPath();
    ctx.moveTo(cx, cy - isoY);
    ctx.lineTo(cx + isoX, cy);
    ctx.stroke();
    ctx.restore();

  }, [project, rotation, scale]);

  return (
    <div className="flex flex-col items-center justify-center h-full gap-4 p-6">
      <div className="flex items-center gap-2 mb-2">
        <button
          onClick={() => setScale(Math.max(1, scale - 1))}
          className="p-1.5 rounded bg-obsidian-800 text-stone-500 hover:text-stone-300 transition-colors"
          title="Zoom Out"
        >
          <ZoomOut size={13} />
        </button>
        <span className="text-[10px] text-stone-500 w-8 text-center">{scale}x</span>
        <button
          onClick={() => setScale(Math.min(4, scale + 1))}
          className="p-1.5 rounded bg-obsidian-800 text-stone-500 hover:text-stone-300 transition-colors"
          title="Zoom In"
        >
          <ZoomIn size={13} />
        </button>
        <div className="w-px h-4 bg-obsidian-700 mx-1" />
        <button
          onClick={rotate}
          className="p-1.5 rounded bg-obsidian-800 text-stone-500 hover:text-stone-300 transition-colors"
          title="Rotate 90°"
        >
          <RotateCcw size={13} />
        </button>
        <span className="text-[10px] text-stone-600">{rotation * 90}°</span>
      </div>

      <div className="bg-obsidian-900 rounded-lg border border-obsidian-700 p-4">
        <canvas
          ref={canvasRef}
          className="block"
          style={{ imageRendering: "pixelated" }}
        />
      </div>

      <div className="text-center">
        <p className="text-[10px] text-stone-600">
          Isometric preview — {project.texture_mode === "all" ? "all faces same texture" : "per-face textures"}
        </p>
        <p className="text-[9px] text-stone-700 mt-1">
          Click rotate to see different faces. Showing: {FACE_LABELS[rotation].join(", ")}
        </p>
      </div>
    </div>
  );
}

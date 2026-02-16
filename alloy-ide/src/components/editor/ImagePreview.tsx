import { useState, useEffect, useRef } from "react";
import { convertFileSrc } from "@tauri-apps/api/core";
import { ZoomIn, ZoomOut, RotateCcw, Maximize2 } from "lucide-react";

interface ImagePreviewProps {
  path: string;
  name: string;
}

export default function ImagePreview({ path, name }: ImagePreviewProps) {
  const [zoom, setZoom] = useState(1);
  const [dimensions, setDimensions] = useState<{ w: number; h: number } | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [fitMode, setFitMode] = useState<"fit" | "actual">("fit");
  const containerRef = useRef<HTMLDivElement>(null);
  const imgRef = useRef<HTMLImageElement>(null);

  const src = convertFileSrc(path);

  const ext = name.split(".").pop()?.toLowerCase() || "";
  const isSvg = ext === "svg";

  useEffect(() => {
    setZoom(1);
    setLoaded(false);
    setDimensions(null);
    setFitMode("fit");
  }, [path]);

  const handleLoad = () => {
    if (imgRef.current) {
      setDimensions({
        w: imgRef.current.naturalWidth,
        h: imgRef.current.naturalHeight,
      });
    }
    setLoaded(true);
  };

  const zoomIn = () => setZoom((z) => Math.min(z * 1.25, 16));
  const zoomOut = () => setZoom((z) => Math.max(z / 1.25, 0.1));
  const resetZoom = () => {
    setZoom(1);
    setFitMode("fit");
  };
  const toggleFit = () => {
    if (fitMode === "fit") {
      setFitMode("actual");
      setZoom(1);
    } else {
      setFitMode("fit");
      setZoom(1);
    }
  };

  // Scroll zoom
  const handleWheel = (e: React.WheelEvent) => {
    if (e.ctrlKey || e.metaKey) {
      e.preventDefault();
      if (e.deltaY < 0) zoomIn();
      else zoomOut();
    }
  };

  const imgStyle: React.CSSProperties =
    fitMode === "fit"
      ? {
          maxWidth: "100%",
          maxHeight: "100%",
          transform: `scale(${zoom})`,
          transformOrigin: "center center",
        }
      : {
          width: dimensions ? dimensions.w * zoom : "auto",
          height: dimensions ? dimensions.h * zoom : "auto",
          transformOrigin: "top left",
        };

  return (
    <div className="flex h-full flex-col bg-obsidian-950">
      {/* Toolbar */}
      <div className="flex h-8 shrink-0 items-center justify-between border-b border-obsidian-700 bg-obsidian-900 px-3">
        <div className="flex items-center gap-2 text-[11px] text-stone-400">
          {dimensions && (
            <>
              <span>
                {dimensions.w} x {dimensions.h}
              </span>
              <span className="text-obsidian-600">|</span>
            </>
          )}
          <span className="uppercase">{ext}</span>
          {isSvg && (
            <>
              <span className="text-obsidian-600">|</span>
              <span className="text-forge-gold">Vector</span>
            </>
          )}
        </div>
        <div className="flex items-center gap-1">
          <span className="text-[10px] text-stone-500 mr-1">
            {Math.round(zoom * 100)}%
          </span>
          <button
            onClick={zoomOut}
            className="rounded p-1 text-stone-400 hover:bg-obsidian-700 hover:text-stone-200 transition-colors"
            title="Zoom Out"
          >
            <ZoomOut size={14} />
          </button>
          <button
            onClick={zoomIn}
            className="rounded p-1 text-stone-400 hover:bg-obsidian-700 hover:text-stone-200 transition-colors"
            title="Zoom In"
          >
            <ZoomIn size={14} />
          </button>
          <button
            onClick={resetZoom}
            className="rounded p-1 text-stone-400 hover:bg-obsidian-700 hover:text-stone-200 transition-colors"
            title="Reset Zoom"
          >
            <RotateCcw size={14} />
          </button>
          <button
            onClick={toggleFit}
            className={
              "rounded p-1 transition-colors " +
              (fitMode === "actual"
                ? "text-ember hover:bg-obsidian-700"
                : "text-stone-400 hover:bg-obsidian-700 hover:text-stone-200")
            }
            title={fitMode === "fit" ? "Actual Size" : "Fit to View"}
          >
            <Maximize2 size={14} />
          </button>
        </div>
      </div>

      {/* Image canvas */}
      <div
        ref={containerRef}
        className="flex-1 overflow-auto flex items-center justify-center"
        onWheel={handleWheel}
        style={{
          backgroundImage:
            "linear-gradient(45deg, #1a1a24 25%, transparent 25%, transparent 75%, #1a1a24 75%), " +
            "linear-gradient(45deg, #1a1a24 25%, transparent 25%, transparent 75%, #1a1a24 75%)",
          backgroundSize: "16px 16px",
          backgroundPosition: "0 0, 8px 8px",
          backgroundColor: "#14141c",
        }}
      >
        {!loaded && (
          <div className="text-stone-500 text-xs">Loading image...</div>
        )}
        <img
          ref={imgRef}
          src={src}
          alt={name}
          onLoad={handleLoad}
          draggable={false}
          className={
            "select-none " +
            (loaded ? "" : "hidden") +
            (isSvg ? "" : " image-rendering-pixelated")
          }
          style={imgStyle}
        />
      </div>

      {/* Minecraft scale indicator for textures */}
      {dimensions && (dimensions.w === 16 || dimensions.w === 32 || dimensions.w === 64) &&
        dimensions.w === dimensions.h && (
        <div className="flex h-6 shrink-0 items-center justify-center border-t border-obsidian-700 bg-obsidian-900">
          <span className="text-[10px] text-forge-gold">
            Minecraft {dimensions.w}x{dimensions.h} texture
          </span>
        </div>
      )}
    </div>
  );
}

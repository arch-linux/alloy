import { useCallback } from "react";
import { X, ImageIcon } from "lucide-react";
import type { BlockProject, BlockFace, BlockTextureMode } from "../../lib/types";

interface Props {
  project: BlockProject;
  onUpdate: (updates: Partial<BlockProject>) => void;
}

const FACE_LABELS: Record<BlockFace, string> = {
  top: "Top",
  bottom: "Bottom",
  north: "North",
  south: "South",
  east: "East",
  west: "West",
};

export default function TexturePanel({ project, onUpdate }: Props) {
  const setTextureMode = useCallback(
    (mode: BlockTextureMode) => {
      onUpdate({ texture_mode: mode });
    },
    [onUpdate],
  );

  const setTexture = useCallback(
    (face: "all" | BlockFace, value: string | null) => {
      onUpdate({
        textures: { ...project.textures, [face]: value },
      });
    },
    [project.textures, onUpdate],
  );

  const handleDrop = useCallback(
    (face: "all" | BlockFace) => (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      // Try to get the file path from the drag data (from file tree)
      const path = e.dataTransfer.getData("text/plain") || e.dataTransfer.getData("text");
      if (path && (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg"))) {
        // Extract name from path (without extension)
        const name = path.split("/").pop()?.replace(/\.\w+$/, "") || path;
        setTexture(face, name);
      }
    },
    [setTexture],
  );

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = "copy";
  }, []);

  return (
    <div className="p-6 max-w-3xl mx-auto">
      {/* Mode Toggle */}
      <div className="flex items-center gap-3 mb-6">
        <span className="text-[11px] text-stone-400">Texture Mode:</span>
        <div className="flex rounded-lg overflow-hidden border border-obsidian-700">
          <button
            onClick={() => setTextureMode("all")}
            className={
              "px-4 py-1.5 text-[11px] font-medium transition-colors " +
              (project.texture_mode === "all"
                ? "bg-ember/15 text-ember"
                : "bg-obsidian-900 text-stone-500 hover:text-stone-300")
            }
          >
            All Faces
          </button>
          <button
            onClick={() => setTextureMode("per_face")}
            className={
              "px-4 py-1.5 text-[11px] font-medium transition-colors border-l border-obsidian-700 " +
              (project.texture_mode === "per_face"
                ? "bg-ember/15 text-ember"
                : "bg-obsidian-900 text-stone-500 hover:text-stone-300")
            }
          >
            Per Face
          </button>
        </div>
      </div>

      {project.texture_mode === "all" ? (
        <div className="flex flex-col items-center gap-4">
          <p className="text-[11px] text-stone-500">
            One texture applied to all 6 faces of the block.
          </p>
          <TextureDropZone
            label="All Faces"
            texture={project.textures.all}
            projectPath={project.mod_id}
            onDrop={handleDrop("all")}
            onDragOver={handleDragOver}
            onClear={() => setTexture("all", null)}
            large
          />
        </div>
      ) : (
        <div className="flex flex-col items-center gap-2">
          <p className="text-[11px] text-stone-500 mb-4">
            Assign different textures to each face. Drag PNG files from the file tree.
          </p>
          {/* Cross/Net layout */}
          <div className="grid gap-2" style={{ gridTemplateColumns: "repeat(4, 80px)", gridTemplateRows: "repeat(3, 80px)" }}>
            {/* Row 1: top */}
            <div />
            <TextureDropZone
              label="Top"
              texture={project.textures.top}
              projectPath={project.mod_id}
              onDrop={handleDrop("top")}
              onDragOver={handleDragOver}
              onClear={() => setTexture("top", null)}
            />
            <div />
            <div />

            {/* Row 2: west, north, east, south */}
            <TextureDropZone
              label="West"
              texture={project.textures.west}
              projectPath={project.mod_id}
              onDrop={handleDrop("west")}
              onDragOver={handleDragOver}
              onClear={() => setTexture("west", null)}
            />
            <TextureDropZone
              label="North"
              texture={project.textures.north}
              projectPath={project.mod_id}
              onDrop={handleDrop("north")}
              onDragOver={handleDragOver}
              onClear={() => setTexture("north", null)}
            />
            <TextureDropZone
              label="East"
              texture={project.textures.east}
              projectPath={project.mod_id}
              onDrop={handleDrop("east")}
              onDragOver={handleDragOver}
              onClear={() => setTexture("east", null)}
            />
            <TextureDropZone
              label="South"
              texture={project.textures.south}
              projectPath={project.mod_id}
              onDrop={handleDrop("south")}
              onDragOver={handleDragOver}
              onClear={() => setTexture("south", null)}
            />

            {/* Row 3: bottom */}
            <div />
            <TextureDropZone
              label="Bottom"
              texture={project.textures.bottom}
              projectPath={project.mod_id}
              onDrop={handleDrop("bottom")}
              onDragOver={handleDragOver}
              onClear={() => setTexture("bottom", null)}
            />
            <div />
            <div />
          </div>
        </div>
      )}

      <div className="mt-6 p-3 rounded-lg bg-obsidian-900 border border-obsidian-700">
        <p className="text-[10px] text-stone-600 leading-relaxed">
          Drag a <code className="text-stone-500">.png</code> file from the file tree onto a face to assign it.
          Texture names reference files in <code className="text-stone-500">assets/{project.mod_id}/textures/block/</code>.
          You can also type a texture name directly.
        </p>
      </div>
    </div>
  );
}

function TextureDropZone({
  label,
  texture,
  projectPath,
  onDrop,
  onDragOver,
  onClear,
  large,
}: {
  label: string;
  texture: string | null;
  projectPath: string;
  onDrop: (e: React.DragEvent) => void;
  onDragOver: (e: React.DragEvent) => void;
  onClear: () => void;
  large?: boolean;
}) {
  const size = large ? "w-40 h-40" : "w-[80px] h-[80px]";

  return (
    <div
      onDrop={onDrop}
      onDragOver={onDragOver}
      className={
        `${size} relative rounded-lg border-2 border-dashed transition-colors ` +
        "flex flex-col items-center justify-center gap-1 " +
        (texture
          ? "border-ember/30 bg-ember/5"
          : "border-obsidian-600 bg-obsidian-900 hover:border-obsidian-500")
      }
    >
      {texture ? (
        <>
          <div className={`${large ? "w-16 h-16" : "w-8 h-8"} bg-obsidian-800 rounded overflow-hidden flex items-center justify-center`}>
            <div
              className="w-full h-full"
              style={{
                imageRendering: "pixelated",
                backgroundColor: "#4a3728",
              }}
            >
              <div className="w-full h-full flex items-center justify-center text-[8px] text-stone-500 font-mono">
                {texture.slice(0, 6)}
              </div>
            </div>
          </div>
          <span className="text-[8px] text-stone-400 font-mono truncate max-w-full px-1">
            {texture}
          </span>
          <button
            onClick={(e) => { e.stopPropagation(); onClear(); }}
            className="absolute top-0.5 right-0.5 p-0.5 rounded bg-obsidian-800/80 text-stone-500 hover:text-red-400 transition-colors"
          >
            <X size={10} />
          </button>
        </>
      ) : (
        <>
          <ImageIcon size={large ? 20 : 14} className="text-stone-600" />
          <span className="text-[9px] text-stone-600">{label}</span>
        </>
      )}
    </div>
  );
}

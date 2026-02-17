import { useState, useCallback, useEffect, useRef } from "react";
import {
  Play,
  Pause,
  SkipBack,
  Plus,
  Trash2,
  Save,
  Code,
  Settings2,
} from "lucide-react";
import { showToast } from "../ui/Toast";
import type { AnimationProject, AnimationTrack, Keyframe, AnimPropertyType } from "../../lib/types";
import Timeline from "./Timeline";
import AnimCodePreview from "./AnimCodePreview";
import AnimPreview from "./AnimPreview";

interface Props {
  path: string;
  content: string;
  onSave: (content: string) => void;
}

let trackIdCounter = 0;
function nextTrackId() {
  return `track_${Date.now()}_${++trackIdCounter}`;
}

const DEFAULT_PROJECT: AnimationProject = {
  name: "new_animation",
  duration_ticks: 40,
  tracks: [],
  sprite_sheet: null,
  frame_width: null,
  frame_height: null,
};

const PROPERTY_LABELS: Record<AnimPropertyType, string> = {
  uv_offset_x: "UV Offset X",
  uv_offset_y: "UV Offset Y",
  opacity: "Opacity",
  color_r: "Color R",
  color_g: "Color G",
  color_b: "Color B",
  sprite_frame: "Sprite Frame",
  scale_x: "Scale X",
  scale_y: "Scale Y",
  rotation: "Rotation",
};

const PROPERTY_OPTIONS: AnimPropertyType[] = [
  "uv_offset_x",
  "uv_offset_y",
  "opacity",
  "color_r",
  "color_g",
  "color_b",
  "sprite_frame",
  "scale_x",
  "scale_y",
  "rotation",
];

export default function AnimationEditor({ path, content, onSave }: Props) {
  const [project, setProject] = useState<AnimationProject>(() => {
    try {
      const parsed = JSON.parse(content);
      if (parsed.name && parsed.tracks !== undefined) return parsed as AnimationProject;
    } catch { /* ignore */ }
    return { ...DEFAULT_PROJECT };
  });

  const [selectedTrackId, setSelectedTrackId] = useState<string | null>(null);
  const [playbackTick, setPlaybackTick] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [showCode, setShowCode] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const playRef = useRef<number | null>(null);

  const selectedTrack = project.tracks.find((t) => t.id === selectedTrackId) || null;

  // Playback loop at 20 TPS (50ms interval)
  useEffect(() => {
    if (!isPlaying) {
      if (playRef.current) cancelAnimationFrame(playRef.current);
      return;
    }

    let lastTime = performance.now();
    const tickInterval = 50; // 20 TPS = 50ms per tick

    const loop = (now: number) => {
      const delta = now - lastTime;
      if (delta >= tickInterval) {
        lastTime = now - (delta % tickInterval);
        setPlaybackTick((prev) => {
          const next = prev + 1;
          return next >= project.duration_ticks ? 0 : next;
        });
      }
      playRef.current = requestAnimationFrame(loop);
    };

    playRef.current = requestAnimationFrame(loop);
    return () => {
      if (playRef.current) cancelAnimationFrame(playRef.current);
    };
  }, [isPlaying, project.duration_ticks]);

  const updateProject = useCallback(
    (updater: (p: AnimationProject) => AnimationProject) => {
      setProject((prev) => updater(prev));
    },
    [],
  );

  const addTrack = useCallback(
    (property: AnimPropertyType) => {
      const track: AnimationTrack = {
        id: nextTrackId(),
        property,
        target_element: "",
        keyframes: [
          { tick: 0, value: 0, easing: "linear" },
          { tick: project.duration_ticks - 1, value: 1, easing: "linear" },
        ],
      };
      updateProject((p) => ({ ...p, tracks: [...p.tracks, track] }));
      setSelectedTrackId(track.id);
    },
    [project.duration_ticks, updateProject],
  );

  const deleteTrack = useCallback(
    (id: string) => {
      updateProject((p) => ({ ...p, tracks: p.tracks.filter((t) => t.id !== id) }));
      if (selectedTrackId === id) setSelectedTrackId(null);
    },
    [updateProject, selectedTrackId],
  );

  const updateTrack = useCallback(
    (id: string, updates: Partial<AnimationTrack>) => {
      updateProject((p) => ({
        ...p,
        tracks: p.tracks.map((t) => (t.id === id ? { ...t, ...updates } : t)),
      }));
    },
    [updateProject],
  );

  const addKeyframe = useCallback(
    (trackId: string, tick: number, value: number) => {
      updateProject((p) => ({
        ...p,
        tracks: p.tracks.map((t) => {
          if (t.id !== trackId) return t;
          // Remove existing keyframe at same tick
          const kfs = t.keyframes.filter((k) => k.tick !== tick);
          kfs.push({ tick, value, easing: "linear" });
          kfs.sort((a, b) => a.tick - b.tick);
          return { ...t, keyframes: kfs };
        }),
      }));
    },
    [updateProject],
  );

  const deleteKeyframe = useCallback(
    (trackId: string, tick: number) => {
      updateProject((p) => ({
        ...p,
        tracks: p.tracks.map((t) => {
          if (t.id !== trackId) return t;
          return { ...t, keyframes: t.keyframes.filter((k) => k.tick !== tick) };
        }),
      }));
    },
    [updateProject],
  );

  const updateKeyframe = useCallback(
    (trackId: string, tick: number, updates: Partial<Keyframe>) => {
      updateProject((p) => ({
        ...p,
        tracks: p.tracks.map((t) => {
          if (t.id !== trackId) return t;
          return {
            ...t,
            keyframes: t.keyframes.map((k) =>
              k.tick === tick ? { ...k, ...updates } : k,
            ),
          };
        }),
      }));
    },
    [updateProject],
  );

  const handleSave = useCallback(() => {
    const json = JSON.stringify(project, null, 2);
    onSave(json);
    showToast("success", "Animation saved");
  }, [project, onSave]);

  const togglePlay = () => {
    if (isPlaying) {
      setIsPlaying(false);
    } else {
      setIsPlaying(true);
    }
  };

  const goToStart = () => {
    setIsPlaying(false);
    setPlaybackTick(0);
  };

  return (
    <div className="flex h-full w-full flex-col bg-obsidian-950">
      {/* Toolbar */}
      <div className="flex items-center gap-1 px-3 py-1.5 border-b border-obsidian-700 bg-obsidian-900 shrink-0">
        <button
          onClick={goToStart}
          className="p-1.5 rounded text-stone-500 hover:text-stone-300 hover:bg-obsidian-800 transition-colors cursor-pointer"
          title="Go to start"
        >
          <SkipBack size={13} />
        </button>
        <button
          onClick={togglePlay}
          className={
            "p-1.5 rounded transition-colors cursor-pointer " +
            (isPlaying ? "bg-ember/15 text-ember" : "text-stone-500 hover:text-stone-300 hover:bg-obsidian-800")
          }
          title={isPlaying ? "Pause" : "Play"}
        >
          {isPlaying ? <Pause size={13} /> : <Play size={13} />}
        </button>

        <div className="flex items-center gap-1 ml-2">
          <span className="text-[10px] font-mono text-ember w-6 text-right">{playbackTick}</span>
          <span className="text-[10px] text-stone-600">/</span>
          <span className="text-[10px] font-mono text-stone-500">{project.duration_ticks}</span>
          <span className="text-[9px] text-stone-600 ml-1">ticks</span>
        </div>

        <div className="flex items-center ml-2 gap-1">
          <span className="text-[9px] text-stone-600">({(playbackTick / 20).toFixed(2)}s</span>
          <span className="text-[9px] text-stone-600">@ 20 TPS)</span>
        </div>

        <div className="flex-1" />

        {/* Add track dropdown */}
        <div className="relative group">
          <button
            className="flex items-center gap-1 p-1.5 rounded text-stone-500 hover:text-stone-300 hover:bg-obsidian-800 transition-colors cursor-pointer"
            title="Add track"
          >
            <Plus size={13} />
            <span className="text-[10px]">Track</span>
          </button>
          <div className="absolute right-0 top-full mt-1 w-44 bg-obsidian-800 border border-obsidian-600 rounded-lg shadow-xl z-20 hidden group-hover:block">
            {PROPERTY_OPTIONS.map((prop) => (
              <button
                key={prop}
                onClick={() => addTrack(prop)}
                className="w-full text-left px-3 py-1.5 text-[11px] text-stone-300 hover:bg-obsidian-700 transition-colors first:rounded-t-lg last:rounded-b-lg cursor-pointer"
              >
                {PROPERTY_LABELS[prop]}
              </button>
            ))}
          </div>
        </div>

        <div className="w-px h-4 bg-obsidian-700 mx-1" />

        <button
          onClick={() => setShowSettings(!showSettings)}
          className={
            "p-1.5 rounded transition-colors cursor-pointer " +
            (showSettings ? "bg-ember/15 text-ember" : "text-stone-500 hover:text-stone-300 hover:bg-obsidian-800")
          }
          title="Settings"
        >
          <Settings2 size={13} />
        </button>

        <button
          onClick={() => setShowCode(!showCode)}
          className={
            "p-1.5 rounded transition-colors cursor-pointer " +
            (showCode ? "bg-ember/15 text-ember" : "text-stone-500 hover:text-stone-300 hover:bg-obsidian-800")
          }
          title="Preview Code"
        >
          <Code size={13} />
        </button>

        <button
          onClick={handleSave}
          className="p-1.5 rounded text-stone-500 hover:text-stone-300 hover:bg-obsidian-800 transition-colors cursor-pointer"
          title="Save"
        >
          <Save size={13} />
        </button>
      </div>

      {/* Settings panel */}
      {showSettings && (
        <div className="flex flex-col gap-2 px-4 py-2 border-b border-obsidian-700 bg-obsidian-900/50">
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              <label className="text-[10px] text-stone-500 uppercase">Name</label>
              <input
                type="text"
                value={project.name}
                onChange={(e) => updateProject((p) => ({ ...p, name: e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, "_") }))}
                className="prop-input w-32"
              />
            </div>
            <div className="flex items-center gap-2">
              <label className="text-[10px] text-stone-500 uppercase">Duration</label>
              <input
                type="number"
                value={project.duration_ticks}
                onChange={(e) => updateProject((p) => ({ ...p, duration_ticks: Math.max(1, +e.target.value) }))}
                className="prop-input w-16"
              />
              <span className="text-[9px] text-stone-600">ticks ({(project.duration_ticks / 20).toFixed(1)}s)</span>
            </div>
          </div>
          {/* Sprite sheet section */}
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              <label className="text-[10px] text-stone-500 uppercase">Sprite Sheet</label>
              <input
                type="text"
                value={project.sprite_sheet || ""}
                onChange={(e) => updateProject((p) => ({ ...p, sprite_sheet: e.target.value || null }))}
                className="prop-input w-48"
                placeholder="path/to/spritesheet.png"
              />
              <button
                onClick={async () => {
                  try {
                    const { open } = await import("@tauri-apps/plugin-dialog");
                    const result = await open({
                      filters: [{ name: "Images", extensions: ["png", "jpg", "jpeg", "gif"] }],
                    });
                    if (result) {
                      updateProject((p) => ({ ...p, sprite_sheet: result as string }));
                    }
                  } catch { /* dialog not available in dev */ }
                }}
                className="px-1.5 py-0.5 rounded text-[9px] text-stone-400 bg-obsidian-700 border border-obsidian-600 hover:bg-obsidian-600 transition-colors"
              >
                Browse
              </button>
            </div>
            {project.sprite_sheet && (
              <>
                <div className="flex items-center gap-2">
                  <label className="text-[10px] text-stone-500 uppercase">Frame W</label>
                  <input
                    type="number"
                    value={project.frame_width || 16}
                    onChange={(e) => updateProject((p) => ({ ...p, frame_width: Math.max(1, +e.target.value) }))}
                    className="prop-input w-12"
                  />
                </div>
                <div className="flex items-center gap-2">
                  <label className="text-[10px] text-stone-500 uppercase">Frame H</label>
                  <input
                    type="number"
                    value={project.frame_height || 16}
                    onChange={(e) => updateProject((p) => ({ ...p, frame_height: Math.max(1, +e.target.value) }))}
                    className="prop-input w-12"
                  />
                </div>
                <span className="text-[9px] text-stone-600">
                  {project.frame_width && project.frame_height
                    ? `${project.frame_width}x${project.frame_height}px per frame`
                    : ""}
                </span>
              </>
            )}
          </div>
        </div>
      )}

      {/* Main content */}
      {showCode ? (
        <AnimCodePreview project={project} />
      ) : (
        <div className="flex flex-1 flex-col min-h-0">
          {/* Preview area */}
          <AnimPreview
            project={project}
            playbackTick={playbackTick}
          />

          {/* Timeline */}
          <Timeline
            project={project}
            playbackTick={playbackTick}
            selectedTrackId={selectedTrackId}
            onSelectTrack={setSelectedTrackId}
            onSeek={setPlaybackTick}
            onAddKeyframe={addKeyframe}
            onDeleteKeyframe={deleteKeyframe}
            onUpdateKeyframe={updateKeyframe}
            onDeleteTrack={deleteTrack}
          />
        </div>
      )}
    </div>
  );
}


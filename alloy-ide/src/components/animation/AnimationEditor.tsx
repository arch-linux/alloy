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
import type { AnimationProject, AnimationTrack, Keyframe, AnimPropertyType, EasingType } from "../../lib/types";
import Timeline from "./Timeline";
import AnimCodePreview from "./AnimCodePreview";

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
        <div className="flex items-center gap-4 px-4 py-2 border-b border-obsidian-700 bg-obsidian-900/50">
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
          <div className="flex items-center gap-2">
            <label className="text-[10px] text-stone-500 uppercase">Sprite Sheet</label>
            <input
              type="text"
              value={project.sprite_sheet || ""}
              onChange={(e) => updateProject((p) => ({ ...p, sprite_sheet: e.target.value || null }))}
              className="prop-input w-40"
              placeholder="path/to/spritesheet.png"
            />
          </div>
        </div>
      )}

      {/* Main content */}
      {showCode ? (
        <AnimCodePreview project={project} />
      ) : (
        <div className="flex flex-1 flex-col min-h-0">
          {/* Preview area */}
          <AnimPreviewArea
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

// Inline preview area showing interpolated values
function AnimPreviewArea({
  project,
  playbackTick,
}: {
  project: AnimationProject;
  playbackTick: number;
}) {
  if (project.tracks.length === 0) {
    return (
      <div className="flex items-center justify-center h-32 text-stone-600 text-[11px]">
        Add tracks to start animating. Each track controls a property over time.
      </div>
    );
  }

  return (
    <div className="flex items-center gap-4 px-4 py-3 border-b border-obsidian-700 bg-obsidian-900/30 overflow-x-auto shrink-0">
      {project.tracks.map((track) => {
        const value = interpolateAtTick(track, playbackTick);
        const progress = project.duration_ticks > 0 ? playbackTick / project.duration_ticks : 0;

        return (
          <div key={track.id} className="flex items-center gap-2 shrink-0">
            <div className="text-[9px] text-stone-500 uppercase w-16 truncate">
              {PROPERTY_LABELS[track.property]?.split(" ").pop()}
            </div>
            <div className="w-16 h-1.5 bg-obsidian-700 rounded-full overflow-hidden">
              <div
                className="h-full bg-ember rounded-full transition-all"
                style={{ width: `${Math.abs(value) * 100}%` }}
              />
            </div>
            <span className="text-[10px] font-mono text-ember w-10 text-right">
              {value.toFixed(2)}
            </span>
          </div>
        );
      })}
    </div>
  );
}

function interpolateAtTick(track: AnimationTrack, tick: number): number {
  const kfs = track.keyframes;
  if (kfs.length === 0) return 0;
  if (kfs.length === 1) return kfs[0].value;
  if (tick <= kfs[0].tick) return kfs[0].value;
  if (tick >= kfs[kfs.length - 1].tick) return kfs[kfs.length - 1].value;

  // Find surrounding keyframes
  for (let i = 0; i < kfs.length - 1; i++) {
    if (tick >= kfs[i].tick && tick <= kfs[i + 1].tick) {
      const t = (tick - kfs[i].tick) / (kfs[i + 1].tick - kfs[i].tick);
      const eased = applyEasing(t, kfs[i].easing);
      return kfs[i].value + (kfs[i + 1].value - kfs[i].value) * eased;
    }
  }
  return kfs[kfs.length - 1].value;
}

function applyEasing(t: number, easing: EasingType): number {
  switch (easing) {
    case "linear":
      return t;
    case "ease-in":
      return t * t;
    case "ease-out":
      return 1 - (1 - t) * (1 - t);
    case "ease-in-out":
      return t < 0.5 ? 2 * t * t : 1 - (-2 * t + 2) ** 2 / 2;
    case "cubic-bezier":
      // Default cubic bezier approximation
      return 3 * (1 - t) * (1 - t) * t * 0.25 + 3 * (1 - t) * t * t * 0.75 + t * t * t;
    default:
      return t;
  }
}

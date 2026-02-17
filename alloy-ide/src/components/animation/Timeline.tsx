import { useState, useCallback, useRef } from "react";
import { Trash2, ChevronDown, ChevronRight } from "lucide-react";
import type { AnimationProject, AnimationTrack, Keyframe, AnimPropertyType, EasingType } from "../../lib/types";
import EasingEditor from "./EasingEditor";

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

const TRACK_COLORS: Record<string, string> = {
  uv_offset_x: "#ff6b00",
  uv_offset_y: "#ff8a33",
  opacity: "#f0b830",
  color_r: "#ff4444",
  color_g: "#44ff44",
  color_b: "#4488ff",
  sprite_frame: "#cc5500",
  scale_x: "#8b5cf6",
  scale_y: "#a78bfa",
  rotation: "#ff4400",
};

interface Props {
  project: AnimationProject;
  playbackTick: number;
  selectedTrackId: string | null;
  onSelectTrack: (id: string | null) => void;
  onSeek: (tick: number) => void;
  onAddKeyframe: (trackId: string, tick: number, value: number) => void;
  onDeleteKeyframe: (trackId: string, tick: number) => void;
  onUpdateKeyframe: (trackId: string, tick: number, updates: Partial<Keyframe>) => void;
  onDeleteTrack: (id: string) => void;
}

const HEADER_WIDTH = 140;
const TRACK_HEIGHT = 28;
const TICK_WIDTH = 8;

export default function Timeline({
  project,
  playbackTick,
  selectedTrackId,
  onSelectTrack,
  onSeek,
  onAddKeyframe,
  onDeleteKeyframe,
  onUpdateKeyframe,
  onDeleteTrack,
}: Props) {
  const timelineRef = useRef<HTMLDivElement>(null);
  const [selectedKeyframe, setSelectedKeyframe] = useState<{ trackId: string; tick: number } | null>(null);
  const [editingKeyframe, setEditingKeyframe] = useState<{ trackId: string; tick: number } | null>(null);

  const totalWidth = project.duration_ticks * TICK_WIDTH;

  const handleTimelineClick = useCallback(
    (e: React.MouseEvent) => {
      if (!timelineRef.current) return;
      const rect = timelineRef.current.getBoundingClientRect();
      const x = e.clientX - rect.left - HEADER_WIDTH + timelineRef.current.scrollLeft;
      const tick = Math.max(0, Math.min(project.duration_ticks - 1, Math.round(x / TICK_WIDTH)));
      onSeek(tick);
    },
    [project.duration_ticks, onSeek],
  );

  const handleTrackDoubleClick = useCallback(
    (e: React.MouseEvent, trackId: string) => {
      if (!timelineRef.current) return;
      const rect = timelineRef.current.getBoundingClientRect();
      const x = e.clientX - rect.left - HEADER_WIDTH + timelineRef.current.scrollLeft;
      const tick = Math.max(0, Math.min(project.duration_ticks - 1, Math.round(x / TICK_WIDTH)));
      onAddKeyframe(trackId, tick, 0);
    },
    [project.duration_ticks, onAddKeyframe],
  );

  const handleKeyframeClick = useCallback(
    (e: React.MouseEvent, trackId: string, tick: number) => {
      e.stopPropagation();
      setSelectedKeyframe({ trackId, tick });
      onSelectTrack(trackId);
    },
    [onSelectTrack],
  );

  const handleKeyframeDoubleClick = useCallback(
    (e: React.MouseEvent, trackId: string, tick: number) => {
      e.stopPropagation();
      setEditingKeyframe({ trackId, tick });
    },
    [],
  );

  const handleKeyframeContextMenu = useCallback(
    (e: React.MouseEvent, trackId: string, tick: number) => {
      e.preventDefault();
      e.stopPropagation();
      onDeleteKeyframe(trackId, tick);
      setSelectedKeyframe(null);
    },
    [onDeleteKeyframe],
  );

  return (
    <div className="flex flex-col border-t border-obsidian-600 bg-obsidian-900 min-h-[120px] flex-1">
      {/* Ruler */}
      <div className="flex shrink-0 border-b border-obsidian-700">
        <div className="shrink-0 bg-obsidian-900 border-r border-obsidian-700" style={{ width: HEADER_WIDTH }}>
          <div className="px-2 py-1 text-[9px] text-stone-600 uppercase">Tracks</div>
        </div>
        <div className="flex-1 overflow-hidden relative" onClick={handleTimelineClick}>
          <svg width={totalWidth} height={20} className="block">
            {/* Tick marks */}
            {Array.from({ length: project.duration_ticks }, (_, i) => {
              const isMajor = i % 20 === 0;
              const isMinor = i % 10 === 0;
              return (
                <g key={i}>
                  <line
                    x1={i * TICK_WIDTH}
                    y1={isMajor ? 0 : isMinor ? 8 : 14}
                    x2={i * TICK_WIDTH}
                    y2={20}
                    stroke={isMajor ? "#6b7280" : isMinor ? "#3a3a48" : "#2a2a36"}
                    strokeWidth={isMajor ? 1 : 0.5}
                  />
                  {isMajor && (
                    <text x={i * TICK_WIDTH + 2} y={10} fill="#6b7280" fontSize={8} fontFamily="monospace">
                      {i}
                    </text>
                  )}
                </g>
              );
            })}
            {/* Playhead on ruler */}
            <line
              x1={playbackTick * TICK_WIDTH}
              y1={0}
              x2={playbackTick * TICK_WIDTH}
              y2={20}
              stroke="#ff6b00"
              strokeWidth={2}
            />
          </svg>
        </div>
      </div>

      {/* Tracks */}
      <div ref={timelineRef} className="flex-1 overflow-auto">
        {project.tracks.length === 0 ? (
          <div className="flex items-center justify-center h-full text-stone-600 text-[11px] py-8">
            No tracks yet. Use "+ Track" to add animation tracks.
          </div>
        ) : (
          project.tracks.map((track) => {
            const color = TRACK_COLORS[track.property] || "#ff6b00";
            const isSelected = track.id === selectedTrackId;

            return (
              <div
                key={track.id}
                className={"flex border-b border-obsidian-700/50 " + (isSelected ? "bg-obsidian-800/30" : "")}
                style={{ height: TRACK_HEIGHT }}
              >
                {/* Track header */}
                <div
                  className="shrink-0 flex items-center gap-1.5 px-2 border-r border-obsidian-700 cursor-pointer hover:bg-obsidian-800/50 transition-colors"
                  style={{ width: HEADER_WIDTH }}
                  onClick={() => onSelectTrack(track.id)}
                >
                  <div className="w-2 h-2 rounded-full shrink-0" style={{ background: color }} />
                  <span className="text-[10px] text-stone-300 truncate flex-1">
                    {PROPERTY_LABELS[track.property] || track.property}
                  </span>
                  <button
                    onClick={(e) => { e.stopPropagation(); onDeleteTrack(track.id); }}
                    className="p-0.5 rounded text-stone-600 hover:text-molten opacity-0 group-hover:opacity-100 hover:opacity-100 transition-all cursor-pointer"
                  >
                    <Trash2 size={10} />
                  </button>
                </div>

                {/* Track timeline area */}
                <div
                  className="flex-1 relative cursor-crosshair"
                  onDoubleClick={(e) => handleTrackDoubleClick(e, track.id)}
                  onClick={handleTimelineClick}
                >
                  <svg width={totalWidth} height={TRACK_HEIGHT} className="block">
                    {/* Interpolation lines between keyframes */}
                    {track.keyframes.map((kf, i) => {
                      if (i === 0) return null;
                      const prev = track.keyframes[i - 1];
                      return (
                        <line
                          key={`line_${i}`}
                          x1={prev.tick * TICK_WIDTH}
                          y1={TRACK_HEIGHT / 2}
                          x2={kf.tick * TICK_WIDTH}
                          y2={TRACK_HEIGHT / 2}
                          stroke={color}
                          strokeWidth={1.5}
                          strokeOpacity={0.4}
                          strokeDasharray={kf.easing === "linear" ? "none" : "3,2"}
                        />
                      );
                    })}

                    {/* Keyframe diamonds */}
                    {track.keyframes.map((kf) => {
                      const isKfSelected =
                        selectedKeyframe?.trackId === track.id && selectedKeyframe?.tick === kf.tick;
                      const cx = kf.tick * TICK_WIDTH;
                      const cy = TRACK_HEIGHT / 2;
                      const size = isKfSelected ? 5 : 4;

                      return (
                        <g key={`kf_${kf.tick}`}>
                          <rect
                            x={cx - size}
                            y={cy - size}
                            width={size * 2}
                            height={size * 2}
                            fill={isKfSelected ? "#ff6b00" : color}
                            stroke={isKfSelected ? "#fff" : "transparent"}
                            strokeWidth={1}
                            transform={`rotate(45, ${cx}, ${cy})`}
                            className="cursor-pointer"
                            onClick={(e) => handleKeyframeClick(e as unknown as React.MouseEvent, track.id, kf.tick)}
                            onDoubleClick={(e) => handleKeyframeDoubleClick(e as unknown as React.MouseEvent, track.id, kf.tick)}
                            onContextMenu={(e) => handleKeyframeContextMenu(e as unknown as React.MouseEvent, track.id, kf.tick)}
                          />
                          {/* Value label on selected keyframe */}
                          {isKfSelected && (
                            <text
                              x={cx}
                              y={cy - 8}
                              fill="#ff6b00"
                              fontSize={8}
                              fontFamily="monospace"
                              textAnchor="middle"
                            >
                              {kf.value.toFixed(1)}
                            </text>
                          )}
                        </g>
                      );
                    })}

                    {/* Playhead line */}
                    <line
                      x1={playbackTick * TICK_WIDTH}
                      y1={0}
                      x2={playbackTick * TICK_WIDTH}
                      y2={TRACK_HEIGHT}
                      stroke="#ff6b00"
                      strokeWidth={1}
                      strokeOpacity={0.6}
                    />
                  </svg>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Keyframe editor (shown when a keyframe is selected) */}
      {selectedKeyframe && (() => {
        const track = project.tracks.find((t) => t.id === selectedKeyframe.trackId);
        const kf = track?.keyframes.find((k) => k.tick === selectedKeyframe.tick);
        if (!track || !kf) return null;

        return (
          <>
            <div className="flex items-center gap-3 px-3 py-1.5 border-t border-obsidian-700 bg-obsidian-800/50 shrink-0">
              <span className="text-[9px] text-stone-500 uppercase">Keyframe</span>
              <div className="flex items-center gap-1">
                <span className="text-[9px] text-stone-600">Tick:</span>
                <input
                  type="number"
                  value={kf.tick}
                  onChange={(e) => {
                    const newTick = Math.max(0, Math.min(project.duration_ticks - 1, +e.target.value));
                    onDeleteKeyframe(track.id, kf.tick);
                    onAddKeyframe(track.id, newTick, kf.value);
                    setSelectedKeyframe({ trackId: track.id, tick: newTick });
                  }}
                  className="prop-input w-14"
                />
              </div>
              <div className="flex items-center gap-1">
                <span className="text-[9px] text-stone-600">Value:</span>
                <input
                  type="number"
                  step="0.1"
                  value={kf.value}
                  onChange={(e) => onUpdateKeyframe(track.id, kf.tick, { value: +e.target.value })}
                  className="prop-input w-16"
                />
              </div>
              <div className="flex items-center gap-1">
                <span className="text-[9px] text-stone-600">Easing:</span>
                <select
                  value={kf.easing}
                  onChange={(e) => onUpdateKeyframe(track.id, kf.tick, { easing: e.target.value as EasingType })}
                  className="prop-input w-24"
                >
                  <option value="linear">Linear</option>
                  <option value="ease-in">Ease In</option>
                  <option value="ease-out">Ease Out</option>
                  <option value="ease-in-out">Ease In/Out</option>
                  <option value="cubic-bezier">Cubic Bezier</option>
                </select>
              </div>
              <button
                onClick={() => { onDeleteKeyframe(track.id, kf.tick); setSelectedKeyframe(null); }}
                className="p-1 rounded text-stone-600 hover:text-molten hover:bg-molten/10 transition-colors cursor-pointer ml-auto"
                title="Delete keyframe"
              >
                <Trash2 size={11} />
              </button>
            </div>
            {kf.easing === "cubic-bezier" && (
              <div className="px-3 py-2 border-t border-obsidian-700/50 bg-obsidian-800/30">
                <EasingEditor
                  handles={kf.bezier_handles || [0.42, 0, 0.58, 1]}
                  onChange={(handles) => onUpdateKeyframe(track.id, kf.tick, { bezier_handles: handles })}
                />
              </div>
            )}
          </>
        );
      })()}
    </div>
  );
}

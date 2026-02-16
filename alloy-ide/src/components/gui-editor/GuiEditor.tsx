import { useState, useCallback, useEffect, useRef } from "react";
import {
  Grid3x3,
  ZoomIn,
  ZoomOut,
  Undo,
  Redo,
  Copy,
  Trash2,
  Code,
  Eye,
  Save,
  Maximize2,
} from "lucide-react";
import { useStore } from "../../lib/store";
import { showToast } from "../ui/Toast";
import type { GuiElement, GuiProject, GuiWidgetType } from "../../lib/types";
import GuiCanvas from "./GuiCanvas";
import WidgetPalette from "./WidgetPalette";
import PropertyPanel from "./PropertyPanel";
import GuiCodePreview from "./GuiCodePreview";

interface Props {
  path: string;
  content: string;
  onSave: (content: string) => void;
}

let idCounter = 0;
function nextId() {
  return `el_${Date.now()}_${++idCounter}`;
}

const DEFAULT_PROJECT: GuiProject = {
  name: "new_gui",
  width: 176,
  height: 166,
  background_texture: null,
  elements: [],
};

export default function GuiEditor({ path, content, onSave }: Props) {
  const [project, setProject] = useState<GuiProject>(() => {
    try {
      const parsed = JSON.parse(content);
      if (parsed.name && parsed.elements) return parsed as GuiProject;
    } catch { /* ignore */ }
    return { ...DEFAULT_PROJECT };
  });

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [zoom, setZoom] = useState(2);
  const [showGrid, setShowGrid] = useState(true);
  const [gridSize, setGridSize] = useState(8);
  const [showCode, setShowCode] = useState(false);
  const [undoStack, setUndoStack] = useState<GuiProject[]>([]);
  const [redoStack, setRedoStack] = useState<GuiProject[]>([]);
  const dirty = useRef(false);

  const selected = project.elements.find((e) => e.id === selectedId) || null;
  const modId = useStore((s) => s.currentProject)?.name?.toLowerCase().replace(/[^a-z0-9]/g, "") || "mymod";

  // Push to undo stack before mutation
  const pushUndo = useCallback(() => {
    setUndoStack((prev) => [...prev.slice(-49), project]);
    setRedoStack([]);
  }, [project]);

  const undo = useCallback(() => {
    if (undoStack.length === 0) return;
    setRedoStack((prev) => [project, ...prev]);
    const prev = undoStack[undoStack.length - 1];
    setUndoStack((s) => s.slice(0, -1));
    setProject(prev);
    dirty.current = true;
  }, [undoStack, project]);

  const redo = useCallback(() => {
    if (redoStack.length === 0) return;
    setUndoStack((prev) => [...prev, project]);
    const next = redoStack[0];
    setRedoStack((s) => s.slice(1));
    setProject(next);
    dirty.current = true;
  }, [redoStack, project]);

  const updateProject = useCallback(
    (updater: (p: GuiProject) => GuiProject) => {
      pushUndo();
      setProject((prev) => {
        const next = updater(prev);
        dirty.current = true;
        return next;
      });
    },
    [pushUndo],
  );

  const addElement = useCallback(
    (type: GuiWidgetType) => {
      const defaults = getWidgetDefaults(type);
      const el: GuiElement = {
        id: nextId(),
        type,
        x: Math.round(project.width / 2 - defaults.width / 2),
        y: Math.round(project.height / 2 - defaults.height / 2),
        ...defaults,
        properties: defaults.properties || {},
      };
      updateProject((p) => ({ ...p, elements: [...p.elements, el] }));
      setSelectedId(el.id);
    },
    [project.width, project.height, updateProject],
  );

  const updateElement = useCallback(
    (id: string, updates: Partial<GuiElement>) => {
      updateProject((p) => ({
        ...p,
        elements: p.elements.map((e) => (e.id === id ? { ...e, ...updates } : e)),
      }));
    },
    [updateProject],
  );

  const deleteElement = useCallback(
    (id: string) => {
      updateProject((p) => ({
        ...p,
        elements: p.elements.filter((e) => e.id !== id),
      }));
      if (selectedId === id) setSelectedId(null);
    },
    [updateProject, selectedId],
  );

  const duplicateElement = useCallback(
    (id: string) => {
      const el = project.elements.find((e) => e.id === id);
      if (!el) return;
      const newEl: GuiElement = { ...el, id: nextId(), x: el.x + 8, y: el.y + 8 };
      updateProject((p) => ({ ...p, elements: [...p.elements, newEl] }));
      setSelectedId(newEl.id);
    },
    [project.elements, updateProject],
  );

  // Keyboard shortcuts
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const mod = e.metaKey || e.ctrlKey;
      if (mod && e.key === "z" && !e.shiftKey) { e.preventDefault(); undo(); }
      if (mod && e.key === "z" && e.shiftKey) { e.preventDefault(); redo(); }
      if (mod && e.key === "y") { e.preventDefault(); redo(); }
      if (e.key === "Delete" || e.key === "Backspace") {
        if (selectedId && document.activeElement === document.body) {
          e.preventDefault();
          deleteElement(selectedId);
        }
      }
      if (mod && e.key === "d" && selectedId) {
        e.preventDefault();
        duplicateElement(selectedId);
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [undo, redo, selectedId, deleteElement, duplicateElement]);

  // Auto-save to store
  const handleSave = useCallback(() => {
    const json = JSON.stringify(project, null, 2);
    onSave(json);
    dirty.current = false;
    showToast("success", "GUI saved");
  }, [project, onSave]);

  return (
    <div className="flex h-full w-full bg-obsidian-950">
      {/* Left: Widget Palette */}
      <WidgetPalette onAdd={addElement} />

      {/* Center: Canvas + Toolbar */}
      <div className="flex flex-1 flex-col min-w-0">
        {/* Toolbar */}
        <div className="flex items-center gap-1 px-3 py-1.5 border-b border-obsidian-700 bg-obsidian-900 shrink-0">
          <span className="text-[11px] text-stone-400 font-mono mr-2 select-none">
            {project.width}x{project.height}
          </span>
          <ToolbarButton icon={<ZoomOut size={13} />} title="Zoom Out" onClick={() => setZoom(Math.max(1, zoom - 0.5))} />
          <span className="text-[10px] text-stone-500 w-8 text-center select-none">{zoom}x</span>
          <ToolbarButton icon={<ZoomIn size={13} />} title="Zoom In" onClick={() => setZoom(Math.min(4, zoom + 0.5))} />
          <div className="w-px h-4 bg-obsidian-700 mx-1" />
          <ToolbarButton icon={<Grid3x3 size={13} />} title="Toggle Grid" active={showGrid} onClick={() => setShowGrid(!showGrid)} />
          <div className="w-px h-4 bg-obsidian-700 mx-1" />
          <ToolbarButton icon={<Undo size={13} />} title="Undo" onClick={undo} disabled={undoStack.length === 0} />
          <ToolbarButton icon={<Redo size={13} />} title="Redo" onClick={redo} disabled={redoStack.length === 0} />
          <div className="w-px h-4 bg-obsidian-700 mx-1" />
          {selectedId && (
            <>
              <ToolbarButton icon={<Copy size={13} />} title="Duplicate" onClick={() => duplicateElement(selectedId)} />
              <ToolbarButton icon={<Trash2 size={13} />} title="Delete" onClick={() => deleteElement(selectedId)} />
              <div className="w-px h-4 bg-obsidian-700 mx-1" />
            </>
          )}
          <div className="flex-1" />
          <ToolbarButton icon={<Code size={13} />} title="Preview Code" active={showCode} onClick={() => setShowCode(!showCode)} />
          <ToolbarButton
            icon={<Save size={13} />}
            title="Save"
            onClick={handleSave}
          />
        </div>

        {/* Canvas area */}
        {showCode ? (
          <GuiCodePreview project={project} modId={modId} />
        ) : (
          <GuiCanvas
            project={project}
            selectedId={selectedId}
            zoom={zoom}
            showGrid={showGrid}
            gridSize={gridSize}
            onSelect={setSelectedId}
            onMoveElement={(id, x, y) => updateElement(id, { x, y })}
            onResizeElement={(id, w, h) => updateElement(id, { width: w, height: h })}
          />
        )}
      </div>

      {/* Right: Property Panel */}
      <PropertyPanel
        element={selected}
        project={project}
        onUpdate={(updates) => selectedId && updateElement(selectedId, updates)}
        onUpdateProject={(updates) => updateProject((p) => ({ ...p, ...updates }))}
        onDelete={() => selectedId && deleteElement(selectedId)}
      />
    </div>
  );
}

function ToolbarButton({
  icon,
  title,
  onClick,
  active,
  disabled,
}: {
  icon: React.ReactNode;
  title: string;
  onClick: () => void;
  active?: boolean;
  disabled?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={
        "p-1.5 rounded transition-colors cursor-pointer " +
        (active
          ? "bg-ember/15 text-ember"
          : "text-stone-500 hover:text-stone-300 hover:bg-obsidian-800") +
        (disabled ? " opacity-30 pointer-events-none" : "")
      }
    >
      {icon}
    </button>
  );
}

function getWidgetDefaults(type: GuiWidgetType): Omit<GuiElement, "id" | "type" | "x" | "y"> {
  switch (type) {
    case "slot":
      return { width: 18, height: 18, properties: { slot_id: 0 } };
    case "progress_bar":
      return { width: 24, height: 17, properties: { direction: "right", max_value: 200 } };
    case "energy_bar":
      return { width: 14, height: 42, properties: { direction: "up", max_value: 10000 } };
    case "fluid_tank":
      return { width: 16, height: 52, properties: { max_mb: 16000 } };
    case "button":
      return { width: 20, height: 20, label: "Button", properties: { action: "toggle" } };
    case "label":
      return { width: 40, height: 9, label: "Label", properties: { color: "#f0f0f4", align: "left" } };
    case "region":
      return { width: 32, height: 32, properties: { tooltip: "" } };
    case "image":
      return { width: 16, height: 16, properties: { texture: "", u: 0, v: 0 } };
    default:
      return { width: 16, height: 16, properties: {} };
  }
}

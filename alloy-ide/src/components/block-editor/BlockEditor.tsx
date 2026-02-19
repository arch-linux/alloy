import { useState, useCallback, useEffect, useRef } from "react";
import {
  Save,
  FileOutput,
  Undo,
  Redo,
  Box,
  Paintbrush,
  Settings,
  Eye,
  Code,
  Layout,
} from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";
import { showToast } from "../ui/Toast";
import type { BlockProject, BlockTextureMode, BlockTextures, BlockProperties } from "../../lib/types";
import BlockPropertiesPanel from "./BlockPropertiesPanel";
import TexturePanel from "./TexturePanel";
import IsometricPreview from "./IsometricPreview";
import BlockCodePreview from "./BlockCodePreview";

interface Props {
  path: string;
  content: string;
  onSave: (content: string) => void;
}

const DEFAULT_BLOCK: BlockProject = {
  name: "new_block",
  display_name: "New Block",
  mod_id: "mymod",
  texture_mode: "all",
  textures: {
    all: null,
    top: null,
    bottom: null,
    north: null,
    south: null,
    east: null,
    west: null,
  },
  properties: {
    hardness: 3.0,
    resistance: 6.0,
    requires_tool: true,
    tool_type: "pickaxe",
    tool_level: 1,
    light_level: 0,
    is_transparent: false,
    has_gravity: false,
    flammable: false,
    slipperiness: 0.6,
  },
  has_gui: false,
  gui_file: null,
  has_block_entity: false,
  custom_code: null,
  code_overrides: {},
};

type Tab = "properties" | "textures" | "preview" | "code" | "gui";

export default function BlockEditor({ path, content, onSave }: Props) {
  const [project, setProject] = useState<BlockProject>(() => {
    try {
      const parsed = JSON.parse(content);
      if (parsed.name && parsed.properties) return parsed as BlockProject;
    } catch { /* ignore */ }
    return { ...DEFAULT_BLOCK };
  });

  const [activeTab, setActiveTab] = useState<Tab>("properties");
  const [undoStack, setUndoStack] = useState<BlockProject[]>([]);
  const [redoStack, setRedoStack] = useState<BlockProject[]>([]);
  const dirty = useRef(false);

  const currentProject = useStore((s) => s.currentProject);
  const openFile = useStore((s) => s.openFile);
  const modId = currentProject?.name?.toLowerCase().replace(/[^a-z0-9]/g, "") || "mymod";

  // Sync mod_id from project
  useEffect(() => {
    if (project.mod_id !== modId) {
      setProject((p) => ({ ...p, mod_id: modId }));
    }
  }, [modId]);

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
    (updater: (p: BlockProject) => BlockProject) => {
      pushUndo();
      setProject((prev) => {
        const next = updater(prev);
        dirty.current = true;
        return next;
      });
    },
    [pushUndo],
  );

  // Keyboard shortcuts
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const mod = e.metaKey || e.ctrlKey;
      if (mod && e.key === "z" && !e.shiftKey) { e.preventDefault(); undo(); }
      if (mod && e.key === "z" && e.shiftKey) { e.preventDefault(); redo(); }
      if (mod && e.key === "y") { e.preventDefault(); redo(); }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [undo, redo]);

  const handleSave = useCallback(() => {
    const json = JSON.stringify(project, null, 2);
    onSave(json);
    dirty.current = false;
    showToast("success", "Block saved");
  }, [project, onSave]);

  const handleExport = useCallback(async () => {
    if (!currentProject) {
      showToast("error", "No project open");
      return;
    }
    // Save first
    const json = JSON.stringify(project, null, 2);
    onSave(json);
    try {
      await invoke("write_file", { path, content: json });
    } catch (e) {
      showToast("error", `Failed to save: ${e}`);
      return;
    }
    try {
      const result = await invoke<{
        created_files: { path: string; file_type: string }[];
        block_class_path: string;
        registration_snippet: string;
      }>("generate_block_code", {
        projectPath: currentProject.path,
        blockJsonPath: path,
      });
      showToast("success", `Exported ${result.created_files.length} files`);
      const fileName = result.block_class_path.split("/").pop() || "Block.java";
      openFile(result.block_class_path, fileName);
    } catch (e) {
      showToast("error", `Export failed: ${e}`);
    }
  }, [project, currentProject, path, onSave, openFile]);

  const tabs: { id: Tab; label: string; icon: React.ReactNode }[] = [
    { id: "properties", label: "Properties", icon: <Settings size={13} /> },
    { id: "textures", label: "Textures", icon: <Paintbrush size={13} /> },
    { id: "preview", label: "3D Preview", icon: <Eye size={13} /> },
    { id: "code", label: "Code", icon: <Code size={13} /> },
    { id: "gui", label: "GUI", icon: <Layout size={13} /> },
  ];

  return (
    <div className="flex h-full w-full flex-col bg-obsidian-950">
      {/* Toolbar */}
      <div className="flex items-center gap-1 px-3 py-1.5 border-b border-obsidian-700 bg-obsidian-900 shrink-0">
        <Box size={14} className="text-ember mr-1" />
        <span className="text-[11px] text-stone-400 font-mono mr-2 select-none truncate max-w-48">
          {project.name}
        </span>
        <div className="w-px h-4 bg-obsidian-700 mx-1" />

        {/* Tabs */}
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={
              "flex items-center gap-1.5 px-2.5 py-1 rounded text-[11px] transition-colors cursor-pointer " +
              (activeTab === tab.id
                ? "bg-ember/15 text-ember"
                : "text-stone-500 hover:text-stone-300 hover:bg-obsidian-800")
            }
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}

        <div className="flex-1" />

        <ToolbarButton icon={<Undo size={13} />} title="Undo" onClick={undo} disabled={undoStack.length === 0} />
        <ToolbarButton icon={<Redo size={13} />} title="Redo" onClick={redo} disabled={redoStack.length === 0} />
        <div className="w-px h-4 bg-obsidian-700 mx-1" />
        <ToolbarButton icon={<Save size={13} />} title="Save" onClick={handleSave} />
        <ToolbarButton icon={<FileOutput size={13} />} title="Export to Java" onClick={handleExport} />
      </div>

      {/* Tab Content */}
      <div className="flex-1 min-h-0 overflow-auto">
        {activeTab === "properties" && (
          <BlockPropertiesPanel
            project={project}
            onUpdate={(updates) => updateProject((p) => ({ ...p, ...updates }))}
          />
        )}
        {activeTab === "textures" && (
          <TexturePanel
            project={project}
            onUpdate={(updates) => updateProject((p) => ({ ...p, ...updates }))}
          />
        )}
        {activeTab === "preview" && (
          <IsometricPreview project={project} />
        )}
        {activeTab === "code" && (
          <BlockCodePreview project={project} />
        )}
        {activeTab === "gui" && (
          <GuiTab project={project} path={path} onUpdate={(updates) => updateProject((p) => ({ ...p, ...updates }))} />
        )}
      </div>
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

function GuiTab({
  project,
  path,
  onUpdate,
}: {
  project: BlockProject;
  path: string;
  onUpdate: (updates: Partial<BlockProject>) => void;
}) {
  const openFile = useStore((s) => s.openFile);

  const handleToggleGui = useCallback(async () => {
    if (project.has_gui) {
      onUpdate({ has_gui: false, gui_file: null, has_block_entity: false });
    } else {
      const guiFileName = `${project.name}.gui.json`;
      const dir = path.substring(0, path.lastIndexOf("/"));
      const guiPath = `${dir}/${guiFileName}`;
      const guiContent = JSON.stringify(
        { name: project.name, width: 176, height: 166, background_texture: null, elements: [] },
        null,
        2,
      );

      // Create the GUI file (or write it if it already exists)
      try {
        await invoke("create_file", { path: guiPath, content: guiContent });
      } catch {
        // File already exists — overwrite only if it's empty/default
        try {
          const existing = await invoke<string>("read_file", { path: guiPath });
          if (!existing || existing.trim() === "" || existing.trim() === "{}") {
            await invoke("write_file", { path: guiPath, content: guiContent });
          }
        } catch {
          // Can't read either — write fresh
          await invoke("write_file", { path: guiPath, content: guiContent });
        }
      }

      onUpdate({ has_gui: true, gui_file: guiFileName, has_block_entity: true });
      showToast("success", `Created ${guiFileName}`);
    }
  }, [project, path, onUpdate]);

  const handleOpenGui = useCallback(async () => {
    if (!project.gui_file) return;
    const dir = path.substring(0, path.lastIndexOf("/"));
    const guiPath = `${dir}/${project.gui_file}`;
    try {
      await openFile(guiPath, project.gui_file);
    } catch (e) {
      showToast("error", `Could not open GUI file: ${e}`);
    }
  }, [project, path, openFile]);

  return (
    <div className="flex flex-col items-center justify-center h-full gap-6 p-8">
      <Layout size={48} className="text-stone-600" />
      <div className="text-center max-w-sm">
        <h3 className="text-sm font-medium text-stone-300 mb-2">
          Block GUI
        </h3>
        <p className="text-xs text-stone-500 leading-relaxed mb-4">
          {project.has_gui
            ? `This block has an attached GUI: ${project.gui_file}`
            : "Enable a GUI for this block to create an interactive screen (like furnaces, chests, etc)."}
        </p>

        <div className="flex items-center justify-center gap-3">
          <button
            onClick={handleToggleGui}
            className={
              "px-4 py-2 rounded-md text-xs font-medium transition-colors " +
              (project.has_gui
                ? "bg-red-500/10 text-red-400 border border-red-500/20 hover:bg-red-500/20"
                : "bg-ember/10 text-ember border border-ember/20 hover:bg-ember/20")
            }
          >
            {project.has_gui ? "Remove GUI" : "Add GUI"}
          </button>

          {project.has_gui && project.gui_file && (
            <button
              onClick={handleOpenGui}
              className="px-4 py-2 rounded-md text-xs font-medium bg-obsidian-800 text-stone-300 border border-obsidian-600 hover:bg-obsidian-700 transition-colors"
            >
              Open GUI Editor
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

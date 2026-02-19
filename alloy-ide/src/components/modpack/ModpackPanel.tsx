import { useState, useEffect, useCallback } from "react";
import {
  Package,
  Plus,
  Trash2,
  ToggleLeft,
  ToggleRight,
  Monitor,
  Server,
  Globe,
  FileArchive,
  Save,
  RefreshCw,
  Download,
  Settings,
} from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { open, save } from "@tauri-apps/plugin-dialog";
import { useStore } from "../../lib/store";
import { showToast } from "../ui/Toast";
import type { ModpackMod, ModpackManifest, ModDependency } from "../../lib/types";
import ConflictResolver from "./ConflictResolver";
import DependencyEditor from "./DependencyEditor";
import ModConfigEditor from "./ModConfigEditor";

const ENV_ICONS: Record<string, typeof Monitor> = {
  client: Monitor,
  server: Server,
  both: Globe,
};

const ENV_COLORS: Record<string, string> = {
  client: "text-blue-400",
  server: "text-green-400",
  both: "text-forge-gold",
};

const ENV_LABELS: Record<string, string> = {
  client: "Client",
  server: "Server",
  both: "Universal",
};

export default function ModpackPanel() {
  const currentProject = useStore((s) => s.currentProject);
  const [manifest, setManifest] = useState<ModpackManifest | null>(null);
  const [loading, setLoading] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [configMod, setConfigMod] = useState<ModpackMod | null>(null);

  const loadManifest = useCallback(async () => {
    if (!currentProject) return;
    setLoading(true);
    try {
      const m = await invoke<ModpackManifest>("load_modpack_manifest", {
        projectPath: currentProject.path,
      });
      setManifest(m);
      setDirty(false);
    } catch (err) {
      showToast("error", `Failed to load modpack: ${err}`);
    } finally {
      setLoading(false);
    }
  }, [currentProject]);

  useEffect(() => {
    loadManifest();
  }, [loadManifest]);

  const saveManifest = async () => {
    if (!currentProject || !manifest) return;
    try {
      await invoke("save_modpack_manifest", {
        projectPath: currentProject.path,
        manifest,
      });
      setDirty(false);
      showToast("success", "Modpack saved");
    } catch (err) {
      showToast("error", `Failed to save: ${err}`);
    }
  };

  const addModFromJar = async () => {
    if (!currentProject) return;
    const selected = await open({
      multiple: false,
      filters: [{ name: "JAR Files", extensions: ["jar"] }],
    });
    if (!selected || typeof selected !== "string") return;

    try {
      const newMod = await invoke<ModpackMod>("add_mod_from_jar", {
        projectPath: currentProject.path,
        jarPath: selected,
      });
      if (manifest) {
        const updated = { ...manifest, mods: [...manifest.mods, newMod] };
        setManifest(updated);
        setDirty(true);
        showToast("success", `Added ${newMod.name}`);
      }
    } catch (err) {
      showToast("error", `Failed to add mod: ${err}`);
    }
  };

  const addModManual = () => {
    if (!manifest) return;
    const newMod: ModpackMod = {
      id: `mod-${Date.now()}`,
      name: "New Mod",
      version: "*",
      environment: "both",
      source: "local",
      source_path: null,
      enabled: true,
      description: null,
      dependencies: [],
    };
    setManifest({ ...manifest, mods: [...manifest.mods, newMod] });
    setDirty(true);
  };

  const toggleMod = (modId: string) => {
    if (!manifest) return;
    setManifest({
      ...manifest,
      mods: manifest.mods.map((m) =>
        m.id === modId ? { ...m, enabled: !m.enabled } : m
      ),
    });
    setDirty(true);
  };

  const removeMod = async (modId: string) => {
    if (!manifest || !currentProject) return;
    try {
      await invoke("remove_mod_from_pack", {
        projectPath: currentProject.path,
        modId,
      });
      setManifest({
        ...manifest,
        mods: manifest.mods.filter((m) => m.id !== modId),
      });
      setDirty(true);
      showToast("info", "Mod removed");
    } catch (err) {
      showToast("error", `Failed to remove mod: ${err}`);
    }
  };

  const updateModDeps = (modId: string, dependencies: ModDependency[]) => {
    if (!manifest) return;
    setManifest({
      ...manifest,
      mods: manifest.mods.map((m) =>
        m.id === modId ? { ...m, dependencies } : m
      ),
    });
    setDirty(true);
  };

  const updateModField = (modId: string, field: keyof ModpackMod, value: string) => {
    if (!manifest) return;
    setManifest({
      ...manifest,
      mods: manifest.mods.map((m) =>
        m.id === modId ? { ...m, [field]: value } : m
      ),
    });
    setDirty(true);
  };

  const updatePackField = (field: keyof ModpackManifest, value: string) => {
    if (!manifest) return;
    setManifest({ ...manifest, [field]: value });
    setDirty(true);
  };

  const [exporting, setExporting] = useState(false);

  const exportPack = async () => {
    if (!currentProject || !manifest) return;
    const outputPath = await save({
      defaultPath: `${manifest.name.replace(/\s+/g, "-").toLowerCase()}-${manifest.version}.alloypack`,
      filters: [{ name: "Alloy Modpack", extensions: ["alloypack"] }],
    });
    if (!outputPath) return;

    setExporting(true);
    try {
      const result = await invoke<string>("export_modpack", {
        projectPath: currentProject.path,
        outputPath,
      });
      showToast("success", result);
    } catch (err) {
      showToast("error", `Export failed: ${err}`);
    } finally {
      setExporting(false);
    }
  };


  if (!currentProject || currentProject.project_type !== "modpack") {
    return (
      <div className="flex flex-col items-center justify-center h-48 text-stone-500 text-xs gap-2 px-4">
        <Package size={24} className="text-stone-600" />
        <span>Open a modpack project to manage mods</span>
      </div>
    );
  }

  if (loading || !manifest) {
    return (
      <div className="flex items-center justify-center h-32 text-stone-500 text-xs">
        Loading modpack...
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full text-xs">
      {/* Pack info */}
      <div className="px-3 py-2 border-b border-obsidian-700 space-y-1.5">
        <div className="flex items-center gap-2">
          <input
            value={manifest.name}
            onChange={(e) => updatePackField("name", e.target.value)}
            className="flex-1 bg-obsidian-800 border border-obsidian-600 rounded px-2 py-1 text-stone-200 focus:outline-none focus:border-ember text-[11px] font-medium"
          />
          {dirty && (
            <button
              onClick={saveManifest}
              className="p-1 rounded bg-ember text-obsidian-950 hover:bg-ember-light transition-colors"
              title="Save modpack"
            >
              <Save size={12} />
            </button>
          )}
          <button
            onClick={exportPack}
            disabled={exporting}
            className="p-1 rounded text-stone-500 hover:text-ember hover:bg-obsidian-700 transition-colors disabled:opacity-50"
            title="Export .alloypack"
          >
            <Download size={12} className={exporting ? "animate-pulse" : ""} />
          </button>
          <button
            onClick={loadManifest}
            className="p-1 rounded text-stone-500 hover:text-stone-200 hover:bg-obsidian-700 transition-colors"
            title="Reload"
          >
            <RefreshCw size={12} />
          </button>
        </div>
        <div className="flex gap-2">
          <div className="flex-1">
            <label className="text-[10px] text-stone-600 uppercase">Version</label>
            <input
              value={manifest.version}
              onChange={(e) => updatePackField("version", e.target.value)}
              className="w-full bg-obsidian-800 border border-obsidian-600 rounded px-1.5 py-0.5 text-stone-300 focus:outline-none focus:border-ember text-[11px]"
            />
          </div>
          <div className="flex-1">
            <label className="text-[10px] text-stone-600 uppercase">MC Version</label>
            <input
              value={manifest.minecraft_version}
              onChange={(e) => updatePackField("minecraft_version", e.target.value)}
              className="w-full bg-obsidian-800 border border-obsidian-600 rounded px-1.5 py-0.5 text-stone-300 focus:outline-none focus:border-ember text-[11px]"
            />
          </div>
        </div>
      </div>

      {/* Conflict resolver */}
      <div className="border-b border-obsidian-700">
        <ConflictResolver />
      </div>

      {/* Action bar */}
      <div className="flex items-center gap-1 px-3 py-1.5 border-b border-obsidian-700">
        <span className="text-stone-500 text-[10px] uppercase tracking-wide font-medium flex-1">
          Mods ({manifest.mods.length})
        </span>
        <button
          onClick={addModFromJar}
          className="flex items-center gap-1 px-2 py-0.5 rounded text-[10px] text-stone-400 hover:text-stone-100 hover:bg-obsidian-700 transition-colors"
          title="Add from JAR file"
        >
          <FileArchive size={10} />
          <span>JAR</span>
        </button>
        <button
          onClick={addModManual}
          className="flex items-center gap-1 px-2 py-0.5 rounded text-[10px] text-ember hover:text-ember-light hover:bg-ember/10 transition-colors"
          title="Add mod manually"
        >
          <Plus size={10} />
          <span>Add</span>
        </button>
      </div>

      {/* Mod list */}
      <div className="flex-1 overflow-y-auto">
        {manifest.mods.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-32 text-stone-600 text-xs gap-2">
            <Package size={20} />
            <span>No mods added yet</span>
          </div>
        ) : (
          manifest.mods.map((mod) => {
            const EnvIcon = ENV_ICONS[mod.environment] || Globe;
            const envColor = ENV_COLORS[mod.environment] || "text-stone-400";

            return (
              <div
                key={mod.id}
                className={
                  "group flex items-start gap-2 px-3 py-2 border-b border-obsidian-700/50 hover:bg-obsidian-800/50 transition-colors" +
                  (!mod.enabled ? " opacity-50" : "")
                }
              >
                {/* Toggle */}
                <button
                  onClick={() => toggleMod(mod.id)}
                  className="mt-0.5 shrink-0 text-stone-500 hover:text-stone-200 transition-colors"
                  title={mod.enabled ? "Disable" : "Enable"}
                >
                  {mod.enabled ? (
                    <ToggleRight size={16} className="text-ember" />
                  ) : (
                    <ToggleLeft size={16} />
                  )}
                </button>

                {/* Mod info */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5">
                    <input
                      value={mod.name}
                      onChange={(e) => updateModField(mod.id, "name", e.target.value)}
                      className="bg-transparent text-stone-200 text-[11px] font-medium focus:outline-none focus:bg-obsidian-800 rounded px-0.5 min-w-0 flex-1"
                    />
                    <span className={"flex items-center gap-0.5 text-[10px] " + envColor}>
                      <EnvIcon size={10} />
                      {ENV_LABELS[mod.environment]}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-[10px] text-stone-600">{mod.id}</span>
                    <span className="text-[10px] text-stone-600">v{mod.version}</span>
                    <span className="text-[10px] text-stone-600 capitalize">{mod.source}</span>
                  </div>
                  <DependencyEditor
                    mod={mod}
                    availableMods={manifest.mods}
                    onChange={(deps) => updateModDeps(mod.id, deps)}
                  />
                </div>

                {/* Environment selector */}
                <select
                  value={mod.environment}
                  onChange={(e) => updateModField(mod.id, "environment", e.target.value)}
                  className="bg-obsidian-800 border border-obsidian-600 rounded px-1 py-0.5 text-[10px] text-stone-400 focus:outline-none focus:border-ember opacity-0 group-hover:opacity-100 transition-opacity"
                >
                  <option value="both">Universal</option>
                  <option value="client">Client</option>
                  <option value="server">Server</option>
                </select>

                {/* Config */}
                <button
                  onClick={() => setConfigMod(mod)}
                  className="mt-0.5 shrink-0 text-stone-600 hover:text-stone-200 opacity-0 group-hover:opacity-100 transition-all"
                  title="Edit config"
                >
                  <Settings size={12} />
                </button>

                {/* Delete */}
                <button
                  onClick={() => removeMod(mod.id)}
                  className="mt-0.5 shrink-0 text-stone-600 hover:text-red-400 opacity-0 group-hover:opacity-100 transition-all"
                  title="Remove mod"
                >
                  <Trash2 size={12} />
                </button>
              </div>
            );
          })
        )}
      </div>

      {/* Config editor modal */}
      {configMod && (
        <ModConfigEditor mod={configMod} onClose={() => setConfigMod(null)} />
      )}
    </div>
  );
}

import { useState, useEffect, useCallback } from "react";
import { X, Save, FileText } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";
import { showToast } from "../ui/Toast";
import type { ModpackMod } from "../../lib/types";

interface ModConfigEditorProps {
  mod: ModpackMod;
  onClose: () => void;
}

export default function ModConfigEditor({ mod: targetMod, onClose }: ModConfigEditorProps) {
  const currentProject = useStore((s) => s.currentProject);
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(true);
  const [dirty, setDirty] = useState(false);
  const [format, setFormat] = useState<"toml" | "json">("toml");

  const loadConfig = useCallback(async () => {
    if (!currentProject) return;
    setLoading(true);
    try {
      const config = await invoke<string>("read_mod_config", {
        projectPath: currentProject.path,
        modId: targetMod.id,
      });
      setContent(config);

      // Detect format from content
      if (config.trim().startsWith("{")) {
        setFormat("json");
      } else {
        setFormat("toml");
      }
      setDirty(false);
    } catch (err) {
      showToast("error", `Failed to load config: ${err}`);
    } finally {
      setLoading(false);
    }
  }, [currentProject, targetMod.id]);

  useEffect(() => {
    loadConfig();
  }, [loadConfig]);

  const saveConfig = async () => {
    if (!currentProject) return;
    try {
      await invoke("save_mod_config", {
        projectPath: currentProject.path,
        modId: targetMod.id,
        content,
        format,
      });
      setDirty(false);
      showToast("success", `Config saved for ${targetMod.name}`);
    } catch (err) {
      showToast("error", `Failed to save config: ${err}`);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if ((e.metaKey || e.ctrlKey) && e.key === "s") {
      e.preventDefault();
      saveConfig();
    }
    if (e.key === "Escape") {
      onClose();
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center" onClick={onClose}>
      <div className="absolute inset-0 bg-obsidian-950/60" />
      <div
        className="relative w-[640px] max-w-[90vw] max-h-[80vh] rounded-lg border border-obsidian-600 bg-obsidian-800 shadow-2xl overflow-hidden flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-2.5 border-b border-obsidian-700">
          <div className="flex items-center gap-2">
            <FileText size={14} className="text-ember" />
            <span className="text-sm text-stone-200 font-medium">
              {targetMod.name} — Config
            </span>
            <span className="text-[10px] text-stone-500 uppercase">{format}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <select
              value={format}
              onChange={(e) => {
                setFormat(e.target.value as "toml" | "json");
                setDirty(true);
              }}
              className="bg-obsidian-700 border border-obsidian-600 rounded px-1.5 py-0.5 text-[10px] text-stone-300 focus:outline-none focus:border-ember"
            >
              <option value="toml">TOML</option>
              <option value="json">JSON</option>
            </select>
            {dirty && (
              <button
                onClick={saveConfig}
                className="flex items-center gap-1 px-2 py-1 rounded bg-ember text-obsidian-950 text-[11px] font-medium hover:bg-ember-light transition-colors"
              >
                <Save size={11} />
                Save
              </button>
            )}
            <button
              onClick={onClose}
              className="p-1 rounded text-stone-500 hover:text-stone-200 hover:bg-obsidian-700 transition-colors"
            >
              <X size={14} />
            </button>
          </div>
        </div>

        {/* Editor */}
        <div className="flex-1 min-h-0 overflow-hidden">
          {loading ? (
            <div className="flex items-center justify-center h-48 text-stone-500 text-xs">
              Loading config...
            </div>
          ) : (
            <textarea
              value={content}
              onChange={(e) => {
                setContent(e.target.value);
                setDirty(true);
              }}
              onKeyDown={handleKeyDown}
              className="w-full h-full min-h-[300px] resize-none bg-obsidian-950 text-stone-200 font-mono text-[12px] leading-relaxed p-4 outline-none"
              spellCheck={false}
              placeholder={format === "toml" ? "# Add configuration values here" : "{\n  \n}"}
            />
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-4 py-1.5 border-t border-obsidian-700 text-[10px] text-stone-600">
          <span>Mod ID: {targetMod.id}</span>
          <span>{dirty ? "Unsaved changes" : "Saved"}</span>
        </div>
      </div>
    </div>
  );
}

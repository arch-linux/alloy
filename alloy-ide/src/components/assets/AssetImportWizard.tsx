import { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import {
  Box,
  Sword,
  Monitor,
  Bug,
  Sparkles,
  Image,
  Check,
  Copy,
  FileCode,
  ChevronRight,
  X,
} from "lucide-react";
import { useStore } from "../../lib/store";
import { showToast } from "../ui/Toast";

interface AssetInfo {
  width: number;
  height: number;
  file_size: number;
  suggested_type: string;
  suggested_name: string;
}

interface CreatedFile {
  path: string;
  file_type: string;
}

interface ImportResult {
  created_files: CreatedFile[];
  registration_code: string | null;
  texture_path: string;
}

type AssetType = "block" | "item" | "gui_element" | "entity" | "particle";

const assetTypes: { value: AssetType; label: string; desc: string; icon: React.ReactNode }[] = [
  { value: "block", label: "Block Texture", desc: "Cube block with model + blockstate", icon: <Box size={18} /> },
  { value: "item", label: "Item Texture", desc: "Handheld or inventory item", icon: <Sword size={18} /> },
  { value: "gui_element", label: "GUI Element", desc: "Machine interface or HUD element", icon: <Monitor size={18} /> },
  { value: "entity", label: "Entity Texture", desc: "Mob, creature, or NPC skin", icon: <Bug size={18} /> },
  { value: "particle", label: "Particle Sprite", desc: "Effect sprite or sprite sheet", icon: <Sparkles size={18} /> },
];

interface Props {
  sourcePath: string;
  onClose: () => void;
}

export default function AssetImportWizard({ sourcePath, onClose }: Props) {
  const currentProject = useStore((s) => s.currentProject);
  const [step, setStep] = useState<"type" | "configure" | "result">("type");
  const [assetInfo, setAssetInfo] = useState<AssetInfo | null>(null);
  const [assetType, setAssetType] = useState<AssetType>("block");
  const [assetName, setAssetName] = useState("");
  const [generateCode, setGenerateCode] = useState(true);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [codeCopied, setCodeCopied] = useState(false);

  // Analyze the image on mount
  useEffect(() => {
    (async () => {
      try {
        const info = await invoke<AssetInfo>("analyze_image", { path: sourcePath });
        setAssetInfo(info);
        setAssetType(info.suggested_type as AssetType);
        setAssetName(info.suggested_name);
      } catch (err) {
        showToast("error", `Failed to analyze image: ${err}`);
      }
    })();
  }, [sourcePath]);

  const fileName = sourcePath.split("/").pop() || sourcePath;
  const modId = currentProject?.name?.toLowerCase().replace(/[^a-z0-9]/g, "") || "mymod";

  const handleImport = async () => {
    if (!currentProject) return;
    setImporting(true);
    try {
      const res = await invoke<ImportResult>("import_asset", {
        args: {
          source_path: sourcePath,
          asset_type: assetType,
          project_path: currentProject.path,
          mod_id: modId,
          asset_name: assetName,
          generate_code: generateCode,
        },
      });
      setResult(res);
      setStep("result");
      showToast("success", `Imported ${assetName} as ${assetType}`);

      // Refresh file tree
      const entries = await useStore.getState().loadFileTree(currentProject.path);
      useStore.setState({ fileTree: entries });
    } catch (err) {
      showToast("error", `Import failed: ${err}`);
    } finally {
      setImporting(false);
    }
  };

  const copyCode = () => {
    if (result?.registration_code) {
      navigator.clipboard.writeText(result.registration_code);
      setCodeCopied(true);
      setTimeout(() => setCodeCopied(false), 2000);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" onClick={onClose}>
      <div className="absolute inset-0 bg-[rgba(6,6,10,0.8)]" />
      <div
        className="relative z-10 w-full max-w-[520px] rounded-xl border border-obsidian-600 bg-obsidian-800 shadow-2xl animate-fade-in-up"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-obsidian-700">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-ember/10 text-ember">
              <Image size={18} />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-stone-100 font-heading">Import Asset</h3>
              <p className="text-[11px] text-stone-500 mt-0.5">{fileName}</p>
            </div>
          </div>
          <button onClick={onClose} className="text-stone-500 hover:text-stone-300 p-1 rounded hover:bg-obsidian-700 transition-colors">
            <X size={16} />
          </button>
        </div>

        {/* Image preview */}
        {assetInfo && (
          <div className="px-5 pt-4 flex items-center gap-4">
            <div className="flex h-16 w-16 items-center justify-center rounded-lg border border-obsidian-600 bg-obsidian-900 overflow-hidden"
              style={{ imageRendering: "pixelated" }}>
              <img src={`asset://localhost/${sourcePath}`} alt={fileName}
                className="max-w-full max-h-full object-contain"
                style={{ imageRendering: "pixelated" }}
                onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }}
              />
            </div>
            <div className="text-xs text-stone-400 space-y-0.5">
              <div>{assetInfo.width} x {assetInfo.height} px</div>
              <div>{(assetInfo.file_size / 1024).toFixed(1)} KB</div>
              <div className="text-stone-600 font-mono">{fileName}</div>
            </div>
          </div>
        )}

        {/* Step content */}
        <div className="p-5">
          {step === "type" && (
            <div className="space-y-3">
              <label className="block text-[11px] text-stone-400 uppercase tracking-wider font-semibold">
                What type of asset is this?
              </label>
              <div className="grid grid-cols-1 gap-2">
                {assetTypes.map((t) => (
                  <button
                    key={t.value}
                    onClick={() => setAssetType(t.value)}
                    className={
                      "flex items-center gap-3 rounded-lg border p-3 text-left transition-all cursor-pointer " +
                      (assetType === t.value
                        ? "border-ember/50 bg-ember/5"
                        : "border-obsidian-600 bg-obsidian-900/50 hover:border-obsidian-500")
                    }
                  >
                    <div className={assetType === t.value ? "text-ember" : "text-stone-500"}>
                      {t.icon}
                    </div>
                    <div className="flex-1">
                      <div className={"text-xs font-medium " + (assetType === t.value ? "text-ember" : "text-stone-300")}>
                        {t.label}
                      </div>
                      <div className="text-[10px] text-stone-600">{t.desc}</div>
                    </div>
                    {assetType === t.value && <Check size={14} className="text-ember" />}
                  </button>
                ))}
              </div>
              <button
                onClick={() => setStep("configure")}
                className="w-full flex items-center justify-center gap-2 mt-2 rounded-lg py-2.5 text-sm font-medium bg-ember text-obsidian-950 hover:bg-ember-light transition-colors cursor-pointer"
              >
                Next <ChevronRight size={14} />
              </button>
            </div>
          )}

          {step === "configure" && (
            <div className="space-y-4">
              <div>
                <label className="block text-[11px] text-stone-400 uppercase tracking-wider font-semibold mb-1.5">
                  Asset Name
                </label>
                <input
                  type="text"
                  value={assetName}
                  onChange={(e) => setAssetName(e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, "_"))}
                  className="w-full rounded-lg bg-obsidian-900 border border-obsidian-600 px-3 py-2.5 text-sm text-stone-200 font-mono focus:outline-none focus:border-ember/50 focus:ring-1 focus:ring-ember/20 transition-colors"
                />
                <div className="mt-1 text-[10px] text-stone-600 font-mono">
                  Texture: assets/{modId}/textures/{assetType === "gui_element" ? "gui" : assetType}/{assetName}.png
                </div>
              </div>

              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={generateCode}
                  onChange={(e) => setGenerateCode(e.target.checked)}
                  className="rounded border-obsidian-600 bg-obsidian-900 text-ember focus:ring-ember/20"
                />
                <span className="text-xs text-stone-300">Generate registration code</span>
              </label>

              <div className="flex gap-2">
                <button
                  onClick={() => setStep("type")}
                  className="flex-1 rounded-lg py-2.5 text-sm font-medium bg-obsidian-700 text-stone-300 border border-obsidian-600 hover:bg-obsidian-600 transition-colors cursor-pointer"
                >
                  Back
                </button>
                <button
                  onClick={handleImport}
                  disabled={importing || !assetName.trim()}
                  className="flex-1 rounded-lg py-2.5 text-sm font-medium bg-ember text-obsidian-950 hover:bg-ember-light transition-colors disabled:opacity-40 disabled:pointer-events-none cursor-pointer"
                >
                  {importing ? "Importing..." : "Import Asset"}
                </button>
              </div>
            </div>
          )}

          {step === "result" && result && (
            <div className="space-y-4">
              <div>
                <div className="flex items-center gap-2 mb-2">
                  <Check size={14} className="text-green-400" />
                  <span className="text-xs font-medium text-green-400">
                    {result.created_files.length} file{result.created_files.length !== 1 ? "s" : ""} created
                  </span>
                </div>
                <div className="space-y-1">
                  {result.created_files.map((f, i) => (
                    <div key={i} className="flex items-center gap-2 text-[10px] text-stone-500 font-mono">
                      <FileCode size={10} className="text-stone-600" />
                      <span className="truncate">{f.path.split(currentProject?.path || "").pop()}</span>
                      <span className="px-1 py-0.5 rounded bg-obsidian-700 text-stone-500 text-[8px]">{f.file_type}</span>
                    </div>
                  ))}
                </div>
              </div>

              {result.registration_code && (
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-[11px] text-stone-400 uppercase tracking-wider font-semibold">
                      Registration Code
                    </span>
                    <button
                      onClick={copyCode}
                      className="flex items-center gap-1 text-[10px] text-stone-500 hover:text-ember transition-colors cursor-pointer"
                    >
                      {codeCopied ? <Check size={10} /> : <Copy size={10} />}
                      {codeCopied ? "Copied!" : "Copy"}
                    </button>
                  </div>
                  <pre className="rounded-lg bg-obsidian-900 border border-obsidian-700 p-3 text-[11px] text-stone-300 font-mono overflow-x-auto max-h-40 overflow-y-auto leading-relaxed">
                    {result.registration_code}
                  </pre>
                  <p className="mt-1.5 text-[10px] text-stone-600">
                    Paste this into your mod's initializer class.
                  </p>
                </div>
              )}

              <button
                onClick={onClose}
                className="w-full rounded-lg py-2.5 text-sm font-medium bg-ember text-obsidian-950 hover:bg-ember-light transition-colors cursor-pointer"
              >
                Done
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

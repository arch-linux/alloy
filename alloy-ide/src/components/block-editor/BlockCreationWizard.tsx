import { useState, useEffect, useCallback } from "react";
import { invoke } from "@tauri-apps/api/core";
import { X, ChevronRight, ChevronLeft, Box, Check, AlertTriangle } from "lucide-react";
import { useStore } from "../../lib/store";
import { showToast } from "../ui/Toast";
import type { BlockTextureMode, BlockToolType } from "../../lib/types";

interface Props {
  onClose: () => void;
}

interface WizardState {
  name: string;
  displayName: string;
  textureMode: BlockTextureMode;
  textures: {
    all: string | null;
    top: string | null;
    bottom: string | null;
    north: string | null;
    south: string | null;
    east: string | null;
    west: string | null;
  };
  hardness: number;
  resistance: number;
  requiresTool: boolean;
  toolType: BlockToolType;
  toolLevel: number;
  lightLevel: number;
  isTransparent: boolean;
  hasGravity: boolean;
  flammable: boolean;
  slipperiness: number;
  hasGui: boolean;
}

const STEPS = ["Name", "Textures", "Properties", "Options", "Review"];

export default function BlockCreationWizard({ onClose }: Props) {
  const [step, setStep] = useState(0);
  const [creating, setCreating] = useState(false);
  const [nameValidation, setNameValidation] = useState<{
    valid: boolean;
    conflict: boolean;
    suggestion: string | null;
  } | null>(null);

  const [state, setState] = useState<WizardState>({
    name: "",
    displayName: "",
    textureMode: "all",
    textures: { all: null, top: null, bottom: null, north: null, south: null, east: null, west: null },
    hardness: 3.0,
    resistance: 6.0,
    requiresTool: true,
    toolType: "pickaxe",
    toolLevel: 1,
    lightLevel: 0,
    isTransparent: false,
    hasGravity: false,
    flammable: false,
    slipperiness: 0.6,
    hasGui: false,
  });

  const currentProject = useStore((s) => s.currentProject);
  const openFile = useStore((s) => s.openFile);
  const hideBlockWizard = useStore((s) => s.hideBlockWizard);

  const modId = currentProject?.name?.toLowerCase().replace(/[^a-z0-9]/g, "") || "mymod";

  // Auto-generate display name from block name
  useEffect(() => {
    if (state.name && !state.displayName) {
      const display = state.name
        .split("_")
        .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
        .join(" ");
      setState((s) => ({ ...s, displayName: display }));
    }
  }, [state.name]);

  // Validate name
  useEffect(() => {
    if (!state.name) {
      setNameValidation(null);
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const result = await invoke<{
          valid: boolean;
          conflict: boolean;
          suggestion: string | null;
        }>("validate_block_name", { name: state.name });
        setNameValidation(result);
      } catch {
        setNameValidation(null);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [state.name]);

  const canProceed = useCallback(() => {
    switch (step) {
      case 0: return state.name.length > 0 && (nameValidation?.valid ?? false);
      case 1: return true; // Textures are optional
      case 2: return true; // Properties have defaults
      case 3: return true; // Options have defaults
      case 4: return true; // Review
      default: return false;
    }
  }, [step, state, nameValidation]);

  const handleCreate = useCallback(async () => {
    if (!currentProject) {
      showToast("error", "No project open");
      return;
    }

    setCreating(true);
    try {
      const result = await invoke<{
        block_json_path: string;
        created_files: { path: string; file_type: string }[];
      }>("create_block_from_wizard", {
        projectPath: currentProject.path,
        blockName: state.name,
        displayName: state.displayName,
        modId: modId,
        textureMode: state.textureMode,
        textures: state.textures,
        properties: {
          hardness: state.hardness,
          resistance: state.resistance,
          requires_tool: state.requiresTool,
          tool_type: state.toolType,
          tool_level: state.toolLevel,
          light_level: state.lightLevel,
          is_transparent: state.isTransparent,
          has_gravity: state.hasGravity,
          flammable: state.flammable,
          slipperiness: state.slipperiness,
        },
        hasGui: state.hasGui,
      });

      showToast("success", `Created ${state.name} (${result.created_files.length} files)`);
      const fileName = result.block_json_path.split("/").pop() || "block.json";
      await openFile(result.block_json_path, fileName);
      hideBlockWizard();
      onClose();
    } catch (e) {
      showToast("error", `Failed to create block: ${e}`);
    } finally {
      setCreating(false);
    }
  }, [currentProject, state, modId, openFile, hideBlockWizard, onClose]);

  // Keyboard: Escape to close
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={onClose}>
      <div
        className="bg-obsidian-900 border border-obsidian-700 rounded-xl shadow-2xl w-[540px] max-h-[80vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-obsidian-700">
          <div className="flex items-center gap-2">
            <Box size={16} className="text-ember" />
            <h2 className="text-sm font-medium text-stone-200">New Block</h2>
          </div>
          <button onClick={onClose} className="p-1 rounded text-stone-500 hover:text-stone-300 hover:bg-obsidian-800">
            <X size={16} />
          </button>
        </div>

        {/* Step indicators */}
        <div className="flex items-center px-5 py-2.5 border-b border-obsidian-800 gap-1">
          {STEPS.map((s, i) => (
            <div key={s} className="flex items-center">
              <span
                className={
                  "text-[10px] px-2 py-0.5 rounded-full " +
                  (i === step
                    ? "bg-ember/15 text-ember font-medium"
                    : i < step
                    ? "text-stone-400"
                    : "text-stone-600")
                }
              >
                {i < step ? <Check size={10} className="inline -mt-0.5" /> : null} {s}
              </span>
              {i < STEPS.length - 1 && <ChevronRight size={12} className="text-stone-700 mx-0.5" />}
            </div>
          ))}
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto px-5 py-4 min-h-0">
          {step === 0 && <StepName state={state} setState={setState} validation={nameValidation} modId={modId} />}
          {step === 1 && <StepTextures state={state} setState={setState} />}
          {step === 2 && <StepProperties state={state} setState={setState} />}
          {step === 3 && <StepOptions state={state} setState={setState} />}
          {step === 4 && <StepReview state={state} modId={modId} />}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-5 py-3 border-t border-obsidian-700">
          <button
            onClick={() => setStep(Math.max(0, step - 1))}
            disabled={step === 0}
            className="flex items-center gap-1 px-3 py-1.5 rounded text-[11px] text-stone-400 hover:text-stone-200 disabled:opacity-30 disabled:pointer-events-none transition-colors"
          >
            <ChevronLeft size={13} />
            Back
          </button>
          {step < STEPS.length - 1 ? (
            <button
              onClick={() => setStep(step + 1)}
              disabled={!canProceed()}
              className="flex items-center gap-1 px-4 py-1.5 rounded-md text-[11px] font-medium bg-ember/15 text-ember hover:bg-ember/25 disabled:opacity-30 disabled:pointer-events-none transition-colors"
            >
              Next
              <ChevronRight size={13} />
            </button>
          ) : (
            <button
              onClick={handleCreate}
              disabled={creating}
              className="flex items-center gap-1 px-4 py-1.5 rounded-md text-[11px] font-medium bg-ember text-white hover:bg-ember/90 disabled:opacity-50 transition-colors"
            >
              {creating ? "Creating..." : "Create Block"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function StepName({
  state,
  setState,
  validation,
  modId,
}: {
  state: WizardState;
  setState: React.Dispatch<React.SetStateAction<WizardState>>;
  validation: { valid: boolean; conflict: boolean; suggestion: string | null } | null;
  modId: string;
}) {
  return (
    <div className="space-y-4">
      <p className="text-[11px] text-stone-500">
        Choose a unique identifier for your block. This will be used in code and registries.
      </p>

      <div>
        <label className="text-[10px] font-medium text-stone-500 uppercase tracking-wider block mb-1.5">
          Block ID
        </label>
        <div className="flex items-center gap-2">
          <span className="text-[11px] text-stone-600 font-mono">{modId}:</span>
          <input
            type="text"
            value={state.name}
            onChange={(e) => setState((s) => ({ ...s, name: e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, "") }))}
            placeholder="electric_furnace"
            className="input-field font-mono flex-1"
            autoFocus
          />
        </div>
        {validation && !validation.valid && state.name.length > 0 && (
          <p className="text-[10px] text-red-400 mt-1.5 flex items-center gap-1">
            <AlertTriangle size={10} />
            Invalid name. Use lowercase a-z, 0-9, and underscores only.
          </p>
        )}
        {validation?.conflict && (
          <p className="text-[10px] text-yellow-400 mt-1.5 flex items-center gap-1">
            <AlertTriangle size={10} />
            Conflicts with vanilla block &quot;{state.name}&quot;.
            {validation.suggestion && (
              <button
                className="text-ember underline ml-1"
                onClick={() => setState((s) => ({ ...s, name: validation.suggestion! }))}
              >
                Use &quot;{validation.suggestion}&quot;
              </button>
            )}
          </p>
        )}
      </div>

      <div>
        <label className="text-[10px] font-medium text-stone-500 uppercase tracking-wider block mb-1.5">
          Display Name
        </label>
        <input
          type="text"
          value={state.displayName}
          onChange={(e) => setState((s) => ({ ...s, displayName: e.target.value }))}
          placeholder="Electric Furnace"
          className="input-field w-full"
        />
        <p className="text-[9px] text-stone-600 mt-1">Shown in-game in tooltips and inventories.</p>
      </div>
    </div>
  );
}

function StepTextures({
  state,
  setState,
}: {
  state: WizardState;
  setState: React.Dispatch<React.SetStateAction<WizardState>>;
}) {
  return (
    <div className="space-y-4">
      <p className="text-[11px] text-stone-500">
        Choose how textures are applied. You can set these later in the Block Editor.
      </p>

      <div className="flex gap-3">
        <button
          onClick={() => setState((s) => ({ ...s, textureMode: "all" }))}
          className={
            "flex-1 p-3 rounded-lg border text-left transition-colors " +
            (state.textureMode === "all"
              ? "border-ember/30 bg-ember/5"
              : "border-obsidian-700 bg-obsidian-800 hover:border-obsidian-600")
          }
        >
          <span className="text-[11px] font-medium text-stone-300 block">All Faces</span>
          <span className="text-[9px] text-stone-500">Same texture on all 6 sides</span>
        </button>
        <button
          onClick={() => setState((s) => ({ ...s, textureMode: "per_face" }))}
          className={
            "flex-1 p-3 rounded-lg border text-left transition-colors " +
            (state.textureMode === "per_face"
              ? "border-ember/30 bg-ember/5"
              : "border-obsidian-700 bg-obsidian-800 hover:border-obsidian-600")
          }
        >
          <span className="text-[11px] font-medium text-stone-300 block">Per Face</span>
          <span className="text-[9px] text-stone-500">Different texture per side</span>
        </button>
      </div>

      <p className="text-[9px] text-stone-600">
        Textures can be assigned after creation in the Block Editor&apos;s Textures tab.
      </p>
    </div>
  );
}

function StepProperties({
  state,
  setState,
}: {
  state: WizardState;
  setState: React.Dispatch<React.SetStateAction<WizardState>>;
}) {
  return (
    <div className="space-y-4">
      <p className="text-[11px] text-stone-500">
        Set the physical properties of your block.
      </p>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="text-[10px] font-medium text-stone-500 uppercase tracking-wider block mb-1.5">
            Hardness
          </label>
          <input
            type="number"
            value={state.hardness}
            onChange={(e) => setState((s) => ({ ...s, hardness: parseFloat(e.target.value) || 0 }))}
            step={0.5}
            min={0}
            className="input-field w-full"
          />
        </div>
        <div>
          <label className="text-[10px] font-medium text-stone-500 uppercase tracking-wider block mb-1.5">
            Resistance
          </label>
          <input
            type="number"
            value={state.resistance}
            onChange={(e) => setState((s) => ({ ...s, resistance: parseFloat(e.target.value) || 0 }))}
            step={0.5}
            min={0}
            className="input-field w-full"
          />
        </div>
      </div>

      <div className="flex items-center gap-3">
        <button
          onClick={() => setState((s) => ({ ...s, requiresTool: !s.requiresTool }))}
          className={
            "relative w-8 h-[18px] rounded-full transition-colors shrink-0 " +
            (state.requiresTool ? "bg-ember" : "bg-obsidian-700")
          }
        >
          <div
            className={
              "absolute top-[2px] h-[14px] w-[14px] rounded-full bg-white transition-transform " +
              (state.requiresTool ? "translate-x-[14px]" : "translate-x-[2px]")
            }
          />
        </button>
        <span className="text-[11px] text-stone-400">Requires tool</span>
      </div>

      {state.requiresTool && (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="text-[10px] font-medium text-stone-500 uppercase tracking-wider block mb-1.5">
              Tool Type
            </label>
            <select
              value={state.toolType}
              onChange={(e) => setState((s) => ({ ...s, toolType: e.target.value as BlockToolType }))}
              className="input-field w-full"
            >
              <option value="pickaxe">Pickaxe</option>
              <option value="axe">Axe</option>
              <option value="shovel">Shovel</option>
              <option value="hoe">Hoe</option>
            </select>
          </div>
          <div>
            <label className="text-[10px] font-medium text-stone-500 uppercase tracking-wider block mb-1.5">
              Min Level
            </label>
            <select
              value={state.toolLevel}
              onChange={(e) => setState((s) => ({ ...s, toolLevel: parseInt(e.target.value) }))}
              className="input-field w-full"
            >
              <option value={0}>Wood / Gold</option>
              <option value={1}>Stone</option>
              <option value={2}>Iron</option>
              <option value={3}>Diamond</option>
              <option value={4}>Netherite</option>
            </select>
          </div>
        </div>
      )}

      <div>
        <label className="text-[10px] font-medium text-stone-500 uppercase tracking-wider block mb-1.5">
          Light Level: {state.lightLevel}
        </label>
        <input
          type="range"
          value={state.lightLevel}
          onChange={(e) => setState((s) => ({ ...s, lightLevel: parseInt(e.target.value) }))}
          min={0}
          max={15}
          className="w-full accent-ember"
        />
      </div>
    </div>
  );
}

function StepOptions({
  state,
  setState,
}: {
  state: WizardState;
  setState: React.Dispatch<React.SetStateAction<WizardState>>;
}) {
  return (
    <div className="space-y-4">
      <p className="text-[11px] text-stone-500">
        Configure optional block behaviors.
      </p>

      <div className="space-y-3">
        {([
          { key: "isTransparent", label: "Transparent", desc: "Light passes through (glass-like)" },
          { key: "hasGravity", label: "Has Gravity", desc: "Falls like sand or gravel" },
          { key: "flammable", label: "Flammable", desc: "Can catch fire and burn" },
          { key: "hasGui", label: "Has GUI", desc: "Opens a screen when right-clicked (auto-enables block entity)" },
        ] as const).map(({ key, label, desc }) => (
          <div key={key} className="flex items-center gap-3 p-2 rounded-lg hover:bg-obsidian-800 transition-colors">
            <button
              onClick={() => setState((s) => ({ ...s, [key]: !s[key] }))}
              className={
                "relative w-8 h-[18px] rounded-full transition-colors shrink-0 " +
                (state[key] ? "bg-ember" : "bg-obsidian-700")
              }
            >
              <div
                className={
                  "absolute top-[2px] h-[14px] w-[14px] rounded-full bg-white transition-transform " +
                  (state[key] ? "translate-x-[14px]" : "translate-x-[2px]")
                }
              />
            </button>
            <div>
              <span className="text-[11px] text-stone-300">{label}</span>
              <p className="text-[9px] text-stone-600">{desc}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function StepReview({
  state,
  modId,
}: {
  state: WizardState;
  modId: string;
}) {
  return (
    <div className="space-y-3">
      <p className="text-[11px] text-stone-500">
        Review your block configuration before creating.
      </p>

      <div className="bg-obsidian-800 rounded-lg border border-obsidian-700 p-3 space-y-2">
        <ReviewRow label="Block ID" value={`${modId}:${state.name}`} />
        <ReviewRow label="Display Name" value={state.displayName} />
        <ReviewRow label="Texture Mode" value={state.textureMode === "all" ? "All Faces" : "Per Face"} />
        <ReviewRow label="Hardness / Resistance" value={`${state.hardness} / ${state.resistance}`} />
        <ReviewRow label="Requires Tool" value={state.requiresTool ? `${state.toolType} (level ${state.toolLevel})` : "No"} />
        <ReviewRow label="Light Level" value={String(state.lightLevel)} />
        <ReviewRow label="Transparent" value={state.isTransparent ? "Yes" : "No"} />
        <ReviewRow label="Has Gravity" value={state.hasGravity ? "Yes" : "No"} />
        <ReviewRow label="Flammable" value={state.flammable ? "Yes" : "No"} />
        <ReviewRow label="Has GUI" value={state.hasGui ? "Yes" : "No"} />
      </div>

      <p className="text-[9px] text-stone-600">
        This will create a <code className="text-stone-500">.block.json</code> file
        {state.hasGui && <> and a <code className="text-stone-500">.gui.json</code> file</>}.
        You can edit all properties later in the Block Editor.
      </p>
    </div>
  );
}

function ReviewRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-[10px] text-stone-500">{label}</span>
      <span className="text-[10px] text-stone-300 font-mono">{value}</span>
    </div>
  );
}

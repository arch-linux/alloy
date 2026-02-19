import { useState, useEffect, useCallback } from "react";
import { invoke } from "@tauri-apps/api/core";
import type { BlockProject, BlockToolType } from "../../lib/types";

interface Props {
  project: BlockProject;
  onUpdate: (updates: Partial<BlockProject>) => void;
}

const TOOL_TYPES: { value: BlockToolType; label: string }[] = [
  { value: "pickaxe", label: "Pickaxe" },
  { value: "axe", label: "Axe" },
  { value: "shovel", label: "Shovel" },
  { value: "hoe", label: "Hoe" },
  { value: "sword", label: "Sword" },
  { value: "none", label: "None" },
];

const TOOL_LEVELS = [
  { value: 0, label: "Wood / Gold" },
  { value: 1, label: "Stone" },
  { value: 2, label: "Iron" },
  { value: 3, label: "Diamond" },
  { value: 4, label: "Netherite" },
];

export default function BlockPropertiesPanel({ project, onUpdate }: Props) {
  const [nameValidation, setNameValidation] = useState<{
    valid: boolean;
    conflict: boolean;
    suggestion: string | null;
  } | null>(null);

  // Debounced name validation
  useEffect(() => {
    if (!project.name) {
      setNameValidation(null);
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const result = await invoke<{
          valid: boolean;
          conflict: boolean;
          suggestion: string | null;
        }>("validate_block_name", { name: project.name });
        setNameValidation(result);
      } catch {
        setNameValidation(null);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [project.name]);

  const updateProp = useCallback(
    (key: string, value: unknown) => {
      onUpdate({
        properties: { ...project.properties, [key]: value },
      } as Partial<BlockProject>);
    },
    [project.properties, onUpdate],
  );

  return (
    <div className="p-4 max-w-2xl mx-auto space-y-6">
      {/* Block Identity */}
      <Section title="Identity">
        <Field label="Block Name">
          <input
            type="text"
            value={project.name}
            onChange={(e) => onUpdate({ name: e.target.value })}
            placeholder="my_block"
            className="input-field font-mono"
          />
          {nameValidation && !nameValidation.valid && (
            <p className="text-[10px] text-red-400 mt-1">
              Invalid name. Use only lowercase a-z, 0-9, and underscores.
              {nameValidation.suggestion && (
                <button
                  className="ml-1 text-ember underline"
                  onClick={() => onUpdate({ name: nameValidation.suggestion! })}
                >
                  Use &quot;{nameValidation.suggestion}&quot;
                </button>
              )}
            </p>
          )}
          {nameValidation?.conflict && (
            <p className="text-[10px] text-yellow-400 mt-1">
              Conflicts with vanilla Minecraft block.
              {nameValidation.suggestion && (
                <button
                  className="ml-1 text-ember underline"
                  onClick={() => onUpdate({ name: nameValidation.suggestion! })}
                >
                  Use &quot;{nameValidation.suggestion}&quot;
                </button>
              )}
            </p>
          )}
        </Field>

        <Field label="Display Name">
          <input
            type="text"
            value={project.display_name}
            onChange={(e) => onUpdate({ display_name: e.target.value })}
            placeholder="My Block"
            className="input-field"
          />
        </Field>
      </Section>

      {/* Strength */}
      <Section title="Strength">
        <div className="grid grid-cols-2 gap-3">
          <Field label="Hardness">
            <input
              type="number"
              value={project.properties.hardness}
              onChange={(e) => updateProp("hardness", parseFloat(e.target.value) || 0)}
              step={0.5}
              min={0}
              className="input-field"
            />
            <p className="text-[9px] text-stone-600 mt-0.5">Break time (stone=1.5, obsidian=50)</p>
          </Field>

          <Field label="Resistance">
            <input
              type="number"
              value={project.properties.resistance}
              onChange={(e) => updateProp("resistance", parseFloat(e.target.value) || 0)}
              step={0.5}
              min={0}
              className="input-field"
            />
            <p className="text-[9px] text-stone-600 mt-0.5">Explosion resistance (stone=6)</p>
          </Field>
        </div>
      </Section>

      {/* Tool Requirements */}
      <Section title="Tool Requirements">
        <div className="flex items-center gap-3 mb-3">
          <Toggle
            checked={project.properties.requires_tool}
            onChange={(v) => updateProp("requires_tool", v)}
          />
          <span className="text-[11px] text-stone-400">Requires correct tool to drop items</span>
        </div>

        {project.properties.requires_tool && (
          <div className="grid grid-cols-2 gap-3">
            <Field label="Tool Type">
              <select
                value={project.properties.tool_type}
                onChange={(e) => updateProp("tool_type", e.target.value)}
                className="input-field"
              >
                {TOOL_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </Field>

            <Field label="Minimum Tool Level">
              <select
                value={project.properties.tool_level}
                onChange={(e) => updateProp("tool_level", parseInt(e.target.value))}
                className="input-field"
              >
                {TOOL_LEVELS.map((l) => (
                  <option key={l.value} value={l.value}>{l.label}</option>
                ))}
              </select>
            </Field>
          </div>
        )}
      </Section>

      {/* Light & Appearance */}
      <Section title="Light & Appearance">
        <Field label={`Light Level: ${project.properties.light_level}`}>
          <input
            type="range"
            value={project.properties.light_level}
            onChange={(e) => updateProp("light_level", parseInt(e.target.value))}
            min={0}
            max={15}
            className="w-full accent-ember"
          />
          <div className="flex justify-between text-[9px] text-stone-600">
            <span>0 (none)</span>
            <span>15 (max)</span>
          </div>
        </Field>

        <Field label={`Slipperiness: ${project.properties.slipperiness.toFixed(2)}`}>
          <input
            type="range"
            value={project.properties.slipperiness * 100}
            onChange={(e) => updateProp("slipperiness", parseInt(e.target.value) / 100)}
            min={0}
            max={100}
            className="w-full accent-ember"
          />
          <div className="flex justify-between text-[9px] text-stone-600">
            <span>0.0 (sticky)</span>
            <span>0.6 (default)</span>
            <span>1.0 (ice)</span>
          </div>
        </Field>
      </Section>

      {/* Behavior Toggles */}
      <Section title="Behavior">
        <div className="space-y-2.5">
          <ToggleRow
            label="Transparent"
            description="Light passes through (like glass)"
            checked={project.properties.is_transparent}
            onChange={(v) => updateProp("is_transparent", v)}
          />
          <ToggleRow
            label="Has Gravity"
            description="Falls like sand/gravel"
            checked={project.properties.has_gravity}
            onChange={(v) => updateProp("has_gravity", v)}
          />
          <ToggleRow
            label="Flammable"
            description="Can catch fire and burn"
            checked={project.properties.flammable}
            onChange={(v) => updateProp("flammable", v)}
          />
        </div>
      </Section>

      {/* Block Entity */}
      <Section title="Advanced">
        <ToggleRow
          label="Has Block Entity"
          description="Stores data per-block (needed for inventories, machines)"
          checked={project.has_block_entity}
          onChange={(v) => onUpdate({ has_block_entity: v })}
        />
        <ToggleRow
          label="Has GUI"
          description="Opens a screen when right-clicked (enables block entity)"
          checked={project.has_gui}
          onChange={(v) => onUpdate({
            has_gui: v,
            has_block_entity: v ? true : project.has_block_entity,
            gui_file: v ? `${project.name}.gui.json` : null,
          })}
        />
      </Section>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="text-[11px] font-semibold text-stone-300 uppercase tracking-wider mb-3">
        {title}
      </h3>
      <div className="bg-obsidian-900 rounded-lg border border-obsidian-700 p-4">
        {children}
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="mb-3 last:mb-0">
      <label className="text-[10px] font-medium text-stone-500 uppercase tracking-wider block mb-1.5">
        {label}
      </label>
      {children}
    </div>
  );
}

function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      onClick={() => onChange(!checked)}
      className={
        "relative w-8 h-[18px] rounded-full transition-colors shrink-0 " +
        (checked ? "bg-ember" : "bg-obsidian-700")
      }
    >
      <div
        className={
          "absolute top-[2px] h-[14px] w-[14px] rounded-full bg-white transition-transform " +
          (checked ? "translate-x-[14px]" : "translate-x-[2px]")
        }
      />
    </button>
  );
}

function ToggleRow({
  label,
  description,
  checked,
  onChange,
}: {
  label: string;
  description: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="flex items-center gap-3">
      <Toggle checked={checked} onChange={onChange} />
      <div>
        <span className="text-[11px] text-stone-300">{label}</span>
        <p className="text-[9px] text-stone-600">{description}</p>
      </div>
    </div>
  );
}

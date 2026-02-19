import { useState } from "react";
import {
  Plus,
  X,
  ChevronDown,
  ChevronRight,
  Link,
} from "lucide-react";
import type { ModDependency, ModpackMod } from "../../lib/types";

interface DependencyEditorProps {
  mod: ModpackMod;
  availableMods: ModpackMod[];
  onChange: (dependencies: ModDependency[]) => void;
}

const CONSTRAINT_PRESETS = [
  { label: "Any", value: "*", desc: "Any version" },
  { label: "Exact", value: "", desc: "Exact version match" },
  { label: "Compatible", value: "^", desc: "Compatible updates (^)" },
  { label: "Patch", value: "~", desc: "Patch updates only (~)" },
  { label: "At least", value: ">=", desc: "Minimum version (>=)" },
];

export default function DependencyEditor({
  mod: currentMod,
  availableMods,
  onChange,
}: DependencyEditorProps) {
  const [open, setOpen] = useState(false);
  const [addingNew, setAddingNew] = useState(false);
  const [newModId, setNewModId] = useState("");
  const [newConstraint, setNewConstraint] = useState("*");
  const [constraintType, setConstraintType] = useState("*");
  const [constraintVersion, setConstraintVersion] = useState("");

  const otherMods = availableMods.filter((m) => m.id !== currentMod.id);

  const addDependency = () => {
    if (!newModId) return;
    let constraint = newConstraint;
    if (constraintType === "*") {
      constraint = "*";
    } else if (constraintType === "") {
      constraint = constraintVersion || "*";
    } else {
      constraint = constraintType + (constraintVersion || "0.0.0");
    }

    onChange([
      ...currentMod.dependencies,
      { mod_id: newModId, version_constraint: constraint },
    ]);
    setAddingNew(false);
    setNewModId("");
    setNewConstraint("*");
    setConstraintType("*");
    setConstraintVersion("");
  };

  const removeDependency = (index: number) => {
    onChange(currentMod.dependencies.filter((_, i) => i !== index));
  };

  const updateConstraint = (index: number, constraint: string) => {
    onChange(
      currentMod.dependencies.map((dep, i) =>
        i === index ? { ...dep, version_constraint: constraint } : dep
      )
    );
  };

  const depCount = currentMod.dependencies.length;

  return (
    <div className="mt-1">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1 text-[10px] text-stone-500 hover:text-stone-300 transition-colors"
      >
        {open ? <ChevronDown size={9} /> : <ChevronRight size={9} />}
        <Link size={9} />
        <span>
          {depCount === 0
            ? "No dependencies"
            : `${depCount} dependenc${depCount === 1 ? "y" : "ies"}`}
        </span>
      </button>

      {open && (
        <div className="mt-1 ml-3 space-y-1">
          {currentMod.dependencies.map((dep, i) => {
            const depMod = availableMods.find((m) => m.id === dep.mod_id);
            return (
              <div
                key={i}
                className="flex items-center gap-1.5 text-[10px]"
              >
                <span className="text-stone-300 truncate max-w-[80px]">
                  {depMod?.name || dep.mod_id}
                </span>
                <input
                  value={dep.version_constraint}
                  onChange={(e) => updateConstraint(i, e.target.value)}
                  className="w-20 bg-obsidian-800 border border-obsidian-600 rounded px-1 py-0.5 text-stone-300 focus:outline-none focus:border-ember text-[10px] font-mono"
                  placeholder="*"
                />
                <button
                  onClick={() => removeDependency(i)}
                  className="p-0.5 rounded text-stone-600 hover:text-red-400 transition-colors"
                >
                  <X size={9} />
                </button>
              </div>
            );
          })}

          {addingNew ? (
            <div className="space-y-1 p-1.5 rounded bg-obsidian-800 border border-obsidian-600">
              <select
                value={newModId}
                onChange={(e) => setNewModId(e.target.value)}
                className="w-full bg-obsidian-900 border border-obsidian-600 rounded px-1 py-0.5 text-[10px] text-stone-300 focus:outline-none focus:border-ember"
              >
                <option value="">Select mod...</option>
                {otherMods.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name} ({m.id})
                  </option>
                ))}
              </select>
              <div className="flex gap-1">
                <select
                  value={constraintType}
                  onChange={(e) => setConstraintType(e.target.value)}
                  className="bg-obsidian-900 border border-obsidian-600 rounded px-1 py-0.5 text-[10px] text-stone-300 focus:outline-none focus:border-ember"
                >
                  {CONSTRAINT_PRESETS.map((p) => (
                    <option key={p.value} value={p.value}>
                      {p.label}
                    </option>
                  ))}
                </select>
                {constraintType !== "*" && (
                  <input
                    value={constraintVersion}
                    onChange={(e) => setConstraintVersion(e.target.value)}
                    placeholder="1.0.0"
                    className="flex-1 bg-obsidian-900 border border-obsidian-600 rounded px-1 py-0.5 text-[10px] text-stone-300 font-mono focus:outline-none focus:border-ember"
                  />
                )}
              </div>
              <div className="flex gap-1">
                <button
                  onClick={addDependency}
                  disabled={!newModId}
                  className="flex-1 text-[10px] py-0.5 rounded bg-ember text-obsidian-950 hover:bg-ember-light disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  Add
                </button>
                <button
                  onClick={() => setAddingNew(false)}
                  className="text-[10px] px-2 py-0.5 rounded text-stone-400 hover:text-stone-200 hover:bg-obsidian-700 transition-colors"
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <button
              onClick={() => setAddingNew(true)}
              className="flex items-center gap-1 text-[10px] text-ember hover:text-ember-light transition-colors"
            >
              <Plus size={9} />
              Add dependency
            </button>
          )}
        </div>
      )}
    </div>
  );
}

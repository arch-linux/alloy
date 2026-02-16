import { Trash2 } from "lucide-react";
import type { GuiElement, GuiProject, GuiWidgetType } from "../../lib/types";

interface Props {
  element: GuiElement | null;
  project: GuiProject;
  onUpdate: (updates: Partial<GuiElement>) => void;
  onUpdateProject: (updates: Partial<GuiProject>) => void;
  onDelete: () => void;
}

export default function PropertyPanel({ element, project, onUpdate, onUpdateProject, onDelete }: Props) {
  return (
    <div className="w-[220px] shrink-0 border-l border-obsidian-700 bg-obsidian-900 flex flex-col overflow-y-auto">
      {element ? (
        <ElementProperties element={element} onUpdate={onUpdate} onDelete={onDelete} />
      ) : (
        <ProjectProperties project={project} onUpdate={onUpdateProject} />
      )}
    </div>
  );
}

function ProjectProperties({
  project,
  onUpdate,
}: {
  project: GuiProject;
  onUpdate: (updates: Partial<GuiProject>) => void;
}) {
  return (
    <>
      <div className="px-3 py-2 border-b border-obsidian-700">
        <h3 className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold">GUI Properties</h3>
      </div>
      <div className="p-3 space-y-3">
        <Field label="Name">
          <input
            type="text"
            value={project.name}
            onChange={(e) => onUpdate({ name: e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, "_") })}
            className="prop-input"
          />
        </Field>
        <div className="flex gap-2">
          <Field label="Width">
            <input
              type="number"
              value={project.width}
              onChange={(e) => onUpdate({ width: clamp(+e.target.value, 16, 512) })}
              className="prop-input"
            />
          </Field>
          <Field label="Height">
            <input
              type="number"
              value={project.height}
              onChange={(e) => onUpdate({ height: clamp(+e.target.value, 16, 512) })}
              className="prop-input"
            />
          </Field>
        </div>
        <div className="pt-2 border-t border-obsidian-700">
          <div className="text-[10px] text-stone-600">
            Standard Minecraft GUI: 176x166
          </div>
          <div className="text-[10px] text-stone-600 mt-0.5">
            Large chest: 176x222
          </div>
          <button
            onClick={() => onUpdate({ width: 176, height: 166 })}
            className="mt-2 text-[10px] text-ember hover:text-ember-light transition-colors cursor-pointer"
          >
            Reset to standard size
          </button>
        </div>
        <div className="pt-2 border-t border-obsidian-700">
          <div className="text-[10px] text-stone-500 mb-1">Elements: {0}</div>
          <div className="text-[9px] text-stone-600 leading-relaxed">
            Select an element on the canvas to edit its properties.
          </div>
        </div>
      </div>
    </>
  );
}

function ElementProperties({
  element,
  onUpdate,
  onDelete,
}: {
  element: GuiElement;
  onUpdate: (updates: Partial<GuiElement>) => void;
  onDelete: () => void;
}) {
  const updateProp = (key: string, value: unknown) => {
    onUpdate({ properties: { ...element.properties, [key]: value } });
  };

  return (
    <>
      <div className="flex items-center justify-between px-3 py-2 border-b border-obsidian-700">
        <h3 className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold">
          {element.type.replace("_", " ")}
        </h3>
        <button
          onClick={onDelete}
          className="p-1 rounded text-stone-600 hover:text-molten hover:bg-molten/10 transition-colors cursor-pointer"
          title="Delete element"
        >
          <Trash2 size={12} />
        </button>
      </div>
      <div className="p-3 space-y-3">
        {/* Position */}
        <div className="flex gap-2">
          <Field label="X">
            <input
              type="number"
              value={element.x}
              onChange={(e) => onUpdate({ x: Math.max(0, +e.target.value) })}
              className="prop-input"
            />
          </Field>
          <Field label="Y">
            <input
              type="number"
              value={element.y}
              onChange={(e) => onUpdate({ y: Math.max(0, +e.target.value) })}
              className="prop-input"
            />
          </Field>
        </div>

        {/* Size */}
        <div className="flex gap-2">
          <Field label="Width">
            <input
              type="number"
              value={element.width}
              onChange={(e) => onUpdate({ width: Math.max(1, +e.target.value) })}
              className="prop-input"
            />
          </Field>
          <Field label="Height">
            <input
              type="number"
              value={element.height}
              onChange={(e) => onUpdate({ height: Math.max(1, +e.target.value) })}
              className="prop-input"
            />
          </Field>
        </div>

        {/* Label for types that support it */}
        {(element.type === "button" || element.type === "label") && (
          <Field label="Label">
            <input
              type="text"
              value={element.label || ""}
              onChange={(e) => onUpdate({ label: e.target.value })}
              className="prop-input"
            />
          </Field>
        )}

        {/* Type-specific properties */}
        <div className="pt-2 border-t border-obsidian-700 space-y-3">
          <div className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold">
            Properties
          </div>
          {renderTypeProperties(element, updateProp)}
        </div>

        {/* Element ID */}
        <div className="pt-2 border-t border-obsidian-700">
          <div className="text-[9px] text-stone-600 font-mono truncate">{element.id}</div>
        </div>
      </div>
    </>
  );
}

function renderTypeProperties(
  el: GuiElement,
  updateProp: (key: string, value: unknown) => void,
) {
  switch (el.type) {
    case "slot":
      return (
        <Field label="Slot ID">
          <input
            type="number"
            value={(el.properties.slot_id as number) ?? 0}
            onChange={(e) => updateProp("slot_id", +e.target.value)}
            className="prop-input"
          />
        </Field>
      );

    case "progress_bar":
      return (
        <>
          <Field label="Direction">
            <select
              value={(el.properties.direction as string) || "right"}
              onChange={(e) => updateProp("direction", e.target.value)}
              className="prop-input"
            >
              <option value="right">Right</option>
              <option value="left">Left</option>
              <option value="up">Up</option>
              <option value="down">Down</option>
            </select>
          </Field>
          <Field label="Max Value">
            <input
              type="number"
              value={(el.properties.max_value as number) ?? 200}
              onChange={(e) => updateProp("max_value", +e.target.value)}
              className="prop-input"
            />
          </Field>
        </>
      );

    case "energy_bar":
      return (
        <>
          <Field label="Direction">
            <select
              value={(el.properties.direction as string) || "up"}
              onChange={(e) => updateProp("direction", e.target.value)}
              className="prop-input"
            >
              <option value="up">Up</option>
              <option value="down">Down</option>
            </select>
          </Field>
          <Field label="Max Energy">
            <input
              type="number"
              value={(el.properties.max_value as number) ?? 10000}
              onChange={(e) => updateProp("max_value", +e.target.value)}
              className="prop-input"
            />
          </Field>
        </>
      );

    case "fluid_tank":
      return (
        <Field label="Max mB">
          <input
            type="number"
            value={(el.properties.max_mb as number) ?? 16000}
            onChange={(e) => updateProp("max_mb", +e.target.value)}
            className="prop-input"
          />
        </Field>
      );

    case "button":
      return (
        <Field label="Action">
          <select
            value={(el.properties.action as string) || "toggle"}
            onChange={(e) => updateProp("action", e.target.value)}
            className="prop-input"
          >
            <option value="toggle">Toggle</option>
            <option value="push">Push</option>
            <option value="cycle">Cycle</option>
          </select>
        </Field>
      );

    case "label":
      return (
        <>
          <Field label="Align">
            <select
              value={(el.properties.align as string) || "left"}
              onChange={(e) => updateProp("align", e.target.value)}
              className="prop-input"
            >
              <option value="left">Left</option>
              <option value="center">Center</option>
              <option value="right">Right</option>
            </select>
          </Field>
          <Field label="Color">
            <input
              type="color"
              value={(el.properties.color as string) || "#f0f0f4"}
              onChange={(e) => updateProp("color", e.target.value)}
              className="prop-input h-7"
            />
          </Field>
        </>
      );

    case "region":
      return (
        <Field label="Tooltip">
          <input
            type="text"
            value={(el.properties.tooltip as string) || ""}
            onChange={(e) => updateProp("tooltip", e.target.value)}
            className="prop-input"
          />
        </Field>
      );

    case "image":
      return (
        <>
          <Field label="Texture">
            <input
              type="text"
              value={(el.properties.texture as string) || ""}
              onChange={(e) => updateProp("texture", e.target.value)}
              className="prop-input"
              placeholder="modid:path"
            />
          </Field>
          <div className="flex gap-2">
            <Field label="U">
              <input
                type="number"
                value={(el.properties.u as number) ?? 0}
                onChange={(e) => updateProp("u", +e.target.value)}
                className="prop-input"
              />
            </Field>
            <Field label="V">
              <input
                type="number"
                value={(el.properties.v as number) ?? 0}
                onChange={(e) => updateProp("v", +e.target.value)}
                className="prop-input"
              />
            </Field>
          </div>
        </>
      );

    default:
      return (
        <div className="text-[10px] text-stone-600">No configurable properties</div>
      );
  }
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex-1 min-w-0">
      <label className="block text-[9px] text-stone-500 uppercase tracking-wider mb-0.5">{label}</label>
      {children}
    </div>
  );
}

function clamp(v: number, min: number, max: number) {
  return Math.max(min, Math.min(max, v));
}

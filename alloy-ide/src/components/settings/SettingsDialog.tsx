import { X, Settings } from "lucide-react";
import { useStore } from "../../lib/store";

interface SettingsDialogProps {
  onClose: () => void;
}

export default function SettingsDialog({ onClose }: SettingsDialogProps) {
  const settings = useStore((s) => s.editorSettings);
  const update = useStore((s) => s.updateEditorSettings);

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center"
      onClick={onClose}
    >
      {/* Backdrop */}
      <div className="absolute inset-0 bg-obsidian-950/80" />

      {/* Dialog */}
      <div
        className="relative w-[480px] max-w-[90vw] max-h-[80vh] rounded-lg border border-obsidian-600 bg-obsidian-800 shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-obsidian-600">
          <div className="flex items-center gap-2">
            <Settings size={16} className="text-stone-400" />
            <span className="text-sm font-medium text-stone-200">Settings</span>
          </div>
          <button
            onClick={onClose}
            className="text-stone-500 hover:text-stone-200 p-1 rounded hover:bg-obsidian-700 transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        {/* Settings content */}
        <div className="p-4 overflow-y-auto space-y-5">
          {/* Editor section */}
          <div>
            <h3 className="text-xs font-semibold text-stone-400 uppercase tracking-wider mb-3">
              Editor
            </h3>

            {/* Font size */}
            <div className="flex items-center justify-between py-2">
              <div>
                <div className="text-sm text-stone-200">Font Size</div>
                <div className="text-[11px] text-stone-500">Controls the font size in pixels</div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => update({ fontSize: Math.max(10, settings.fontSize - 1) })}
                  className="w-6 h-6 rounded border border-obsidian-600 text-stone-400 hover:text-stone-200 hover:border-obsidian-500 flex items-center justify-center text-sm transition-colors"
                >
                  -
                </button>
                <span className="text-sm text-stone-200 w-6 text-center font-mono">
                  {settings.fontSize}
                </span>
                <button
                  onClick={() => update({ fontSize: Math.min(24, settings.fontSize + 1) })}
                  className="w-6 h-6 rounded border border-obsidian-600 text-stone-400 hover:text-stone-200 hover:border-obsidian-500 flex items-center justify-center text-sm transition-colors"
                >
                  +
                </button>
              </div>
            </div>

            {/* Tab size */}
            <div className="flex items-center justify-between py-2">
              <div>
                <div className="text-sm text-stone-200">Tab Size</div>
                <div className="text-[11px] text-stone-500">Number of spaces per tab</div>
              </div>
              <select
                value={settings.tabSize}
                onChange={(e) => update({ tabSize: parseInt(e.target.value) })}
                className="bg-obsidian-900 border border-obsidian-600 rounded px-2 py-1 text-sm text-stone-200 outline-none focus:border-ember/50"
              >
                <option value={2}>2</option>
                <option value={4}>4</option>
                <option value={8}>8</option>
              </select>
            </div>

            {/* Word wrap */}
            <div className="flex items-center justify-between py-2">
              <div>
                <div className="text-sm text-stone-200">Word Wrap</div>
                <div className="text-[11px] text-stone-500">Wrap lines at viewport width</div>
              </div>
              <ToggleSwitch
                value={settings.wordWrap}
                onChange={(v) => update({ wordWrap: v })}
              />
            </div>

            {/* Line numbers */}
            <div className="flex items-center justify-between py-2">
              <div>
                <div className="text-sm text-stone-200">Line Numbers</div>
                <div className="text-[11px] text-stone-500">Show line numbers in gutter</div>
              </div>
              <ToggleSwitch
                value={settings.lineNumbers}
                onChange={(v) => update({ lineNumbers: v })}
              />
            </div>

            {/* Minimap */}
            <div className="flex items-center justify-between py-2">
              <div>
                <div className="text-sm text-stone-200">Minimap</div>
                <div className="text-[11px] text-stone-500">Show code overview minimap</div>
              </div>
              <ToggleSwitch
                value={settings.minimap}
                onChange={(v) => update({ minimap: v })}
              />
            </div>

            {/* Indent guides */}
            <div className="flex items-center justify-between py-2">
              <div>
                <div className="text-sm text-stone-200">Indent Guides</div>
                <div className="text-[11px] text-stone-500">Show vertical indent guide lines</div>
              </div>
              <ToggleSwitch
                value={settings.indentGuides}
                onChange={(v) => update({ indentGuides: v })}
              />
            </div>

            {/* Auto-save */}
            <div className="flex items-center justify-between py-2">
              <div>
                <div className="text-sm text-stone-200">Auto Save</div>
                <div className="text-[11px] text-stone-500">Save files automatically after changes</div>
              </div>
              <ToggleSwitch
                value={settings.autoSave}
                onChange={(v) => update({ autoSave: v })}
              />
            </div>

            {settings.autoSave && (
              <div className="flex items-center justify-between py-2 pl-4">
                <div>
                  <div className="text-sm text-stone-200">Auto Save Delay</div>
                  <div className="text-[11px] text-stone-500">Delay in ms before saving</div>
                </div>
                <select
                  value={settings.autoSaveDelay}
                  onChange={(e) => update({ autoSaveDelay: parseInt(e.target.value) })}
                  className="bg-obsidian-900 border border-obsidian-600 rounded px-2 py-1 text-sm text-stone-200 outline-none focus:border-ember/50"
                >
                  <option value={1000}>1 second</option>
                  <option value={2000}>2 seconds</option>
                  <option value={5000}>5 seconds</option>
                  <option value={10000}>10 seconds</option>
                </select>
              </div>
            )}
          </div>

          {/* About section */}
          <div className="pt-2 border-t border-obsidian-700">
            <h3 className="text-xs font-semibold text-stone-400 uppercase tracking-wider mb-3">
              About
            </h3>
            <div className="text-xs text-stone-500 space-y-1">
              <div>Alloy IDE v0.1.0</div>
              <div>Built with Tauri 2, React 19, CodeMirror 6</div>
              <div className="text-stone-600">Where Mods Are Forged</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function ToggleSwitch({
  value,
  onChange,
}: {
  value: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <button
      onClick={() => onChange(!value)}
      className={
        "w-10 h-5 rounded-full transition-colors relative " +
        (value ? "bg-ember" : "bg-obsidian-600")
      }
    >
      <span
        className={
          "absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform " +
          (value ? "translate-x-5" : "translate-x-0.5")
        }
      />
    </button>
  );
}

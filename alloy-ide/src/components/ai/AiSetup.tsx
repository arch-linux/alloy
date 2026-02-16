import { useState } from "react";
import { Key, Bot, Download, ChevronDown, ChevronRight } from "lucide-react";
import { useStore } from "../../lib/store";

export default function AiSetup() {
  const [showApiKey, setShowApiKey] = useState(false);
  const [apiKey, setApiKey] = useState("");
  const [saving, setSaving] = useState(false);
  const setAiConfig = useStore((s) => s.setAiConfig);

  const handleSave = async () => {
    if (!apiKey.trim()) return;
    setSaving(true);
    try {
      await setAiConfig({
        api_key: apiKey.trim(),
        model: "claude-sonnet-4-5-20250929",
        max_tokens: 4096,
      });
    } finally {
      setSaving(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      handleSave();
    }
  };

  return (
    <div className="flex flex-col items-center h-full px-4 py-8 gap-6 overflow-y-auto scrollbar-thin">
      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-ember/10 border border-ember/20">
        <Bot size={24} className="text-ember" />
      </div>

      <div className="text-center">
        <h3 className="text-stone-100 text-sm font-medium mb-1">AI Assistant</h3>
        <p className="text-stone-500 text-xs leading-relaxed max-w-[240px]">
          Connect an AI assistant to your IDE. It can read and edit files, run builds, and help you write Alloy mods.
        </p>
      </div>

      {/* Primary: MCP download */}
      <div className="w-full max-w-[280px] flex flex-col gap-3">
        <a
          href="https://alloymc.net/mcp"
          target="_blank"
          rel="noopener noreferrer"
          className={
            "flex items-center justify-center gap-2 w-full rounded-md py-2.5 text-[13px] font-medium transition-colors " +
            "bg-ember text-obsidian-950 hover:bg-ember-light"
          }
        >
          <Download size={14} />
          Download Alloy MCP
        </a>
        <p className="text-stone-500 text-[10px] text-center leading-relaxed">
          The Alloy MCP plugin lets external AI agents like Claude Code connect to your running IDE and control it directly.
        </p>
      </div>

      {/* Secondary: API key (collapsible) */}
      <div className="w-full max-w-[280px] border-t border-obsidian-600 pt-4">
        <button
          onClick={() => setShowApiKey(!showApiKey)}
          className="flex items-center gap-1.5 text-[11px] text-stone-500 hover:text-stone-300 transition-colors w-full"
        >
          {showApiKey ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
          <Key size={11} />
          Or use your own API key
        </button>

        {showApiKey && (
          <div className="mt-3 flex flex-col gap-2.5">
            <p className="text-stone-500 text-[10px] leading-relaxed">
              Enter your Anthropic API key for a built-in AI assistant. Stored locally, never shared.
            </p>
            <input
              type="password"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="sk-ant-..."
              className={
                "w-full rounded-md border border-obsidian-600 bg-obsidian-950 px-3 py-2 " +
                "text-[13px] text-stone-100 placeholder:text-stone-500 " +
                "focus:border-ember focus:outline-none focus:ring-1 focus:ring-ember/30"
              }
            />
            <button
              onClick={handleSave}
              disabled={!apiKey.trim() || saving}
              className={
                "w-full rounded-md py-2 text-[13px] font-medium transition-colors " +
                (apiKey.trim() && !saving
                  ? "bg-obsidian-700 text-stone-100 hover:bg-obsidian-600 border border-obsidian-600"
                  : "bg-obsidian-800 text-stone-500 cursor-not-allowed border border-obsidian-700")
              }
            >
              {saving ? "Saving..." : "Connect"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

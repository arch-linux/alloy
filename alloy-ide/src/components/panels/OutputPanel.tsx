import { useEffect, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { Bot, Trash2 } from "lucide-react";
import { useStore } from "../../lib/store";
import { AnsiLine, stripAnsi } from "../../lib/ansi";

export default function OutputPanel() {
  const [lines, setLines] = useState<string[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);
  const sendMessage = useStore((s) => s.sendMessage);
  const setSidebarPanel = useStore((s) => s.setSidebarPanel);
  const aiConfig = useStore((s) => s.aiConfig);
  const buildRunning = useStore((s) => s.buildRunning);

  // Listen for build output events
  useEffect(() => {
    const unlisten = listen<string>("build:output", (event) => {
      setLines((prev) => [...prev, event.payload]);
    });
    return () => {
      unlisten.then((fn) => fn());
    };
  }, []);

  // Auto-scroll to bottom
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [lines]);

  const hasError = lines.some((l) => {
    const plain = stripAnsi(l).toLowerCase();
    return plain.includes("error") || plain.includes("failed");
  });

  const handleExplainError = () => {
    const last50 = lines.slice(-50).join("\n");
    setSidebarPanel("ai");
    sendMessage(
      `Explain this build output and help me fix any errors:\n\n\`\`\`\n${last50}\n\`\`\``,
    );
  };

  return (
    <div className="flex flex-col h-full">
      {/* Toolbar */}
      <div className="flex items-center justify-between px-3 py-1 border-b border-obsidian-700 shrink-0">
        <div className="flex items-center gap-2 text-[10px] text-stone-500">
          {buildRunning && (
            <span className="flex items-center gap-1 text-ember">
              <span className="h-1.5 w-1.5 rounded-full bg-ember animate-pulse" />
              Building...
            </span>
          )}
          {!buildRunning && lines.length > 0 && (
            <span>{lines.length} lines</span>
          )}
        </div>
        <div className="flex items-center gap-1">
          {hasError && aiConfig?.api_key && (
            <button
              onClick={handleExplainError}
              className="flex items-center gap-1 rounded px-2 py-0.5 text-[10px] text-ember hover:bg-ember/10 transition-colors"
            >
              <Bot size={10} />
              Explain
            </button>
          )}
          <button
            onClick={() => setLines([])}
            className="text-stone-500 hover:text-stone-300 p-0.5 rounded hover:bg-obsidian-800 transition-colors"
          >
            <Trash2 size={12} />
          </button>
        </div>
      </div>

      {/* Output content */}
      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto p-2 font-mono text-[11px] leading-relaxed"
        style={{ background: "#06060a" }}
      >
        {lines.length === 0 ? (
          <span className="text-stone-600">No build output yet. Run a build task to see output here.</span>
        ) : (
          lines.map((line, i) => {
            const hasAnsi = /\x1b\[/.test(line);
            if (hasAnsi) {
              return (
                <div key={i} className="text-stone-400">
                  <AnsiLine text={line} />
                </div>
              );
            }
            // Fallback keyword-based coloring for plain text lines
            let color = "text-stone-400";
            const lower = line.toLowerCase();
            if (lower.includes("error")) color = "text-red-400";
            else if (lower.includes("warning")) color = "text-yellow-400";
            else if (line.includes("BUILD SUCCESSFUL")) color = "text-green-400";
            else if (line.includes("BUILD FAILED")) color = "text-red-400";
            else if (line.startsWith(">")) color = "text-stone-300";

            return (
              <div key={i} className={color}>
                {line}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}

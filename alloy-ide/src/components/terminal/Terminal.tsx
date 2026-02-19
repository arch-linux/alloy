import { useState, useEffect, useRef, useCallback } from "react";
import { Terminal as XTerm } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import { WebLinksAddon } from "@xterm/addon-web-links";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { useStore } from "../../lib/store";
import { Plus, X } from "lucide-react";
import "@xterm/xterm/css/xterm.css";

const ALLOY_THEME = {
  background: "#0a0a10",
  foreground: "#f0f0f4",
  cursor: "#ff6b00",
  cursorAccent: "#0a0a10",
  selectionBackground: "rgba(255, 107, 0, 0.2)",
  selectionForeground: "#f0f0f4",
  black: "#06060a",
  red: "#f87171",
  green: "#4ade80",
  yellow: "#f0b830",
  blue: "#60a5fa",
  magenta: "#c084fc",
  cyan: "#22d3ee",
  white: "#f0f0f4",
  brightBlack: "#3a3a48",
  brightRed: "#fca5a5",
  brightGreen: "#86efac",
  brightYellow: "#fde68a",
  brightBlue: "#93c5fd",
  brightMagenta: "#d8b4fe",
  brightCyan: "#67e8f9",
  brightWhite: "#ffffff",
};

interface TerminalTab {
  id: string;
  label: string;
}

let termCounter = 0;

function TerminalInstance({ id, visible }: { id: string; visible: boolean }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const termRef = useRef<XTerm | null>(null);
  const fitRef = useRef<FitAddon | null>(null);
  const createdRef = useRef(false);
  const currentProject = useStore((s) => s.currentProject);

  useEffect(() => {
    if (!containerRef.current || createdRef.current) return;
    createdRef.current = true;

    const term = new XTerm({
      theme: ALLOY_THEME,
      fontFamily: "JetBrains Mono, Menlo, Monaco, monospace",
      fontSize: 13,
      lineHeight: 1.4,
      cursorBlink: true,
      cursorStyle: "bar",
      allowProposedApi: true,
    });

    const fitAddon = new FitAddon();
    term.loadAddon(fitAddon);
    term.loadAddon(new WebLinksAddon());

    term.open(containerRef.current);
    fitAddon.fit();

    termRef.current = term;
    fitRef.current = fitAddon;

    const cwd = currentProject?.path ?? undefined;
    invoke("terminal_create", { id, cwd }).catch((err) => {
      term.writeln(`\x1b[31mFailed to create terminal: ${err}\x1b[0m`);
    });

    const unlistenData = listen<string>(`terminal:data:${id}`, (event) => {
      term.write(event.payload);
    });

    const unlistenExit = listen(`terminal:exit:${id}`, () => {
      term.writeln("\r\n\x1b[90m[Process exited]\x1b[0m");
    });

    const onData = term.onData((data) => {
      invoke("terminal_write", { id, data }).catch(() => {});
    });

    const observer = new ResizeObserver(() => {
      fitAddon.fit();
    });
    observer.observe(containerRef.current);

    return () => {
      onData.dispose();
      observer.disconnect();
      unlistenData.then((fn) => fn());
      unlistenExit.then((fn) => fn());
      invoke("terminal_destroy", { id }).catch(() => {});
      term.dispose();
      createdRef.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  // Re-fit when visibility changes
  useEffect(() => {
    if (visible && fitRef.current) {
      setTimeout(() => fitRef.current?.fit(), 50);
    }
  }, [visible]);

  return (
    <div
      ref={containerRef}
      className="h-full w-full"
      style={{
        background: "#0a0a10",
        padding: "4px 0 0 8px",
        display: visible ? "block" : "none",
      }}
    />
  );
}

export default function TerminalComponent() {
  const [tabs, setTabs] = useState<TerminalTab[]>(() => {
    termCounter++;
    return [{ id: `term-${termCounter}`, label: "Terminal 1" }];
  });
  const [activeTab, setActiveTab] = useState(() => tabs[0].id);

  const addTerminal = useCallback(() => {
    termCounter++;
    const newTab: TerminalTab = {
      id: `term-${termCounter}`,
      label: `Terminal ${termCounter}`,
    };
    setTabs((prev) => [...prev, newTab]);
    setActiveTab(newTab.id);
    return newTab.id;
  }, []);

  // Listen for "run command in terminal" events from the store
  useEffect(() => {
    const handler = (e: Event) => {
      const command = (e as CustomEvent).detail?.command;
      if (!command) return;
      // Send the command to the active terminal
      // Small delay to ensure terminal is focused and ready
      setTimeout(() => {
        invoke("terminal_write", { id: activeTab, data: command + "\n" }).catch(() => {});
      }, 100);
    };
    window.addEventListener("terminal:run-command", handler);
    return () => window.removeEventListener("terminal:run-command", handler);
  }, [activeTab]);

  const closeTerminal = useCallback(
    (id: string) => {
      setTabs((prev) => {
        const filtered = prev.filter((t) => t.id !== id);
        if (filtered.length === 0) {
          // Always keep at least one terminal
          termCounter++;
          const newTab = { id: `term-${termCounter}`, label: `Terminal ${termCounter}` };
          setActiveTab(newTab.id);
          return [newTab];
        }
        if (activeTab === id) {
          setActiveTab(filtered[filtered.length - 1].id);
        }
        return filtered;
      });
      invoke("terminal_destroy", { id }).catch(() => {});
    },
    [activeTab],
  );

  return (
    <div className="flex flex-col h-full">
      {/* Terminal tab bar */}
      <div className="flex items-center h-7 bg-obsidian-900 border-b border-obsidian-700 px-1 shrink-0 gap-0.5">
        {tabs.map((tab) => (
          <div
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={
              "group flex items-center gap-1 px-2 py-0.5 rounded text-[11px] cursor-pointer transition-colors " +
              (tab.id === activeTab
                ? "bg-obsidian-800 text-stone-200"
                : "text-stone-500 hover:text-stone-300")
            }
          >
            <span className="truncate max-w-[100px]">{tab.label}</span>
            {tabs.length > 1 && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  closeTerminal(tab.id);
                }}
                className="opacity-0 group-hover:opacity-100 text-stone-500 hover:text-stone-200 transition-opacity"
              >
                <X size={11} />
              </button>
            )}
          </div>
        ))}
        <button
          onClick={addTerminal}
          className="flex items-center justify-center h-5 w-5 rounded text-stone-500 hover:text-stone-200 hover:bg-obsidian-800 transition-colors ml-0.5"
        >
          <Plus size={13} />
        </button>
      </div>

      {/* Terminal instances */}
      <div className="flex-1 min-h-0">
        {tabs.map((tab) => (
          <TerminalInstance key={tab.id} id={tab.id} visible={tab.id === activeTab} />
        ))}
      </div>
    </div>
  );
}

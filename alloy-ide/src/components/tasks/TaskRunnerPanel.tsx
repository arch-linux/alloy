import { useState } from "react";
import {
  Play,
  Hammer,
  Trash2,
  Wrench,
  Server,
  TestTube,
  Package,
  ChevronDown,
  ChevronRight,
  History,
  RotateCcw,
  Terminal,
} from "lucide-react";
import { useStore } from "../../lib/store";

interface GradleTask {
  name: string;
  label: string;
  description: string;
  category: string;
}

const TASK_CATEGORIES: { label: string; id: string; icon: typeof Hammer }[] = [
  { label: "Build", id: "build", icon: Hammer },
  { label: "Run", id: "run", icon: Play },
  { label: "Test", id: "test", icon: TestTube },
  { label: "Setup", id: "setup", icon: Wrench },
];

const ALLOY_TASKS: GradleTask[] = [
  { name: "build", label: "Build", description: "Compile and package the mod", category: "build" },
  { name: "clean", label: "Clean", description: "Remove all build artifacts", category: "build" },
  { name: "jar", label: "Build JAR", description: "Build the distributable JAR", category: "build" },
  { name: "classes", label: "Compile Classes", description: "Compile Java source files", category: "build" },
  { name: "launchClient", label: "Launch Client", description: "Start Minecraft with the mod (client-side)", category: "run" },
  { name: "launchServer", label: "Launch Server", description: "Start a dedicated server with the mod", category: "run" },
  { name: "runClient", label: "Run Client (Debug)", description: "Launch client with debug output", category: "run" },
  { name: "test", label: "Run Tests", description: "Execute all unit tests", category: "test" },
  { name: "setupWorkspace", label: "Setup Workspace", description: "Initialize the development environment", category: "setup" },
  { name: "setupDecompWorkspace", label: "Decomp Workspace", description: "Setup with decompiled sources", category: "setup" },
];

const CATEGORY_ICONS: Record<string, typeof Hammer> = {
  build: Hammer,
  run: Play,
  test: TestTube,
  setup: Wrench,
};

export default function TaskRunnerPanel() {
  const currentProject = useStore((s) => s.currentProject);
  const buildRunning = useStore((s) => s.buildRunning);
  const runBuild = useStore((s) => s.runBuild);
  const runInTerminal = useStore((s) => s.runInTerminal);

  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(
    new Set(["build", "run"])
  );
  const [taskHistory, setTaskHistory] = useState<{ name: string; time: number; success: boolean }[]>([]);
  const [customTask, setCustomTask] = useState("");

  const toggleCategory = (id: string) => {
    setExpandedCategories((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const executeInTerminal = (taskName: string) => {
    if (!currentProject) return;
    const cmd = `./gradlew ${taskName} --console=plain`;
    runInTerminal(cmd);
  };

  const executeTask = async (taskName: string) => {
    if (buildRunning || !currentProject) return;
    const startTime = Date.now();
    try {
      await runBuild(taskName);
      setTaskHistory((prev) => [
        { name: taskName, time: startTime, success: true },
        ...prev.slice(0, 19),
      ]);
    } catch {
      setTaskHistory((prev) => [
        { name: taskName, time: startTime, success: false },
        ...prev.slice(0, 19),
      ]);
    }
  };

  const handleCustomTask = (e: React.FormEvent) => {
    e.preventDefault();
    if (customTask.trim()) {
      executeTask(customTask.trim());
      setCustomTask("");
    }
  };

  if (!currentProject) {
    return (
      <div className="flex flex-col items-center justify-center h-48 text-stone-500 text-xs gap-2 px-4">
        <Hammer size={24} className="text-stone-600" />
        <span>Open a project to see tasks</span>
      </div>
    );
  }

  const tasksByCategory = TASK_CATEGORIES.map((cat) => ({
    ...cat,
    tasks: ALLOY_TASKS.filter((t) => t.category === cat.id),
  }));

  return (
    <div className="flex flex-col h-full text-xs">
      {/* Custom task input */}
      <form onSubmit={handleCustomTask} className="px-3 py-2 border-b border-obsidian-700">
        <div className="flex gap-1.5">
          <input
            type="text"
            value={customTask}
            onChange={(e) => setCustomTask(e.target.value)}
            placeholder="Custom Gradle task..."
            disabled={buildRunning}
            className="flex-1 bg-obsidian-800 border border-obsidian-600 rounded px-2 py-1 text-stone-200 placeholder:text-stone-600 focus:outline-none focus:border-ember text-[11px]"
          />
          <button
            type="submit"
            disabled={buildRunning || !customTask.trim()}
            className="px-2 py-1 bg-ember text-obsidian-950 rounded font-medium disabled:opacity-40 hover:bg-ember-light transition-colors"
          >
            <Play size={11} />
          </button>
        </div>
      </form>

      {/* Task categories */}
      <div className="flex-1 overflow-y-auto py-1">
        {tasksByCategory.map(({ id, label, icon: CatIcon, tasks }) => {
          const expanded = expandedCategories.has(id);
          return (
            <div key={id}>
              <button
                onClick={() => toggleCategory(id)}
                className="flex w-full items-center gap-1.5 px-3 py-1.5 text-stone-300 hover:bg-obsidian-800 transition-colors"
              >
                {expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
                <CatIcon size={12} className="text-stone-500" />
                <span className="font-medium text-[11px] tracking-wide uppercase">{label}</span>
                <span className="text-stone-600 ml-auto text-[10px]">{tasks.length}</span>
              </button>
              {expanded && (
                <div className="pl-3">
                  {tasks.map((task) => (
                    <div
                      key={task.name}
                      className="flex w-full items-center gap-2 px-3 py-1.5 text-stone-400 hover:text-stone-100 hover:bg-obsidian-800 transition-colors group"
                    >
                      <button
                        onClick={() => executeTask(task.name)}
                        disabled={buildRunning}
                        className="text-stone-600 group-hover:text-ember shrink-0 transition-colors disabled:opacity-40 cursor-pointer"
                        title="Run task"
                      >
                        <Play size={11} />
                      </button>
                      <button
                        onClick={() => executeInTerminal(task.name)}
                        className="text-stone-600 group-hover:text-stone-400 hover:!text-ember shrink-0 transition-colors cursor-pointer"
                        title="Run in terminal"
                      >
                        <Terminal size={11} />
                      </button>
                      <div className="flex flex-col items-start min-w-0 cursor-pointer flex-1" onClick={() => executeTask(task.name)}>
                        <span className="text-[11px] truncate">{task.label}</span>
                        <span className="text-[10px] text-stone-600 truncate">{task.description}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Task history */}
      {taskHistory.length > 0 && (
        <div className="border-t border-obsidian-700">
          <div className="flex items-center gap-1.5 px-3 py-1.5 text-stone-500">
            <History size={11} />
            <span className="text-[10px] uppercase tracking-wide font-medium">Recent</span>
            <button
              onClick={() => setTaskHistory([])}
              className="ml-auto text-stone-600 hover:text-stone-300 transition-colors"
              title="Clear history"
            >
              <RotateCcw size={10} />
            </button>
          </div>
          <div className="max-h-28 overflow-y-auto">
            {taskHistory.slice(0, 5).map((entry, i) => (
              <button
                key={i}
                onClick={() => executeTask(entry.name)}
                disabled={buildRunning}
                className="flex w-full items-center gap-2 px-3 py-1 text-stone-500 hover:text-stone-200 hover:bg-obsidian-800 disabled:opacity-40 transition-colors"
              >
                <span
                  className={
                    "h-1.5 w-1.5 rounded-full shrink-0 " +
                    (entry.success ? "bg-green-400" : "bg-red-400")
                  }
                />
                <span className="text-[11px] truncate">{entry.name}</span>
                <span className="text-[10px] text-stone-600 ml-auto shrink-0">
                  {new Date(entry.time).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                </span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Running indicator */}
      {buildRunning && (
        <div className="flex items-center gap-2 px-3 py-2 bg-ember/10 border-t border-ember/20">
          <Hammer size={12} className="text-ember animate-pulse" />
          <span className="text-ember text-[11px]">Task running...</span>
        </div>
      )}
    </div>
  );
}

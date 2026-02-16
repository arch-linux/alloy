import { AlertTriangle, XCircle, Bot } from "lucide-react";
import { useStore } from "../../lib/store";

export default function ProblemsPanel() {
  const buildErrors = useStore((s) => s.buildErrors);
  const sendMessage = useStore((s) => s.sendMessage);
  const setSidebarPanel = useStore((s) => s.setSidebarPanel);
  const aiConfig = useStore((s) => s.aiConfig);
  const openFile = useStore((s) => s.openFile);
  const currentProject = useStore((s) => s.currentProject);

  const handleFixWithAi = (problem: { file: string; line: number; message: string }) => {
    setSidebarPanel("ai");
    sendMessage(
      `Fix this build error in ${problem.file} at line ${problem.line}:\n\n${problem.message}\n\nPlease read the file and suggest a fix.`,
    );
  };

  const handleClickError = (problem: { file: string; line: number }) => {
    const name = problem.file.split("/").pop() || problem.file;
    openFile(problem.file, name);
  };

  const errors = buildErrors.filter((e) => e.severity === "error");
  const warnings = buildErrors.filter((e) => e.severity === "warning");

  if (buildErrors.length === 0) {
    return (
      <div className="flex h-full items-center justify-center gap-2 text-stone-500 text-xs">
        <AlertTriangle size={14} />
        <span>No problems detected</span>
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto">
      {/* Summary */}
      <div className="flex items-center gap-3 px-3 py-1.5 border-b border-obsidian-700 text-[10px]">
        {errors.length > 0 && (
          <span className="flex items-center gap-1 text-red-400">
            <XCircle size={11} /> {errors.length} error{errors.length !== 1 ? "s" : ""}
          </span>
        )}
        {warnings.length > 0 && (
          <span className="flex items-center gap-1 text-yellow-400">
            <AlertTriangle size={11} /> {warnings.length} warning{warnings.length !== 1 ? "s" : ""}
          </span>
        )}
      </div>

      {/* Error list */}
      {buildErrors.map((problem, i) => {
        const isError = problem.severity === "error";
        const relativePath = currentProject
          ? problem.file.replace(currentProject.path + "/", "")
          : problem.file;

        return (
          <div
            key={i}
            className="flex items-center justify-between gap-2 px-3 py-1.5 border-b border-obsidian-700/50 hover:bg-obsidian-800/50 cursor-pointer"
            onClick={() => handleClickError(problem)}
          >
            <div className="flex items-center gap-2 min-w-0">
              {isError ? (
                <XCircle size={13} className="text-red-400 shrink-0" />
              ) : (
                <AlertTriangle size={13} className="text-yellow-400 shrink-0" />
              )}
              <span className="text-stone-300 text-xs truncate">{problem.message}</span>
              <span className="text-stone-500 text-[10px] shrink-0">
                {relativePath}:{problem.line}
              </span>
            </div>
            {aiConfig?.api_key && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  handleFixWithAi(problem);
                }}
                className="flex items-center gap-1 shrink-0 rounded px-1.5 py-0.5 text-[10px] text-ember hover:bg-ember/10 transition-colors"
              >
                <Bot size={10} />
                Fix
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}

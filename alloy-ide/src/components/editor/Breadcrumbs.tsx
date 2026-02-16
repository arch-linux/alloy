import { ChevronRight } from "lucide-react";
import { useStore } from "../../lib/store";

export default function Breadcrumbs() {
  const activeFilePath = useStore((s) => s.activeFilePath);
  const currentProject = useStore((s) => s.currentProject);

  if (!activeFilePath || !currentProject) return null;

  // Build relative path segments
  const relativePath = activeFilePath.startsWith(currentProject.path)
    ? activeFilePath.slice(currentProject.path.length + 1)
    : activeFilePath;

  const segments = relativePath.split("/");
  const projectName = currentProject.name;

  return (
    <div className="flex items-center h-6 px-3 bg-obsidian-950 border-b border-obsidian-700 text-[11px] text-stone-500 overflow-x-auto shrink-0 select-none">
      <span className="text-stone-400 shrink-0">{projectName}</span>
      {segments.map((segment, i) => {
        const isLast = i === segments.length - 1;
        return (
          <span key={i} className="flex items-center shrink-0">
            <ChevronRight size={11} className="mx-0.5 text-stone-600" />
            <span
              className={
                isLast
                  ? "text-stone-200"
                  : "text-stone-400 hover:text-stone-200 cursor-pointer"
              }
            >
              {segment}
            </span>
          </span>
        );
      })}
    </div>
  );
}

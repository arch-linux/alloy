import { useState, useEffect, useCallback } from "react";
import {
  AlertTriangle,
  AlertCircle,
  Copy,
  ChevronDown,
  ChevronRight,
  RefreshCw,
  Shield,
  Puzzle,
  GitFork,
  Layers,
} from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";
import type { ModConflict } from "../../lib/types";

const CONFLICT_CONFIG: Record<
  string,
  { icon: typeof AlertTriangle; color: string; label: string; severity: "error" | "warning" }
> = {
  missing_dependency: {
    icon: Puzzle,
    color: "text-red-400",
    label: "Missing Dependency",
    severity: "error",
  },
  version_mismatch: {
    icon: GitFork,
    color: "text-yellow-400",
    label: "Version Mismatch",
    severity: "warning",
  },
  environment_conflict: {
    icon: Shield,
    color: "text-orange-400",
    label: "Environment Conflict",
    severity: "warning",
  },
  duplicate_id: {
    icon: Layers,
    color: "text-red-400",
    label: "Duplicate ID",
    severity: "error",
  },
};

export default function ConflictResolver() {
  const currentProject = useStore((s) => s.currentProject);
  const [conflicts, setConflicts] = useState<ModConflict[]>([]);
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  const checkConflicts = useCallback(async () => {
    if (!currentProject) return;
    setLoading(true);
    try {
      const result = await invoke<ModConflict[]>("check_modpack_conflicts", {
        projectPath: currentProject.path,
      });
      setConflicts(result);
    } catch {
      setConflicts([]);
    } finally {
      setLoading(false);
    }
  }, [currentProject]);

  useEffect(() => {
    checkConflicts();
  }, [checkConflicts]);

  const toggleExpand = (index: number) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  };

  const errors = conflicts.filter(
    (c) => CONFLICT_CONFIG[c.kind]?.severity === "error"
  );
  const warnings = conflicts.filter(
    (c) => CONFLICT_CONFIG[c.kind]?.severity !== "error"
  );

  if (conflicts.length === 0 && !loading) {
    return (
      <div className="px-3 py-2">
        <div className="flex items-center justify-between mb-2">
          <span className="text-[10px] text-stone-500 uppercase tracking-wider font-medium">
            Dependency Check
          </span>
          <button
            onClick={checkConflicts}
            className="p-0.5 rounded text-stone-500 hover:text-stone-200 hover:bg-obsidian-700 transition-colors"
            title="Re-check"
          >
            <RefreshCw size={10} />
          </button>
        </div>
        <div className="flex items-center gap-1.5 text-green-400 text-[11px]">
          <Shield size={12} />
          <span>No conflicts detected</span>
        </div>
      </div>
    );
  }

  return (
    <div className="px-3 py-2">
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <span className="text-[10px] text-stone-500 uppercase tracking-wider font-medium">
            Conflicts
          </span>
          {errors.length > 0 && (
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-red-400/15 text-red-400 border border-red-400/30">
              {errors.length} error{errors.length !== 1 ? "s" : ""}
            </span>
          )}
          {warnings.length > 0 && (
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-yellow-400/15 text-yellow-400 border border-yellow-400/30">
              {warnings.length} warning{warnings.length !== 1 ? "s" : ""}
            </span>
          )}
        </div>
        <button
          onClick={checkConflicts}
          disabled={loading}
          className="p-0.5 rounded text-stone-500 hover:text-stone-200 hover:bg-obsidian-700 transition-colors disabled:opacity-50"
          title="Re-check conflicts"
        >
          <RefreshCw size={10} className={loading ? "animate-spin" : ""} />
        </button>
      </div>

      <div className="space-y-1">
        {conflicts.map((conflict, i) => {
          const config = CONFLICT_CONFIG[conflict.kind] || {
            icon: AlertCircle,
            color: "text-stone-400",
            label: conflict.kind,
            severity: "warning" as const,
          };
          const Icon = config.icon;
          const isExpanded = expanded.has(i);

          return (
            <div
              key={i}
              className={
                "rounded border transition-colors " +
                (config.severity === "error"
                  ? "border-red-400/20 bg-red-400/5"
                  : "border-yellow-400/20 bg-yellow-400/5")
              }
            >
              <button
                onClick={() => toggleExpand(i)}
                className="flex items-start gap-2 w-full px-2 py-1.5 text-left"
              >
                {isExpanded ? (
                  <ChevronDown size={10} className="text-stone-500 mt-0.5 shrink-0" />
                ) : (
                  <ChevronRight size={10} className="text-stone-500 mt-0.5 shrink-0" />
                )}
                <Icon size={12} className={config.color + " mt-0.5 shrink-0"} />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5">
                    <span className={"text-[10px] font-medium " + config.color}>
                      {config.label}
                    </span>
                    {conflict.mod_name && (
                      <span className="text-[10px] text-stone-400 truncate">
                        {conflict.mod_name}
                      </span>
                    )}
                  </div>
                  <p className="text-[10px] text-stone-400 mt-0.5 leading-relaxed">
                    {conflict.details}
                  </p>
                </div>
              </button>

              {isExpanded && (
                <div className="px-2 pb-2 pl-8 space-y-1.5">
                  {conflict.affected_mods.length > 0 && (
                    <div>
                      <span className="text-[9px] text-stone-600 uppercase tracking-wider">
                        Affected
                      </span>
                      <div className="flex flex-wrap gap-1 mt-0.5">
                        {conflict.affected_mods.map((mod, j) => (
                          <span
                            key={j}
                            className="text-[10px] px-1.5 py-0.5 rounded bg-obsidian-700 text-stone-300"
                          >
                            {mod}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                  {conflict.suggestion && (
                    <div className="flex items-start gap-1.5 mt-1">
                      <AlertTriangle size={10} className="text-forge-gold mt-0.5 shrink-0" />
                      <span className="text-[10px] text-forge-gold">
                        {conflict.suggestion}
                      </span>
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

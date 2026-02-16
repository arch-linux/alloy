import { useState } from "react";
import { ChevronRight, ChevronDown, Wrench, Check, AlertCircle, Loader2 } from "lucide-react";
import type { ToolCall } from "../../lib/types";

interface ToolCallBadgeProps {
  toolCall: ToolCall;
}

export default function ToolCallBadge({ toolCall }: ToolCallBadgeProps) {
  const [expanded, setExpanded] = useState(false);

  const statusIcon = () => {
    switch (toolCall.status) {
      case "running":
        return <Loader2 size={12} className="animate-spin text-ember" />;
      case "done":
        return <Check size={12} className="text-green-400" />;
      case "error":
        return <AlertCircle size={12} className="text-red-400" />;
    }
  };

  const statusBorder = () => {
    switch (toolCall.status) {
      case "running":
        return "border-ember/30";
      case "done":
        return "border-green-400/30";
      case "error":
        return "border-red-400/30";
    }
  };

  return (
    <div className="my-1">
      <button
        onClick={() => setExpanded(!expanded)}
        className={
          "flex items-center gap-1.5 rounded px-2 py-0.5 text-[11px] font-mono border transition-colors " +
          "bg-obsidian-900 hover:bg-obsidian-800 " +
          statusBorder()
        }
      >
        {expanded ? (
          <ChevronDown size={10} className="text-stone-500" />
        ) : (
          <ChevronRight size={10} className="text-stone-500" />
        )}
        <Wrench size={10} className="text-stone-400" />
        <span className="text-stone-300">{toolCall.name}</span>
        {statusIcon()}
      </button>

      {expanded && (
        <div className="mt-1 ml-2 rounded border border-obsidian-600 bg-obsidian-950 p-2 text-[11px] font-mono">
          <div className="text-stone-500 mb-1">Input:</div>
          <pre className="text-stone-300 whitespace-pre-wrap break-all max-h-32 overflow-y-auto">
            {JSON.stringify(toolCall.input, null, 2)}
          </pre>
          {toolCall.result && (
            <>
              <div className="text-stone-500 mt-2 mb-1">Result:</div>
              <pre className="text-stone-300 whitespace-pre-wrap break-all max-h-48 overflow-y-auto">
                {toolCall.result}
              </pre>
            </>
          )}
        </div>
      )}
    </div>
  );
}

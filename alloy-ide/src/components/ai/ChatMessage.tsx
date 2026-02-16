import { Bot, User } from "lucide-react";
import type { ChatMessage as ChatMessageType } from "../../lib/types";
import ToolCallBadge from "./ToolCallBadge";

interface ChatMessageProps {
  message: ChatMessageType;
}

export default function ChatMessage({ message }: ChatMessageProps) {
  const isUser = message.role === "user";

  // Don't render tool-result messages (they have role=user but content is empty with tool_calls)
  if (isUser && !message.content && message.tool_calls.length > 0) {
    return null;
  }

  return (
    <div className={`flex gap-2 px-3 py-2 ${isUser ? "flex-row-reverse" : ""}`}>
      <div
        className={
          "flex h-6 w-6 shrink-0 items-center justify-center rounded-md " +
          (isUser ? "bg-ember/15" : "bg-obsidian-700")
        }
      >
        {isUser ? (
          <User size={14} className="text-ember" />
        ) : (
          <Bot size={14} className="text-stone-300" />
        )}
      </div>

      <div className={`flex flex-col min-w-0 max-w-[85%] ${isUser ? "items-end" : ""}`}>
        {message.content && (
          <div
            className={
              "rounded-lg px-3 py-2 text-[13px] leading-relaxed whitespace-pre-wrap break-words " +
              (isUser
                ? "bg-ember/10 text-stone-100 border border-ember/20"
                : "bg-obsidian-800 text-stone-200 border border-obsidian-600")
            }
          >
            {message.content}
          </div>
        )}

        {message.tool_calls.length > 0 && (
          <div className="mt-1 flex flex-col gap-0.5">
            {message.tool_calls.map((tc) => (
              <ToolCallBadge key={tc.id} toolCall={tc} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

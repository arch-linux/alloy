import { useEffect, useRef } from "react";
import { Trash2, Loader2 } from "lucide-react";
import { useStore, initAiListeners } from "../../lib/store";
import ChatMessage from "./ChatMessage";
import ChatInput from "./ChatInput";
import AiSetup from "./AiSetup";
import Tooltip from "../ui/Tooltip";

export default function AiPanel() {
  const chatMessages = useStore((s) => s.chatMessages);
  const aiLoading = useStore((s) => s.aiLoading);
  const aiConfig = useStore((s) => s.aiConfig);
  const aiConfigLoaded = useStore((s) => s.aiConfigLoaded);
  const sendMessage = useStore((s) => s.sendMessage);
  const clearChat = useStore((s) => s.clearChat);
  const loadAiConfig = useStore((s) => s.loadAiConfig);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    initAiListeners();
    if (!aiConfigLoaded) {
      loadAiConfig();
    }
  }, [aiConfigLoaded, loadAiConfig]);

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [chatMessages]);

  // Show setup if no API key configured
  if (!aiConfigLoaded) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 size={20} className="animate-spin text-stone-500" />
      </div>
    );
  }

  if (!aiConfig?.api_key) {
    return <AiSetup />;
  }

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-obsidian-600 px-3 py-1.5">
        <span className="text-[11px] text-stone-400 font-medium uppercase tracking-wider">
          AI Chat
        </span>
        <Tooltip content="Clear chat" side="bottom">
          <button
            onClick={clearChat}
            className="flex h-6 w-6 items-center justify-center rounded text-stone-500 hover:text-stone-300 hover:bg-obsidian-800 transition-colors"
          >
            <Trash2 size={13} />
          </button>
        </Tooltip>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto scrollbar-thin">
        {chatMessages.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full px-4 gap-3 text-center">
            <div className="text-stone-500 text-xs leading-relaxed max-w-[220px]">
              Ask me anything about your Alloy mod project. I can read and edit files, run builds, and help with code.
            </div>
          </div>
        ) : (
          <div className="py-2">
            {chatMessages.map((msg, i) => (
              <ChatMessage key={`${msg.timestamp}-${i}`} message={msg} />
            ))}
            {aiLoading && chatMessages[chatMessages.length - 1]?.role === "user" && (
              <div className="flex items-center gap-2 px-3 py-2">
                <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-obsidian-700">
                  <Loader2 size={14} className="animate-spin text-ember" />
                </div>
                <span className="text-[12px] text-stone-500">Thinking...</span>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      {/* Input */}
      <ChatInput onSend={sendMessage} disabled={aiLoading} />
    </div>
  );
}

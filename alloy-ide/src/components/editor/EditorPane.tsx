import { useState, useCallback } from "react";
import { Eye, Code } from "lucide-react";
import { useStore } from "../../lib/store";
import CodeEditor from "./CodeEditor";
import EditorContextMenu from "./EditorContextMenu";
import ImagePreview from "./ImagePreview";
import MarkdownPreview from "./MarkdownPreview";
import GuiEditor from "../gui-editor/GuiEditor";
import AnimationEditor from "../animation/AnimationEditor";

const IMAGE_EXTENSIONS = new Set([
  "png", "jpg", "jpeg", "gif", "svg", "bmp", "webp", "ico", "tga",
]);

const AI_PROMPTS: Record<string, (text: string, path: string) => string> = {
  explain: (text, path) =>
    `Explain this code from ${path}:\n\n\`\`\`\n${text}\n\`\`\``,
  refactor: (text, path) =>
    `Refactor this code from ${path} to improve readability and quality. Show the improved version:\n\n\`\`\`\n${text}\n\`\`\``,
  tests: (text, path) =>
    `Generate unit tests for this code from ${path}:\n\n\`\`\`\n${text}\n\`\`\``,
  document: (text, path) =>
    `Add proper Javadoc/documentation comments to this code from ${path}:\n\n\`\`\`\n${text}\n\`\`\``,
};

export default function EditorPane() {
  const activeFilePath = useStore((s) => s.activeFilePath);
  const openFiles = useStore((s) => s.openFiles);
  const updateFileContent = useStore((s) => s.updateFileContent);
  const setCursorPosition = useStore((s) => s.setCursorPosition);
  const saveFile = useStore((s) => s.saveFile);
  const sendMessage = useStore((s) => s.sendMessage);
  const setSidebarPanel = useStore((s) => s.setSidebarPanel);

  const [contextMenu, setContextMenu] = useState<{
    x: number;
    y: number;
    selectedText: string;
  } | null>(null);

  const [markdownPreview, setMarkdownPreview] = useState(false);

  const file = openFiles.find((f) => f.path === activeFilePath);

  const handleContextMenu = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      const selection = window.getSelection()?.toString() || "";
      setContextMenu({
        x: e.clientX,
        y: e.clientY,
        selectedText: selection,
      });
    },
    [],
  );

  const handleAiAction = useCallback(
    (action: string, text: string) => {
      if (!file) return;
      const promptFn = AI_PROMPTS[action];
      if (!promptFn) return;

      const prompt = promptFn(text, file.path);
      setSidebarPanel("ai");
      sendMessage(prompt);
    },
    [file, setSidebarPanel, sendMessage],
  );

  if (!file) return null;

  // Detect file type
  const ext = file.name.split(".").pop()?.toLowerCase() || "";
  const isImage = IMAGE_EXTENSIONS.has(ext);
  const isMarkdown = file.language === "markdown";
  const isGui = file.name.endsWith(".gui.json");
  const isAnim = file.name.endsWith(".anim.json");

  // GUI visual editor
  if (isGui) {
    return (
      <GuiEditor
        key={file.path}
        path={file.path}
        content={file.content}
        onSave={(content) => {
          updateFileContent(file.path, content);
          saveFile(file.path);
        }}
      />
    );
  }

  // Animation timeline editor
  if (isAnim) {
    return (
      <AnimationEditor
        key={file.path}
        path={file.path}
        content={file.content}
        onSave={(content) => {
          updateFileContent(file.path, content);
          saveFile(file.path);
        }}
      />
    );
  }

  // Image preview
  if (isImage) {
    return <ImagePreview path={file.path} name={file.name} />;
  }

  // Markdown with preview toggle
  if (isMarkdown) {
    return (
      <div className="relative h-full w-full">
        {/* Preview toggle button */}
        <button
          onClick={() => setMarkdownPreview((v) => !v)}
          className={
            "absolute top-2 right-4 z-10 flex items-center gap-1.5 rounded-md px-2.5 py-1 text-[11px] border transition-colors " +
            (markdownPreview
              ? "bg-ember/15 border-ember/30 text-ember"
              : "bg-obsidian-800 border-obsidian-600 text-stone-400 hover:text-stone-200 hover:border-obsidian-500")
          }
          title={markdownPreview ? "Show Source" : "Show Preview"}
        >
          {markdownPreview ? (
            <>
              <Code size={12} />
              <span>Source</span>
            </>
          ) : (
            <>
              <Eye size={12} />
              <span>Preview</span>
            </>
          )}
        </button>

        {markdownPreview ? (
          <MarkdownPreview content={file.content} />
        ) : (
          <div onContextMenu={handleContextMenu}>
            <CodeEditor
              key={file.path}
              path={file.path}
              content={file.content}
              language={file.language}
              onChange={(content) => updateFileContent(file.path, content)}
              onCursorChange={setCursorPosition}
              onSave={() => saveFile(file.path)}
            />
            {contextMenu && (
              <EditorContextMenu
                x={contextMenu.x}
                y={contextMenu.y}
                selectedText={contextMenu.selectedText}
                onClose={() => setContextMenu(null)}
                onAiAction={handleAiAction}
              />
            )}
          </div>
        )}
      </div>
    );
  }

  // Default: code editor
  return (
    <div className="relative h-full w-full" onContextMenu={handleContextMenu}>
      <CodeEditor
        key={file.path}
        path={file.path}
        content={file.content}
        language={file.language}
        onChange={(content) => updateFileContent(file.path, content)}
        onCursorChange={setCursorPosition}
        onSave={() => saveFile(file.path)}
      />
      {contextMenu && (
        <EditorContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          selectedText={contextMenu.selectedText}
          onClose={() => setContextMenu(null)}
          onAiAction={handleAiAction}
        />
      )}
    </div>
  );
}

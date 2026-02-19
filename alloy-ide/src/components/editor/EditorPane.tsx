import { useState, useCallback, lazy, Suspense } from "react";
import { Eye, Code, ServerOff } from "lucide-react";
import { useStore } from "../../lib/store";
import { isVisualEditorAvailable, getVisualEditorUnavailableReason } from "../../lib/environment";
import CodeEditor from "./CodeEditor";
import EditorContextMenu from "./EditorContextMenu";
import ImagePreview from "./ImagePreview";
import MarkdownPreview from "./MarkdownPreview";
import GuiEditor from "../gui-editor/GuiEditor";
import AnimationEditor from "../animation/AnimationEditor";
const BlockEditor = lazy(() => import("../block-editor/BlockEditor"));

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
  const currentProject = useStore((s) => s.currentProject);
  const openToSide = useStore((s) => s.openToSide);
  const updateEditorSettings = useStore((s) => s.updateEditorSettings);
  const editorSettings = useStore((s) => s.editorSettings);

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
  const isBlock = file.name.endsWith(".block.json");
  const isGui = file.name.endsWith(".gui.json");
  const isAnim = file.name.endsWith(".anim.json");

  // Block visual editor
  if (isBlock) {
    const envAvailable = isVisualEditorAvailable(currentProject?.environment ?? null);
    if (!envAvailable) {
      return (
        <VisualEditorBlocked
          reason={getVisualEditorUnavailableReason(currentProject?.environment ?? null)}
          fileName={file.name}
        />
      );
    }
    return (
      <Suspense fallback={<div className="flex items-center justify-center h-full text-stone-500 text-xs">Loading Block Editor...</div>}>
        <BlockEditor
          key={file.path}
          path={file.path}
          content={file.content}
          onSave={(content) => {
            updateFileContent(file.path, content);
            saveFile(file.path);
          }}
        />
      </Suspense>
    );
  }

  // GUI visual editor
  if (isGui) {
    const envAvailable = isVisualEditorAvailable(currentProject?.environment ?? null);
    if (!envAvailable) {
      return (
        <VisualEditorBlocked
          reason={getVisualEditorUnavailableReason(currentProject?.environment ?? null)}
          fileName={file.name}
        />
      );
    }
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
    const envAvailable = isVisualEditorAvailable(currentProject?.environment ?? null);
    if (!envAvailable) {
      return (
        <VisualEditorBlocked
          reason={getVisualEditorUnavailableReason(currentProject?.environment ?? null)}
          fileName={file.name}
        />
      );
    }
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
                onSplitRight={() => { if (activeFilePath) openToSide(activeFilePath, "horizontal"); }}
                onToggleWordWrap={() => updateEditorSettings({ wordWrap: !editorSettings.wordWrap })}
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

/** Shown when visual editors are blocked due to environment */
function VisualEditorBlocked({
  reason,
  fileName,
}: {
  reason: string | null;
  fileName: string;
}) {
  return (
    <div className="flex flex-col items-center justify-center h-full gap-4 p-8">
      <ServerOff size={48} className="text-stone-600" />
      <div className="text-center max-w-sm">
        <h3 className="text-sm font-medium text-stone-300 mb-2">
          Visual Editor Unavailable
        </h3>
        <p className="text-xs text-stone-500 leading-relaxed">
          {reason || `Cannot open visual editor for "${fileName}" in this environment.`}
        </p>
        <p className="text-xs text-stone-600 mt-3">
          Change the mod environment to "client" or "both" in alloy.mod.json to enable visual editors.
        </p>
      </div>
    </div>
  );
}

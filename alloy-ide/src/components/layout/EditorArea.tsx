import { X } from "lucide-react";
import { useStore } from "../../lib/store";
import EditorTabs from "./EditorTabs";
import EditorPane from "../editor/EditorPane";
import CodeEditor from "../editor/CodeEditor";
import Breadcrumbs from "../editor/Breadcrumbs";
import WelcomeTab from "../editor/WelcomeTab";

function SplitEditorPane({ filePath }: { filePath: string }) {
  const openFiles = useStore((s) => s.openFiles);
  const updateFileContent = useStore((s) => s.updateFileContent);
  const setCursorPosition = useStore((s) => s.setCursorPosition);
  const saveFile = useStore((s) => s.saveFile);
  const closeSplit = useStore((s) => s.closeSplit);
  const setFocusedPane = useStore((s) => s.setFocusedPane);
  const focusedPane = useStore((s) => s.focusedPane);

  const file = openFiles.find((f) => f.path === filePath);
  if (!file) return null;

  const isFocused = focusedPane === "split";

  return (
    <div
      className={"flex flex-1 flex-col min-w-0 min-h-0 bg-obsidian-950" + (isFocused ? " ring-1 ring-inset ring-ember/30" : "")}
      onClick={() => setFocusedPane("split")}
    >
      {/* Split pane header */}
      <div className="flex h-9 shrink-0 items-center justify-between bg-obsidian-900 border-b border-obsidian-700 px-3">
        <span className="text-xs text-stone-300 truncate">{file.name}</span>
        <button
          onClick={(e) => {
            e.stopPropagation();
            closeSplit();
          }}
          className="rounded p-0.5 text-stone-500 hover:text-stone-200 hover:bg-obsidian-700 transition-colors"
        >
          <X size={14} />
        </button>
      </div>
      <div className="flex-1 min-h-0 overflow-hidden">
        <CodeEditor
          key={file.path + "-split"}
          path={file.path}
          content={file.content}
          language={file.language}
          onChange={(content: string) => updateFileContent(file.path, content)}
          onCursorChange={setCursorPosition}
          onSave={() => saveFile(file.path)}
        />
      </div>
    </div>
  );
}

export default function EditorArea() {
  const activeFilePath = useStore((s) => s.activeFilePath);
  const openFiles = useStore((s) => s.openFiles);
  const splitDirection = useStore((s) => s.splitDirection);
  const splitFilePath = useStore((s) => s.splitFilePath);
  const focusedPane = useStore((s) => s.focusedPane);
  const setFocusedPane = useStore((s) => s.setFocusedPane);

  const hasSplit = splitDirection !== "none" && splitFilePath;
  const isHorizontal = splitDirection === "horizontal";
  const isPrimaryFocused = focusedPane === "primary";

  return (
    <div className="flex flex-1 flex-col min-w-0 min-h-0 bg-obsidian-950">
      <EditorTabs />
      <div
        className={
          "flex flex-1 min-h-0 " +
          (hasSplit ? (isHorizontal ? "flex-row" : "flex-col") : "flex-col")
        }
      >
        {/* Primary pane */}
        <div
          className={
            "flex flex-1 flex-col min-w-0 min-h-0" +
            (hasSplit && isPrimaryFocused ? " ring-1 ring-inset ring-ember/30" : "")
          }
          onClick={() => hasSplit && setFocusedPane("primary")}
        >
          {activeFilePath && openFiles.length > 0 && <Breadcrumbs />}
          <div className="flex-1 min-h-0 overflow-hidden">
            {activeFilePath && openFiles.length > 0 ? (
              <EditorPane />
            ) : (
              <WelcomeTab />
            )}
          </div>
        </div>

        {/* Split divider + second pane */}
        {hasSplit && (
          <>
            <div
              className={
                isHorizontal
                  ? "w-px bg-obsidian-700 shrink-0"
                  : "h-px bg-obsidian-700 shrink-0"
              }
            />
            <SplitEditorPane filePath={splitFilePath} />
          </>
        )}
      </div>
    </div>
  );
}

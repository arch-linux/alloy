import { useState, useCallback } from "react";
import { X, AlertTriangle, RefreshCw, FileDown } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useStore } from "../../lib/store";
import EditorTabs from "./EditorTabs";
import EditorPane from "../editor/EditorPane";
import CodeEditor from "../editor/CodeEditor";
import Breadcrumbs from "../editor/Breadcrumbs";
import WelcomeTab from "../editor/WelcomeTab";
import DiffEditor from "../editor/DiffEditor";

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

function ExternalModBanner({ path }: { path: string }) {
  const handleReload = async () => {
    try {
      const content = await invoke<string>("read_file", { path });
      const store = useStore.getState();
      useStore.setState({
        openFiles: store.openFiles.map((f) =>
          f.path === path ? { ...f, content, dirty: false, externallyModified: false } : f,
        ),
      });
    } catch { /* ignore */ }
  };

  const handleDismiss = () => {
    const store = useStore.getState();
    useStore.setState({
      openFiles: store.openFiles.map((f) =>
        f.path === path ? { ...f, externallyModified: false } : f,
      ),
    });
  };

  return (
    <div className="flex items-center gap-2 px-3 py-1.5 bg-yellow-400/10 border-b border-yellow-400/20 text-xs">
      <AlertTriangle size={13} className="text-yellow-400 shrink-0" />
      <span className="text-yellow-300 flex-1">
        This file was modified outside the IDE.
      </span>
      <button
        onClick={handleReload}
        className="flex items-center gap-1 px-2 py-0.5 rounded bg-yellow-400/15 text-yellow-300 hover:bg-yellow-400/25 transition-colors"
      >
        <RefreshCw size={11} />
        Reload
      </button>
      <button
        onClick={handleDismiss}
        className="px-2 py-0.5 rounded text-stone-400 hover:text-stone-200 hover:bg-obsidian-700 transition-colors"
      >
        Dismiss
      </button>
    </div>
  );
}

const IMAGE_EXTS = new Set(["png", "jpg", "jpeg", "gif", "bmp", "webp", "tga", "svg", "ico"]);

export default function EditorArea() {
  const activeFilePath = useStore((s) => s.activeFilePath);
  const openFiles = useStore((s) => s.openFiles);
  const splitDirection = useStore((s) => s.splitDirection);
  const splitFilePath = useStore((s) => s.splitFilePath);
  const focusedPane = useStore((s) => s.focusedPane);
  const setFocusedPane = useStore((s) => s.setFocusedPane);
  const diffView = useStore((s) => s.diffView);
  const closeDiffView = useStore((s) => s.closeDiffView);
  const [dropActive, setDropActive] = useState(false);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    if (e.dataTransfer.types.includes("Files") || e.dataTransfer.types.includes("text/plain")) {
      e.dataTransfer.dropEffect = "copy";
      setDropActive(true);
    }
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    // Only deactivate if leaving the container entirely
    if (e.currentTarget.contains(e.relatedTarget as Node)) return;
    setDropActive(false);
  }, []);

  const handleDrop = useCallback(async (e: React.DragEvent) => {
    e.preventDefault();
    setDropActive(false);

    // Handle file tree internal drag (path as text)
    const textData = e.dataTransfer.getData("text/uri-list") || e.dataTransfer.getData("text/plain");
    if (textData && textData.startsWith("/")) {
      const name = textData.split("/").pop() || textData;
      useStore.getState().openFile(textData, name);
      return;
    }

    // Handle OS file drops
    const files = e.dataTransfer.files;
    if (files.length === 0) return;

    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      const filePath = (file as File & { path?: string }).path;
      if (!filePath) continue;

      const ext = file.name.split(".").pop()?.toLowerCase() || "";

      // If it's an image and we have a project, offer asset import
      if (IMAGE_EXTS.has(ext) && useStore.getState().currentProject) {
        useStore.getState().showAssetImport(filePath);
        return; // Only one asset import at a time
      }

      // Otherwise, open as a file
      await useStore.getState().openFile(filePath, file.name);
    }
  }, []);

  const hasSplit = splitDirection !== "none" && splitFilePath;
  const isHorizontal = splitDirection === "horizontal";
  const isPrimaryFocused = focusedPane === "primary";
  const activeFile = openFiles.find((f) => f.path === activeFilePath);

  return (
    <div
      className={"relative flex flex-1 flex-col min-w-0 min-h-0 bg-obsidian-950" + (dropActive ? " ring-2 ring-inset ring-ember/40" : "")}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
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
          {activeFile?.externallyModified && activeFilePath && (
            <ExternalModBanner path={activeFilePath} />
          )}
          {activeFilePath && openFiles.length > 0 && <Breadcrumbs />}
          <div className="flex-1 min-h-0 overflow-hidden">
            {diffView ? (
              <div className="flex flex-col h-full">
                <div className="flex items-center justify-between px-3 py-1 bg-obsidian-900 border-b border-obsidian-700 shrink-0">
                  <span className="text-[10px] text-stone-500 uppercase tracking-wider">Diff View</span>
                  <button
                    onClick={closeDiffView}
                    className="text-[10px] text-stone-500 hover:text-stone-200 px-2 py-0.5 rounded hover:bg-obsidian-700 transition-colors"
                  >
                    Close Diff
                  </button>
                </div>
                <DiffEditor
                  originalContent={diffView.originalContent}
                  modifiedContent={diffView.modifiedContent}
                  originalTitle={diffView.originalTitle}
                  modifiedTitle={diffView.modifiedTitle}
                />
              </div>
            ) : activeFilePath && openFiles.length > 0 ? (
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
      {/* Drop overlay */}
      {dropActive && (
        <div className="absolute inset-0 z-10 flex items-center justify-center bg-obsidian-950/80 pointer-events-none">
          <div className="flex flex-col items-center gap-2 text-ember">
            <FileDown size={32} className="animate-bounce" />
            <span className="text-sm font-medium">Drop to open</span>
          </div>
        </div>
      )}
    </div>
  );
}

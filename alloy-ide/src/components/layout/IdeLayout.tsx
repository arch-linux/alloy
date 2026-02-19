import { useState, useEffect, useCallback } from "react";
import { useStore } from "../../lib/store";
import TitleBar from "./TitleBar";
import ActivityBar from "./ActivityBar";
import Sidebar from "./Sidebar";
import EditorArea from "./EditorArea";
import BottomPanel from "./BottomPanel";
import StatusBar from "./StatusBar";
import FileTree from "../filetree/FileTree";
import SearchPanel from "../search/SearchPanel";
import TerminalComponent from "../terminal/Terminal";
import ProblemsPanel from "../panels/ProblemsPanel";
import OutputPanel from "../panels/OutputPanel";
import AiPanel from "../ai/AiPanel";
import GitPanel from "../git/GitPanel";
import ExtensionsPanel from "../extensions/ExtensionsPanel";
import TaskRunnerPanel from "../tasks/TaskRunnerPanel";
import CommandPalette from "../palette/CommandPalette";
import QuickOpen from "../palette/QuickOpen";
import GoToLine from "../palette/GoToLine";
import SymbolSearch from "../palette/SymbolSearch";
import FileSymbolSearch from "../palette/FileSymbolSearch";
import SettingsDialog from "../settings/SettingsDialog";
import ToastContainer from "../ui/Toast";
import ConfirmDialog, { registerConfirmHandler, resolveConfirm } from "../ui/ConfirmDialog";
import AssetImportWizard from "../assets/AssetImportWizard";
import BlockCreationWizard from "../block-editor/BlockCreationWizard";
import KeyboardShortcuts from "../settings/KeyboardShortcuts";
import ModpackPanel from "../modpack/ModpackPanel";
import SidebarSection from "./SidebarSection";
import RenameDialog from "../editor/RenameDialog";
import ReferencesPanel from "../panels/ReferencesPanel";
import CodeActionsMenu from "../editor/CodeActionsMenu";
import OutlinePanel from "../editor/OutlinePanel";
import { lspRename } from "../../lib/lsp";
import type { LspCodeAction } from "../../lib/lsp";
import { invoke } from "@tauri-apps/api/core";

interface ConfirmState {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: "default" | "danger";
}

export default function IdeLayout() {
  const sidebarPanel = useStore((s) => s.sidebarPanel);
  const bottomPanel = useStore((s) => s.bottomPanel);
  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false);
  const [quickOpenOpen, setQuickOpenOpen] = useState(false);
  const [goToLineOpen, setGoToLineOpen] = useState(false);
  const [symbolSearchOpen, setSymbolSearchOpen] = useState(false);
  const [fileSymbolSearchOpen, setFileSymbolSearchOpen] = useState(false);
  const settingsOpen = useStore((s) => s.settingsOpen);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const assetImportSource = useStore((s) => s.assetImportSource);
  const hideAssetImport = useStore((s) => s.hideAssetImport);
  const blockWizardOpen = useStore((s) => s.blockWizardOpen);
  const hideBlockWizard = useStore((s) => s.hideBlockWizard);
  const [confirmDialog, setConfirmDialog] = useState<ConfirmState | null>(null);
  const [renameState, setRenameState] = useState<{
    path: string;
    line: number;
    character: number;
    currentName: string;
  } | null>(null);
  const [codeActionsMenu, setCodeActionsMenu] = useState<{
    actions: LspCodeAction[];
    x: number;
    y: number;
  } | null>(null);

  // Register global confirm handler
  useEffect(() => {
    registerConfirmHandler((opts) => setConfirmDialog(opts));
  }, []);

  // Save workspace state on window close and periodically
  useEffect(() => {
    const save = () => useStore.getState().saveWorkspaceState();
    window.addEventListener("beforeunload", save);
    const interval = setInterval(save, 30_000); // Auto-save every 30s
    return () => {
      window.removeEventListener("beforeunload", save);
      clearInterval(interval);
    };
  }, []);

  // Auto-save dirty files
  const editorSettings = useStore((s) => s.editorSettings);
  const openFiles = useStore((s) => s.openFiles);
  useEffect(() => {
    if (!editorSettings.autoSave) return;
    const dirtyFiles = openFiles.filter((f) => f.dirty);
    if (dirtyFiles.length === 0) return;

    const timer = setTimeout(() => {
      const state = useStore.getState();
      for (const file of dirtyFiles) {
        // Re-check in case it was saved manually
        const current = state.openFiles.find((f) => f.path === file.path);
        if (current?.dirty) {
          state.saveFile(current.path);
        }
      }
    }, editorSettings.autoSaveDelay);

    return () => clearTimeout(timer);
  }, [editorSettings.autoSave, editorSettings.autoSaveDelay, openFiles]);

  // LSP go-to-definition across files
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      if (detail?.path && detail?.line) {
        const state = useStore.getState();
        const name = detail.path.split("/").pop() || detail.path;
        state.openFile(detail.path, name).then(() => {
          state.goToLine(detail.line);
        });
      }
    };
    window.addEventListener("lsp:goto", handler);
    return () => window.removeEventListener("lsp:goto", handler);
  }, []);

  // LSP rename symbol dialog
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      if (detail?.path && detail?.currentName) {
        setRenameState({
          path: detail.path,
          line: detail.line,
          character: detail.character,
          currentName: detail.currentName,
        });
      }
    };
    window.addEventListener("lsp:rename", handler);
    return () => window.removeEventListener("lsp:rename", handler);
  }, []);

  // LSP find references — auto-open references panel
  useEffect(() => {
    const handler = () => {
      const state = useStore.getState();
      state.setBottomPanel("references");
      if (!state.bottomPanelVisible) {
        state.toggleBottomPanel();
      }
    };
    window.addEventListener("lsp:references", handler);
    return () => window.removeEventListener("lsp:references", handler);
  }, []);

  // LSP code actions menu
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      if (detail?.actions?.length > 0) {
        setCodeActionsMenu({
          actions: detail.actions,
          x: detail.x,
          y: detail.y,
        });
      }
    };
    window.addEventListener("lsp:codeActions", handler);
    return () => window.removeEventListener("lsp:codeActions", handler);
  }, []);

  // Global keyboard shortcuts
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const mod = e.metaKey || e.ctrlKey;

      // Ctrl/Cmd+Shift+P — Command Palette
      if (mod && e.shiftKey && e.key === "P") {
        e.preventDefault();
        setQuickOpenOpen(false);
        setCommandPaletteOpen((v) => !v);
        return;
      }
      // Ctrl/Cmd+P — Quick Open (Go to File)
      if (mod && e.key === "p" && !e.shiftKey) {
        e.preventDefault();
        setCommandPaletteOpen(false);
        setQuickOpenOpen((v) => !v);
        return;
      }
      // Ctrl/Cmd+G — Go to Line
      if (mod && e.key === "g" && !e.shiftKey) {
        e.preventDefault();
        setGoToLineOpen((v) => !v);
        return;
      }
      // Ctrl/Cmd+T — Go to Symbol in Workspace
      if (mod && e.key === "t" && !e.shiftKey) {
        e.preventDefault();
        setSymbolSearchOpen((v) => !v);
        return;
      }
      // Ctrl/Cmd+Shift+O — Go to Symbol in File
      if (mod && e.key === "O" && e.shiftKey) {
        e.preventDefault();
        setFileSymbolSearchOpen((v) => !v);
        return;
      }
      // Ctrl/Cmd+, — Settings
      if (mod && e.key === ",") {
        e.preventDefault();
        useStore.getState().toggleSettings();
        return;
      }
      // Ctrl/Cmd+/ — Keyboard shortcuts
      if (mod && e.key === "/") {
        e.preventDefault();
        setShortcutsOpen((v) => !v);
        return;
      }
      // Ctrl/Cmd+O — Open folder
      if (mod && e.key === "o" && !e.shiftKey) {
        e.preventDefault();
        useStore.getState().openFolderDialog();
      }
      // Ctrl/Cmd+Shift+E — Toggle explorer
      if (mod && e.shiftKey && e.key === "E") {
        e.preventDefault();
        useStore.getState().setSidebarPanel("files");
      }
      // Ctrl/Cmd+Shift+F — Search
      if (mod && e.shiftKey && e.key === "F") {
        e.preventDefault();
        useStore.getState().setSidebarPanel("search");
      }
      // Ctrl/Cmd+Shift+H — Search & Replace
      if (mod && e.shiftKey && e.key === "H") {
        e.preventDefault();
        useStore.getState().setSidebarPanel("search");
      }
      // Ctrl/Cmd+Shift+G — Source Control
      if (mod && e.shiftKey && e.key === "G") {
        e.preventDefault();
        useStore.getState().setSidebarPanel("git");
      }
      // Ctrl/Cmd+Shift+B — Build
      if (mod && e.shiftKey && e.key === "B") {
        e.preventDefault();
        useStore.getState().runBuild("build");
        return;
      }
      // Ctrl/Cmd+` — Toggle terminal
      if (mod && e.key === "`") {
        e.preventDefault();
        useStore.getState().toggleBottomPanel();
      }
      // Ctrl/Cmd+B — Toggle sidebar
      if (mod && e.key === "b" && !e.shiftKey) {
        e.preventDefault();
        useStore.getState().toggleSidebar();
      }
      // Ctrl/Cmd+S — Save (handled by CodeMirror too, but catch it globally)
      if (mod && e.key === "s" && !e.shiftKey) {
        e.preventDefault();
        const state = useStore.getState();
        if (state.activeFilePath) {
          state.saveFile(state.activeFilePath);
        }
      }
      // Ctrl/Cmd+Shift+T — Reopen closed tab
      if (mod && e.shiftKey && e.key === "T") {
        e.preventDefault();
        useStore.getState().reopenClosedTab();
        return;
      }
      // Ctrl/Cmd+\ — Split editor right
      if (mod && e.key === "\\" && !e.shiftKey) {
        e.preventDefault();
        const state = useStore.getState();
        if (state.activeFilePath) {
          state.openToSide(state.activeFilePath, "horizontal");
        }
        return;
      }
      // Ctrl/Cmd+Shift+\ — Close split
      if (mod && e.shiftKey && e.key === "\\") {
        e.preventDefault();
        useStore.getState().closeSplit();
        return;
      }
      // Ctrl/Cmd+W — Close tab (skip if pinned)
      if (mod && e.key === "w" && !e.shiftKey) {
        e.preventDefault();
        const state = useStore.getState();
        if (state.activeFilePath) {
          const file = state.openFiles.find((f) => f.path === state.activeFilePath);
          if (!file?.pinned) {
            state.closeFile(state.activeFilePath);
          }
        }
        return;
      }
      // Ctrl/Cmd+Shift+W — Close all tabs
      if (mod && e.key === "W" && e.shiftKey) {
        e.preventDefault();
        const state = useStore.getState();
        [...state.openFiles].reverse().forEach((f) => state.closeFile(f.path));
        return;
      }
      // Ctrl+Tab — Next tab
      if (e.ctrlKey && e.key === "Tab" && !e.shiftKey) {
        e.preventDefault();
        const state = useStore.getState();
        const { openFiles, activeFilePath } = state;
        if (openFiles.length < 2) return;
        const idx = openFiles.findIndex((f) => f.path === activeFilePath);
        const next = (idx + 1) % openFiles.length;
        state.setActiveFile(openFiles[next].path);
        return;
      }
      // Ctrl+Shift+Tab — Previous tab
      if (e.ctrlKey && e.key === "Tab" && e.shiftKey) {
        e.preventDefault();
        const state = useStore.getState();
        const { openFiles, activeFilePath } = state;
        if (openFiles.length < 2) return;
        const idx = openFiles.findIndex((f) => f.path === activeFilePath);
        const prev = (idx - 1 + openFiles.length) % openFiles.length;
        state.setActiveFile(openFiles[prev].path);
        return;
      }
      // Ctrl/Cmd+= — Zoom in
      if (mod && (e.key === "=" || e.key === "+") && !e.shiftKey) {
        e.preventDefault();
        const s = useStore.getState();
        s.updateEditorSettings({ fontSize: Math.min(s.editorSettings.fontSize + 1, 32) });
      }
      // Ctrl/Cmd+- — Zoom out
      if (mod && e.key === "-" && !e.shiftKey) {
        e.preventDefault();
        const s = useStore.getState();
        s.updateEditorSettings({ fontSize: Math.max(s.editorSettings.fontSize - 1, 8) });
      }
      // Ctrl/Cmd+0 — Reset zoom
      if (mod && e.key === "0" && !e.shiftKey) {
        e.preventDefault();
        useStore.getState().updateEditorSettings({ fontSize: 13 });
      }
    };

    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  const currentProject = useStore((s) => s.currentProject);
  const isModpack = currentProject?.project_type === "modpack";

  const sidebarContent = () => {
    switch (sidebarPanel) {
      case "files":
        return isModpack ? (
          <div className="flex flex-col h-full">
            <ModpackPanel />
            <div className="border-t border-obsidian-700 flex-1 min-h-0 overflow-y-auto">
              <FileTree />
            </div>
          </div>
        ) : (
          <div className="flex flex-col h-full">
            {openFiles.length > 0 && (
              <SidebarSection key="open-editors" title="Open Editors" badge={openFiles.length} defaultOpen={false}>
                <div className="py-0.5">
                  {openFiles.map((f) => (
                    <button
                      key={f.path}
                      onClick={() => useStore.getState().setActiveFile(f.path)}
                      className={
                        "flex w-full items-center gap-2 px-4 py-0.5 text-[11px] transition-colors " +
                        (f.path === useStore.getState().activeFilePath
                          ? "bg-ember/10 text-stone-100"
                          : "text-stone-400 hover:text-stone-200 hover:bg-obsidian-800")
                      }
                    >
                      {f.dirty && <span className="h-1.5 w-1.5 rounded-full bg-ember shrink-0" />}
                      <span className="truncate">{f.name}</span>
                    </button>
                  ))}
                </div>
              </SidebarSection>
            )}
            <SidebarSection key="files" title="Files">
              <div className="flex-1 min-h-0">
                <FileTree />
              </div>
            </SidebarSection>
            <SidebarSection key="outline" title="Outline" defaultOpen={false}>
              <OutlinePanel />
            </SidebarSection>
          </div>
        );
      case "search":
        return <SearchPanel />;
      case "git":
        return <GitPanel />;
      case "extensions":
        return <ExtensionsPanel />;
      case "tasks":
        return <TaskRunnerPanel />;
      case "ai":
        return <AiPanel />;
      default:
        return null;
    }
  };

  const bottomContent = () => {
    switch (bottomPanel) {
      case "terminal":
        return <TerminalComponent />;
      case "problems":
        return <ProblemsPanel />;
      case "output":
        return <OutputPanel />;
      case "references":
        return <ReferencesPanel />;
      default:
        return null;
    }
  };

  return (
    <div className="flex h-screen w-screen flex-col overflow-hidden">
      <TitleBar />
      <div className="flex flex-1 min-h-0 overflow-hidden">
        <ActivityBar />
        <Sidebar>{sidebarContent()}</Sidebar>
        <div className="flex flex-1 flex-col min-w-0 min-h-0">
          <EditorArea />
          <BottomPanel>{bottomContent()}</BottomPanel>
        </div>
      </div>
      <StatusBar />

      {/* Overlays */}
      {commandPaletteOpen && (
        <CommandPalette onClose={() => setCommandPaletteOpen(false)} />
      )}
      {quickOpenOpen && (
        <QuickOpen onClose={() => setQuickOpenOpen(false)} />
      )}
      {goToLineOpen && (
        <GoToLine
          onClose={() => setGoToLineOpen(false)}
          onGo={(line) => useStore.getState().goToLine(line)}
        />
      )}
      {symbolSearchOpen && (
        <SymbolSearch onClose={() => setSymbolSearchOpen(false)} />
      )}
      {fileSymbolSearchOpen && (
        <FileSymbolSearch onClose={() => setFileSymbolSearchOpen(false)} />
      )}
      {settingsOpen && <SettingsDialog onClose={() => useStore.getState().toggleSettings()} />}
      {shortcutsOpen && <KeyboardShortcuts onClose={() => setShortcutsOpen(false)} />}
      {confirmDialog && (
        <ConfirmDialog
          title={confirmDialog.title}
          message={confirmDialog.message}
          confirmLabel={confirmDialog.confirmLabel}
          cancelLabel={confirmDialog.cancelLabel}
          variant={confirmDialog.variant}
          onConfirm={() => {
            resolveConfirm(true);
            setConfirmDialog(null);
          }}
          onCancel={() => {
            resolveConfirm(false);
            setConfirmDialog(null);
          }}
        />
      )}
      {assetImportSource && (
        <AssetImportWizard sourcePath={assetImportSource} onClose={hideAssetImport} />
      )}
      {blockWizardOpen && (
        <BlockCreationWizard onClose={hideBlockWizard} />
      )}
      {codeActionsMenu && (
        <CodeActionsMenu
          actions={codeActionsMenu.actions}
          x={codeActionsMenu.x}
          y={codeActionsMenu.y}
          onSelect={async (action) => {
            // Apply workspace edits from the code action
            for (const edit of action.edits) {
              try {
                const content = await invoke<string>("read_file", {
                  path: edit.path,
                });
                const lines = content.split("\n");
                const sorted = [...edit.edits].sort(
                  (a, b) =>
                    b.start_line - a.start_line ||
                    b.start_character - a.start_character
                );
                let result = content;
                for (const e of sorted) {
                  const startIdx =
                    lines
                      .slice(0, e.start_line)
                      .reduce((sum, l) => sum + l.length + 1, 0) +
                    e.start_character;
                  const endIdx =
                    lines
                      .slice(0, e.end_line)
                      .reduce((sum, l) => sum + l.length + 1, 0) +
                    e.end_character;
                  result =
                    result.substring(0, startIdx) +
                    e.new_text +
                    result.substring(endIdx);
                }
                await invoke("write_file", { path: edit.path, content: result });

                const state = useStore.getState();
                const openFile = state.openFiles.find(
                  (f) => f.path === edit.path
                );
                if (openFile) {
                  const refreshed = await invoke<string>("read_file", {
                    path: edit.path,
                  });
                  state.updateFileContent(edit.path, refreshed);
                }
              } catch {
                // Ignore errors
              }
            }
          }}
          onClose={() => setCodeActionsMenu(null)}
        />
      )}
      {renameState && (
        <RenameDialog
          currentName={renameState.currentName}
          onRename={async (newName) => {
            try {
              const edits = await lspRename(
                renameState.path,
                renameState.line,
                renameState.character,
                newName
              );
              // Apply workspace edits
              for (const edit of edits) {
                const content = await invoke<string>("read_file", {
                  path: edit.path,
                });
                const lines = content.split("\n");
                // Apply edits in reverse order to preserve positions
                const sorted = [...edit.edits].sort(
                  (a, b) =>
                    b.start_line - a.start_line ||
                    b.start_character - a.start_character
                );
                for (const e of sorted) {
                  const startIdx =
                    lines
                      .slice(0, e.start_line)
                      .reduce((sum, l) => sum + l.length + 1, 0) +
                    e.start_character;
                  const endIdx =
                    lines
                      .slice(0, e.end_line)
                      .reduce((sum, l) => sum + l.length + 1, 0) +
                    e.end_character;
                  const full = content;
                  const newContent =
                    full.substring(0, startIdx) +
                    e.new_text +
                    full.substring(endIdx);
                  await invoke("write_file", {
                    path: edit.path,
                    content: newContent,
                  });
                }
                // Refresh open file if affected
                const state = useStore.getState();
                const openFile = state.openFiles.find(
                  (f) => f.path === edit.path
                );
                if (openFile) {
                  const refreshed = await invoke<string>("read_file", {
                    path: edit.path,
                  });
                  state.updateFileContent(edit.path, refreshed);
                }
              }
            } catch {
              // Rename failed silently
            }
          }}
          onClose={() => setRenameState(null)}
        />
      )}
      <ToastContainer />
    </div>
  );
}

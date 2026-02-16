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
import SettingsDialog from "../settings/SettingsDialog";
import ToastContainer from "../ui/Toast";
import ConfirmDialog, { registerConfirmHandler, resolveConfirm } from "../ui/ConfirmDialog";
import AssetImportWizard from "../assets/AssetImportWizard";
import ModpackPanel from "../modpack/ModpackPanel";

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
  const settingsOpen = useStore((s) => s.settingsOpen);
  const assetImportSource = useStore((s) => s.assetImportSource);
  const hideAssetImport = useStore((s) => s.hideAssetImport);
  const [confirmDialog, setConfirmDialog] = useState<ConfirmState | null>(null);

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
      // Ctrl/Cmd+, — Settings
      if (mod && e.key === ",") {
        e.preventDefault();
        useStore.getState().toggleSettings();
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
      // Ctrl/Cmd+W — Close tab
      if (mod && e.key === "w" && !e.shiftKey) {
        e.preventDefault();
        const state = useStore.getState();
        if (state.activeFilePath) {
          state.closeFile(state.activeFilePath);
        }
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
          <FileTree />
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
      {settingsOpen && <SettingsDialog onClose={() => useStore.getState().toggleSettings()} />}
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
      <ToastContainer />
    </div>
  );
}

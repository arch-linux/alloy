import { create } from "zustand";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { open } from "@tauri-apps/plugin-dialog";
import { showToast } from "../components/ui/Toast";
import type {
  ProjectInfo,
  RecentProject,
  FileEntry,
  OpenFile,
  SidebarPanel,
  SplitDirection,
  BottomPanelTab,
  CursorPosition,
  ChatMessage,
  AiConfig,
  ToolCall,
  SearchResult,
  BuildError,
  BuildResult,
  EditorSettings,
} from "./types";
import { lspStart, lspStop } from "./lsp";

function detectLanguage(path: string): string {
  const name = path.split("/").pop()?.toLowerCase() || "";
  const ext = name.split(".").pop()?.toLowerCase();

  // Special filenames
  if (name === "alloy.mod.json" || name === "alloy.pack.toml") return "json";

  // Multi-part extensions
  if (name.endsWith(".gradle.kts")) return "kotlin";
  if (name.endsWith(".d.ts")) return "typescript";

  switch (ext) {
    case "java":
      return "java";
    case "kt":
    case "kts":
      return "kotlin";
    case "json":
    case "mcmeta":
      return "json";
    case "gradle":
      return "kotlin"; // Groovy → Kotlin highlighting is close enough
    case "toml":
      return "toml";
    case "md":
    case "mdx":
      return "markdown";
    case "xml":
    case "fxml":
      return "xml";
    case "properties":
    case "cfg":
    case "conf":
    case "ini":
      return "properties";
    case "yml":
    case "yaml":
      return "yaml";
    case "ts":
    case "tsx":
      return "typescript";
    case "js":
    case "jsx":
    case "mjs":
    case "cjs":
      return "javascript";
    case "css":
    case "scss":
    case "less":
      return "css";
    case "html":
    case "htm":
      return "html";
    case "sh":
    case "bash":
    case "zsh":
      return "text"; // No shell highlighting installed
    case "bat":
    case "cmd":
    case "ps1":
      return "text";
    case "svg":
      return "xml";
    case "txt":
    case "log":
    case "gitignore":
      return "text";
    default:
      return "text";
  }
}

interface IdeStore {
  // Project
  currentProject: ProjectInfo | null;
  recentProjects: RecentProject[];

  // File tree
  fileTree: FileEntry[];
  expandedDirs: Set<string>;

  // Editor
  openFiles: OpenFile[];
  activeFilePath: string | null;
  cursorPosition: CursorPosition;

  // Panels
  sidebarPanel: SidebarPanel;
  sidebarVisible: boolean;
  sidebarWidth: number;
  bottomPanel: BottomPanelTab;
  bottomPanelVisible: boolean;
  bottomPanelHeight: number;

  // Search
  searchQuery: string;
  searchResults: SearchResult[];
  searchLoading: boolean;

  // Settings
  editorSettings: EditorSettings;
  settingsOpen: boolean;

  // Build
  buildRunning: boolean;
  buildErrors: BuildError[];
  buildOutput: string[];

  // Recently closed tabs
  recentlyClosed: { path: string; name: string }[];

  // Split editor
  splitDirection: SplitDirection;
  splitFilePath: string | null;
  focusedPane: "primary" | "split";

  // Asset import
  assetImportSource: string | null;

  // Diff view
  diffView: {
    originalContent: string;
    modifiedContent: string;
    originalTitle: string;
    modifiedTitle: string;
  } | null;

  // AI
  chatMessages: ChatMessage[];
  aiLoading: boolean;
  aiConfig: AiConfig | null;
  aiConfigLoaded: boolean;

  // LSP
  lspRunning: boolean;

  // Block wizard
  blockWizardOpen: boolean;

  // Actions — Project
  openProject: (path: string) => Promise<void>;
  openFolderDialog: () => Promise<void>;
  loadRecentProjects: () => Promise<void>;

  // Actions — File tree
  loadFileTree: (path: string) => Promise<FileEntry[]>;
  refreshFileTree: () => Promise<void>;
  toggleDirectory: (path: string) => Promise<void>;

  // Actions — Editor
  openFile: (path: string, name: string) => Promise<void>;
  closeFile: (path: string) => void;
  pinFile: (path: string) => void;
  setActiveFile: (path: string) => void;
  updateFileContent: (path: string, content: string) => void;
  saveFile: (path: string) => Promise<void>;
  setCursorPosition: (pos: CursorPosition) => void;

  // Actions — Panels
  setSidebarPanel: (panel: SidebarPanel) => void;
  toggleSidebar: () => void;
  setSidebarWidth: (width: number) => void;
  setBottomPanel: (panel: BottomPanelTab) => void;
  toggleBottomPanel: () => void;
  setBottomPanelHeight: (height: number) => void;
  runInTerminal: (command: string) => void;

  // Actions — Search
  searchFiles: (query: string) => Promise<void>;
  setSearchQuery: (query: string) => void;

  // Actions — File ops
  createFile: (path: string, content?: string) => Promise<void>;
  createDirectory: (path: string) => Promise<void>;
  deletePath: (path: string) => Promise<void>;
  renamePath: (oldPath: string, newPath: string) => Promise<void>;

  // Actions — Settings
  updateEditorSettings: (settings: Partial<EditorSettings>) => void;
  toggleSettings: () => void;

  // Actions — Editor navigation
  pendingGoToLine: number | null;
  goToLine: (line: number) => void;
  clearGoToLine: () => void;

  // Actions — Build
  runBuild: (task?: string) => Promise<void>;
  validateEnvironment: () => Promise<void>;

  // Actions — Tab management
  reorderFiles: (fromIndex: number, toIndex: number) => void;
  reopenClosedTab: () => Promise<void>;

  // Actions — Workspace persistence
  saveWorkspaceState: () => Promise<void>;
  restoreWorkspaceState: (projectPath: string) => Promise<void>;

  // Actions — Split editor
  openToSide: (path: string, direction?: "horizontal" | "vertical") => void;
  closeSplit: () => void;
  setFocusedPane: (pane: "primary" | "split") => void;

  // Actions — Asset import
  showAssetImport: (sourcePath: string) => void;
  hideAssetImport: () => void;
  showDiffView: (original: string, modified: string, originalTitle: string, modifiedTitle: string) => void;
  closeDiffView: () => void;

  // Actions — Block wizard
  showBlockWizard: () => void;
  hideBlockWizard: () => void;

  // Actions — AI
  sendMessage: (message: string) => Promise<void>;
  clearChat: () => Promise<void>;
  loadAiConfig: () => Promise<void>;
  setAiConfig: (config: AiConfig) => Promise<void>;
  addChatMessage: (message: ChatMessage) => void;
  updateToolCall: (toolCall: ToolCall) => void;
}

export const useStore = create<IdeStore>((set, get) => ({
  // Initial state
  currentProject: null,
  recentProjects: [],
  fileTree: [],
  expandedDirs: new Set<string>(),
  openFiles: [],
  activeFilePath: null,
  cursorPosition: { line: 1, column: 1 },
  sidebarPanel: "files",
  sidebarVisible: true,
  sidebarWidth: 260,
  bottomPanel: "terminal",
  bottomPanelVisible: false,
  bottomPanelHeight: 200,

  // Search initial state
  searchQuery: "",
  searchResults: [],
  searchLoading: false,

  // Settings
  editorSettings: {
    fontSize: 13,
    tabSize: 4,
    wordWrap: true,
    lineNumbers: true,
    minimap: false,
    indentGuides: true,
    autoSave: false,
    autoSaveDelay: 2000,
  },
  settingsOpen: false,

  // Navigation
  pendingGoToLine: null,

  // Recently closed
  recentlyClosed: [],

  // Build initial state
  buildRunning: false,
  buildErrors: [],
  buildOutput: [],

  // Split editor
  splitDirection: "none",
  splitFilePath: null,
  focusedPane: "primary",

  // Asset import
  assetImportSource: null,

  // Diff view
  diffView: null,

  // AI initial state
  chatMessages: [],
  aiLoading: false,
  aiConfig: null,
  aiConfigLoaded: false,

  // LSP initial state
  lspRunning: false,

  // Block wizard initial state
  blockWizardOpen: false,

  openProject: async (path: string) => {
    // Save current workspace state before switching
    const prev = get();
    if (prev.currentProject) {
      await get().saveWorkspaceState();
      // Stop previous LSP server
      lspStop().catch(() => {});
      set({ lspRunning: false });
    }

    const info = await invoke<ProjectInfo>("open_project", { path });
    const entries = await get().loadFileTree(path);
    set({
      currentProject: info,
      fileTree: entries,
      openFiles: [],
      activeFilePath: null,
      sidebarPanel: "files",
      sidebarVisible: true,
    });

    // Restore workspace state for the new project
    await get().restoreWorkspaceState(path);

    // Start LSP server for the project (non-blocking)
    lspStart(path)
      .then(() => set({ lspRunning: true }))
      .catch(() => set({ lspRunning: false }));
  },

  openFolderDialog: async () => {
    const selected = await open({ directory: true, multiple: false });
    if (selected && typeof selected === "string") {
      await get().openProject(selected);
    }
  },

  loadRecentProjects: async () => {
    try {
      const recents = await invoke<RecentProject[]>("get_recent_projects");
      set({ recentProjects: recents });
    } catch {
      // ignore
    }
  },

  loadFileTree: async (dirPath: string) => {
    const { expandedDirs } = get();
    const entries = await invoke<FileEntry[]>("list_directory", { path: dirPath });

    // Recursively rebuild tree, re-expanding previously expanded dirs
    async function buildEntries(items: FileEntry[]): Promise<FileEntry[]> {
      const result: FileEntry[] = [];
      for (const e of items) {
        if (e.is_dir && expandedDirs.has(e.path)) {
          const children = await invoke<FileEntry[]>("list_directory", { path: e.path });
          result.push({ ...e, expanded: true, children: await buildEntries(children) });
        } else {
          result.push({ ...e, expanded: false, children: undefined });
        }
      }
      return result;
    }

    return buildEntries(entries);
  },

  refreshFileTree: async () => {
    const project = get().currentProject;
    if (!project) return;
    const entries = await get().loadFileTree(project.path);
    set({ fileTree: entries });
  },

  toggleDirectory: async (path: string) => {
    const { fileTree, expandedDirs, loadFileTree } = get();

    async function toggleIn(entries: FileEntry[]): Promise<FileEntry[]> {
      const result: FileEntry[] = [];
      for (const entry of entries) {
        if (entry.path === path && entry.is_dir) {
          if (entry.expanded) {
            const next = new Set(expandedDirs);
            next.delete(path);
            set({ expandedDirs: next });
            result.push({ ...entry, expanded: false, children: undefined });
          } else {
            const next = new Set(expandedDirs);
            next.add(path);
            set({ expandedDirs: next });
            const children = await loadFileTree(path);
            result.push({ ...entry, expanded: true, children });
          }
        } else if (entry.children) {
          result.push({ ...entry, children: await toggleIn(entry.children) });
        } else {
          result.push(entry);
        }
      }
      return result;
    }

    const updated = await toggleIn(fileTree);
    set({ fileTree: updated });
  },

  openFile: async (path: string, name: string) => {
    const { openFiles } = get();
    const existing = openFiles.find((f) => f.path === path);
    if (existing) {
      set({ activeFilePath: path });
      return;
    }

    const content = await invoke<string>("read_file", { path });
    const language = detectLanguage(path);
    const file: OpenFile = { path, name, content, language, dirty: false, preview: true };

    // Replace existing preview tab (if any and not dirty), otherwise append
    const previewIdx = openFiles.findIndex((f) => f.preview && !f.dirty);
    let updated: OpenFile[];
    if (previewIdx >= 0) {
      updated = [...openFiles];
      updated[previewIdx] = file;
    } else {
      updated = [...openFiles, file];
    }
    set({
      openFiles: updated,
      activeFilePath: path,
    });

    // Watch this file for external changes
    invoke("watch_file", { path }).catch(() => {});
  },

  pinFile: (path: string) => {
    const files = get().openFiles.map((f) =>
      f.path === path ? { ...f, preview: false, pinned: !f.pinned } : f,
    );
    // Sort: pinned tabs first, then unpinned (preserve relative order within each group)
    const pinned = files.filter((f) => f.pinned);
    const unpinned = files.filter((f) => !f.pinned);
    set({ openFiles: [...pinned, ...unpinned] });
  },

  closeFile: (path: string) => {
    const { openFiles, activeFilePath, recentlyClosed } = get();
    const file = openFiles.find((f) => f.path === path);
    if (file?.dirty) {
      const confirmed = window.confirm(
        `"${file.name}" has unsaved changes. Close without saving?`,
      );
      if (!confirmed) return;
    }
    const filtered = openFiles.filter((f) => f.path !== path);
    let newActive = activeFilePath;
    if (activeFilePath === path) {
      newActive = filtered.length > 0 ? filtered[filtered.length - 1].path : null;
    }
    // Track closed tab for reopen
    if (file) {
      const updated = [{ path: file.path, name: file.name }, ...recentlyClosed].slice(0, 20);
      set({ openFiles: filtered, activeFilePath: newActive, recentlyClosed: updated });
    } else {
      set({ openFiles: filtered, activeFilePath: newActive });
    }
  },

  setActiveFile: (path: string) => {
    set({ activeFilePath: path });
  },

  updateFileContent: (path: string, content: string) => {
    set({
      openFiles: get().openFiles.map((f) =>
        f.path === path ? { ...f, content, dirty: true, preview: false } : f,
      ),
    });
  },

  saveFile: async (path: string) => {
    const file = get().openFiles.find((f) => f.path === path);
    if (!file) return;
    try {
      await invoke("write_file", { path, content: file.content });
      set({
        openFiles: get().openFiles.map((f) =>
          f.path === path ? { ...f, dirty: false } : f,
        ),
      });
      // Notify LSP about saved file
      if (file.language === "java" && get().lspRunning) {
        import("./lsp").then(({ lspDidSave }) => {
          lspDidSave(path, file.content).catch(() => {});
        });
      }
      showToast("success", `Saved ${file.name}`);
    } catch (err) {
      showToast("error", `Failed to save: ${err}`);
    }
  },

  setCursorPosition: (pos: CursorPosition) => {
    set({ cursorPosition: pos });
  },

  setSidebarPanel: (panel: SidebarPanel) => {
    const { sidebarPanel, sidebarVisible } = get();
    if (sidebarPanel === panel && sidebarVisible) {
      set({ sidebarVisible: false });
    } else {
      set({ sidebarPanel: panel, sidebarVisible: true });
    }
  },

  toggleSidebar: () => {
    set({ sidebarVisible: !get().sidebarVisible });
  },

  setSidebarWidth: (width: number) => {
    set({ sidebarWidth: Math.max(180, Math.min(500, width)) });
  },

  setBottomPanel: (panel: BottomPanelTab) => {
    const { bottomPanel, bottomPanelVisible } = get();
    if (bottomPanel === panel && bottomPanelVisible) {
      set({ bottomPanelVisible: false });
    } else {
      set({ bottomPanel: panel, bottomPanelVisible: true });
    }
  },

  toggleBottomPanel: () => {
    set({ bottomPanelVisible: !get().bottomPanelVisible });
  },

  setBottomPanelHeight: (height: number) => {
    set({ bottomPanelHeight: Math.max(100, Math.min(500, height)) });
  },

  runInTerminal: (command: string) => {
    // Switch to terminal panel and dispatch a custom event that Terminal component listens for
    set({ bottomPanel: "terminal", bottomPanelVisible: true });
    window.dispatchEvent(new CustomEvent("terminal:run-command", { detail: { command } }));
  },

  // Search actions

  searchFiles: async (query: string) => {
    const project = get().currentProject;
    if (!project || !query.trim()) {
      set({ searchResults: [], searchQuery: query });
      return;
    }
    set({ searchLoading: true, searchQuery: query });
    try {
      const results = await invoke<SearchResult[]>("search_files", {
        query: query.trim(),
        searchPath: project.path,
      });
      set({ searchResults: results, searchLoading: false });
    } catch {
      set({ searchResults: [], searchLoading: false });
    }
  },

  setSearchQuery: (query: string) => {
    set({ searchQuery: query });
  },

  // File ops

  createFile: async (path: string, content?: string) => {
    await invoke("create_file", { path, content: content ?? null });
    await get().refreshFileTree();
  },

  createDirectory: async (path: string) => {
    await invoke("create_directory", { path });
    await get().refreshFileTree();
  },

  deletePath: async (path: string) => {
    try {
      await invoke("delete_path", { path });
      // Close file if open
      const openFiles = get().openFiles;
      if (openFiles.some((f) => f.path === path)) {
        get().closeFile(path);
      }
      await get().refreshFileTree();
      const name = path.split("/").pop() || path;
      showToast("info", `Deleted ${name}`);
    } catch (err) {
      showToast("error", `Failed to delete: ${err}`);
    }
  },

  renamePath: async (oldPath: string, newPath: string) => {
    await invoke("rename_path", { oldPath, newPath });
    await get().refreshFileTree();
  },

  // Settings

  updateEditorSettings: (settings: Partial<EditorSettings>) => {
    set({ editorSettings: { ...get().editorSettings, ...settings } });
  },

  toggleSettings: () => {
    set({ settingsOpen: !get().settingsOpen });
  },

  // Editor navigation

  goToLine: (line: number) => {
    set({ pendingGoToLine: line });
  },

  clearGoToLine: () => {
    set({ pendingGoToLine: null });
  },

  // Build actions

  runBuild: async (task = "build") => {
    const project = get().currentProject;
    if (!project) {
      showToast("error", "No project open");
      return;
    }
    if (get().buildRunning) {
      showToast("warning", "Build already running");
      return;
    }

    set({ buildRunning: true, buildErrors: [], buildOutput: [] });
    showToast("info", `Running gradle ${task}...`);

    // Run environment validation first
    let envErrors: BuildError[] = [];
    try {
      envErrors = await invoke<BuildError[]>("validate_environment", {
        projectPath: project.path,
      });
    } catch {
      // Non-fatal — continue with build
    }

    try {
      const result = await invoke<BuildResult>("run_gradle_task", {
        projectPath: project.path,
        task,
      });
      const allErrors = [...envErrors, ...result.errors];
      set({
        buildRunning: false,
        buildErrors: allErrors,
      });
      if (result.success && envErrors.length === 0) {
        showToast("success", `Build succeeded`);
      } else if (result.success && envErrors.length > 0) {
        showToast("warning", `Build succeeded with ${envErrors.length} environment warning(s)`);
        set({ bottomPanelVisible: true, bottomPanel: "problems" });
      } else {
        showToast("error", `Build failed with ${allErrors.length} error(s)`);
        if (allErrors.length > 0) {
          set({ bottomPanelVisible: true, bottomPanel: "problems" });
        }
      }
    } catch (err) {
      set({ buildRunning: false, buildErrors: envErrors });
      showToast("error", `Build failed: ${err}`);
      if (envErrors.length > 0) {
        set({ bottomPanelVisible: true, bottomPanel: "problems" });
      }
    }
  },

  validateEnvironment: async () => {
    const project = get().currentProject;
    if (!project) return;
    try {
      const errors = await invoke<BuildError[]>("validate_environment", {
        projectPath: project.path,
      });
      set({ buildErrors: errors });
      if (errors.length > 0) {
        set({ bottomPanelVisible: true, bottomPanel: "problems" });
        showToast("warning", `${errors.length} environment warning(s) found`);
      } else {
        showToast("success", "No environment violations found");
      }
    } catch (err) {
      showToast("error", `Validation failed: ${err}`);
    }
  },

  // Tab management

  reorderFiles: (fromIndex: number, toIndex: number) => {
    const files = [...get().openFiles];
    const [moved] = files.splice(fromIndex, 1);
    files.splice(toIndex, 0, moved);
    set({ openFiles: files });
  },

  reopenClosedTab: async () => {
    const { recentlyClosed, openFiles } = get();
    if (recentlyClosed.length === 0) return;
    // Find first closed tab that isn't currently open
    const idx = recentlyClosed.findIndex(
      (c) => !openFiles.some((f) => f.path === c.path),
    );
    if (idx === -1) return;
    const { path, name } = recentlyClosed[idx];
    const updated = [...recentlyClosed];
    updated.splice(idx, 1);
    set({ recentlyClosed: updated });
    await get().openFile(path, name);
    get().pinFile(path); // Pin reopened tabs
  },

  // Workspace persistence

  saveWorkspaceState: async () => {
    const { currentProject, openFiles, activeFilePath, sidebarPanel, sidebarVisible, sidebarWidth, bottomPanel, bottomPanelVisible, bottomPanelHeight } = get();
    if (!currentProject) return;
    try {
      await invoke("save_workspace_state", {
        projectPath: currentProject.path,
        state: {
          open_files: openFiles.map((f) => f.path),
          active_file: activeFilePath,
          sidebar_panel: sidebarPanel,
          sidebar_visible: sidebarVisible,
          sidebar_width: sidebarWidth,
          bottom_panel: bottomPanel,
          bottom_panel_visible: bottomPanelVisible,
          bottom_panel_height: bottomPanelHeight,
        },
      });
    } catch {
      // Silent fail — workspace state is non-critical
    }
  },

  restoreWorkspaceState: async (projectPath: string) => {
    try {
      const saved = await invoke<{
        open_files: string[];
        active_file: string | null;
        sidebar_panel: string;
        sidebar_visible: boolean;
        sidebar_width: number;
        bottom_panel: string;
        bottom_panel_visible: boolean;
        bottom_panel_height: number;
      } | null>("load_workspace_state", { projectPath });

      if (!saved) return;

      // Restore panel state immediately
      set({
        sidebarPanel: saved.sidebar_panel as SidebarPanel,
        sidebarVisible: saved.sidebar_visible,
        sidebarWidth: saved.sidebar_width,
        bottomPanel: saved.bottom_panel as BottomPanelTab,
        bottomPanelVisible: saved.bottom_panel_visible,
        bottomPanelHeight: saved.bottom_panel_height,
      });

      // Re-open files (sequentially to preserve order)
      for (const filePath of saved.open_files) {
        const name = filePath.split("/").pop() || filePath;
        try {
          await get().openFile(filePath, name);
          get().pinFile(filePath); // Restored tabs should be pinned, not preview
        } catch {
          // File may have been deleted — skip it
        }
      }

      // Set the active file last
      if (saved.active_file && get().openFiles.some((f) => f.path === saved.active_file)) {
        set({ activeFilePath: saved.active_file });
      }
    } catch {
      // Silent fail
    }
  },

  // Split editor

  openToSide: (path: string, direction = "horizontal" as "horizontal" | "vertical") => {
    const { openFiles } = get();
    // Ensure the file is in openFiles
    const exists = openFiles.some((f) => f.path === path);
    if (!exists) {
      // Open the file first, then split
      const name = path.split("/").pop() || path;
      get().openFile(path, name).then(() => {
        set({ splitDirection: direction, splitFilePath: path, focusedPane: "split" });
      });
      return;
    }
    set({ splitDirection: direction, splitFilePath: path, focusedPane: "split" });
  },

  closeSplit: () => {
    set({ splitDirection: "none", splitFilePath: null, focusedPane: "primary" });
  },

  setFocusedPane: (pane: "primary" | "split") => {
    set({ focusedPane: pane });
  },

  // Asset import

  showAssetImport: (sourcePath: string) => {
    set({ assetImportSource: sourcePath });
  },

  hideAssetImport: () => {
    set({ assetImportSource: null });
  },

  showDiffView: (original: string, modified: string, originalTitle: string, modifiedTitle: string) => {
    set({
      diffView: { originalContent: original, modifiedContent: modified, originalTitle, modifiedTitle },
    });
  },

  closeDiffView: () => {
    set({ diffView: null });
  },

  // Block wizard

  showBlockWizard: () => {
    set({ blockWizardOpen: true });
  },

  hideBlockWizard: () => {
    set({ blockWizardOpen: false });
  },

  // AI actions

  sendMessage: async (message: string) => {
    const userMsg: ChatMessage = {
      role: "user",
      content: message,
      tool_calls: [],
      timestamp: Math.floor(Date.now() / 1000),
    };

    set((s) => ({
      chatMessages: [...s.chatMessages, userMsg],
      aiLoading: true,
    }));

    try {
      const result = await invoke<ChatMessage>("ai_send_message", { message });
      // The response-done event will handle adding the assistant message
      // but we add it here as a fallback
      set((s) => {
        const hasResponse = s.chatMessages.some(
          (m) => m.role === "assistant" && m.timestamp === result.timestamp,
        );
        if (hasResponse) return { aiLoading: false };
        return {
          chatMessages: [...s.chatMessages, result],
          aiLoading: false,
        };
      });
    } catch (err) {
      const errorMsg: ChatMessage = {
        role: "assistant",
        content: `Error: ${err}`,
        tool_calls: [],
        timestamp: Math.floor(Date.now() / 1000),
      };
      set((s) => ({
        chatMessages: [...s.chatMessages, errorMsg],
        aiLoading: false,
      }));
    }
  },

  clearChat: async () => {
    await invoke("ai_clear_history");
    set({ chatMessages: [] });
  },

  loadAiConfig: async () => {
    try {
      const config = await invoke<AiConfig>("ai_get_config");
      set({ aiConfig: config, aiConfigLoaded: true });
    } catch {
      set({ aiConfig: { api_key: null, model: "claude-sonnet-4-5-20250929", max_tokens: 4096 }, aiConfigLoaded: true });
    }
  },

  setAiConfig: async (config: AiConfig) => {
    await invoke("ai_set_config", { config });
    set({ aiConfig: config });
  },

  addChatMessage: (message: ChatMessage) => {
    set((s) => ({ chatMessages: [...s.chatMessages, message] }));
  },

  updateToolCall: (toolCall: ToolCall) => {
    set((s) => {
      const messages = [...s.chatMessages];
      // Find the last assistant message and update the tool call
      for (let i = messages.length - 1; i >= 0; i--) {
        if (messages[i].role === "assistant") {
          const msg = { ...messages[i] };
          const tcIdx = msg.tool_calls.findIndex((tc) => tc.id === toolCall.id);
          if (tcIdx >= 0) {
            msg.tool_calls = [...msg.tool_calls];
            msg.tool_calls[tcIdx] = toolCall;
          } else {
            msg.tool_calls = [...msg.tool_calls, toolCall];
          }
          messages[i] = msg;
          break;
        }
      }
      return { chatMessages: messages };
    });
  },
}));

// Set up Tauri event listeners for AI and file watcher events
let listenersInitialized = false;

export function initAiListeners() {
  if (listenersInitialized) return;
  listenersInitialized = true;

  // File watcher events
  listen<{ path: string; kind: string }>("file:changed", async (event) => {
    const { path, kind } = event.payload;
    const store = useStore.getState();
    const file = store.openFiles.find((f) => f.path === path);
    if (!file) return;

    // Don't reload if file has unsaved changes
    if (file.dirty) {
      // Mark as externally modified
      useStore.setState({
        openFiles: store.openFiles.map((f) =>
          f.path === path ? { ...f, externallyModified: true } : f,
        ),
      });
      const { showToast } = await import("../components/ui/Toast");
      const fileName = path.split("/").pop() || path;
      showToast("warning", `"${fileName}" changed on disk — reload or keep your changes`);
      return;
    }

    // Auto-reload if not dirty
    if (kind === "modified") {
      try {
        const content = await invoke<string>("read_file", { path });
        if (content !== file.content) {
          useStore.setState({
            openFiles: store.openFiles.map((f) =>
              f.path === path ? { ...f, content } : f,
            ),
          });
        }
      } catch {
        // File may have been deleted
      }
    }
  });

  listen<{ text: string }>("ai:response-chunk", (event) => {
    // Streaming chunks — update or create the last assistant message
    const store = useStore.getState();
    const messages = [...store.chatMessages];
    const lastMsg = messages[messages.length - 1];

    if (lastMsg && lastMsg.role === "assistant" && store.aiLoading) {
      messages[messages.length - 1] = {
        ...lastMsg,
        content: lastMsg.content + event.payload.text,
      };
      useStore.setState({ chatMessages: messages });
    } else if (store.aiLoading) {
      const newMsg: ChatMessage = {
        role: "assistant",
        content: event.payload.text,
        tool_calls: [],
        timestamp: Math.floor(Date.now() / 1000),
      };
      useStore.setState({ chatMessages: [...messages, newMsg] });
    }
  });

  listen<{ tool_call: ToolCall }>("ai:tool-start", (event) => {
    useStore.getState().updateToolCall(event.payload.tool_call);
  });

  listen<{ tool_call: ToolCall }>("ai:tool-done", (event) => {
    useStore.getState().updateToolCall(event.payload.tool_call);
  });

  listen<{ message: ChatMessage }>("ai:response-done", (_event) => {
    useStore.setState({ aiLoading: false });
  });

  listen<{ error: string }>("ai:error", (event) => {
    const errorMsg: ChatMessage = {
      role: "assistant",
      content: `Error: ${event.payload.error}`,
      tool_calls: [],
      timestamp: Math.floor(Date.now() / 1000),
    };
    const store = useStore.getState();
    useStore.setState({
      chatMessages: [...store.chatMessages, errorMsg],
      aiLoading: false,
    });
  });
}

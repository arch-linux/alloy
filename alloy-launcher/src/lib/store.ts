import { create } from "zustand";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import type {
  MinecraftProfile,
  AuthResult,
  LauncherSettings,
  DownloadProgress,
  LaunchState,
} from "./types";

interface AppStore {
  // Auth
  isAuthenticated: boolean;
  profile: MinecraftProfile | null;
  isLoggingIn: boolean;
  authError: string | null;

  // Launch
  launchState: LaunchState;
  downloadProgress: number;
  downloadMessage: string;
  gameError: string | null;

  // Settings
  settings: LauncherSettings;
  settingsLoaded: boolean;

  // Actions
  login: () => Promise<void>;
  logout: () => Promise<void>;
  checkAuth: () => Promise<void>;
  launchGame: () => Promise<void>;
  loadSettings: () => Promise<void>;
  updateSettings: (settings: LauncherSettings) => Promise<void>;
  initListeners: () => Promise<() => void>;
}

const DEFAULT_SETTINGS: LauncherSettings = {
  memory_mb: 2048,
  java_path: null,
  jvm_args: "",
  client_id: "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb",
  cache_dir: null,
  minecraft_version: "1.21.11",
};

export const useStore = create<AppStore>((set, get) => ({
  isAuthenticated: false,
  profile: null,
  isLoggingIn: false,
  authError: null,

  launchState: "ready",
  downloadProgress: 0,
  downloadMessage: "",
  gameError: null,

  settings: DEFAULT_SETTINGS,
  settingsLoaded: false,

  login: async () => {
    set({ isLoggingIn: true, authError: null });
    try {
      const result = await invoke<AuthResult>("login");
      if (result.success && result.profile) {
        set({
          isAuthenticated: true,
          profile: result.profile,
          isLoggingIn: false,
        });
      } else {
        set({
          isLoggingIn: false,
          authError: result.error || "Login failed",
        });
      }
    } catch (e) {
      set({ isLoggingIn: false, authError: String(e) });
    }
  },

  logout: async () => {
    try {
      await invoke("logout");
    } catch {
      // ignore
    }
    set({ isAuthenticated: false, profile: null, launchState: "ready" });
  },

  checkAuth: async () => {
    try {
      const result = await invoke<AuthResult>("check_auth");
      if (result.success && result.profile) {
        set({ isAuthenticated: true, profile: result.profile });
      }
    } catch {
      // Not authenticated, that's fine
    }
  },

  launchGame: async () => {
    set({ launchState: "preparing", gameError: null });
    try {
      await invoke("launch_game");
    } catch (e) {
      set({ launchState: "ready", gameError: String(e) });
    }
  },

  loadSettings: async () => {
    try {
      const settings = await invoke<LauncherSettings>("get_settings");
      set({ settings, settingsLoaded: true });
    } catch {
      set({ settingsLoaded: true });
    }
  },

  updateSettings: async (settings: LauncherSettings) => {
    try {
      await invoke("update_settings", { settings });
      set({ settings });
    } catch (e) {
      console.error("Failed to save settings:", e);
    }
  },

  initListeners: async () => {
    const unlistenProgress = await listen<DownloadProgress>(
      "download-progress",
      (event) => {
        set({
          downloadProgress: event.payload.percent,
          downloadMessage: event.payload.message,
        });
      },
    );

    const unlistenState = await listen<string>("launch-state", (event) => {
      set({ launchState: event.payload as LaunchState });
    });

    const unlistenError = await listen<string>("game-error", (event) => {
      set({ gameError: event.payload, launchState: "ready" });
    });

    return () => {
      unlistenProgress();
      unlistenState();
      unlistenError();
    };
  },
}));

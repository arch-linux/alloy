export type ModEnvironment = "client" | "server" | "both";

export type ProjectType = "mod" | "modpack" | "unknown";

export interface ProjectInfo {
  name: string;
  path: string;
  project_type: ProjectType;
  environment: ModEnvironment | null;
}

export interface RecentProject {
  name: string;
  path: string;
  project_type: ProjectType;
  last_opened: number;
}

export interface FileEntry {
  name: string;
  path: string;
  is_dir: boolean;
  extension: string | null;
  children?: FileEntry[];
  expanded?: boolean;
}

export interface OpenFile {
  path: string;
  name: string;
  content: string;
  language: string;
  dirty: boolean;
  preview?: boolean;
}

export type SidebarPanel = "files" | "search" | "git" | "extensions" | "tasks" | "ai";

export type SplitDirection = "none" | "horizontal" | "vertical";

export type BottomPanelTab = "terminal" | "problems" | "output";

export interface CursorPosition {
  line: number;
  column: number;
  selected?: number;
}

// Search types

export interface SearchResult {
  path: string;
  name: string;
  line_number: number;
  line_content: string;
}

// Quick Open types

export interface QuickOpenEntry {
  name: string;
  path: string;
  relative_path: string;
}

// Git types

export interface GitStatus {
  branch: string;
  files: GitFileStatus[];
}

export interface GitFileStatus {
  path: string;
  status: string;
}

// AI types

export type ChatRole = "user" | "assistant";

export type ToolCallStatus = "running" | "done" | "error";

export interface ToolCall {
  id: string;
  name: string;
  input: Record<string, unknown>;
  status: ToolCallStatus;
  result: string | null;
}

export interface ChatMessage {
  role: ChatRole;
  content: string;
  tool_calls: ToolCall[];
  timestamp: number;
}

export interface AiConfig {
  api_key: string | null;
  model: string;
  max_tokens: number;
}

// Build types

export interface BuildError {
  file: string;
  line: number;
  column: number;
  message: string;
  severity: string;
}

export interface BuildResult {
  success: boolean;
  errors: BuildError[];
  output: string;
}

// GUI Editor types

export type GuiWidgetType =
  | "slot"
  | "progress_bar"
  | "energy_bar"
  | "fluid_tank"
  | "button"
  | "label"
  | "region"
  | "image";

export interface GuiElement {
  id: string;
  type: GuiWidgetType;
  x: number;
  y: number;
  width: number;
  height: number;
  label?: string;
  properties: Record<string, unknown>;
}

export interface GuiProject {
  name: string;
  width: number;
  height: number;
  background_texture: string | null;
  elements: GuiElement[];
}

// Animation Editor types

export type EasingType = "linear" | "ease-in" | "ease-out" | "ease-in-out" | "cubic-bezier";

export type AnimPropertyType =
  | "uv_offset_x"
  | "uv_offset_y"
  | "opacity"
  | "color_r"
  | "color_g"
  | "color_b"
  | "sprite_frame"
  | "scale_x"
  | "scale_y"
  | "rotation";

export interface Keyframe {
  tick: number;
  value: number;
  easing: EasingType;
  bezier_handles?: [number, number, number, number];
}

export interface AnimationTrack {
  id: string;
  property: AnimPropertyType;
  target_element: string;
  keyframes: Keyframe[];
}

export interface AnimationProject {
  name: string;
  duration_ticks: number;
  tracks: AnimationTrack[];
  sprite_sheet: string | null;
  frame_width: number | null;
  frame_height: number | null;
}

// Modpack types

export interface ModpackMod {
  id: string;
  name: string;
  version: string;
  environment: ModEnvironment;
  source: "local" | "jar" | "git" | "hub";
  source_path: string | null;
  enabled: boolean;
  description: string | null;
}

export interface ModpackManifest {
  name: string;
  version: string;
  minecraft_version: string;
  alloy_version: string;
  mods: ModpackMod[];
}

// Editor settings

export interface EditorSettings {
  fontSize: number;
  tabSize: number;
  wordWrap: boolean;
  lineNumbers: boolean;
  minimap: boolean;
}

export interface MinecraftProfile {
  username: string;
  uuid: string;
  skin_url: string | null;
}

export interface AuthResult {
  success: boolean;
  profile: MinecraftProfile | null;
  error: string | null;
}

export interface LauncherSettings {
  memory_mb: number;
  java_path: string | null;
  jvm_args: string;
  client_id: string;
  cache_dir: string | null;
  minecraft_version: string;
}

export interface SetupStatus {
  is_cached: boolean;
  version_id: string;
  loader_found: boolean;
  java_found: boolean;
  java_path: string | null;
}

export interface DownloadProgress {
  stage: string;
  message: string;
  percent: number;
  bytes_downloaded: number;
  bytes_total: number;
}

export type LaunchState =
  | "ready"
  | "preparing"
  | "downloading"
  | "launching"
  | "running";

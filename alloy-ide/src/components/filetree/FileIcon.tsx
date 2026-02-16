import {
  Folder, FolderOpen, File, FileCode, FileJson, FileText, Image,
  FileType, Settings, Shield, Lock, Hash
} from "lucide-react";

interface FileIconProps {
  isDir: boolean;
  extension: string | null;
  expanded?: boolean;
  name?: string;
}

export default function FileIcon({ isDir, extension, expanded, name }: FileIconProps) {
  if (isDir) {
    const Icon = expanded ? FolderOpen : Folder;

    // Special folder colors
    const lowerName = name?.toLowerCase() || "";
    if (lowerName === "src" || lowerName === "main" || lowerName === "java") {
      return <Icon size={14} className="text-blue-400 shrink-0" />;
    }
    if (lowerName === "test" || lowerName === "tests") {
      return <Icon size={14} className="text-green-400 shrink-0" />;
    }
    if (lowerName === "resources" || lowerName === "assets") {
      return <Icon size={14} className="text-purple-400 shrink-0" />;
    }
    return <Icon size={14} className="text-forge-gold shrink-0" />;
  }

  // Special file names
  const lowerName = name?.toLowerCase() || "";
  if (lowerName === "alloy.mod.json" || lowerName === "alloy.pack.toml") {
    return <Shield size={14} className="text-ember shrink-0" />;
  }
  if (lowerName === "build.gradle" || lowerName === "build.gradle.kts" || lowerName === "settings.gradle.kts") {
    return <Settings size={14} className="text-green-400 shrink-0" />;
  }
  if (lowerName === "gradlew" || lowerName === "gradlew.bat") {
    return <Hash size={14} className="text-green-400 shrink-0" />;
  }
  if (lowerName === ".gitignore" || lowerName === ".gitattributes") {
    return <FileText size={14} className="text-stone-500 shrink-0" />;
  }
  if (lowerName === "cargo.toml" || lowerName === "cargo.lock") {
    return <Settings size={14} className="text-orange-400 shrink-0" />;
  }
  if (lowerName === "package.json" || lowerName === "tsconfig.json") {
    return <Settings size={14} className="text-yellow-400 shrink-0" />;
  }
  if (lowerName.includes("license") || lowerName.includes("licence")) {
    return <Lock size={14} className="text-stone-400 shrink-0" />;
  }

  switch (extension) {
    // Java
    case "java":
      return <FileCode size={14} className="text-orange-400 shrink-0" />;
    // Web
    case "ts":
    case "tsx":
      return <FileCode size={14} className="text-blue-400 shrink-0" />;
    case "js":
    case "jsx":
      return <FileCode size={14} className="text-yellow-300 shrink-0" />;
    case "css":
      return <FileType size={14} className="text-blue-300 shrink-0" />;
    case "html":
      return <FileCode size={14} className="text-orange-300 shrink-0" />;
    // Data formats
    case "json":
      return <FileJson size={14} className="text-yellow-400 shrink-0" />;
    case "xml":
      return <FileCode size={14} className="text-red-400 shrink-0" />;
    case "toml":
    case "yml":
    case "yaml":
      return <FileCode size={14} className="text-pink-400 shrink-0" />;
    case "properties":
      return <Settings size={14} className="text-stone-400 shrink-0" />;
    // Build
    case "gradle":
    case "kts":
      return <FileCode size={14} className="text-green-400 shrink-0" />;
    // Rust
    case "rs":
      return <FileCode size={14} className="text-orange-400 shrink-0" />;
    // Python
    case "py":
      return <FileCode size={14} className="text-green-400 shrink-0" />;
    // Shell
    case "sh":
    case "bash":
    case "zsh":
    case "bat":
    case "cmd":
      return <Hash size={14} className="text-stone-400 shrink-0" />;
    // Images
    case "png":
    case "jpg":
    case "jpeg":
    case "gif":
    case "svg":
    case "ico":
    case "webp":
      return <Image size={14} className="text-purple-400 shrink-0" />;
    // Docs
    case "md":
      return <FileText size={14} className="text-blue-400 shrink-0" />;
    case "txt":
    case "log":
      return <FileText size={14} className="text-stone-400 shrink-0" />;
    default:
      return <File size={14} className="text-stone-500 shrink-0" />;
  }
}

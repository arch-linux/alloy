import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import {
  FolderOpen,
  Plus,
  Clock,
  Hammer,
  Shield,
  ChevronRight,
  Monitor,
  Server,
  Layers,
  Package,
  ArrowLeft,
  Sparkles,
} from "lucide-react";
import { open } from "@tauri-apps/plugin-dialog";
import { invoke } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { useStore } from "../lib/store";
import type { ProjectInfo, RecentProject } from "../lib/types";
import GridOverlay from "../components/effects/GridOverlay";
import EmberGlow from "../components/effects/EmberGlow";
import alloyLogo from "../assets/alloy-logo.svg";

type Screen = "home" | "new-project";
type ProjectTypeChoice = "mod" | "modpack";
type EnvChoice = "client" | "server" | "both";

export default function WelcomePage() {
  const navigate = useNavigate();
  const appWindow = getCurrentWindow();
  const recentProjects = useStore((s) => s.recentProjects);
  const loadRecentProjects = useStore((s) => s.loadRecentProjects);
  const openProject = useStore((s) => s.openProject);

  const [screen, setScreen] = useState<Screen>("home");

  // New project form
  const [projectType, setProjectType] = useState<ProjectTypeChoice>("mod");
  const [projectName, setProjectName] = useState("");
  const [modId, setModId] = useState("");
  const [packageName, setPackageName] = useState("com.example");
  const [environment, setEnvironment] = useState<EnvChoice>("both");
  const [projectLocation, setProjectLocation] = useState("");
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    loadRecentProjects();
  }, [loadRecentProjects]);

  // Auto-generate mod ID from project name
  useEffect(() => {
    if (projectName) {
      setModId(projectName.toLowerCase().replace(/[^a-z0-9]/g, ""));
    }
  }, [projectName]);

  const handleOpenFolder = async () => {
    const selected = await open({ directory: true, multiple: false });
    if (selected && typeof selected === "string") {
      await openProject(selected);
      navigate("/editor", { replace: true });
    }
  };

  const handleOpenRecent = async (path: string) => {
    await openProject(path);
    navigate("/editor", { replace: true });
  };

  const handlePickLocation = async () => {
    const selected = await open({ directory: true, multiple: false });
    if (selected && typeof selected === "string") {
      setProjectLocation(selected);
    }
  };

  const handleCreateProject = async () => {
    if (!projectName.trim()) {
      setError("Project name is required");
      return;
    }
    if (!projectLocation) {
      setError("Choose a location for your project");
      return;
    }
    if (projectType === "mod" && !modId.trim()) {
      setError("Mod ID is required");
      return;
    }

    setCreating(true);
    setError("");

    try {
      await invoke<ProjectInfo>("create_project", {
        args: {
          name: projectName.trim(),
          path: projectLocation,
          project_type: projectType,
          environment,
          mod_id: modId.trim() || null,
          package_name: packageName.trim() || null,
        },
      });
      // Open the newly created project
      const fullPath = projectLocation + "/" + projectName.trim();
      await openProject(fullPath);
      navigate("/editor", { replace: true });
    } catch (err) {
      setError(String(err));
      setCreating(false);
    }
  };

  const formatTime = (timestamp: number) => {
    const date = new Date(timestamp * 1000);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins < 1) return "just now";
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  };

  const projectTypeBadge = (type: string) => {
    switch (type) {
      case "mod":
        return (
          <span className="flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[9px] font-medium bg-ember/15 text-ember border border-ember/30">
            <Shield size={8} /> MOD
          </span>
        );
      case "modpack":
        return (
          <span className="flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[9px] font-medium bg-forge-gold/15 text-forge-gold border border-forge-gold/30">
            <Hammer size={8} /> MODPACK
          </span>
        );
      default:
        return (
          <span className="px-1.5 py-0.5 rounded text-[9px] font-medium bg-obsidian-700 text-stone-400 border border-obsidian-600">
            PROJECT
          </span>
        );
    }
  };

  return (
    <div className="flex h-screen w-screen flex-col bg-obsidian-950">
      {/* Title bar */}
      <div
        data-tauri-drag-region
        className="flex h-8 shrink-0 items-center justify-between bg-obsidian-950 border-b border-obsidian-700 select-none"
        onDoubleClick={() => appWindow.toggleMaximize()}
      >
        <div data-tauri-drag-region className="flex items-center gap-2 flex-1 pl-3 pointer-events-none">
          <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" className="h-4 w-4">
            <path d="M20 4L6 14v4l14 10 14-10v-4L20 4z" fill="url(#wp-g)" />
            <path d="M20 18L6 14v4l14 10V18z" fill="#e06000" />
            <path d="M20 18l14-4v4L20 28V18z" fill="#cc5500" />
            <circle cx="20" cy="12" r="2" fill="#f0b830" />
            <defs>
              <linearGradient id="wp-g" x1="6" y1="4" x2="34" y2="28">
                <stop stopColor="#ff9a4a" />
                <stop offset="1" stopColor="#f04800" />
              </linearGradient>
            </defs>
          </svg>
          <span className="text-xs font-medium text-stone-500 font-heading">Alloy IDE</span>
        </div>
        <div className="flex">
          <button onClick={() => appWindow.minimize()} className="flex h-8 w-11 items-center justify-center text-stone-500 hover:bg-obsidian-800 hover:text-stone-300 transition-colors">
            <svg width="10" height="1" viewBox="0 0 10 1" fill="currentColor"><rect width="10" height="1" /></svg>
          </button>
          <button onClick={() => appWindow.toggleMaximize()} className="flex h-8 w-11 items-center justify-center text-stone-500 hover:bg-obsidian-800 hover:text-stone-300 transition-colors">
            <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1"><rect x="0.5" y="0.5" width="9" height="9" /></svg>
          </button>
          <button onClick={() => appWindow.close()} className="flex h-8 w-11 items-center justify-center text-stone-500 hover:bg-red-600 hover:text-white transition-colors">
            <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.2"><line x1="1" y1="1" x2="9" y2="9" /><line x1="9" y1="1" x2="1" y2="9" /></svg>
          </button>
        </div>
      </div>

      {/* Main content */}
      <div className="relative flex flex-1 overflow-hidden">
        <GridOverlay />
        <EmberGlow />

        {screen === "home" ? (
          <HomeScreen
            recentProjects={recentProjects}
            onNewProject={() => setScreen("new-project")}
            onOpenFolder={handleOpenFolder}
            onOpenRecent={handleOpenRecent}
            formatTime={formatTime}
            projectTypeBadge={projectTypeBadge}
          />
        ) : (
          <NewProjectScreen
            projectType={projectType}
            setProjectType={setProjectType}
            projectName={projectName}
            setProjectName={setProjectName}
            modId={modId}
            setModId={setModId}
            packageName={packageName}
            setPackageName={setPackageName}
            environment={environment}
            setEnvironment={setEnvironment}
            projectLocation={projectLocation}
            onPickLocation={handlePickLocation}
            creating={creating}
            error={error}
            onBack={() => { setScreen("home"); setError(""); }}
            onCreate={handleCreateProject}
          />
        )}
      </div>
    </div>
  );
}

/* ─── Home Screen ─── */

function HomeScreen({
  recentProjects,
  onNewProject,
  onOpenFolder,
  onOpenRecent,
  formatTime,
  projectTypeBadge,
}: {
  recentProjects: RecentProject[];
  onNewProject: () => void;
  onOpenFolder: () => void;
  onOpenRecent: (path: string) => void;
  formatTime: (ts: number) => string;
  projectTypeBadge: (type: string) => React.ReactNode;
}) {
  return (
    <div className="relative z-10 flex flex-1 items-center justify-center">
      <div className="flex flex-col items-center gap-10 animate-fade-in-up max-w-[720px] w-full px-8">
        {/* Branding */}
        <div className="flex flex-col items-center gap-3">
          <img src={alloyLogo} alt="Alloy IDE" className="h-20 w-20 animate-float" />
          <h1 className="font-heading text-4xl font-bold text-stone-100 text-glow">
            Alloy IDE
          </h1>
          <p className="text-sm text-stone-500">Where Mods Are Forged</p>
        </div>

        {/* Action cards */}
        <div className="flex gap-4 w-full max-w-[480px]">
          <button
            onClick={onNewProject}
            className="flex-1 group flex flex-col items-center gap-3 rounded-xl border border-obsidian-600 bg-obsidian-800/60 backdrop-blur-sm p-6 transition-all duration-300 hover:border-ember/30 hover:shadow-[0_0_40px_rgba(255,107,0,0.06)] cursor-pointer"
          >
            <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-ember/10 text-ember group-hover:bg-ember/20 transition-colors">
              <Plus size={24} />
            </div>
            <div className="text-center">
              <div className="text-sm font-medium text-stone-200 group-hover:text-stone-100">New Project</div>
              <div className="text-xs text-stone-500 mt-1">Create a mod or modpack</div>
            </div>
          </button>

          <button
            onClick={onOpenFolder}
            className="flex-1 group flex flex-col items-center gap-3 rounded-xl border border-obsidian-600 bg-obsidian-800/60 backdrop-blur-sm p-6 transition-all duration-300 hover:border-ember/30 hover:shadow-[0_0_40px_rgba(255,107,0,0.06)] cursor-pointer"
          >
            <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-ember/10 text-ember group-hover:bg-ember/20 transition-colors">
              <FolderOpen size={24} />
            </div>
            <div className="text-center">
              <div className="text-sm font-medium text-stone-200 group-hover:text-stone-100">Open Folder</div>
              <div className="text-xs text-stone-500 mt-1">Open an existing project</div>
            </div>
          </button>
        </div>

        {/* Recent projects */}
        <div className="w-full max-w-[480px]">
          <div className="flex items-center gap-2 mb-3">
            <Clock size={12} className="text-stone-500" />
            <span className="text-[11px] text-stone-500 uppercase tracking-wider font-semibold">
              Recent Projects
            </span>
          </div>

          {recentProjects.length === 0 ? (
            <div className="text-xs text-stone-600 py-6 text-center">
              No recent projects yet. Create or open a project to get started.
            </div>
          ) : (
            <div className="flex flex-col gap-0.5">
              {[...recentProjects]
                .sort((a, b) => b.last_opened - a.last_opened)
                .slice(0, 6)
                .map((p) => (
                <button
                  key={p.path}
                  onClick={() => onOpenRecent(p.path)}
                  className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-left text-xs hover:bg-obsidian-800/80 transition-colors group cursor-pointer"
                >
                  {p.project_type === "mod" ? (
                    <Shield size={14} className="text-ember/50 group-hover:text-ember shrink-0 transition-colors" />
                  ) : p.project_type === "modpack" ? (
                    <Package size={14} className="text-forge-gold/50 group-hover:text-forge-gold shrink-0 transition-colors" />
                  ) : (
                    <FolderOpen size={14} className="text-stone-600 group-hover:text-ember/70 shrink-0 transition-colors" />
                  )}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-stone-200 font-medium truncate group-hover:text-stone-100">
                        {p.name}
                      </span>
                      {projectTypeBadge(p.project_type)}
                    </div>
                    <div className="text-[10px] text-stone-600 truncate mt-0.5 font-mono">
                      {p.path}
                    </div>
                  </div>
                  <span className="text-[10px] text-stone-600 shrink-0">
                    {formatTime(p.last_opened)}
                  </span>
                  <ChevronRight size={12} className="text-stone-700 group-hover:text-stone-500 shrink-0 transition-colors" />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="text-[10px] text-stone-600 flex items-center gap-1.5">
          <Sparkles size={10} className="text-ember/40" />
          Alloy IDE v0.1.0 — alloymc.net
        </div>
      </div>
    </div>
  );
}

/* ─── New Project Screen ─── */

function NewProjectScreen({
  projectType,
  setProjectType,
  projectName,
  setProjectName,
  modId,
  setModId,
  packageName,
  setPackageName,
  environment,
  setEnvironment,
  projectLocation,
  onPickLocation,
  creating,
  error,
  onBack,
  onCreate,
}: {
  projectType: ProjectTypeChoice;
  setProjectType: (t: ProjectTypeChoice) => void;
  projectName: string;
  setProjectName: (s: string) => void;
  modId: string;
  setModId: (s: string) => void;
  packageName: string;
  setPackageName: (s: string) => void;
  environment: EnvChoice;
  setEnvironment: (e: EnvChoice) => void;
  projectLocation: string;
  onPickLocation: () => void;
  creating: boolean;
  error: string;
  onBack: () => void;
  onCreate: () => void;
}) {
  const envOptions: { value: EnvChoice; label: string; desc: string; icon: React.ReactNode }[] = [
    { value: "client", label: "Client", desc: "Runs on the player's game only", icon: <Monitor size={14} /> },
    { value: "server", label: "Server", desc: "Runs on the server only", icon: <Server size={14} /> },
    { value: "both", label: "Both", desc: "Runs on both client and server", icon: <Layers size={14} /> },
  ];

  return (
    <div className="relative z-10 flex flex-1 items-center justify-center">
      <div className="flex flex-col gap-6 animate-fade-in-up w-full max-w-[520px] px-8">
        {/* Header */}
        <div className="flex items-center gap-3">
          <button onClick={onBack} className="flex h-8 w-8 items-center justify-center rounded-lg text-stone-400 hover:text-stone-200 hover:bg-obsidian-800 transition-colors cursor-pointer">
            <ArrowLeft size={16} />
          </button>
          <div>
            <h2 className="font-heading text-xl font-semibold text-stone-100">New Project</h2>
            <p className="text-xs text-stone-500 mt-0.5">Set up a new Alloy project</p>
          </div>
        </div>

        {/* Project type selector */}
        <div className="flex gap-3">
          <button
            onClick={() => setProjectType("mod")}
            className={
              "flex-1 flex items-center gap-3 rounded-lg border p-4 transition-all cursor-pointer " +
              (projectType === "mod"
                ? "border-ember/50 bg-ember/5"
                : "border-obsidian-600 bg-obsidian-800/40 hover:border-obsidian-500")
            }
          >
            <Shield size={20} className={projectType === "mod" ? "text-ember" : "text-stone-500"} />
            <div className="text-left">
              <div className={"text-sm font-medium " + (projectType === "mod" ? "text-ember" : "text-stone-300")}>
                Mod
              </div>
              <div className="text-[10px] text-stone-500">Blocks, items, GUIs, events</div>
            </div>
          </button>
          <button
            onClick={() => setProjectType("modpack")}
            className={
              "flex-1 flex items-center gap-3 rounded-lg border p-4 transition-all cursor-pointer " +
              (projectType === "modpack"
                ? "border-forge-gold/50 bg-forge-gold/5"
                : "border-obsidian-600 bg-obsidian-800/40 hover:border-obsidian-500")
            }
          >
            <Package size={20} className={projectType === "modpack" ? "text-forge-gold" : "text-stone-500"} />
            <div className="text-left">
              <div className={"text-sm font-medium " + (projectType === "modpack" ? "text-forge-gold" : "text-stone-300")}>
                Modpack
              </div>
              <div className="text-[10px] text-stone-500">Bundle mods for distribution</div>
            </div>
          </button>
        </div>

        {/* Form fields */}
        <div className="flex flex-col gap-4">
          {/* Project name */}
          <div>
            <label className="block text-[11px] text-stone-400 uppercase tracking-wider font-semibold mb-1.5">
              Project Name
            </label>
            <input
              type="text"
              value={projectName}
              onChange={(e) => setProjectName(e.target.value)}
              placeholder={projectType === "mod" ? "My Awesome Mod" : "My Modpack"}
              className="w-full rounded-lg bg-obsidian-900 border border-obsidian-600 px-3 py-2.5 text-sm text-stone-200 placeholder-stone-600 focus:outline-none focus:border-ember/50 focus:ring-1 focus:ring-ember/20 transition-colors"
            />
          </div>

          {/* Mod-specific fields */}
          {projectType === "mod" && (
            <>
              <div className="flex gap-3">
                <div className="flex-1">
                  <label className="block text-[11px] text-stone-400 uppercase tracking-wider font-semibold mb-1.5">
                    Mod ID
                  </label>
                  <input
                    type="text"
                    value={modId}
                    onChange={(e) => setModId(e.target.value.toLowerCase().replace(/[^a-z0-9]/g, ""))}
                    placeholder="mymod"
                    className="w-full rounded-lg bg-obsidian-900 border border-obsidian-600 px-3 py-2.5 text-sm text-stone-200 placeholder-stone-600 font-mono focus:outline-none focus:border-ember/50 focus:ring-1 focus:ring-ember/20 transition-colors"
                  />
                </div>
                <div className="flex-1">
                  <label className="block text-[11px] text-stone-400 uppercase tracking-wider font-semibold mb-1.5">
                    Package
                  </label>
                  <input
                    type="text"
                    value={packageName}
                    onChange={(e) => setPackageName(e.target.value)}
                    placeholder="com.example"
                    className="w-full rounded-lg bg-obsidian-900 border border-obsidian-600 px-3 py-2.5 text-sm text-stone-200 placeholder-stone-600 font-mono focus:outline-none focus:border-ember/50 focus:ring-1 focus:ring-ember/20 transition-colors"
                  />
                </div>
              </div>

              {/* Environment selector */}
              <div>
                <label className="block text-[11px] text-stone-400 uppercase tracking-wider font-semibold mb-1.5">
                  Environment
                </label>
                <div className="flex gap-2">
                  {envOptions.map((opt) => (
                    <button
                      key={opt.value}
                      onClick={() => setEnvironment(opt.value)}
                      className={
                        "flex-1 flex flex-col items-center gap-1.5 rounded-lg border p-3 transition-all cursor-pointer " +
                        (environment === opt.value
                          ? "border-ember/50 bg-ember/5"
                          : "border-obsidian-600 bg-obsidian-800/40 hover:border-obsidian-500")
                      }
                    >
                      <div className={environment === opt.value ? "text-ember" : "text-stone-500"}>
                        {opt.icon}
                      </div>
                      <div className={"text-xs font-medium " + (environment === opt.value ? "text-ember" : "text-stone-400")}>
                        {opt.label}
                      </div>
                      <div className="text-[9px] text-stone-600 text-center leading-tight">
                        {opt.desc}
                      </div>
                    </button>
                  ))}
                </div>
              </div>
            </>
          )}

          {/* Location */}
          <div>
            <label className="block text-[11px] text-stone-400 uppercase tracking-wider font-semibold mb-1.5">
              Location
            </label>
            <div className="flex gap-2">
              <div className="flex-1 rounded-lg bg-obsidian-900 border border-obsidian-600 px-3 py-2.5 text-sm text-stone-400 font-mono truncate">
                {projectLocation || "Choose a folder..."}
              </div>
              <button
                onClick={onPickLocation}
                className="rounded-lg bg-obsidian-700 border border-obsidian-600 px-4 py-2.5 text-sm text-stone-300 hover:bg-obsidian-600 hover:border-obsidian-500 transition-colors cursor-pointer"
              >
                Browse
              </button>
            </div>
            {projectLocation && projectName && (
              <div className="mt-1.5 text-[10px] text-stone-600 font-mono">
                Will create: {projectLocation}/{projectName}
              </div>
            )}
          </div>
        </div>

        {/* Error */}
        {error && (
          <div className="text-xs text-red-400 bg-red-400/5 border border-red-400/20 rounded-lg px-3 py-2">
            {error}
          </div>
        )}

        {/* Create button */}
        <button
          onClick={onCreate}
          disabled={creating || !projectName.trim() || !projectLocation}
          className={
            "w-full rounded-lg py-3 text-sm font-semibold transition-all duration-200 cursor-pointer " +
            "bg-gradient-to-r from-ember to-ember-dark text-obsidian-950 " +
            "hover:from-ember-light hover:to-ember shadow-[0_0_24px_rgba(255,107,0,0.3)] " +
            "hover:shadow-[0_0_32px_rgba(255,107,0,0.5)] active:scale-[0.98] " +
            "disabled:opacity-40 disabled:pointer-events-none disabled:shadow-none"
          }
        >
          {creating ? "Creating..." : `Create ${projectType === "mod" ? "Mod" : "Modpack"} Project`}
        </button>
      </div>
    </div>
  );
}

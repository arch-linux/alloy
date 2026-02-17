/**
 * Environment intelligence for Alloy mod development.
 *
 * Enforces client/server/both boundaries:
 * - Server mods cannot use client-only API
 * - Client mods cannot use server-only API
 * - "both" mods get warnings for unsafely accessing side-specific APIs
 */

import type { ModEnvironment } from "./types";

/** Classes/packages that are client-only */
const CLIENT_ONLY_PACKAGES = [
  "net.alloymc.api.client",
  "net.alloymc.api.render",
  "net.alloymc.api.gui",
  "net.alloymc.api.hud",
  "net.alloymc.api.particle",
  "net.alloymc.api.sound",
  "net.alloymc.api.texture",
  "net.alloymc.api.model",
  "net.alloymc.api.shader",
  "net.alloymc.api.input",
];

/** Classes/packages that are server-only */
const SERVER_ONLY_PACKAGES = [
  "net.alloymc.api.server",
  "net.alloymc.api.permission",
  "net.alloymc.api.network.server",
  "net.alloymc.api.world.gen",
  "net.alloymc.api.command.server",
];

/** Specific class names that are client-only */
const CLIENT_ONLY_CLASSES = new Set([
  "AlloyRenderer",
  "ScreenRenderer",
  "GuiScreen",
  "GuiElement",
  "GuiContainer",
  "HudOverlay",
  "HudElement",
  "ParticleEmitter",
  "ParticleRenderer",
  "SoundManager",
  "TextureAtlas",
  "ModelLoader",
  "BlockModel",
  "ItemModel",
  "EntityModel",
  "ShaderProgram",
  "KeyBinding",
  "MouseHandler",
  "ClientTickHandler",
]);

/** Specific class names that are server-only */
const SERVER_ONLY_CLASSES = new Set([
  "ServerTickHandler",
  "PermissionManager",
  "ServerCommand",
  "WorldGenerator",
  "ChunkGenerator",
  "BiomeProvider",
  "StructureGenerator",
  "ServerNetworkHandler",
]);

export interface EnvironmentDiagnostic {
  line: number;
  column: number;
  message: string;
  severity: "error" | "warning";
  importText: string;
}

/**
 * Analyze a Java file for environment violations.
 * Returns diagnostics for imports that violate the mod's environment.
 */
export function analyzeEnvironment(
  content: string,
  environment: ModEnvironment | null,
): EnvironmentDiagnostic[] {
  if (!environment || environment === "both") {
    // "both" mods get warnings (not errors) for direct usage of side-specific APIs
    // null environment = can't determine, skip
    if (environment === "both") {
      return analyzeBothEnvironment(content);
    }
    return [];
  }

  const diagnostics: EnvironmentDiagnostic[] = [];
  const lines = content.split("\n");

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const importMatch = line.match(/^\s*import\s+([\w.]+)\s*;/);
    if (!importMatch) continue;

    const importPath = importMatch[1];
    const className = importPath.split(".").pop() || "";

    if (environment === "server") {
      // Server mods cannot use client-only APIs
      const isClientImport =
        CLIENT_ONLY_PACKAGES.some((pkg) => importPath.startsWith(pkg)) ||
        CLIENT_ONLY_CLASSES.has(className);

      if (isClientImport) {
        const col = line.indexOf("import") + 1;
        diagnostics.push({
          line: i + 1,
          column: col,
          message: `Server mod cannot import client-only class "${className}". This class is only available on the client side. Check your alloy.mod.json environment setting.`,
          severity: "error",
          importText: importPath,
        });
      }
    } else if (environment === "client") {
      // Client mods cannot use server-only APIs
      const isServerImport =
        SERVER_ONLY_PACKAGES.some((pkg) => importPath.startsWith(pkg)) ||
        SERVER_ONLY_CLASSES.has(className);

      if (isServerImport) {
        const col = line.indexOf("import") + 1;
        diagnostics.push({
          line: i + 1,
          column: col,
          message: `Client mod cannot import server-only class "${className}". This class is only available on the server side. Check your alloy.mod.json environment setting.`,
          severity: "error",
          importText: importPath,
        });
      }
    }
  }

  return diagnostics;
}

/**
 * For "both" mods, warn about direct usage of side-specific APIs
 * without proper side-gating.
 */
function analyzeBothEnvironment(content: string): EnvironmentDiagnostic[] {
  const diagnostics: EnvironmentDiagnostic[] = [];
  const lines = content.split("\n");

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const importMatch = line.match(/^\s*import\s+([\w.]+)\s*;/);
    if (!importMatch) continue;

    const importPath = importMatch[1];
    const className = importPath.split(".").pop() || "";

    const isClientImport =
      CLIENT_ONLY_PACKAGES.some((pkg) => importPath.startsWith(pkg)) ||
      CLIENT_ONLY_CLASSES.has(className);

    const isServerImport =
      SERVER_ONLY_PACKAGES.some((pkg) => importPath.startsWith(pkg)) ||
      SERVER_ONLY_CLASSES.has(className);

    if (isClientImport) {
      const col = line.indexOf("import") + 1;
      diagnostics.push({
        line: i + 1,
        column: col,
        message: `"${className}" is client-only. In a universal mod, wrap usage in a client-side check (e.g., if (AlloyEnvironment.isClient()) { ... }).`,
        severity: "warning",
        importText: importPath,
      });
    }

    if (isServerImport) {
      const col = line.indexOf("import") + 1;
      diagnostics.push({
        line: i + 1,
        column: col,
        message: `"${className}" is server-only. In a universal mod, wrap usage in a server-side check (e.g., if (AlloyEnvironment.isServer()) { ... }).`,
        severity: "warning",
        importText: importPath,
      });
    }
  }

  return diagnostics;
}

/**
 * Check if visual editors (GUI, Animation) should be available
 * based on the mod's environment.
 */
export function isVisualEditorAvailable(environment: ModEnvironment | null): boolean {
  // Server-only mods don't have GUIs or animations
  return environment !== "server";
}

/**
 * Get the reason why visual editors are unavailable.
 */
export function getVisualEditorUnavailableReason(environment: ModEnvironment | null): string | null {
  if (environment === "server") {
    return "GUI and Animation editors are disabled for server-only mods. Server mods cannot render client-side visuals.";
  }
  return null;
}

/**
 * Filter available Alloy templates based on environment.
 */
export function filterTemplatesForEnvironment(
  templateIds: string[],
  environment: ModEnvironment | null,
): string[] {
  if (environment === "server") {
    // Server mods can't use GUI or animation templates
    return templateIds.filter((id) => id !== "gui_screen" && id !== "animation");
  }
  return templateIds;
}

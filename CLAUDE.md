# Alloy — A New Era of Minecraft Modding

## Project Overview

Alloy is a from-scratch Minecraft modding ecosystem. Not a fork, not built on Forge, Fabric, or any existing loader. Every layer — from bytecode manipulation to the launcher — is ours.

**Domain:** alloymc.net
**Tagline:** "Forged with Alloy"

## Philosophy

1. **Future-proof above all.** Minecraft updates should NEVER require modders to rewrite their mods. The API is a firewall between modders and Minecraft internals.
2. **Own everything.** No dependence on third-party modding ecosystems (Forge, Fabric, NeoForge, Quilt). We use foundational libraries (ASM for bytecode, etc.) but the loader, API, mappings pipeline, pack format, and launcher are all ours.
3. **Minimal by design.** Don't accumulate backwards-compatibility debt. Don't over-abstract. Don't design for hypothetical futures — design for clean evolution.
4. **Modern stack.** No legacy patterns from 1.7.10. Target current and future Minecraft versions only.
5. **Cross-platform.** Develop on macOS, cross-compile to Windows and Linux. Java is inherently portable — leverage that.
6. **Built for developers, not machines.** Every API, every abstraction, every pattern must be intuitive to a human Java developer. Clear naming, obvious control flow, minimal magic. A modder should be able to read the API and understand it without a tutorial. If it needs a wall of documentation to explain, the API is wrong.
7. **Automation is non-negotiable.** Every step of the pipeline — fetching Minecraft JARs, applying mappings, deobfuscation, remapping, building against new versions — MUST be fully automated via Gradle tasks and scripts. Zero manual steps. A developer clones the repo, runs a single command, and everything resolves. When Mojang drops a new version, updating should be one command or one CI trigger, not a week of manual work.
8. **This sets the new standard.** Every decision should be made with the understanding that this project will be scrutinized by the entire Minecraft modding community. Code quality, API design, documentation, developer experience — all of it must be best-in-class. We are not building "another mod loader." We are building the one that makes everything before it obsolete.

## Non-Negotiable Standards

- **No magic.** If a modder can't trace what their code does by reading it, the abstraction is too clever. Prefer explicit over implicit. No annotation processors that generate invisible code. No classpath scanning surprises.
- **Errors must be actionable.** Every error message must tell the developer: what went wrong, why, and what to do about it. Stack traces alone are not acceptable. Wrap them with context.
- **Modular by default.** Components must be independently understandable. A developer working on the event bus shouldn't need to understand the bytecode injection system. Clean interfaces between layers.
- **The build is the source of truth.** `./gradlew build` must do everything: fetch Minecraft, apply mappings, compile, test. If it's not in the build, it doesn't exist. No "oh you also need to run this other script first" — that's how projects rot.

## Unified Build Tool — anvil

The `anvil` script at the repo root builds any combination of targets:

```bash
./anvil --all                    # Build everything
./anvil --ide --dev              # Launch the IDE in dev mode
./anvil --launcher --dev         # Launch the launcher in dev mode
./anvil --client --api           # Build Java client + API
./anvil --java                   # Build all Java modules
./anvil --apps                   # Build both Tauri apps
./anvil --java --clean           # Clean + rebuild all Java
./anvil --xxWeLoveClaudexx       # :)
```

**Dev ports:** Launcher = 1420, IDE = 1425 (can run simultaneously)

## Architecture

> **IMPORTANT: This repo contains TWO Tauri apps. They are DIFFERENT applications.**
> - `alloy-launcher/` — Game launcher for PLAYERS (auth, download, launch Minecraft)
> - `alloy-ide/` — Mod development IDE for DEVELOPERS (code editor, GUI builder, terminal)
> Do NOT confuse them. They have different purposes, dependencies, and app identifiers.

```
alloy/                          — Monorepo: the entire Alloy ecosystem
├── anvil                       — Unified build script (./anvil --help)
├── alloy-mappings/             — Deobfuscation pipeline: fetches Mojang maps, remaps JARs
├── alloy-loader/               — Mod loader: classloading, bytecode injection, mod lifecycle
├── alloy-api/                  — Modding API: events, registries, hooks (what modders code against)
├── alloy-core/                 — Core modding infrastructure
├── alloy-client/               — Client-side runtime
├── alloy-packs/                — Modpack format spec & tooling
├── alloy-installer/            — Installs Alloy into a Minecraft profile
├── alloy-launcher/             — GAME LAUNCHER: Tauri app (auth, download, launch MC) [port 1420]
├── alloy-ide/                  — MODDING IDE: Tauri app (editor, GUI builder, terminal) [port 1425]
├── alloy-hub/                  — Backend API for mod/pack distribution (future)
├── docs/                       — Internal documentation
├── build.gradle.kts            — Root Gradle config (Java modules only)
└── settings.gradle.kts         — Gradle module includes

alloymc-web/                    — Separate repo: website (alloymc.net)
```

### Two Tauri Apps — Quick Reference

| | Alloy Launcher | Alloy IDE |
|---|---|---|
| **Purpose** | Launch Minecraft with Alloy | Develop Alloy mods |
| **Users** | Players | Mod developers |
| **Directory** | `alloy-launcher/` | `alloy-ide/` |
| **App ID** | `net.alloymc.launcher` | `net.alloymc.ide` |
| **Dev port** | 1420 | 1425 |
| **Dev command** | `./anvil --launcher --dev` | `./anvil --ide --dev` |
| **Build command** | `./anvil --launcher` | `./anvil --ide` |
| **Key deps** | keyring, auth, download | CodeMirror 6, xterm.js, MCP |
| **Window** | 1024x640 | 1280x800 |

### Component Responsibilities

**alloy-mappings** (Foundation — build this first)
- Fetches Mojang's version manifest from piston-meta
- Downloads client.jar and official mappings (ProGuard format)
- Parses and applies mappings to deobfuscate the client JAR
- Produces a remapped JAR with human-readable class/method/field names
- Automates this per Minecraft version — when Mojang releases a new version, mappings update with zero manual work

**alloy-loader** (Core runtime)
- Bootstraps before Minecraft's main class
- Custom classloader that loads mod JARs
- Bytecode injection via ASM (ObjectWeb ASM 9.x)
- Mixin-style injection system (our own implementation, not SpongePowered Mixin)
- Mod discovery, dependency resolution, load ordering
- Lifecycle hooks: pre-init, init, post-init, per-tick, shutdown

**alloy-api** (What modders use)
- Event bus: stable event types that survive MC updates (onBlockBreak, onEntitySpawn, etc.)
- Registry system: items, blocks, entities, biomes, dimensions
- Networking: custom packet registration and handling
- Configuration: per-mod config with hot-reload
- CRITICAL: modders NEVER import Minecraft classes directly. They go through Alloy's API.
  If Mojang renames or refactors something, Alloy's mappings + API absorb the change.

**alloy-packs** (Modpack format)
- Declarative pack definition (TOML or JSON manifest)
- Version pinning for mods, Minecraft version, Alloy version
- Dependency declaration and conflict detection
- Server-side and client-side mod distinction
- Export/import tooling

**alloy-installer**
- Standalone JAR that installs Alloy into the vanilla Minecraft launcher
- Creates a new launcher profile with Alloy's launch arguments
- Downloads required libraries

**alloy-launcher** (Desktop app — later phase)
- Built with Tauri (Rust backend, web frontend)
- Browse, install, update modpacks
- Instance management (multiple MC versions/packs)
- Mod configuration UI
- Auto-updates
- NOT Electron. Small, fast, native.

## Tech Stack

| Component | Technology |
|---|---|
| Language (core) | Java 21+ (development uses OpenJDK 25, target release 21) |
| Build system | Gradle 9.x with Kotlin DSL (.gradle.kts) |
| Bytecode manipulation | ObjectWeb ASM 9.x |
| Injection framework | Custom (our own, not SpongePowered Mixin) |
| Mappings format | Mojang official (ProGuard format) |
| Launcher backend | Rust (via Tauri) |
| Launcher frontend | React or Svelte + TypeScript |
| Launcher build | Tauri CLI |
| Pack format | TOML (primary) with JSON schema |
| CI/CD | GitHub Actions |
| Cross-compilation | GitHub Actions matrix (macOS, Windows, Linux) |
| Website | alloymc-web repo (separate) |

## Development Environment

**Installed (as of 2026-02-15):**
```
OpenJDK 25.0.2 (Homebrew) — JAVA_HOME set in ~/.zshrc
Gradle 9.3.1 (Homebrew)
Git 2.50.1 (Apple Git)
Rust 1.93.0
Node.js 25.3.0
Bun (available)
```

**JAVA_HOME:** `/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home`

**Note:** We develop with OpenJDK 25 but target Java 21 compatibility via `--release 21` in Gradle. This ensures mods run on the Java 21 runtime that Minecraft ships with.

## Minecraft JAR Pipeline

```
1. GET https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
2. Parse → find latest release version ID
3. GET that version's metadata JSON (URL from manifest)
4. From metadata, download:
   ├── client.jar (obfuscated)
   ├── client_mappings.txt (ProGuard format, official Mojang mappings)
   ├── All required libraries (listed in metadata)
   └── Asset index URL → download assets
5. Parse ProGuard mappings → build name mapping tables
6. Remap client.jar using mappings → deobfuscated JAR
7. This deobfuscated JAR is what the loader and API build against
```

## Coding Standards

- **Java 21 features encouraged:** records, sealed classes, pattern matching, virtual threads where appropriate
- **No star imports.** Explicit imports only.
- **Package structure:** `net.alloymc.<component>.<subpackage>`
  - Example: `net.alloymc.loader.classload`, `net.alloymc.api.event`, `net.alloymc.mappings.proguard`
- **Naming:**
  - Classes: PascalCase
  - Methods/fields: camelCase
  - Constants: UPPER_SNAKE_CASE
  - Packages: lowercase, single word where possible
- **No Lombok.** Use Java 21 records and standard patterns.
- **Minimal dependencies.** Every external dependency must be justified. ASM is justified (foundational bytecode lib). Random utility libraries are not — write the code.
- **Tests:** JUnit 5. Test the mappings pipeline, loader mechanics, and API contracts. Don't test getters/setters.
- **Documentation:** Javadoc on public API surfaces. Internal code should be self-documenting. Don't over-comment.

## Git & Branching

- `main` — stable, always compiles, always passes tests
- `dev` — active development, may be unstable
- Feature branches: `feature/<short-description>`
- Bug fixes: `fix/<short-description>`
- Commit messages: imperative mood, concise ("Add block registry event system", not "Added some events")

## Build Order (Development Phases)

### Phase 1: Foundation
1. alloy-mappings — fetch MC JAR, parse Mojang mappings, produce deobfuscated JAR
2. alloy-loader — custom classloader, basic mod discovery, launch Minecraft with loader injected

### Phase 2: Core
3. Bytecode injection system (ASM-based, within alloy-loader)
4. alloy-api — event bus, basic registries (blocks, items)

### Phase 3: Modding
5. First proof-of-concept mod built on Alloy API
6. alloy-installer — install into vanilla launcher
7. alloy-packs — pack format spec, basic tooling

### Phase 4: Distribution
8. alloy-launcher — Tauri desktop app
9. alloy-hub — web backend for mod/pack hosting
10. alloymc.net — public website

## Legal Notes

- Mojang's official mappings are provided under their EULA — they are free to use for modding.
- We NEVER distribute Minecraft's code. The loader downloads it from Mojang's servers at runtime.
- Alloy itself is our original code.
- License TBD (likely MIT or Apache 2.0 for maximum community adoption).

## Key Decisions Log

| Date | Decision | Rationale |
|---|---|---|
| 2026-02-15 | Build from scratch, no Forge/Fabric dependency | Full control, no inherited tech debt |
| 2026-02-15 | Use Mojang official mappings only | First-party, legally clear, auto-updated per version |
| 2026-02-15 | Tauri for launcher (not Electron) | Smaller binary, faster, modern |
| 2026-02-15 | Java 21 minimum | MC 1.21+ requires it, gives us modern Java features |
| 2026-02-15 | Monorepo during early dev, split later | Faster iteration while components are tightly coupled |
| 2026-02-15 | Own injection system (not SpongePowered Mixin) | Full ownership, no external ecosystem dependency |
| 2026-02-15 | API as firewall — modders never touch MC internals | Core future-proofing strategy |

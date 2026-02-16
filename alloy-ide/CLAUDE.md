# Alloy IDE — The Modding Studio

> **This is the MODDING IDE, not the game launcher.**
> The Alloy Launcher (for players to launch Minecraft) is at `../alloy-launcher/`.
> Both live in the `alloy/` monorepo. Use `./anvil` from the repo root to build.

## Project Overview

Alloy IDE is a purpose-built integrated development environment for creating Minecraft mods, modpacks, and resource packs on the Alloy modding platform. It is not a general-purpose code editor with plugins bolted on — every feature exists to serve Alloy mod development.

**Domain:** alloymc.net
**Tagline:** "Where Mods Are Forged"
**App ID:** `net.alloymc.ide`
**Dev port:** 1425

## Philosophy

1. **Purpose-built, not adapted.** This is not VS Code with a theme. Every panel, every workflow, every shortcut exists because Alloy modders need it. If a feature doesn't serve modding, it doesn't ship.
2. **Visual-first where it matters.** Code is the backbone, but GUIs, animations, textures, and block models should be created visually — not by hand-editing JSON or guessing pixel coordinates.
3. **Environment-aware at every layer.** The IDE understands client-side, server-side, and universal ("both") mods as a first-class concept. Templates, validation, build output, and testing all respect the mod's declared environment.
4. **Zero-to-modpack in one tool.** A developer should be able to create a mod, design its GUIs, animate its machines, test it in-game, then package it into a modpack — all without leaving the IDE.
5. **Cross-platform is non-negotiable.** macOS, Windows, Linux. Same experience. Same features. No second-class citizens.
6. **Lightweight and fast.** Sub-second startup. Under 50MB RAM idle. 5-10MB installer. Developers run the IDE alongside Minecraft — resource efficiency matters.

## What Alloy IDE Is

### Project Types

**Mod Project** (`alloy.mod.json`)
- Scaffolds a complete Alloy mod with correct package structure (`net.alloymc.*` or user-defined)
- Environment selector at creation: client, server, or both — this gates available API surfaces
- Auto-generates `alloy.mod.json` manifest, Gradle build files, and entry point class
- Provides Alloy API autocompletion, inline documentation, and error checking via LSP
- Drag-and-drop asset pipeline: drop a PNG onto the project tree and the IDE generates the corresponding block/item/entity registration code, resource JSON, and texture binding
- Visual GUI editor for in-game screens (machine interfaces, config UIs, HUDs)
- Animation timeline editor for subtle visual effects (electricity flow, progress bars, particle emitters)
- Live preview panel showing how GUIs and animations render at Minecraft resolution
- One-click build → test cycle: compiles the mod, copies to `mods/`, launches Minecraft with Alloy loader

**Modpack Project** (`alloy.pack.toml`)
- Aggregates multiple mods into a distributable modpack
- Import mods from: local projects, local JAR files, Git repositories (cloned and built), or the Alloy Hub
- Dependency conflict detection and resolution UI
- Environment validation: ensures server-side modpacks don't accidentally include client-only mods
- Version pinning UI with semver constraint editor
- One-click export to Alloy modpack format (`.alloypack`) for submission to Alloy Hub
- Side-by-side mod configuration editor (edit each mod's config without launching the game)

**Resource Pack Project** (future)
- Texture and model editing integrated into the IDE
- Block model visual editor with rotation, UV mapping, and face culling
- Preview textures at Minecraft resolution scales

### Core Code Editor

Built on **CodeMirror 6** with full Language Server Protocol integration.

**Features:**
- Java syntax highlighting, code folding, bracket matching, multi-cursor editing
- Autocompletion powered by Eclipse JDT Language Server (runs as a sidecar process)
- Inline errors and warnings with quick-fix suggestions
- Go-to-definition, find references, rename symbol across project
- Alloy API-aware: recognizes `@EventHandler` annotations, `ModInitializer` implementations, event types, registry calls
- Environment-aware linting: warns if a server-side mod references client-only API (`AlloyRenderer`, HUD classes, etc.)
- Mod manifest (`alloy.mod.json`) schema validation and autocompletion
- Gradle build file support (`.gradle.kts` syntax highlighting and basic completion)

### Visual GUI Editor

The flagship differentiator. Alloy mods that add machines (furnaces, generators, processors) need in-game GUIs. Today, modders code these pixel-by-pixel. The IDE makes it visual.

**Capabilities:**
- WYSIWYG canvas at Minecraft GUI resolution (256x256 standard, scalable)
- Drag-and-drop widget palette: slots (input/output), progress bars, energy bars, fluid tanks, buttons, labels, custom regions
- Snap-to-grid with configurable grid size
- Widget property inspector: position, size, texture coordinates, tooltip text, slot ID binding
- Background texture layer: import or paint the GUI background texture
- Real-time preview at 1x, 2x, and 3x Minecraft GUI scale
- Export generates: the GUI class (extending Alloy's screen API), the texture PNG, and the container/handler binding code
- Round-trip editing: changes in the visual editor update the code, and vice versa (code changes reflect in the visual editor when possible)

### Animation Timeline Editor

For machines and blocks that need subtle, polished animations.

**Use Cases:**
- Electricity flowing through cables (UV scrolling, sprite sheet animation)
- Machine processing progress (animated progress bar, color transitions)
- Particle emitter configuration (spawn rate, velocity, lifetime, color curve)
- Furnace flame animations, liquid level changes
- Custom block state transitions (active/inactive glow)

**Capabilities:**
- Horizontal timeline with keyframes per property
- Property tracks: UV offset, opacity, color, sprite frame, scale, rotation
- Easing curve editor (linear, ease-in, ease-out, cubic bezier)
- Preview playback at target FPS (20 ticks/sec = Minecraft tick rate)
- Export generates: animation data file + rendering code using Alloy's client API
- Sprite sheet importer: drag in a sprite sheet, define frame bounds, preview animation

### Asset Pipeline

**Drag-and-Drop Workflow:**
1. Developer drags a PNG file onto the project tree (or into a specific assets folder)
2. IDE detects the image dimensions and context
3. Presents a wizard: "What is this? Block texture / Item texture / GUI element / Entity texture / Particle sprite"
4. Based on selection, generates:
   - The proper file path in `src/main/resources/assets/<modid>/textures/`
   - Registration code in the mod's initializer (e.g., `Registry.register(Blocks.class, "my_block", ...)`)
   - JSON model file (for blocks/items)
   - Blockstate JSON (for blocks with variants)
5. The mod compiles and the new content is immediately available for testing

### Integrated Terminal

- Full PTY-backed terminal (xterm.js in the frontend, PTY process in Rust backend)
- Pre-configured with Alloy Gradle tasks: `build`, `setupWorkspace`, `launchClient`, `launchServer`
- Task runner panel with one-click access to common operations
- Build output parsed for errors → click error to jump to source location

### Environment Intelligence

The IDE treats mod environment as a core axis, not an afterthought.

**At project creation:**
- Template selection changes based on environment (server mods don't scaffold GUI code)
- API surface is filtered (client-only classes grayed out or hidden for server mods)

**During development:**
- Linting rules enforce environment boundaries
- Importing a client-only class in a server mod → error with explanation
- The GUI editor and animation editor are disabled/hidden for server-only mods
- The command and event system surfaces change based on environment

**At build time:**
- Server-only mods are validated to have no client dependencies
- Client-only mods are flagged if they accidentally import server internals
- "Both" mods are checked for proper side-gating (client code in client-gated blocks)

**In modpacks:**
- Environment column in the mod list shows client/server/both per mod
- Conflict detection warns if a server modpack includes client-only mods
- Export filters mods by target environment

## Tech Stack

| Layer | Technology | Rationale |
|---|---|---|
| Desktop framework | Tauri 2 | Same as alloy-launcher. Small binary (5-10MB), native performance, cross-platform. Not Electron. |
| Frontend framework | React 19 + TypeScript 5 | Same as alloy-launcher. Largest UI ecosystem. Hot reload. Maximum development velocity. |
| Code editor | CodeMirror 6 | Modular, lightweight (300KB core), official LSP client, independent editor instances, better docs than Monaco. |
| Styling | Tailwind CSS 4 | Same as alloy-launcher. Consistent design system. |
| State management | Zustand | Same as alloy-launcher. Simple, performant. |
| Visual editors | HTML5 Canvas + Fabric.js or Konva | 2D canvas rendering for GUI builder and animation editor. Well-supported across all WebViews. |
| Terminal emulator | xterm.js | Industry standard web terminal. Full PTY support via Tauri backend. |
| Language intelligence | Eclipse JDT LS (Language Server) | Runs as a sidecar process managed by Rust backend. Communicates via LSP stdio. |
| File system / process | Rust (Tauri core) | Native file I/O, PTY management, process spawning, LSP lifecycle. |
| Build system integration | Gradle (via Rust backend) | Invokes `./gradlew` tasks, parses output, reports to frontend. |

## Design System

Exact color scheme from alloymc.net — the canonical Alloy design system. Dark-only theme. Every color below is authoritative.

### Color Palette

#### Obsidian — Background Layers (darkest to lightest)
| Token | Hex | Usage |
|---|---|---|
| `obsidian-950` | `#06060a` | Deepest background, main canvas |
| `obsidian-900` | `#0c0c12` | Primary surface (panels, sidebars) |
| `obsidian-800` | `#14141c` | Elevated surfaces (cards, modals, dropdowns) |
| `obsidian-700` | `#1e1e28` | Secondary button backgrounds, hover states |
| `obsidian-600` | `#2a2a36` | Borders, dividers, subtle separators |
| `obsidian-500` | `#3a3a48` | Disabled borders, muted outlines |

#### Stone — Text Hierarchy (brightest to dimmest)
| Token | Hex | Usage |
|---|---|---|
| `stone-100` | `#f0f0f4` | Primary text (headings, body) |
| `stone-200` | `#d1d5db` | Secondary text (descriptions) |
| `stone-300` | `#b8bfc9` | Tertiary text (labels, captions) |
| `stone-400` | `#9ca3af` | Muted text (placeholders, hints) |
| `stone-500` | `#6b7280` | Disabled text, subtle metadata |

#### Ember — Primary Accent (warm orange)
| Token | Hex | Usage |
|---|---|---|
| `ember` | `#ff6b00` | Primary accent, active states, links, focus rings |
| `ember-light` | `#ff8a33` | Hover states, lighter accents |
| `ember-dark` | `#cc5500` | Pressed states, darker accents, logo faces |

#### Brand Accents
| Token | Hex | Usage |
|---|---|---|
| `molten` | `#ff4400` | Emphasis, destructive hover, intense warmth |
| `forge-gold` | `#f0b830` | Secondary accent, badges, spark effects, value displays |

#### Semantic Colors
| Token | Hex | Usage |
|---|---|---|
| `error` | Tailwind `red-400` | Error text, error borders |
| `success` | Tailwind `green-400` | Success indicators (TPS good, build passed) |
| `warning` | Tailwind `yellow-400` | Warnings |
| `info` | Tailwind `blue-400` | Informational highlights |
| `discord` | `#5865F2` | Discord integration (community links) |

#### Terminal Window Dots (macOS-style decorations)
| Dot | Hex |
|---|---|
| Close (red) | `#ff5f57` |
| Minimize (yellow) | `#febc2e` |
| Maximize (green) | `#28c840` |

### Logo Colors
| Element | Hex |
|---|---|
| Gradient start | `#ff9a4a` |
| Gradient end | `#f04800` |
| Left face | `#e06000` |
| Right face | `#cc5500` |
| Spark | `#f0b830` |

### Gradients

**Ember glow (hover/focus effects):**
- Shadow: `rgba(255, 107, 0, 0.4)` primary, `rgba(255, 107, 0, 0.15)` subtle

**Molten crack gradient (decorative):**
- `0%: #ff6b00` → `50%: #f0b830` → `100%: #ff4400`

**Lava drip (decorative):**
- `0%: rgba(240, 184, 48, 0.85)` → `40%: rgba(255, 107, 0, 0.75)` → `100%: rgba(204, 85, 0, 0.5)`

**Anvil ring animation:**
- Start: `rgba(255, 107, 0, 0.15)` → Mid: `rgba(255, 107, 0, 0.06)` → End: `rgba(255, 107, 0, 0)`

**Terminal background:**
- Linear 180° from `#111118` to `#0a0a10`

**Dialog backdrop:**
- `rgba(6, 6, 10, 0.8)`

**Grid overlay:**
- `rgba(255, 255, 255, 0.015)`

### Opacity Patterns
Common opacity modifiers used with accent colors:
- `/10` — Subtle tinted backgrounds (e.g., `bg-ember/10`)
- `/15` — Badge backgrounds (e.g., `bg-ember/15`, `bg-forge-gold/15`)
- `/20` — Light borders
- `/30` — Badge borders (e.g., `border-ember/30`)
- `/50` — Medium-weight borders and overlays

### Component Color Schemes

**Buttons:**
| Variant | Background | Text | Hover | Border |
|---|---|---|---|---|
| Primary | `ember` | `obsidian-950` | `ember-light` | — |
| Secondary | `obsidian-700` | `stone-100` | `obsidian-600` | `obsidian-600` |
| Ghost | transparent | `stone-300` | `obsidian-800` | — |
| Danger | `molten/15` | `molten` | `molten/25` | `molten/30` |
| Disabled | `obsidian-800` | `stone-500` | — | `obsidian-700` |

**Badges:**
| Variant | Background | Text | Border |
|---|---|---|---|
| Default | `obsidian-700` | `stone-300` | `obsidian-600` |
| Ember | `ember/15` | `ember` | `ember/30` |
| Gold | `forge-gold/15` | `forge-gold` | `forge-gold/30` |

**Cards:**
- Background: `obsidian-800` or `obsidian-800/50` (translucent)
- Border: `obsidian-600`
- Hover glow: `rgba(255, 107, 0, 0.04)`

### Fonts
| Role | Family | CSS Token |
|---|---|---|
| Headings | Space Grotesk, sans-serif | `--font-heading` |
| Body / UI | Inter, sans-serif | `--font-body` |
| Code / Terminal | JetBrains Mono, monospace | `--font-mono` |

### CodeMirror Editor Theme
The code editor must use a custom CodeMirror theme matching the Alloy palette:
- **Editor background:** `obsidian-950` (`#06060a`)
- **Gutter background:** `obsidian-900` (`#0c0c12`)
- **Gutter text:** `stone-500` (`#6b7280`)
- **Selection:** `ember/15` (`rgba(255, 107, 0, 0.15)`)
- **Active line:** `obsidian-900` (`#0c0c12`)
- **Cursor:** `ember` (`#ff6b00`)
- **Matching bracket:** `ember/30` background
- **Search highlight:** `forge-gold/20`
- **Strings:** `forge-gold` (`#f0b830`)
- **Keywords:** `ember` (`#ff6b00`)
- **Comments:** `stone-500` (`#6b7280`)
- **Types/classes:** `stone-100` (`#f0f0f4`)
- **Methods/functions:** `ember-light` (`#ff8a33`)
- **Numbers:** `molten` (`#ff4400`)
- **Annotations:** `forge-gold` (`#f0b830`)

### Terminal Theme (xterm.js)
- **Background:** `#0a0a10`
- **Foreground:** `stone-100` (`#f0f0f4`)
- **Cursor:** `ember` (`#ff6b00`)
- **Selection:** `ember/20`
- **ANSI colors:** Use obsidian/stone/ember palette mapped to standard 16 ANSI colors

## Project Structure

```
alloy-ide/
├── src-tauri/                  — Rust backend (Tauri core)
│   ├── src/
│   │   ├── main.rs             — Tauri application entry
│   │   ├── lib.rs              — Plugin and command registration
│   │   ├── state.rs            — Application state management
│   │   ├── commands/           — Tauri command handlers
│   │   │   ├── project.rs      — Project creation, opening, management
│   │   │   ├── filesystem.rs   — File tree operations, file I/O
│   │   │   ├── build.rs        — Gradle task execution, build parsing
│   │   │   ├── terminal.rs     — PTY process management
│   │   │   └── lsp.rs          — LSP server lifecycle and communication
│   │   ├── lsp/                — Language Server Protocol management
│   │   │   ├── manager.rs      — LSP server process lifecycle
│   │   │   ├── transport.rs    — stdio JSON-RPC transport
│   │   │   └── jdtls.rs        — Eclipse JDT LS-specific configuration
│   │   ├── project/            — Project type logic
│   │   │   ├── mod_project.rs  — Mod project scaffolding and validation
│   │   │   ├── pack_project.rs — Modpack project management
│   │   │   └── templates.rs    — Project templates per environment
│   │   └── assets/             — Asset pipeline logic
│   │       ├── importer.rs     — Image import, classification, path resolution
│   │       └── codegen.rs      — Registration code generation
│   ├── Cargo.toml
│   └── tauri.conf.json
├── src/                        — React/TypeScript frontend
│   ├── main.tsx                — React entry point
│   ├── App.tsx                 — Root component with routing
│   ├── pages/
│   │   ├── WelcomePage.tsx     — Project launcher (new/open/recent)
│   │   └── EditorPage.tsx      — Main IDE workspace
│   ├── components/
│   │   ├── layout/
│   │   │   ├── ActivityBar.tsx — Left icon bar (file tree, search, git, etc.)
│   │   │   ├── Sidebar.tsx     — Collapsible side panel
│   │   │   ├── EditorTabs.tsx  — Tab bar for open files
│   │   │   ├── StatusBar.tsx   — Bottom status bar (environment, build, branch)
│   │   │   └── PanelArea.tsx   — Bottom panel (terminal, output, problems)
│   │   ├── editor/
│   │   │   ├── CodeEditor.tsx  — CodeMirror 6 wrapper component
│   │   │   ├── EditorPane.tsx  — Single editor pane (supports splits)
│   │   │   └── WelcomeTab.tsx  — Default tab when no file is open
│   │   ├── filetree/
│   │   │   ├── FileTree.tsx    — Project file explorer
│   │   │   └── FileIcon.tsx    — File type icons
│   │   ├── terminal/
│   │   │   └── Terminal.tsx    — xterm.js wrapper
│   │   ├── gui-editor/
│   │   │   ├── GuiCanvas.tsx   — Visual GUI editor canvas
│   │   │   ├── WidgetPalette.tsx — Draggable widget sidebar
│   │   │   ├── PropertyPanel.tsx — Selected widget property editor
│   │   │   └── GuiPreview.tsx  — Real-time GUI preview at MC scale
│   │   ├── animation/
│   │   │   ├── Timeline.tsx    — Animation timeline component
│   │   │   ├── KeyframeTrack.tsx — Per-property keyframe track
│   │   │   ├── EasingEditor.tsx — Bezier curve easing editor
│   │   │   └── AnimPreview.tsx — Animation playback preview
│   │   ├── modpack/
│   │   │   ├── ModList.tsx     — Modpack mod list manager
│   │   │   ├── ModImporter.tsx — Import from file/git/hub
│   │   │   └── ConflictResolver.tsx — Dependency conflict UI
│   │   └── ui/                 — Shared UI primitives (Button, Card, Input, etc.)
│   ├── lib/
│   │   ├── store.ts            — Zustand global state
│   │   ├── lsp.ts              — LSP client bridge (frontend ↔ Tauri backend)
│   │   ├── project.ts          — Project type definitions and utilities
│   │   └── environment.ts      — Client/server/both environment logic
│   └── styles/
│       └── index.css           — Tailwind + custom theme
├── package.json
├── vite.config.ts
├── tailwind.config.ts
├── tsconfig.json
├── CLAUDE.md                   — This file
└── README.md
```

## Development Environment

**Required:**
```
Node.js 22+ or Bun 1.x
Rust 1.80+ (for Tauri)
Java 21+ (for running Eclipse JDT LS and testing mods)
Gradle 9.x (for mod builds — bundled or user-installed)
```

**Install & Run:**
```bash
# From the alloy monorepo root — preferred method:
./anvil --ide --dev          # Dev mode with hot reload (port 1425)
./anvil --ide                # Production build

# Or directly:
cd alloy-ide
bun install
bun tauri dev                # Dev mode
bun tauri build              # Production build
```

## Coding Standards

### Rust (src-tauri/)
- Rust 2021 edition
- Use `thiserror` for error types, `anyhow` for propagation in commands
- Async with tokio where appropriate
- Tauri commands are `async` and return `Result<T, String>` for frontend error handling
- No `unwrap()` in production code — propagate errors

### TypeScript (src/)
- Strict TypeScript (`"strict": true`)
- Functional React components only (no class components)
- State: Zustand for global, `useState`/`useReducer` for local
- Named exports preferred over default exports
- No `any` — use proper types or `unknown` with type guards
- File naming: PascalCase for components (`CodeEditor.tsx`), camelCase for utilities (`lsp.ts`)

### CSS
- Tailwind utility classes for layout and standard styling
- CSS custom properties for theme tokens (colors, spacing, radii)
- No inline `style={}` except for dynamic computed values (canvas positions, etc.)

## Non-Negotiable Requirements

1. **The IDE must understand mod environment.** Every feature — templates, linting, API surface, visual editors, build output — must respect whether a mod is client, server, or both.
2. **Drag-and-drop assets must generate code.** Dropping an image into the project must produce working registration code, not just copy the file.
3. **The GUI editor must produce real code.** Not a separate format that requires a runtime interpreter. The output is standard Alloy API Java code that modders can read, modify, and learn from.
4. **The animation editor must target Minecraft's tick rate.** 20 TPS is the animation base. The timeline operates in ticks, not milliseconds.
5. **Builds must work without the IDE.** Every project the IDE creates must be buildable with `./gradlew build` from the command line. The IDE is a tool, not a dependency.
6. **Cross-platform parity.** Every feature works identically on macOS, Windows, and Linux. Test on all three.

## Build & Release

- CI/CD: GitHub Actions
- Build matrix: macOS (arm64, x86_64), Windows (x64), Linux (x64, arm64)
- Auto-update: Tauri's built-in updater
- Distribution: `.dmg` (macOS), `.msi` (Windows), `.AppImage` + `.deb` (Linux)

## Key Decisions Log

| Date | Decision | Rationale |
|---|---|---|
| 2026-02-15 | Tauri 2 + React + CodeMirror 6 | Same stack as launcher (team expertise), small binary, best dev velocity, CodeMirror 6 has official LSP client |
| 2026-02-15 | CodeMirror 6 over Monaco | Modular (300KB vs 5MB), independent instances for split panes, better docs, official LSP client released 2025 |
| 2026-02-15 | Eclipse JDT LS for Java intelligence | Industry standard Java language server, powers VS Code's Java support, runs as sidecar process |
| 2026-02-15 | HTML5 Canvas (Fabric.js/Konva) for visual editors | 2D Canvas universally supported across all WebViews, rich library ecosystem, sufficient for GUI/animation editing |
| 2026-02-15 | xterm.js for terminal | Industry standard, full PTY support, proven in VS Code and every web terminal |
| 2026-02-15 | Dark-only theme | Modders work in dark mode. Minecraft is a dark game. One theme to maintain, done right. |
| 2026-02-15 | GUI editor outputs real Java code | Modders learn from generated code. No proprietary runtime format. Projects must build without the IDE. |

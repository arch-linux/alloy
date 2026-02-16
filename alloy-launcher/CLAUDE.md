# Alloy Launcher — Game Client Launcher

> **This is the GAME LAUNCHER, not the IDE.**
> The Alloy IDE is a SEPARATE project at `/Users/archlinuxusa/Desktop/alloy-ide/`.
> Do NOT confuse the two — they are different applications with different purposes.

## What This Is

The Alloy Launcher is a desktop application for **launching Minecraft with the Alloy mod loader**. It handles:

- Microsoft/Xbox/Minecraft authentication (OAuth 2.0 flow)
- Downloading Minecraft versions, JVM, libraries, and assets
- Installing the Alloy mod loader into Minecraft
- Managing game instances (multiple versions/modpacks)
- Launching Minecraft with the correct JVM arguments and Alloy loader injected
- Auto-updating the launcher itself

## What This Is NOT

- **NOT an IDE.** This app does not edit code, files, or projects.
- **NOT the Alloy IDE.** The IDE is at `~/Desktop/alloy-ide/` — a completely separate Tauri app with CodeMirror, terminal, GUI editor, etc.
- **NOT a mod development tool.** This is for players to launch the game.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Desktop framework | Tauri 2 (Rust backend + WebView frontend) |
| Frontend | React 19 + TypeScript 5 + Tailwind CSS 4 |
| State management | Zustand |
| Auth | Microsoft OAuth 2.0 → Xbox Live → Minecraft tokens |
| Key storage | `keyring` crate (OS keychain) |
| Downloads | `reqwest` (async HTTP) |
| Persistent settings | `tauri-plugin-store` |

## Dev & Build

```bash
# From the repo root — preferred method:
./anvil --launcher --dev     # Dev mode with hot reload (port 1420)
./anvil --launcher           # Production build

# Or directly:
cd alloy-launcher && bun install && bun tauri dev
```

**Port: 1420** (the IDE uses port 1425 — they can run simultaneously)

## Project Structure

```
alloy-launcher/
├── src/                        — React frontend
│   ├── components/
│   │   ├── home/               — LaunchButton, PlayerCard, ProgressBar
│   │   ├── layout/             — AppLayout, Sidebar, TitleBar
│   │   ├── effects/            — EmberGlow, ForgeSparks, GridOverlay
│   │   └── ui/                 — Button, Card, Input, Spinner
│   ├── pages/
│   │   ├── HomePage.tsx        — Main launch screen
│   │   ├── LoginPage.tsx       — Microsoft auth flow
│   │   └── SettingsPage.tsx    — Preferences
│   └── lib/
│       ├── store.ts            — Zustand state
│       └── types.ts            — TypeScript types
├── src-tauri/                  — Rust backend
│   └── src/
│       ├── commands/
│       │   ├── auth_commands.rs    — Microsoft/Xbox/MC auth
│       │   ├── launch_commands.rs  — Game launch logic
│       │   └── settings_commands.rs — Preferences
│       ├── auth/                   — Token management
│       │   ├── microsoft.rs        — OAuth 2.0 + MSAL
│       │   ├── xbox.rs             — Xbox Live tokens
│       │   ├── minecraft.rs        — MC authentication
│       │   └── tokens.rs           — Keychain storage
│       └── minecraft/              — Game integration
│           ├── download.rs         — JVM/assets/libs
│           ├── versions.rs         — Version manifest
│           └── launch.rs           — JVM invocation
├── package.json
├── vite.config.ts
└── src-tauri/
    ├── Cargo.toml
    └── tauri.conf.json         — identifier: net.alloymc.launcher
```

## App Identifier

- **Bundle ID:** `net.alloymc.launcher`
- **Product Name:** Alloy Launcher
- **Window:** 1024x640 (min 800x500)

## Design

Uses the same Obsidian + Ember color palette as the IDE and alloymc.net. Dark-only theme.

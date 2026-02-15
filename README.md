<p align="center">
  <img src="https://alloymc.net/icon.svg" width="80" height="80" alt="Alloy" />
</p>

<h1 align="center">Alloy</h1>

<p align="center">
  <strong>A from-scratch Minecraft modding ecosystem.</strong><br />
  Not a fork. Not built on Forge, Fabric, or any existing loader.<br />
  Every layer — from bytecode manipulation to the launcher — is ours.
</p>

<p align="center">
  <a href="https://alloymc.net">Website</a> &nbsp;&middot;&nbsp;
  <a href="https://alloymc.net/getting-started">Getting Started</a> &nbsp;&middot;&nbsp;
  <a href="https://alloymc.net/docs">Documentation</a> &nbsp;&middot;&nbsp;
  <a href="https://alloymc.net/community">Community</a>
</p>

---

## Why Alloy?

Every existing Minecraft modding platform is a patchwork. A loader from one project, community-maintained mappings from another, three competing launchers, and an API designed by committee a decade ago. None of it was built to work together. All of it carries years of technical debt.

Alloy is a clean-sheet design. One integrated stack where every piece was engineered alongside every other piece.

- **Future-proof.** Minecraft updates should never require modders to rewrite their mods. The API is a firewall between modders and Minecraft internals.
- **Mods and plugins are the same thing.** No artificial split. A land protection system, a tech mod, and a minimap are all mods — differentiated only by an environment flag (`server`, `client`, or `both`). One format, one loader, one `mods/` folder.
- **Fully automated.** Clone, run one command, everything resolves. When Mojang drops a new version, updating is one command — not a week of manual work.
- **Modern Java.** Java 21+. Records, sealed classes, pattern matching. No legacy patterns from 1.7.10.
- **Minimal dependencies.** We use foundational libraries (ASM for bytecode, Gson for JSON) and write everything else ourselves.

## Architecture

```
alloy/
├── alloy-mappings/    Deobfuscation pipeline — fetches Mojang maps, remaps JARs
├── alloy-loader/      Mod loader — classloading, bytecode injection, mod lifecycle
├── alloy-api/         Modding API — events, registries, commands, permissions
├── alloy-client/      Client-side mod — custom UI, screens, rendering hooks
└── alloy-launcher/    Desktop launcher — Tauri + React, instance management
```

| Component | What it does |
|---|---|
| **alloy-mappings** | Fetches Mojang's official mappings, downloads the client JAR, deobfuscates it, resolves all libraries and assets. Fully automated per Minecraft version. |
| **alloy-loader** | Bootstraps before Minecraft's main class. Custom classloader, mod discovery, dependency resolution, ASM-based bytecode injection. |
| **alloy-api** | What modders code against. Event bus, block/entity/player events, command registration, permissions, scheduler, configuration. Modders never import Minecraft classes directly. |
| **alloy-client** | Alloy's client-side presence — custom title screen, pause menu, mod list, HUD system, rendering utilities. Ships as a built-in mod. |
| **alloy-launcher** | Native desktop app built with Tauri (Rust) and React. Microsoft/Xbox/Minecraft authentication, game launching, settings management. |

## Requirements

- **Java 21+** (we develop on OpenJDK 25, target `--release 21`)
- **Gradle 9.x** (wrapper included)
- **Rust** (for the launcher — Tauri)
- **Bun or Node.js** (for the launcher frontend)

## Quick Start

```bash
# Clone the repo
git clone https://github.com/arch-linux/alloy.git
cd alloy

# Set up the workspace — fetches Minecraft, downloads libraries,
# applies Mojang mappings, produces a deobfuscated JAR
./gradlew :alloy-mappings:setupWorkspace

# Build everything
./gradlew build

# Launch Minecraft with Alloy
./gradlew launchClient
```

That's it. No manual JAR downloads. No "also run this other script first." The build is the source of truth.

## Writing a Mod

Every mod implements `ModInitializer` and declares itself in `alloy.mod.json`:

```java
package com.example.mymod;

import net.alloymc.loader.api.ModInitializer;
import net.alloymc.api.AlloyAPI;
import net.alloymc.api.event.player.PlayerJoinEvent;
import net.alloymc.api.event.EventHandler;
import net.alloymc.api.event.Listener;

public class MyMod implements ModInitializer, Listener {

    @Override
    public void onInitialize() {
        AlloyAPI.getEventBus().register(this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage("Welcome!");
    }
}
```

```json
{
  "id": "mymod",
  "name": "My Mod",
  "version": "1.0.0",
  "entrypoint": "com.example.mymod.MyMod",
  "environment": "server"
}
```

The `environment` field is the only thing that determines where your mod runs:

| Value | Behavior |
|---|---|
| `"server"` | Server-side only. Vanilla clients can connect without installing it. |
| `"client"` | Client-side only. The server doesn't need it. |
| `"both"` | Runs on both sides. Required when adding new blocks, items, or GUIs. |

No separate "plugin" system. No second API to learn. Everything is a mod.

## Building Individual Modules

```bash
./gradlew :alloy-api:build        # Modding API
./gradlew :alloy-loader:build     # Mod loader
./gradlew :alloy-mappings:build   # Mappings pipeline
./gradlew :alloy-client:build     # Client mod
```

## Tech Stack

| Layer | Technology |
|---|---|
| Core | Java 21+ (OpenJDK) |
| Build | Gradle 9.x, Kotlin DSL |
| Bytecode | ObjectWeb ASM 9.x |
| Mappings | Mojang official (ProGuard format) |
| Launcher backend | Rust (Tauri) |
| Launcher frontend | React + TypeScript |
| Injection | Custom (not SpongePowered Mixin) |

## Legal

- Mojang's official mappings are provided under their EULA and are free to use for modding.
- **We never distribute Minecraft's code.** The mappings pipeline downloads it from Mojang's servers at build time. Nothing from Mojang is committed to this repository.
- Alloy itself is original work.

## Links

- **Website:** [alloymc.net](https://alloymc.net)
- **GriefPrevention (Alloy mod):** [arch-linux/GriefPrevention](https://github.com/arch-linux/GriefPrevention)

---

<p align="center">
  <em>Forged with Alloy.</em>
</p>

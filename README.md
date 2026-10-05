# Utility Nexus Admin

A utility mod for Minecraft server administration, featuring datapack loading functionality and other admin tools.

## Features

- **Datapack Loader**: Load and manage datapacks dynamically
- **Admin Tools**: Various server administration utilities
- **Utility Functions**: Helper functions for server management
- **Alphabetical Creative Tabs**: Client-side only — creative inventory tabs are sorted by the
  name shown to the player, using the collation rules of their language. The vanilla tabs stay at
  the front in their usual order unless `creative.vanillaTabsFirst = false`. Disable the whole
  thing with `creative.sortTabsAlphabetically = false` in `config/utility_nexus/admin/config.toml`

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.249
- Java 21

## Installation

1. Install NeoForge 21.1.249 for Minecraft 1.21.1
2. Download the latest release from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/utility-nexus-admin) or [GitLab](https://gitlab.com/stalking-dragons/minecraft/utility-nexus-admin/-/releases)
3. Place the JAR file in your `mods` folder
4. Launch Minecraft with the NeoForge profile

## Building from Source

```bash
./gradlew build
```

The built JAR will be in `build/libs/`.

## Links

- [GitLab Repository](https://gitlab.com/stalking-dragons/minecraft/utility-nexus-admin)
- [Issues](https://gitlab.com/stalking-dragons/minecraft/utility-nexus-admin/-/issues)
- [Releases](https://gitlab.com/stalking-dragons/minecraft/utility-nexus-admin/-/releases)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
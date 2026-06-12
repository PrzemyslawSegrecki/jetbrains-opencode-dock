# OpenCode Dock

JetBrains plugin that brings OpenCode into a dedicated IDE tool window, following the active IDE theme.

## Highlights

- Uses your system-installed OpenCode runtime from `PATH`.
- ACP-based communication with the OpenCode runtime.
- Structured chat output for tool calls, thinking blocks, plans, commands, diffs, and file edits.
- File change review with accept/revert from the IDE.
- Inline file references, code references, slash commands, @mentions, and image paste.
- Chat history with rename, delete, bulk delete, fork, and resume flows.
- Model selection with provider-based grouping derived from OpenCode model IDs.
- MCP server configuration, prompt library, system instructions, and commit message generation.
- Status bar quota widget and Windows voice input.

## Prerequisites

**OpenCode is required** — this plugin is a UI frontend for the OpenCode CLI and will not work without it.

- Install OpenCode system-wide using the [official installer](https://opencode.ai) for your platform.
- The `opencode` binary must be available on your `PATH` **before** opening the plugin.
- OpenCode uses the IDE terminal for some authentication and runtime flows.

Verify the installation:

```sh
opencode --version
```

- JetBrains IDE based on IntelliJ Platform 2025.1 or newer.

## Stack

- Backend: Kotlin + Gradle
- Frontend: React + TypeScript + Tailwind
- Runtime protocol: ACP (`com.agentclientprotocol:acp`)

## Screenshots

| Start | Chat | History | Settings |
|-------|------|---------|----------|
| ![Start](docs/images/opencode-dock-start.png) | ![Chat](docs/images/opencode-dock-chat.png) | ![History](docs/images/opencode-dock-history.png) | ![Settings](docs/images/opencode-dock-settings.png) |

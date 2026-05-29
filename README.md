# OpenCode Dock

OpenCode Dock is a JetBrains plugin that brings OpenCode into a dedicated IDE tool window while keeping runtime data isolated from the original Agent Dock plugin.

## Highlights

- OpenCode-only runtime management inside the IDE.
- ACP-based communication with the OpenCode runtime.
- Structured chat output for tool calls, plans, commands, diffs, and file edits.
- Inline file references, code references, slash commands, and image paste support.
- Chat history with rename, delete, bulk delete, and fork flows.
- Model selection with provider-based grouping derived from OpenCode model IDs.
- MCP configuration, prompt library, system instructions, and commit message generation.
- Optional status bar quota widget and Windows voice input support.

## Isolation

OpenCode Dock is intended to coexist with Agent Dock without conflicts.

- Plugin ID: `opencodedock`
- Tool window ID: `OpenCode Dock`
- Runtime storage: `~/.opencode-dock`
- Frontend local storage keys: `opencode-dock.*`

## Requirements

- JetBrains IDE based on IntelliJ Platform 2025.1 or newer.
- OpenCode uses the IDE terminal for some authentication and runtime flows.
- On macOS and Linux, installing the runtime requires `curl` and `tar`.

## Stack

- Backend: Kotlin + Gradle
- Frontend: React + TypeScript + Tailwind
- Runtime protocol: ACP (`com.agentclientprotocol:acp`)

## Screenshot

![OpenCode Dock chat interface](docs/images/agent-dock-chat.png)

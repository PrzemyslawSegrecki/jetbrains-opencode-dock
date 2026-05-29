# Refactor Plan: OpenCode Dock

## Current State

Plugin is a JetBrains plugin with ID `agentdock`, name `Agent Dock`, vendor `EdgarsR`, tool window `Agent Dock`, actions `AgentDock.*`, status widget `AgentDockQuotaWidget`, and runtime storage in `~/.agent-dock`.

ACP adapters are loaded from `src/main/resources/acp-adapters/index.json`, which currently includes multiple providers: Claude, Codex, Cursor, Gemini, GitHub Copilot, Kilo, Qoder, and OpenCode.

The frontend still uses generic concepts such as agents, adapters, and providers. The model picker groups models by adapter/provider name. For OpenCode Dock there should be only one runtime adapter, `opencode`, while OpenCode models should be grouped by their underlying model provider.

## Target State

The plugin becomes an independent product named `OpenCode Dock`, installable side by side with `Agent Dock`.

New identity:

- Plugin ID: `opencodedock`
- Plugin name: `OpenCode Dock`
- Vendor: `Przemyslaw Segrecki`
- Separate action IDs, widget IDs, tool window ID, package namespace, runtime storage, and frontend localStorage keys

Adapters:

- Keep only `opencode.json`
- Remove or disconnect all other ACP service providers
- Preserve ACP dependency because OpenCode still uses ACP

Models:

- No provider/agent switching in the UI
- One OpenCode runtime
- OpenCode models grouped by model provider, for example by the prefix before `/` in model IDs such as `openai/gpt-5`, `anthropic/claude-*`, or `google/*`
- Fallback group for model IDs without provider prefix

## Affected Files

| File | Change Type | Dependencies |
|------|-------------|--------------|
| `src/main/resources/META-INF/plugin.xml` | modify | Plugin ID, name, vendor, toolWindow, actions, status widget, icons |
| `gradle.properties` | modify | `pluginGroup`, version strategy |
| `build.gradle.kts` | modify | Generated `BuildConfig` package path |
| `src/main/kotlin/agentdock/**` | move/modify | Rename package namespace to `opencodedock`; update imports/classes/log labels |
| `src/test/kotlin/agentdock/**` | move/modify | Tests follow package/runtime path changes |
| `src/main/resources/acp-adapters/index.json` | modify | Keep only `acp-adapters/opencode.json` |
| `src/main/resources/acp-adapters/*.json` | delete/keep | Remove non-OpenCode configs or leave unused; prefer delete for clarity |
| `src/main/resources/icons/*` | modify/delete | Use OpenCode icon for plugin/actions/toolwindow; remove unused provider icons |
| `src/main/kotlin/**/AcpExecutionMode.kt` | modify | Runtime dir from `.agent-dock` to `.opencode-dock` |
| `src/main/kotlin/**/AcpAdapterPaths.kt` | modify | System property from `agentdock.acp.adapter.name` to `opencodedock.acp.adapter.name`; comments |
| `src/main/kotlin/**/history/*` | modify/delete | Remove histories for providers other than OpenCode where unused |
| `src/main/kotlin/**/acp/AcpBridgeAdapterStatus.kt` | modify | Payload only OpenCode; optional model provider metadata |
| `src/main/kotlin/**/acp/AcpAgentPreferencesStore.kt` | modify | Storage is separate via new runtime dir; maybe simplify last agent/system adapter state |
| `frontend/src/components/AgentManagement.tsx` | modify | Rename Service Provider UI to OpenCode runtime; remove usage widgets for non-OpenCode |
| `frontend/src/components/SettingsView.tsx` | modify | Visible Models text and grouping for OpenCode model providers |
| `frontend/src/hooks/chatSession/agentSelection.ts` | modify | Model groups by OpenCode provider, not service provider |
| `frontend/src/components/chat/GroupedModelDropdown.tsx` | modify | Group labels become model providers; no service provider icon per group unless useful |
| `frontend/src/hooks/useAvailableAgents.ts` | modify | localStorage key from `agent-dock.adapters` to `opencode-dock.adapters` |
| `frontend/index.html` | modify | Title `OpenCode Dock` |
| `frontend/package.json`, `frontend/package-lock.json` | modify | Package name if currently `agent-dock-webview` |
| `README.md` | rewrite | OpenCode Dock only |
| `docs/images/*` | optional | Rename/update screenshots later |

## Execution Plan

### Phase 1: Product Identity And Isolation

- [ ] Rename plugin metadata in `plugin.xml`: ID `opencodedock`, name `OpenCode Dock`, vendor `Przemyslaw Segrecki`, description focused only on OpenCode.
- [ ] Change tool window ID from `Agent Dock` to `OpenCode Dock`.
- [ ] Change action IDs from `AgentDock.*` to `OpenCodeDock.*`.
- [ ] Change status widget ID from `AgentDockQuotaWidget` to `OpenCodeDockQuotaWidget`.
- [ ] Change plugin icons in `plugin.xml` from `/icons/agent_dock_toolwindow.svg` to OpenCode icon path.
- [ ] Change `pluginGroup` in `gradle.properties` to `opencodedock`.
- [ ] Verify: `git grep "AgentDock\\|Agent Dock\\|agentdock"` still shows only code package references before namespace phase.

### Phase 2: Namespace And Runtime Separation

- [ ] Move Kotlin package namespace from `agentdock` to `opencodedock`.
- [ ] Update generated `BuildConfig.kt` package/output path in `build.gradle.kts`.
- [ ] Rename key classes where useful: `AgentDockToolWindowFactory` to `OpenCodeDockToolWindowFactory`, `AgentDockQuotaWidget*` to `OpenCodeDockQuotaWidget*`, `AgentDockHistoryService` to `OpenCodeDockHistoryService`.
- [ ] Update `plugin.xml` class references to the new package/classes.
- [ ] Change runtime dir from `~/.agent-dock` to `~/.opencode-dock`.
- [ ] Change system property `agentdock.acp.adapter.name` to `opencodedock.acp.adapter.name`.
- [ ] Update frontend localStorage keys from `agent-dock.*` to `opencode-dock.*`.
- [ ] Verify: old Agent Dock and OpenCode Dock no longer share plugin ID, action IDs, widget ID, tool window ID, runtime files, or localStorage keys.

### Phase 3: OpenCode-Only Adapter Backend

- [ ] Change `acp-adapters/index.json` to include only `acp-adapters/opencode.json`.
- [ ] Remove unused adapter JSON files for Claude, Codex, Cursor, Gemini, Copilot, Kilo, Qoder.
- [ ] Remove backend code paths that are only for deleted providers if they become unreachable: provider-specific history strategies and usage fetchers.
- [ ] Keep ACP protocol dependency `com.agentclientprotocol:acp`, because OpenCode still uses ACP.
- [ ] Verify: `pushAdapters()` returns only `opencode`.

### Phase 4: OpenCode-Only Frontend UX

- [ ] Rename UI labels from `Service Provider`, `Agent`, and generic provider wording to OpenCode-specific wording.
- [ ] Simplify management screen to show only OpenCode install/update/auth/runtime status.
- [ ] Remove usage components for deleted providers: `ClaudeUsage`, `CodexUsage`, `GeminiUsage`, `CopilotUsage`, `CursorUsage`, `QoderUsage`, unless OpenCode usage support is added separately.
- [ ] Remove switching between service providers from new-chat UX; keep model and mode selection.
- [ ] Verify: frontend has no visible multi-provider management UX.

### Phase 5: Model Grouping By OpenCode Model Provider

- [ ] Extend model picker grouping so all groups are derived from OpenCode model IDs/names, not adapter names.
- [ ] Preferred grouping rule: if model ID contains `/`, use prefix before `/` as provider group, for example `openai`, `anthropic`, or `google`.
- [ ] Add fallback group `OpenCode` or `Other` for model IDs without provider prefix.
- [ ] Apply the same grouping in `Visible Models` settings.
- [ ] Preserve hidden model preferences by model ID under the single `opencode` adapter.
- [ ] Verify: model dropdown shows one OpenCode runtime with grouped model providers, not multiple service providers.

### Phase 6: Docs, Tests, Cleanup

- [ ] Rewrite `README.md` for `OpenCode Dock`.
- [ ] Update frontend package metadata/title.
- [ ] Remove unused icons and screenshots only if not referenced.
- [ ] Update tests for new package and `.opencode-dock` storage path.
- [ ] Run `./gradlew.bat test`.
- [ ] Run `./gradlew.bat buildPlugin`.
- [ ] If feasible, run plugin sandbox with `./gradlew.bat runIde` and verify tool window/action registration.

## Rollback Plan

If namespace/package rename causes wide build breakage:

1. Keep the branch `opencode-dock` and inspect with `git diff`.
2. Revert only the namespace phase with targeted patches, not `git reset --hard`.
3. Fall back to minimal coexistence isolation: plugin ID, action IDs, widget IDs, tool window ID, runtime dir, localStorage keys.
4. Re-run `./gradlew.bat test` after each recovery step.

## Risks

- Full package rename touches many files and can create import/test churn. Mitigation: do it as one mechanical phase, then build immediately.
- OpenCode model provider grouping depends on model ID shape returned by OpenCode ACP. Mitigation: implement robust fallback for IDs without `/`.
- OpenCode icon may not meet JetBrains tool window icon conventions. Mitigation: first use existing OpenCode SVG as requested, then adjust only if IDE rendering/build complains.
- History migration is intentionally not planned. This keeps OpenCode Dock independent from Agent Dock and avoids shared state.
- Some code still uses generic names like `adapter` internally. That can remain if it is internal ACP terminology, but user-facing naming should become OpenCode-specific.

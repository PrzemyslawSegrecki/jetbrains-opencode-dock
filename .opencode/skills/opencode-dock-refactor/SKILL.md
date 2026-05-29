---
name: opencode-dock-refactor
description: Use when refactoring the OpenCode Dock JetBrains plugin from Agent Dock. Covers plugin identity migration, Kotlin namespace rename, ACP adapter cleanup, React/TypeScript frontend simplification, model provider grouping, and documentation. Trigger keywords: opencode dock, agent dock, refactor, plugin.xml, gradle, acp-adapters, model grouping, namespace rename.
---

# OpenCode Dock Refactor

This skill guides the 6-phase refactor of the OpenCode Dock plugin from its Agent Dock origins.

## Context

The plugin is a JetBrains plugin with ID `agentdock` that must become an independent product named `OpenCode Dock` with ID `opencodedock`.
The full plan lives in `docs/opencode-dock-refactor-plan.md`.

## Execution Rules

1. Always proceed phase by phase. Do not skip phases.
2. Ask for user confirmation before starting a phase unless they explicitly said to continue without stopping.
3. Verify after each phase with `git diff` and relevant build/test commands.
4. Use associated skills: `refactor-plan`, `clean-code`, `typescript-refactor`, `docs`.
5. If a phase fails, follow the rollback steps in the plan before continuing.

## Phases

### Phase 1: Product Identity And Isolation
- Rename plugin metadata in `plugin.xml`: ID `opencodedock`, name `OpenCode Dock`, vendor `Przemyslaw Segrecki`.
- Change tool window ID to `OpenCode Dock`, action IDs to `OpenCodeDock.*`, widget ID to `OpenCodeDockQuotaWidget`.
- Update `gradle.properties` `pluginGroup` to `opencodedock`.
- Update icons to OpenCode branding.
- **Verify**: `git grep "AgentDock\\|Agent Dock\\|agentdock"` shows only code package references.

### Phase 2: Namespace And Runtime Separation
- Move Kotlin package from `agentdock` to `opencodedock`.
- Rename key classes: `AgentDockToolWindowFactory` → `OpenCodeDockToolWindowFactory`, `AgentDockQuotaWidget*` → `OpenCodeDockQuotaWidget*`, `AgentDockHistoryService` → `OpenCodeDockHistoryService`.
- Update `BuildConfig.kt` package path in `build.gradle.kts`.
- Change runtime dir from `~/.agent-dock` to `~/.opencode-dock`.
- Change system property `agentdock.acp.adapter.name` → `opencodedock.acp.adapter.name`.
- Update frontend localStorage keys from `agent-dock.*` to `opencode-dock.*`.
- **Verify**: old and new versions share no plugin ID, action IDs, widget ID, tool window ID, runtime files, or localStorage keys.

### Phase 3: OpenCode-Only Adapter Backend
- Change `acp-adapters/index.json` to include only `acp-adapters/opencode.json`.
- Remove unused adapter JSON files: Claude, Codex, Cursor, Gemini, Copilot, Kilo, Qoder.
- Remove backend code paths for deleted providers (provider-specific history strategies, usage fetchers).
- Keep ACP dependency `com.agentclientprotocol:acp`.
- **Verify**: `pushAdapters()` returns only `opencode`.

### Phase 4: OpenCode-Only Frontend UX
- Rename UI labels from `Service Provider`, `Agent` to OpenCode-specific wording.
- Simplify `AgentManagement.tsx` to show only OpenCode install/update/auth/runtime status.
- Remove usage components for deleted providers: `ClaudeUsage`, `CodexUsage`, `GeminiUsage`, `CopilotUsage`, `CursorUsage`, `QoderUsage`.
- Remove provider switching from new-chat UX; keep model and mode selection.
- **Verify**: no visible multi-provider management UX remains.

### Phase 5: Model Grouping By OpenCode Model Provider
- Extend model picker grouping in `GroupedModelDropdown.tsx` and `agentSelection.ts`.
- Rule: if model ID contains `/`, use prefix before `/` as provider group (e.g., `openai`, `anthropic`, `google`).
- Fallback group `Other` for model IDs without provider prefix.
- Apply same grouping in `Visible Models` settings (`SettingsView.tsx`).
- Preserve hidden model preferences by model ID under single `opencode` adapter.
- **Verify**: model dropdown shows one OpenCode runtime with grouped model providers.

### Phase 6: Docs, Tests, Cleanup
- Rewrite `README.md` for `OpenCode Dock`.
- Update frontend package metadata/title (`package.json`, `index.html`).
- Remove unused icons and screenshots only if unreferenced.
- Update tests for new package and `.opencode-dock` storage path.
- Run `./gradlew.bat test`.
- Run `./gradlew.bat buildPlugin`.
- If feasible, run sandbox with `./gradlew.bat runIde` and verify registration.

## Rollback

If namespace rename causes wide build breakage:
1. Keep branch `opencode-dock` and inspect with `git diff`.
2. Revert only namespace phase with targeted patches.
3. Fall back to minimal coexistence isolation (plugin ID, action IDs, widget IDs, tool window ID, runtime dir, localStorage keys).
4. Re-run `./gradlew.bat test` after each recovery step.

## Risks

- **Full package rename** touches many files. Mitigation: do as one mechanical phase, then build immediately.
- **Model provider grouping** depends on OpenCode ACP model ID shape. Mitigation: robust fallback for IDs without `/`.
- **History migration** is intentionally not planned to keep independence.

# Refactor Plan: OpenCode Dock Follow-Up UX And Performance

## Current State

OpenCode-only backend is already in place, but a few flows still behave like leftovers from the multi-provider architecture:

- The OpenCode runtime starts in the background on project startup, but the actual chat session connection still starts lazily only on first `Send`.
  - `src/main/kotlin/agentdock/acp/AcpStartupActivity.kt`
  - `src/main/kotlin/agentdock/acp/AcpAdapterInitializer.kt`
  - `frontend/src/hooks/useChatSession.ts`
- The `OpenCode` tab still exists as a separate singleton management tab with `Enable/Disable`, install, update, and auth actions.
  - `frontend/src/components/AgentManagement.tsx`
  - `frontend/src/App.tsx`
  - `frontend/src/components/AppTabContent.tsx`
  - `frontend/src/components/TabBar.tsx`
- History loads the full conversation list at once and renders it without pagination.
  - `frontend/src/components/HistoryPanel.tsx`
  - `frontend/src/components/history/useHistoryPanelController.ts`
  - `src/main/kotlin/agentdock/history/HistoryBridge.kt`
  - `src/main/kotlin/agentdock/history/HistorySyncService.kt`
  - `src/main/kotlin/agentdock/acp/AcpSessionListing.kt`
- Model visibility settings still exist and group models by provider, but the UX likely needs improvement or better discoverability.
  - `frontend/src/components/SettingsView.tsx`
  - `frontend/src/hooks/chatSession/agentSelection.ts`
  - `frontend/src/utils/bridge.ts`
  - `src/main/kotlin/agentdock/acp/AcpAgentPreferencesStore.kt`

## Target State

After these follow-up changes, the plugin should behave like this:

- OpenCode chat session connect starts automatically when a fresh active chat opens, without waiting for the first message.
- There is no separate `OpenCode` runtime-management tab.
- A compact runtime status icon appears in the top bar near `New`, `History`, and the hamburger menu:
  - green: ready,
  - yellow: connecting / initializing,
  - red: error / missing OpenCode / runtime problem.
- Detailed runtime state is shown only in a tooltip on hover.
- The plugin no longer offers OpenCode install/update/enable/disable flows.
- The plugin assumes `opencode` is installed system-wide; if not, it shows a clear diagnostic message.
- History initially loads a limited number of items, for example 10, with a `More` action to reveal more.
- `Settings` clearly allow hiding/showing:
  - provider groups,
  - individual models inside each provider group.

## Affected Files

| File | Change Type | Dependencies |
|------|-------------|--------------|
| `frontend/src/hooks/useChatSession.ts` | modify | auto-connect behavior, chat lifecycle |
| `frontend/src/hooks/chatSession/useAgentRuntimeOptions.ts` | review/modify | model/mode sync after eager connect |
| `src/main/kotlin/agentdock/acp/AcpSessionLifecycle.kt` | maybe modify | only if eager frontend connect exposes race/health issues |
| `frontend/src/components/TabBar.tsx` | modify | add runtime status icon near top actions |
| `frontend/src/components/chat/shared/Tooltip.tsx` | reuse/maybe modify | status details tooltip |
| `frontend/src/components/AgentManagement.tsx` | remove or replace | blocked by top-bar status implementation |
| `frontend/src/components/AppTabContent.tsx` | modify | remove `management` view route |
| `frontend/src/App.tsx` | modify | remove singleton `OpenCode` tab opening |
| `frontend/src/components/tabbar/HamburgerMenuPanel.tsx` | modify | remove `OpenCode` menu entry |
| `frontend/src/components/EmptyStateView.tsx` | modify | remove management shortcut, align no-runtime messaging |
| `frontend/src/types/chat.ts` | modify | if a dedicated runtime status shape/helper is introduced |
| `src/main/kotlin/agentdock/acp/AcpBridgeAdapterStatus.kt` | modify | simplify payload for system-only OpenCode status if needed |
| `src/main/kotlin/agentdock/acp/AcpAdapterInitializer.kt` | modify | remove install/download assumptions, system-runtime readiness |
| `src/main/kotlin/agentdock/acp/AcpAdapterLaunchSupport.kt` | modify | detect missing `opencode` and expose clearer failure |
| `src/main/kotlin/agentdock/acp/AcpAdapterConfig.kt` | review | confirm OpenCode remains system/runtime-only |
| `frontend/src/components/HistoryPanel.tsx` | modify | add page size / More UX |
| `frontend/src/components/history/useHistoryPanelController.ts` | modify | client-side incremental exposure and request behavior |
| `frontend/src/components/EmptyStateView.tsx` | modify | avoid full-history polling just to show recent items |
| `src/main/kotlin/agentdock/history/HistoryBridge.kt` | maybe modify | add paged history API if frontend-only slicing is not enough |
| `src/main/kotlin/agentdock/history/HistorySyncService.kt` | maybe modify | backend limit/sort optimization |
| `src/main/kotlin/agentdock/acp/AcpSessionListing.kt` | maybe modify | limit session discovery if current full scan is too heavy |
| `frontend/src/components/SettingsView.tsx` | modify | make provider/model visibility controls explicit and usable |
| `src/main/kotlin/agentdock/acp/AcpAgentPreferencesStore.kt` | maybe modify | persist provider-level hidden state if added explicitly |
| `frontend/src/hooks/chatSession/agentSelection.ts` | modify | apply provider-hidden state together with hidden models |
| `frontend/src/utils/bridge.ts` | maybe modify | bridge payload for provider visibility if needed |

## Execution Plan

### Phase 1: Runtime Startup And Auto-Connect

- [ ] Trace and document the exact difference between backend runtime initialization and frontend chat-session connection.
- [ ] Change chat startup so the active new chat auto-runs `startAgent()` when the tab becomes active and OpenCode is the selected runtime.
- [ ] Prevent duplicate/eager reconnect loops when switching tabs, opening history chats, or restoring an already-started chat.
- [ ] Ensure missing `opencode` binary yields a stable error state instead of a delayed first-send failure.
- [ ] Verify:
  - Open a fresh chat and confirm status moves to connecting/ready before first message.
  - Open a history chat and confirm it does not wrongly auto-start a new live session.
  - Run `./gradlew.bat compileKotlin` and `npm run build`.

### Phase 2: Replace OpenCode Tab With Compact Runtime Status

- [ ] Define a small derived runtime-status model from existing adapter fields: `ready`, `initializing`, `initializationError`, auth/runtime diagnostics.
- [ ] Add a status icon to `TabBar.tsx` near `New` / `History` / hamburger.
- [ ] Reuse tooltip UI to show detailed runtime state only on hover.
- [ ] Remove navigation paths that open the `OpenCode` management tab:
  - app singleton tab opening,
  - hamburger menu item,
  - empty-state shortcut.
- [ ] Remove or fully retire `AgentManagement.tsx` if no longer needed.
- [ ] Verify:
  - No visible `OpenCode` / `Service providers` tab entry remains.
  - Status icon changes correctly between ready/connecting/error states.
  - `npm run build` passes.

### Phase 3: Remove Plugin-Side Install/Enable/Disable Flows

- [ ] Remove install/update/download/cancel-install UI and bridge actions from frontend where no longer relevant.
- [ ] Remove `Enable/Disable` controls and any behavior that assumes optional plugin-managed runtime installation.
- [ ] Simplify backend assumptions to system-installed OpenCode only.
- [ ] Standardize the missing-runtime message shown when `opencode` is not found.
- [ ] Verify:
  - No install/update/enable/disable actions remain in UI.
  - Missing `opencode` path shows a user-readable error.
  - Kotlin compile still passes.

### Phase 4: History Performance And Incremental Loading

- [ ] Measure the current list path and decide the minimal safe first step:
  - frontend-only initial slice of 10 with `More`,
  - or backend paged API if full list transfer is still too slow.
- [ ] Implement initial page size for `HistoryPanel`.
- [ ] Add `More` action to reveal additional batches.
- [ ] Stop loading the entire history repeatedly for `EmptyStateView`; fetch less often or reuse already-loaded state.
- [ ] If needed, add backend pagination/limit support in `HistoryBridge` and `HistorySyncService`.
- [ ] Verify:
  - History tab opens quickly with 90+ items.
  - `More` loads subsequent items without breaking selection/open behavior.
  - Empty state no longer polls a heavy full history list every 5 seconds.

### Phase 5: Settings For Provider And Model Visibility

- [ ] Re-check why the current `Visible Models` UI feels missing or insufficient despite existing code.
- [ ] Clarify the UX so users can explicitly hide/show:
  - provider groups,
  - individual models.
- [ ] Decide whether provider hide is:
  - derived from hiding all models in a group,
  - or stored explicitly as separate provider visibility state.
- [ ] If explicit provider visibility is needed, extend preferences and filtering logic accordingly.
- [ ] Ensure hidden provider/model state applies consistently in:
  - model picker,
  - visible models settings,
  - any Git commit model selection settings.
- [ ] Verify:
  - Hiding a provider removes its models from picker.
  - Hiding individual models still works.
  - Preferences persist after refresh/restart.

### Phase 6: Cleanup And Validation

- [ ] Remove dead code, tab types, bridge handlers, and props left after management/install-flow removal.
- [ ] Update README or help text if runtime assumptions changed materially.
- [ ] Run:
  - `npm run build` in `frontend`
  - `./gradlew.bat test`
  - `./gradlew.bat buildPlugin`
- [ ] Optionally smoke-test the plugin UI manually for:
  - fresh chat,
  - history tab,
  - missing runtime message,
  - status tooltip.

## Rollback Plan

If something fails:

1. Revert only the affected phase with targeted patches, especially for:
   - eager auto-connect in `useChatSession.ts`,
   - history paging API changes.
2. If auto-connect causes unstable session churn, fall back to:
   - eager backend runtime warm-up only,
   - but keep first-send session creation.
3. If backend pagination becomes risky, keep backend full-list API and implement only frontend incremental reveal first.
4. Re-run `npm run build` and `./gradlew.bat compileKotlin` after each recovery step.

## Risks

- Auto-connect may accidentally start sessions for tabs that should stay passive, especially history/replay tabs.
- Removing the management tab can also remove the only visible place where detailed runtime diagnostics currently live, so tooltip quality matters.
- History slowness may come from backend full session discovery, not only frontend rendering; frontend slicing alone may help perceived speed but not total request cost.
- Provider visibility already exists partly via grouped hidden models, so adding a second provider-hide abstraction can create duplicated state unless designed carefully.

## Open Questions

1. Should the status icon be informational only, or should clicking it open runtime diagnostics/logs?
2. For history, do you prefer fixed batches of `10`, or `10` initially and then `+20` per `More` click?
3. For provider visibility, is hiding all models in a provider enough, or do you want a separate provider-level checkbox independent from model checkboxes?

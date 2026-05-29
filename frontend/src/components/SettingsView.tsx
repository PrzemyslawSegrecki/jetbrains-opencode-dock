import { ChangeEvent, useEffect, useRef, useState } from 'react';
import { AgentOption, AudioTranscriptionFeatureState, AudioTranscriptionSettings, GitCommitGenerationSettings as GitCommitGenerationSettingsValue, GlobalSettingsPayload } from '../types/chat';
import { ACPBridge } from '../utils/bridge';
import ConfirmationModal from './ConfirmationModal';
import { GitCommitGenerationSettings } from './settings/GitCommitGenerationSettings';
import { SettingsCardShell } from './settings/SettingsCardShell';
import { SettingsSelectCard } from './settings/SettingsSelectCard';
import { SettingsToggleCard } from './settings/SettingsToggleCard';
import { Button } from './ui/Button';
import { DropdownOption, DropdownSelect } from './ui/DropdownSelect';
import { getModelProviderGroupLabel } from '../hooks/chatSession/agentSelection';

const defaultGlobalSettings: GlobalSettingsPayload = {
  settings: {
    audioNotificationsEnabled: true,
    uiFontSizeOffsetPx: 0,
    userMessageBackgroundStyle: 'default',
    audioTranscription: { language: 'auto' },
    gitCommitGeneration: { enabled: false, adapterId: '', modelId: '', instructions: '' },
  },
};

function SettingsLoadingSpinner({ className = 'w-3.5 h-3.5' }: { className?: string }) {
  return <div className={`${className} shrink-0 rounded-full border-2 border-current border-t-transparent animate-spin`} />;
}

function normalizeGitCommitGenerationSettings(payload: Partial<GitCommitGenerationSettingsValue> | undefined): GitCommitGenerationSettingsValue {
  return {
    enabled: Boolean(payload?.enabled),
    adapterId: payload?.adapterId?.trim() ?? '',
    modelId: payload?.modelId?.trim() ?? '',
    instructions: payload?.instructions ?? '',
  };
}

function normalizeGlobalSettings(payload: Partial<GlobalSettingsPayload> | undefined): GlobalSettingsPayload {
  const uiFontSizeOffsetPx = Number.isFinite(payload?.settings?.uiFontSizeOffsetPx)
    ? Math.max(-3, Math.min(3, Math.round(payload!.settings!.uiFontSizeOffsetPx)))
    : 0;
  return {
    settings: {
      audioNotificationsEnabled: payload?.settings?.audioNotificationsEnabled ?? true,
      uiFontSizeOffsetPx,
      userMessageBackgroundStyle: (() => {
        const raw = payload?.settings?.userMessageBackgroundStyle;
        if (typeof raw === 'string' && raw.startsWith('custom:')) {
          const hex = raw.slice('custom:'.length);
          return /^#[0-9a-fA-F]{6}$/.test(hex) ? raw : 'default';
        }
        return userMessageBackgroundOptions.some((option) => option.id === raw) ? raw! : 'default';
      })(),
      audioTranscription: payload?.settings?.audioTranscription ?? { language: 'auto' },
      gitCommitGeneration: normalizeGitCommitGenerationSettings(payload?.settings?.gitCommitGeneration),
    },
  };
}

function readIdeFontSizePx(): number {
  if (typeof window === 'undefined') {
    return 14;
  }
  const value = window.getComputedStyle(document.documentElement).getPropertyValue('--ide-font-size').trim();
  const px = Number.parseFloat(value);
  return Number.isFinite(px) ? Math.round(px) : 14;
}

const userMessageBackgroundOptions: Array<{
  id: string;
  background: string;
  toneClass: string;
}> = [
  { id: 'default', background: 'var(--ide-user-message-default-bg)', toneClass: 'bg-[var(--ide-user-message-default-bg)]' },
  { id: 'blue', background: 'var(--ide-user-message-blue-bg)', toneClass: 'bg-[var(--ide-user-message-blue-bg)]' },
  { id: 'green', background: 'var(--ide-user-message-green-bg)', toneClass: 'bg-[var(--ide-user-message-green-bg)]' },
  { id: 'purple', background: 'var(--ide-user-message-purple-bg)', toneClass: 'bg-[var(--ide-user-message-purple-bg)]' },
  { id: 'orange', background: 'var(--ide-user-message-orange-bg)', toneClass: 'bg-[var(--ide-user-message-orange-bg)]' },
  { id: 'teal', background: 'var(--ide-user-message-teal-bg)', toneClass: 'bg-[var(--ide-user-message-teal-bg)]' },
  { id: 'rose', background: 'var(--ide-user-message-rose-bg)', toneClass: 'bg-[var(--ide-user-message-rose-bg)]' },
  { id: 'background-secondary', background: 'var(--ide-background-secondary)', toneClass: 'bg-background-secondary' },
  { id: 'primary', background: 'var(--ide-Button-default-startBackground)', toneClass: 'bg-primary' },
  { id: 'secondary', background: 'var(--ide-Button-startBackground)', toneClass: 'bg-secondary' },
  { id: 'accent', background: 'var(--ide-List-selectionBackground)', toneClass: 'bg-accent' },
  { id: 'input', background: 'var(--ide-TextField-background)', toneClass: 'bg-input' },
  { id: 'editor-bg', background: 'var(--ide-editor-bg)', toneClass: 'bg-[var(--ide-editor-bg)]' },
];

const emptyState: AudioTranscriptionFeatureState = {
  id: 'whisper-transcription',
  title: 'Audio Input',
  installed: false,
  installing: false,
  supported: false,
  status: 'Loading',
  detail: '',
  installPath: '',
};

const whisperLanguageOptions: DropdownOption[] = [
  { value: 'auto', label: 'auto' },
  { value: 'en', label: 'English (en)' },
  { value: 'de', label: 'German (de)' },
  { value: 'lv', label: 'Latvian (lv)' },
  { value: 'fr', label: 'French (fr)' },
  { value: 'es', label: 'Spanish (es)' },
];

function applyUserMessageTheme(styleId: GlobalSettingsPayload['settings']['userMessageBackgroundStyle']) {
  if (styleId.startsWith('custom:')) {
    const hex = styleId.slice('custom:'.length);
    document.documentElement.style.setProperty('--user-message-bg', hex);
    return;
  }
  const selected = userMessageBackgroundOptions.find((option) => option.id === styleId) ?? userMessageBackgroundOptions[0];
  document.documentElement.style.setProperty('--user-message-bg', selected.background);
}

function isModelVisible(agent: AgentOption, modelId: string): boolean {
  return !(agent.hiddenModels ?? []).includes(modelId);
}

function normalizeHiddenModelIds(modelIds: string[]): string[] {
  return Array.from(new Set(modelIds.filter(Boolean))).sort();
}

function sameHiddenModelIds(left: string[] | undefined, right: string[] | undefined): boolean {
  const normalizedLeft = normalizeHiddenModelIds(left ?? []);
  const normalizedRight = normalizeHiddenModelIds(right ?? []);
  if (normalizedLeft.length !== normalizedRight.length) return false;
  return normalizedLeft.every((value, index) => value === normalizedRight[index]);
}

function buildHiddenModelsForVisibility(agent: AgentOption, modelIds: string[], visible: boolean): string[] {
  const hiddenModels = new Set(agent.hiddenModels ?? []);
  modelIds.forEach((modelId) => {
    if (visible) {
      hiddenModels.delete(modelId);
      return;
    }
    hiddenModels.add(modelId);
  });
  return normalizeHiddenModelIds(Array.from(hiddenModels));
}

function applyHiddenModelsToAgents(agents: AgentOption[], adapterId: string, hiddenModels: string[]): AgentOption[] {
  return agents.map((agent) => (
    agent.id === adapterId
      ? { ...agent, hiddenModels }
      : agent
  ));
}

function groupModelsByProvider(agent: AgentOption): Array<{ label: string; modelIds: string[]; models: NonNullable<AgentOption['availableModels']> }> {
  const groups = new Map<string, NonNullable<AgentOption['availableModels']>>();

  (agent.availableModels ?? []).forEach((model) => {
    const label = getModelProviderGroupLabel(model.modelId);
    const existing = groups.get(label);
    if (existing) {
      existing.push(model);
      return;
    }
    groups.set(label, [model]);
  });

  return Array.from(groups.entries()).map(([label, models]) => ({
    label,
    modelIds: models.map((model) => model.modelId),
    models,
  }));
}

export function SettingsView() {
  const [feature, setFeature] = useState<AudioTranscriptionFeatureState>(emptyState);
  const [settings, setSettings] = useState<AudioTranscriptionSettings>({ language: 'auto' });
  const [globalSettings, setGlobalSettings] = useState<GlobalSettingsPayload>(defaultGlobalSettings);
  const [installedAgents, setInstalledAgents] = useState<AgentOption[]>([]);
  const [pendingAudioInputUninstall, setPendingAudioInputUninstall] = useState(false);
  const [uiFontSizeBasePx, setUiFontSizeBasePx] = useState(() => readIdeFontSizePx());
  const installedAgentsRef = useRef<AgentOption[]>([]);
  const lastToggledModelIndexByAdapterRef = useRef<Record<string, number>>({});
  const uiFontSizeOptions = Array.from({ length: 7 }, (_, index) => {
    const offset = index - 3;
    const px = uiFontSizeBasePx + offset;
    return {
      offset,
      label: offset === 0 ? `${px}px (default)` : `${px}px`,
    };
  });
  const uiFontSizeSelectOptions: DropdownOption[] = uiFontSizeOptions.map((option) => ({
    value: String(option.offset),
    label: option.label,
  }));

  useEffect(() => {
    document.documentElement.style.setProperty('--ui-font-size-offset', `${globalSettings.settings.uiFontSizeOffsetPx}px`);
  }, [globalSettings.settings.uiFontSizeOffsetPx]);

  useEffect(() => {
    setUiFontSizeBasePx(readIdeFontSizePx());
  }, [globalSettings]);

  useEffect(() => {
    applyUserMessageTheme(globalSettings.settings.userMessageBackgroundStyle);
  }, [globalSettings.settings.userMessageBackgroundStyle]);

  const updateInstalledAgents = (updater: (prev: AgentOption[]) => AgentOption[]) => {
    setInstalledAgents((prev) => {
      const next = updater(prev);
      installedAgentsRef.current = next;
      return next;
    });
  };

  useEffect(() => {
    const requestSettings = () => {
      ACPBridge.loadAudioTranscriptionFeature();
      ACPBridge.loadAudioTranscriptionSettings();
      ACPBridge.loadGlobalSettings();
      ACPBridge.requestAdapters();
    };

    const cleanupFeature = ACPBridge.onAudioTranscriptionFeature((e) => {
      setFeature(e.detail.state);
    });
    const cleanupSettings = ACPBridge.onAudioTranscriptionSettings((e) => {
      setSettings(e.detail.settings);
    });
    const cleanupGlobalSettings = ACPBridge.onGlobalSettings((e) => {
      setGlobalSettings(normalizeGlobalSettings(e.detail?.payload));
    });
    const cleanupAdapters = ACPBridge.onAdapters((e) => {
      const nextInstalledAgents = Array.isArray(e.detail.adapters)
        ? e.detail.adapters.filter((agent) => agent.downloaded === true)
        : [];
      installedAgentsRef.current = nextInstalledAgents;
      setInstalledAgents(nextInstalledAgents);
    });

    const handleBridgeReady = () => {
      requestSettings();
    };

    if (window.__settingsBridgeReady) {
      requestSettings();
    } else {
      window.addEventListener('settings-bridge-ready', handleBridgeReady);
    }

    return () => {
      cleanupFeature();
      cleanupSettings();
      cleanupGlobalSettings();
      cleanupAdapters();
      window.removeEventListener('settings-bridge-ready', handleBridgeReady);
    };
  }, []);

  const actionLabel = feature.installed ? 'Uninstall' : 'Install';
  const showAudioInputDetails = feature.installed || feature.installing;

  const handleAudioInputAction = () => {
    if (feature.installed) {
      setPendingAudioInputUninstall(true);
      return;
    }
    ACPBridge.installAudioTranscriptionFeature();
  };

  const confirmAudioInputUninstall = () => {
    ACPBridge.uninstallAudioTranscriptionFeature();
    setPendingAudioInputUninstall(false);
  };

  const handleLanguageChange = (language: string) => {
    const next = { language };
    setSettings(next);
    ACPBridge.saveAudioTranscriptionSettings(next);
  };

  const handleGitCommitGenerationChange = (gitCommitGeneration: GitCommitGenerationSettingsValue) => {
    const next = { ...globalSettings.settings, gitCommitGeneration };
    setGlobalSettings(prev => ({ ...prev, settings: next }));
    ACPBridge.saveGlobalSettings(next);
  };

  const handleAudioNotificationsChange = (audioNotificationsEnabled: boolean) => {
    const next = { ...globalSettings.settings, audioNotificationsEnabled };
    setGlobalSettings(prev => ({ ...prev, settings: next }));
    ACPBridge.saveGlobalSettings(next);
  };

  

  const handleUiFontSizeChange = (uiFontSizeOffsetPx: number) => {
    const next = { ...globalSettings.settings, uiFontSizeOffsetPx };
    setGlobalSettings(prev => ({ ...prev, settings: next }));
    ACPBridge.saveGlobalSettings(next);
  };

  const handleUserMessageBackgroundStyleChange = (userMessageBackgroundStyle: GlobalSettingsPayload['settings']['userMessageBackgroundStyle']) => {
    const next = { ...globalSettings.settings, userMessageBackgroundStyle };
    setGlobalSettings(prev => ({ ...prev, settings: next }));
    ACPBridge.saveGlobalSettings(next);
  };

  const persistHiddenModels = (adapterId: string, hiddenModels: string[]) => {
    updateInstalledAgents((prev) => applyHiddenModelsToAgents(prev, adapterId, hiddenModels));
    ACPBridge.setHiddenModels({ adapterId, modelIds: hiddenModels });
  };

  const handleModelVisibilityChange = (adapterId: string, modelIds: string[], visible: boolean) => {
    const agent = installedAgentsRef.current.find((item) => item.id === adapterId);
    if (!agent) return;

    const nextHiddenModels = buildHiddenModelsForVisibility(agent, modelIds, visible);
    if (sameHiddenModelIds(agent.hiddenModels, nextHiddenModels)) return;
    persistHiddenModels(adapterId, nextHiddenModels);
  };

  const handleModelVisibilityInputChange = (agent: AgentOption, modelId: string, event: ChangeEvent<HTMLInputElement>) => {
    const availableModels = agent.availableModels ?? [];
    const currentIndex = availableModels.findIndex((model) => model.modelId === modelId);
    if (currentIndex < 0) return;

    const lastIndex = lastToggledModelIndexByAdapterRef.current[agent.id];
    lastToggledModelIndexByAdapterRef.current[agent.id] = currentIndex;
    const shiftPressed = 'shiftKey' in event.nativeEvent && Boolean((event.nativeEvent as MouseEvent).shiftKey);

    if (shiftPressed && typeof lastIndex === 'number' && lastIndex >= 0) {
      const startIndex = Math.min(lastIndex, currentIndex);
      const endIndex = Math.max(lastIndex, currentIndex);
      const modelIds = availableModels.slice(startIndex, endIndex + 1).map((model) => model.modelId);
      handleModelVisibilityChange(agent.id, modelIds, event.target.checked);
      return;
    }

    handleModelVisibilityChange(agent.id, [modelId], event.target.checked);
  };

  const agentsWithModels = installedAgents.filter((agent) => (agent.availableModels?.length ?? 0) > 0);

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="flex-1 overflow-y-auto w-full px-2 py-2">
        <div className="mx-auto flex w-full max-w-[1200px] flex-col divide-y divide-border">

          <SettingsSelectCard title="Base Font Size">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-ide-small text-foreground-secondary">Size: </span>
              <DropdownSelect
                value={String(globalSettings.settings.uiFontSizeOffsetPx)}
                onChange={(value) => handleUiFontSizeChange(Number(value))}
                options={uiFontSizeSelectOptions}
                className="min-w-[180px]"
              />
            </div>
          </SettingsSelectCard>

          <SettingsCardShell
            title="User Message Background"
            description="Choose the background color used for your chat messages:"
          >
            <div className="mt-2 flex flex-wrap items-center gap-1">
              {userMessageBackgroundOptions.map((option) => {
                const selected = globalSettings.settings.userMessageBackgroundStyle === option.id;
                return (
                  <button
                    key={option.id}
                    type="button"
                    onClick={() => handleUserMessageBackgroundStyleChange(option.id)}
                    aria-pressed={selected}
                    className={`h-7 w-7 rounded-[4px] border border-[var(--ide-Button-disabledBorderColor)] focus:outline-none focus-visible:shadow-[0_0_0_1px_var(--ide-Button-default-focusColor)] ${
                      selected
                        ? 'shadow-[0_0_0_1px_var(--ide-Button-default-focusColor)]'
                        : ''
                    }`}
                  >
                    <span className={`block h-full w-full rounded-[4px] ${option.toneClass}`} />
                    <span className="sr-only">{option.id}</span>
                  </button>
                );
              })}
              {(() => {
                const isCustom = globalSettings.settings.userMessageBackgroundStyle.startsWith('custom:');
                const customHex = isCustom ? globalSettings.settings.userMessageBackgroundStyle.slice('custom:'.length) : '#3b82f6';
                return (
                  <div className="flex items-center gap-1.5">
                    <button
                      type="button"
                      onClick={() => handleUserMessageBackgroundStyleChange(`custom:${customHex}`)}
                      aria-pressed={isCustom}
                      className={`h-7 w-7 rounded-[4px] border border-[var(--ide-Button-disabledBorderColor)] focus:outline-none focus-visible:shadow-[0_0_0_1px_var(--ide-Button-default-focusColor)] relative overflow-hidden ${
                        isCustom
                          ? 'shadow-[0_0_0_1px_var(--ide-Button-default-focusColor)]'
                          : ''
                      }`}
                    >
                      <span className="block h-full w-full rounded-[4px]" style={{ backgroundColor: customHex }} />
                      <span className="sr-only">Custom color</span>
                    </button>
                    <label className="relative flex items-center gap-1 cursor-pointer">
                      <input
                        type="color"
                        value={customHex}
                        onChange={(e) => {
                          const next = `custom:${e.target.value}`;
                          handleUserMessageBackgroundStyleChange(next);
                        }}
                        className="absolute inset-0 opacity-0 w-0 h-0"
                        tabIndex={-1}
                      />
                      <span className="text-ide-small text-foreground-secondary border border-[var(--ide-Button-disabledBorderColor)] rounded-[4px] px-1.5 py-0.5 hover:bg-hover">
                        {isCustom ? customHex : 'Pick'}
                      </span>
                    </label>
                  </div>
                );
              })()}
            </div>
          </SettingsCardShell>

          <SettingsToggleCard
            title="Audio Notifications"
            description="Play sounds for new assistant messages and permission requests"
            enabled={globalSettings.settings.audioNotificationsEnabled}
            onToggle={() => handleAudioNotificationsChange(!globalSettings.settings.audioNotificationsEnabled)}
            ariaLabel="Enable audio notifications"
          />

          <SettingsCardShell
            title="Visible Models"
            description="Choose which providers and models appear in the OpenCode model picker. Toggle a provider checkbox to hide or show its whole group."
          >
            <div className="flex flex-col gap-3">
              {agentsWithModels.length === 0 ? (
                <div className="text-foreground-secondary">OpenCode model lists are not available yet.</div>
              ) : agentsWithModels.map((agent) => {
                const hiddenModels = new Set(agent.hiddenModels ?? []);
                const visibleCount = (agent.availableModels ?? []).filter((model) => !hiddenModels.has(model.modelId)).length;
                const providerGroups = groupModelsByProvider(agent);

                return (
                  <div key={agent.id} className="rounded-[6px] border border-border px-3 py-3">
                    <div className="mb-2 flex items-center gap-2">
                      {agent.iconPath ? <img src={agent.iconPath} alt="" className="h-4 w-4" /> : null}
                      <span className="font-medium">{agent.name}</span>
                      <span className="text-foreground-secondary text-ide-small">{visibleCount}/{agent.availableModels?.length ?? 0} visible</span>
                      <div className="ml-auto flex items-center gap-1.5">
                        <Button
                          variant="secondary"
                          className="min-w-0 px-2 py-1 text-ide-small"
                          onClick={() => handleModelVisibilityChange(agent.id, (agent.availableModels ?? []).map((model) => model.modelId), true)}
                        >
                          Show all
                        </Button>
                        <Button
                          variant="secondary"
                          className="min-w-0 px-2 py-1 text-ide-small"
                          onClick={() => handleModelVisibilityChange(agent.id, (agent.availableModels ?? []).map((model) => model.modelId), false)}
                        >
                          Hide all
                        </Button>
                      </div>
                    </div>
                    <div className="flex flex-col gap-2">
                      {providerGroups.map((group) => {
                        const groupVisibleCount = group.models.filter((model) => !hiddenModels.has(model.modelId)).length;

                        return (
                          <div key={`${agent.id}:${group.label}`} className="rounded-[4px] border border-border/60 px-2 py-2">
                            <div className="mb-1.5 flex items-center gap-2">
                              <label className="flex cursor-pointer items-center gap-2">
                                <input
                                  type="checkbox"
                                  checked={groupVisibleCount === group.models.length}
                                  ref={(el) => {
                                    if (el) el.indeterminate = groupVisibleCount > 0 && groupVisibleCount < group.models.length;
                                  }}
                                  onChange={(event) => handleModelVisibilityChange(agent.id, group.modelIds, event.target.checked)}
                                />
                                <span className="text-[11px] uppercase tracking-[0.04em] text-foreground-secondary">{group.label}</span>
                              </label>
                              <span className="text-foreground-secondary text-ide-small">{groupVisibleCount}/{group.models.length} visible</span>
                            </div>
                            <div className="flex flex-col gap-1.5">
                              {group.models.map((model) => {
                                const visible = isModelVisible(agent, model.modelId);

                                return (
                                  <label key={model.modelId} className={`flex items-start gap-2 rounded-[4px] px-2 py-1.5 ${visible ? 'hover:bg-hover' : 'opacity-85 hover:bg-hover'}`}>
                                    <input
                                      type="checkbox"
                                      checked={visible}
                                      onChange={(event) => handleModelVisibilityInputChange(agent, model.modelId, event)}
                                      className="mt-[2px]"
                                    />
                                    <span className="min-w-0">
                                      <span className="block truncate">{model.name}</span>
                                      {model.description ? (
                                        <span className="block text-foreground-secondary text-ide-small">{model.description}</span>
                                      ) : null}
                                    </span>
                                  </label>
                                );
                              })}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
          </SettingsCardShell>

          <GitCommitGenerationSettings
            settings={globalSettings.settings.gitCommitGeneration}
            installedAgents={installedAgents}
            onChange={handleGitCommitGenerationChange}
          />

          {feature.supported && (
            <SettingsCardShell title="Audio Input">
              {showAudioInputDetails && (
                <div className="flex flex-col gap-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-ide-small text-foreground-secondary">Language:</span>
                    <DropdownSelect
                      value={settings.language}
                      onChange={handleLanguageChange}
                      options={whisperLanguageOptions}
                      disabled={!feature.installed}
                    />
                  </div>
                  {feature.installed && feature.installPath && (
                    <div className="break-all text-foreground-secondary">
                      Path: <span className="font-mono">{feature.installPath}</span>
                    </div>
                  )}
                  <div className="text-foreground-secondary">
                    Status: {feature.status}
                  </div>
                </div>
              )}
              <div>
                <Button
                  onClick={handleAudioInputAction}
                  disabled={feature.installing || (!feature.installed && !feature.supported)}
                  variant={feature.installed ? 'accentOutline' : 'install'}
                  className="text-ide-regular"
                  leftIcon={feature.installing ? <SettingsLoadingSpinner className="w-3 h-3" /> : undefined}
                >
                  <span>{actionLabel}</span>
                </Button>
              </div>
            </SettingsCardShell>
          )}
        </div>
      </div>

      <ConfirmationModal
        isOpen={pendingAudioInputUninstall}
        title="Uninstall Audio Input"
        message="Do you want to uninstall Audio Input?"
        onConfirm={confirmAudioInputUninstall}
        onCancel={() => setPendingAudioInputUninstall(false)}
      />
    </div>
  );
}

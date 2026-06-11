import { AgentOption, DropdownOption, ModeOption } from '../../types/chat';

export type ModelPickerOption = {
  agentId: string;
  modelId: string;
  label: string;
  description?: string;
};

export type ModelPickerGroup = {
  agentId: string;
  label: string;
  iconPath?: string;
  options: ModelPickerOption[];
};

export function getModelProviderGroupLabel(modelId: string | undefined): string {
  const normalized = modelId?.trim();
  if (!normalized) return 'Other';

  const slashIndex = normalized.indexOf('/');
  if (slashIndex <= 0) return 'Other';

  const provider = normalized.slice(0, slashIndex).trim();
  return provider || 'Other';
}

function buildGroupedModelOptions(
  agentId: string,
  options: ModelPickerOption[],
  iconPath?: string,
): ModelPickerGroup[] {
  const groups = new Map<string, ModelPickerOption[]>();

  options.forEach((option) => {
    const groupLabel = getModelProviderGroupLabel(option.modelId);
    const existing = groups.get(groupLabel);
    if (existing) {
      existing.push(option);
      return;
    }
    groups.set(groupLabel, [option]);
  });

  return Array.from(groups.entries()).map(([label, groupedOptions]) => ({
    agentId,
    label,
    iconPath,
    options: groupedOptions,
  }));
}

export type PinnedAgentSnapshot = {
  id: string;
  name?: string;
  iconPath?: string;
  currentModelId?: string;
  availableModels?: AgentOption['availableModels'];
  currentModeId?: string;
  availableModes?: AgentOption['availableModes'];
  currentVariant?: string;
  availableVariants?: AgentOption['availableVariants'];
};

export function toPinnedAgentSnapshot(agent: AgentOption): PinnedAgentSnapshot {
  return {
    id: agent.id,
    name: agent.name,
    iconPath: agent.iconPath,
    currentModelId: agent.currentModelId,
    availableModels: agent.availableModels,
    currentModeId: agent.currentModeId,
    availableModes: agent.availableModes,
    currentVariant: agent.currentVariant,
    availableVariants: agent.availableVariants,
  };
}

export function resolveSelectedAgent(
  selectedAgent: AgentOption | undefined,
  pinnedSnapshot: PinnedAgentSnapshot | null,
  pinnedAgentId: string
): AgentOption | undefined {
  if (selectedAgent) return selectedAgent;
  if (!pinnedSnapshot || pinnedSnapshot.id !== pinnedAgentId) return undefined;
  return {
    id: pinnedSnapshot.id,
    name: pinnedSnapshot.name,
    iconPath: pinnedSnapshot.iconPath,
    currentModelId: pinnedSnapshot.currentModelId,
    availableModels: pinnedSnapshot.availableModels,
    currentModeId: pinnedSnapshot.currentModeId,
    availableModes: pinnedSnapshot.availableModes,
    currentVariant: pinnedSnapshot.currentVariant,
    availableVariants: pinnedSnapshot.availableVariants,
  } as AgentOption;
}

export function getVisibleModels(agent: Pick<AgentOption, 'availableModels' | 'hiddenModels'>): NonNullable<AgentOption['availableModels']> {
  const availableModels = agent.availableModels ?? [];
  const hiddenModelIds = new Set((agent.hiddenModels ?? []).filter(Boolean));
  return availableModels.filter((model) => !hiddenModelIds.has(model.modelId));
}

export function buildAgentOptions(
  availableAgents: AgentOption[],
  pinnedSnapshot: PinnedAgentSnapshot | null,
  pinnedAgentId: string
): DropdownOption[] {
  const options = availableAgents.map((agent) => ({
    id: agent.id,
    label: agent.name,
    iconPath: agent.iconPath,
    subOptions: getVisibleModels(agent).map(m => ({
      id: m.modelId,
      label: m.name,
      description: m.description,
    }))
  }));

  if (
    pinnedSnapshot &&
    pinnedAgentId &&
    pinnedSnapshot.id === pinnedAgentId &&
    !options.some((option) => option.id === pinnedAgentId)
  ) {
    options.unshift({
      id: pinnedSnapshot.id,
      label: pinnedSnapshot.name || pinnedSnapshot.id,
      iconPath: pinnedSnapshot.iconPath,
      subOptions: pinnedSnapshot.availableModels?.map((model) => ({
        id: model.modelId,
        label: model.name,
        description: model.description,
      })) || (pinnedSnapshot.currentModelId ? [{
        id: pinnedSnapshot.currentModelId,
        label: pinnedSnapshot.currentModelId,
        description: undefined,
      }] : []),
    });
  }

  return options;
}

export function buildModelPickerGroups(
  availableAgents: AgentOption[],
  pinnedSnapshot: PinnedAgentSnapshot | null,
  pinnedAgentId: string
): ModelPickerGroup[] {
  const groups = availableAgents.flatMap((agent) => buildGroupedModelOptions(
    agent.id,
    getVisibleModels(agent).map((model) => ({
      agentId: agent.id,
      modelId: model.modelId,
      label: model.name,
      description: model.description,
    })),
    agent.iconPath,
  )).filter((group) => group.options.length > 0);

  if (
    pinnedSnapshot &&
    pinnedAgentId &&
    pinnedSnapshot.id === pinnedAgentId &&
    !groups.some((group) => group.agentId === pinnedAgentId)
  ) {
    const pinnedOptions = (pinnedSnapshot.availableModels ?? []).map((model) => ({
      agentId: pinnedSnapshot.id,
      modelId: model.modelId,
      label: model.name,
      description: model.description,
    }));

    if (pinnedOptions.length > 0) {
      groups.unshift(...buildGroupedModelOptions(pinnedSnapshot.id, pinnedOptions, pinnedSnapshot.iconPath));
    } else if (pinnedSnapshot.currentModelId) {
      groups.unshift({
        agentId: pinnedSnapshot.id,
        label: getModelProviderGroupLabel(pinnedSnapshot.currentModelId),
        iconPath: pinnedSnapshot.iconPath,
        options: [{
          agentId: pinnedSnapshot.id,
          modelId: pinnedSnapshot.currentModelId,
          label: pinnedSnapshot.currentModelId,
          description: undefined,
        }],
      });
    }
  }

  return groups;
}

export function buildModeOptions(availableModes: ModeOption[], selectedModeId: string): DropdownOption[] {
  const options = availableModes.map((mode) => ({
    id: mode.id,
    label: mode.name,
    description: mode.description,
  }));

  if (options.length > 0) return options;
  if (!selectedModeId) return [];
  return [{
    id: selectedModeId,
    label: selectedModeId,
    description: undefined,
  }];
}

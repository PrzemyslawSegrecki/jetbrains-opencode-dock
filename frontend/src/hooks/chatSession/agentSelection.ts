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

export type PinnedAgentSnapshot = {
  id: string;
  name?: string;
  iconPath?: string;
  currentModelId?: string;
  availableModels?: AgentOption['availableModels'];
  currentModeId?: string;
  availableModes?: AgentOption['availableModes'];
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
  const groups = availableAgents.map((agent) => ({
    agentId: agent.id,
    label: agent.name,
    iconPath: agent.iconPath,
    options: getVisibleModels(agent).map((model) => ({
      agentId: agent.id,
      modelId: model.modelId,
      label: model.name,
      description: model.description,
    })),
  })).filter((group) => group.options.length > 0);

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
      groups.unshift({
        agentId: pinnedSnapshot.id,
        label: pinnedSnapshot.name || pinnedSnapshot.id,
        iconPath: pinnedSnapshot.iconPath,
        options: pinnedOptions,
      });
    } else if (pinnedSnapshot.currentModelId) {
      groups.unshift({
        agentId: pinnedSnapshot.id,
        label: pinnedSnapshot.name || pinnedSnapshot.id,
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

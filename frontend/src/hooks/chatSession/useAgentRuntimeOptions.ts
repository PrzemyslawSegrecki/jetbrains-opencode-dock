import { MutableRefObject, useEffect, useState } from 'react';
import { AgentOption, HistorySessionMeta } from '../../types/chat';
import { getVisibleModels } from './agentSelection';

type UseAgentRuntimeOptionsArgs = {
  availableAgents: AgentOption[];
  effectiveSelectedAgent: AgentOption | undefined;
  selectedAgentId: string;
  conversationId: string;
  status: string;
  historySession?: HistorySessionMeta;
  startedAgentIdRef: MutableRefObject<string>;
  startedModelIdRef: MutableRefObject<string>;
  startedModeIdRef: MutableRefObject<string>;
  startedVariantRef: MutableRefObject<string>;
};

export function useAgentRuntimeOptions({
  availableAgents,
  effectiveSelectedAgent,
  selectedAgentId,
  conversationId,
  status,
  historySession,
  startedAgentIdRef,
  startedModelIdRef,
  startedModeIdRef,
  startedVariantRef,
}: UseAgentRuntimeOptionsArgs) {
  const [selectedModelByAgent, setSelectedModelByAgent] = useState<Record<string, string>>({});
  const [selectedModeByAgent, setSelectedModeByAgent] = useState<Record<string, string>>({});
  const [selectedVariantByAgent, setSelectedVariantByAgent] = useState<Record<string, string>>({});
  const availableModels = effectiveSelectedAgent ? getVisibleModels(effectiveSelectedAgent) : [];
  const availableModes = effectiveSelectedAgent?.availableModes ?? [];
  const availableVariants = effectiveSelectedAgent?.availableVariants ?? [];

  const resolveVisibleModelId = (agent: AgentOption | undefined, preferredModelId?: string) => {
    if (!agent) return '';
    const visibleModels = getVisibleModels(agent);
    if (preferredModelId && visibleModels.some((model) => model.modelId === preferredModelId)) {
      return preferredModelId;
    }
    return agent.currentModelId && visibleModels.some((model) => model.modelId === agent.currentModelId)
      ? agent.currentModelId
      : visibleModels[0]?.modelId || '';
  };

  const selectedModelId = effectiveSelectedAgent
    ? resolveVisibleModelId(effectiveSelectedAgent, selectedModelByAgent[effectiveSelectedAgent.id])
    : '';

  const selectedModeId = effectiveSelectedAgent
    ? (selectedModeByAgent[effectiveSelectedAgent.id] || effectiveSelectedAgent.currentModeId || availableModes[0]?.id || '')
    : '';

  const selectedVariantId = effectiveSelectedAgent
    ? (selectedVariantByAgent[effectiveSelectedAgent.id] || effectiveSelectedAgent.currentVariant || availableVariants[0] || '')
    : '';

  const modelIdForStart = selectedAgentId
    ? resolveVisibleModelId(effectiveSelectedAgent, selectedModelByAgent[selectedAgentId])
    : '';

  useEffect(() => {
    if (availableAgents.length === 0) return;
    setSelectedModelByAgent((prev) => {
      const next: Record<string, string> = { ...prev };
      availableAgents.forEach((agent) => {
        if (next[agent.id]) return;
        const currentModel = resolveVisibleModelId(agent, next[agent.id]);
        if (currentModel) next[agent.id] = currentModel;
      });
      return next;
    });

    setSelectedModeByAgent((prev) => {
      const next: Record<string, string> = { ...prev };
      availableAgents.forEach((agent) => {
        if (next[agent.id]) return;
        const currentMode = agent.currentModeId || agent.availableModes?.[0]?.id || '';
        if (currentMode) next[agent.id] = currentMode;
      });
      return next;
    });

    setSelectedVariantByAgent((prev) => {
      const next: Record<string, string> = { ...prev };
      availableAgents.forEach((agent) => {
        if (next[agent.id]) return;
        const currentVariant = agent.currentVariant || agent.availableVariants?.[0] || '';
        if (currentVariant) next[agent.id] = currentVariant;
      });
      return next;
    });
  }, [availableAgents]);

  useEffect(() => {
    if (!historySession) return;
    if (historySession.modelId) {
      setSelectedModelByAgent((prev) => ({
        ...prev,
        [historySession.adapterName]: historySession.modelId as string
      }));
    }
    if (historySession.modeId) {
      setSelectedModeByAgent((prev) => ({
        ...prev,
        [historySession.adapterName]: historySession.modeId as string
      }));
    }
  }, [historySession]);

  useEffect(() => {
    if (!selectedAgentId || !selectedModelId) return;
    if (status !== 'ready') return;
    if (startedAgentIdRef.current !== selectedAgentId) return;
    if (startedModelIdRef.current === selectedModelId) return;
    if (typeof window.__setModel !== 'function') return;

    try {
      window.__setModel(conversationId, selectedAgentId, selectedModelId);
      startedModelIdRef.current = selectedModelId;
    } catch (e) {
      console.warn('[useChatSession] Failed to set model:', e);
    }
  }, [conversationId, selectedAgentId, selectedModelId, status, startedAgentIdRef, startedModelIdRef]);

  useEffect(() => {
    if (!selectedAgentId || !selectedModeId) return;
    if (status !== 'ready') return;
    if (startedAgentIdRef.current !== selectedAgentId) return;
    if (startedModeIdRef.current === selectedModeId) return;
    if (typeof window.__setMode !== 'function') return;

    try {
      window.__setMode(conversationId, selectedAgentId, selectedModeId);
      startedModeIdRef.current = selectedModeId;
    } catch (e) {
      console.warn('[useChatSession] Failed to set mode:', e);
    }
  }, [conversationId, selectedAgentId, selectedModeId, status, startedAgentIdRef, startedModeIdRef]);

  useEffect(() => {
    if (!selectedAgentId || !selectedVariantId) return;
    if (status !== 'ready') return;
    if (startedAgentIdRef.current !== selectedAgentId) return;
    if (startedVariantRef.current === selectedVariantId) return;
    if (typeof window.__setEffort !== 'function') return;

    try {
      window.__setEffort(conversationId, selectedAgentId, selectedVariantId);
      startedVariantRef.current = selectedVariantId;
    } catch (e) {
      console.warn('[useChatSession] Failed to set effort:', e);
    }
  }, [conversationId, selectedAgentId, selectedVariantId, status, startedAgentIdRef, startedVariantRef]);

  const handleModelChange = (modelId: string, targetAgentId?: string) => {
    const agentId = targetAgentId || selectedAgentId;
    setSelectedModelByAgent((prev) => (
      agentId ? { ...prev, [agentId]: modelId } : prev
    ));
  };

  const handleModeChange = (modeId: string) => {
    setSelectedModeByAgent((prev) => (
      selectedAgentId ? { ...prev, [selectedAgentId]: modeId } : prev
    ));
  };

  const handleEffortChange = (variantId: string) => {
    setSelectedVariantByAgent((prev) => (
      selectedAgentId ? { ...prev, [selectedAgentId]: variantId } : prev
    ));
  };

  return {
    availableModels,
    availableModes,
    availableVariants,
    selectedModelId,
    selectedModeId,
    selectedVariantId,
    modelIdForStart,
    handleModelChange,
    handleModeChange,
    handleEffortChange,
  };
}

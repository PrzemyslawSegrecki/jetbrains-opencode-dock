import { useEffect, useState } from 'react';
import { AgentOption } from '../../types/chat';
import { ACPBridge } from '../../utils/bridge';
import { Tooltip } from '../chat/shared/Tooltip';

type RuntimeStatus = { tone: 'success' | 'warning' | 'error'; label: string; detail: string };

function deriveRuntimeStatus(agent: AgentOption | undefined): RuntimeStatus {
  if (!agent || agent.downloadedKnown !== true) {
    return { tone: 'warning', label: 'Checking OpenCode', detail: 'Detecting the OpenCode runtime…' };
  }
  if (agent.downloaded !== true) {
    if (agent.runtimeProbeError) {
      return { tone: 'error', label: 'Runtime probe failed', detail: agent.runtimeProbeError };
    }
    return { tone: 'error', label: 'OpenCode not found', detail: 'Install OpenCode system-wide so it is on your PATH, then reopen.' };
  }
  if (agent.initializationError) {
    return { tone: 'error', label: 'Runtime error', detail: agent.initializationError };
  }
  if (agent.initializing) {
    return { tone: 'warning', label: 'Connecting', detail: agent.initializationDetail || 'Starting the OpenCode runtime…' };
  }
  if (agent.ready === true) {
    return { tone: 'success', label: 'Ready', detail: 'The OpenCode runtime is ready.' };
  }
  return { tone: 'success', label: 'Available', detail: 'OpenCode is installed and ready to start.' };
}

export function RuntimeStatusIndicator({ agents }: { agents: AgentOption[] }) {
  const [agent, setAgent] = useState<AgentOption | undefined>(() => agents.find((a) => a.id === 'opencode') ?? agents[0]);

  useEffect(() => {
    setAgent(agents.find((a) => a.id === 'opencode') ?? agents[0]);
  }, [agents]);

  useEffect(() => {
    const dispose = ACPBridge.onAdapters((e) => {
      const list = Array.isArray(e.detail.adapters) ? e.detail.adapters : [];
      setAgent(list.find((a) => a.id === 'opencode') ?? list[0]);
    });
    ACPBridge.requestAdapters();
    return dispose;
  }, []);

  const status = deriveRuntimeStatus(agent);
  const dotClass = status.tone === 'success' ? 'bg-success' : status.tone === 'warning' ? 'bg-warning' : 'bg-error';

  return (
    <Tooltip
      content={
        <div className="max-w-[220px]">
          <div className="font-semibold">{`OpenCode: ${status.label}`}</div>
          <div className="text-foreground-secondary">{status.detail}</div>
        </div>
      }
    >
      <span className="flex items-center justify-center w-[28px] h-[24px]" role="status" aria-label={`OpenCode runtime: ${status.label}`}>
        <span className={`h-2.5 w-2.5 rounded-full ${dotClass} ${status.tone === 'warning' ? 'animate-pulse' : ''}`} />
      </span>
    </Tooltip>
  );
}

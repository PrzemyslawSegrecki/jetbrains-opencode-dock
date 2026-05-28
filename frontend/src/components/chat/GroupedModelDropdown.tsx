import { KeyboardEvent as ReactKeyboardEvent, useEffect, useMemo, useRef, useState } from 'react';
import { ModelPickerGroup } from '../../hooks/chatSession/agentSelection';
import { Tooltip } from './shared/Tooltip';

type GroupedModelDropdownProps = {
  selectedAgentId: string;
  selectedModelId: string;
  groups: ModelPickerGroup[];
  placeholder: string;
  disabled: boolean;
  collapsed?: boolean;
  className?: string;
  onChange: (agentId: string, modelId: string) => void;
};

type FlatModelOption = {
  agentId: string;
  modelId: string;
  label: string;
  description?: string;
  groupLabel: string;
  iconPath?: string;
};

export function GroupedModelDropdown({
  selectedAgentId,
  selectedModelId,
  groups,
  placeholder,
  disabled,
  collapsed = false,
  className = '',
  onChange,
}: GroupedModelDropdownProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);

  const flatOptions = useMemo<FlatModelOption[]>(() => (
    groups.flatMap((group) => group.options.map((option) => ({
      ...option,
      groupLabel: group.label,
      iconPath: group.iconPath,
    })))
  ), [groups]);

  const selectedOption = useMemo(
    () => flatOptions.find((option) => option.agentId === selectedAgentId && option.modelId === selectedModelId),
    [flatOptions, selectedAgentId, selectedModelId]
  );

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const onEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        triggerRef.current?.focus();
      }
    };
    window.addEventListener('mousedown', onPointerDown);
    window.addEventListener('keydown', onEscape);
    return () => {
      window.removeEventListener('mousedown', onPointerDown);
      window.removeEventListener('keydown', onEscape);
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const selectedIndex = flatOptions.findIndex((option) => option.agentId === selectedAgentId && option.modelId === selectedModelId);
    const targetIndex = selectedIndex >= 0 ? selectedIndex : 0;
    optionRefs.current[targetIndex]?.focus();
  }, [flatOptions, open, selectedAgentId, selectedModelId]);

  const renderProviderIcon = (iconPath?: string, className: string = 'w-4 h-4') => {
    if (!iconPath) return null;
    return <img src={iconPath} className={className} alt="" />;
  };

  const handleOptionKeyDown = (event: ReactKeyboardEvent<HTMLButtonElement>, index: number) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      optionRefs.current[(index + 1) % flatOptions.length]?.focus();
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      optionRefs.current[(index - 1 + flatOptions.length) % flatOptions.length]?.focus();
      return;
    }
    if (event.key === 'Escape') {
      event.preventDefault();
      setOpen(false);
      triggerRef.current?.focus();
    }
  };

  const triggerText = selectedOption?.label || placeholder;

  return (
    <div ref={rootRef} className={`text-ide-small relative inline-flex min-w-0 items-stretch h-full overflow-visible ${className}`}>
      <button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        onClick={() => setOpen((current) => !current)}
        className={`inline-flex max-w-full appearance-none border-0 items-center ${collapsed ? 'justify-center gap-0.5' : 'justify-start gap-1 min-w-0'}
          h-full py-1 px-1.5 rounded bg-editor-bg text-foreground transition-colors
          disabled:text-foreground-secondary disabled:cursor-not-allowed group disabled:pointer-events-none
          whitespace-nowrap outline-none focus-visible:bg-hover
          focus-visible:text-foreground focus-visible:shadow-[0_0_0_1px_var(--ide-Button-default-focusColor)]
          ${open ? 'bg-hover' : 'hover:text-foreground hover:bg-hover'}`}
      >
        {renderProviderIcon(selectedOption?.iconPath, 'w-4 h-4 shrink-0 mr-0.5 opacity-80')}
        {!collapsed && <span className="min-w-0 max-w-[160px] truncate">{triggerText}</span>}
        <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none"
          stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"
          className="flex-shrink-0 opacity-50 group-hover:opacity-100 transition-opacity"
        >
          <polyline points="6 9 12 15 18 9"></polyline>
        </svg>
      </button>

      {open && !disabled && (
        <div className="absolute bottom-full left-0 z-[100] mb-2 w-max min-w-[260px] rounded-md border border-border bg-background px-1 py-1 animate-in fade-in duration-75">
          <div className="max-h-[400px] overflow-y-auto pr-1">
            {groups.map((group) => (
              <div key={group.agentId} className="py-0.5">
                <div className="flex items-center gap-2 px-2 py-1 text-[11px] uppercase tracking-[0.04em] text-foreground-secondary">
                  {renderProviderIcon(group.iconPath, 'w-3.5 h-3.5 opacity-75')}
                  <span>{group.label}</span>
                </div>
                <div>
                  {group.options.map((option) => {
                    const flatIndex = flatOptions.findIndex((item) => item.agentId === option.agentId && item.modelId === option.modelId);
                    const isSelected = option.agentId === selectedAgentId && option.modelId === selectedModelId;
                    const button = (
                      <button
                        key={`${option.agentId}:${option.modelId}`}
                        ref={(node) => {
                          optionRefs.current[flatIndex] = node;
                        }}
                        type="button"
                        onKeyDown={(event) => handleOptionKeyDown(event, flatIndex)}
                        onClick={() => {
                          onChange(option.agentId, option.modelId);
                          setOpen(false);
                        }}
                        className={`flex w-full items-center rounded px-2 py-1.5 text-left transition-colors outline-none focus-visible:shadow-[inset_0_0_0_1px_var(--ide-Button-default-focusColor)] ${
                          isSelected ? 'bg-accent text-accent-foreground' : 'text-foreground hover:bg-accent hover:text-accent-foreground'
                        }`}
                      >
                        <span className="truncate">{option.label}</span>
                      </button>
                    );

                    return option.description && option.description !== option.label ? (
                      <Tooltip key={`${option.agentId}:${option.modelId}`} variant="default" content={option.description} className="w-full flex" delay={300}>
                        {button}
                      </Tooltip>
                    ) : (
                      <div key={`${option.agentId}:${option.modelId}`} className="w-full flex">{button}</div>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

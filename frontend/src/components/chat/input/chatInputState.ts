import {
  AudioTranscriptionFeatureState,
  AvailableCommand,
  ChatAttachment,
  DropdownOption,
} from '../../../types/chat';
import { ModelPickerGroup } from '../../../hooks/chatSession/agentSelection';

export interface ChatInputProps {
  conversationId: string;
  contextTokensUsed?: number;
  contextWindowSize?: number;
  inputValue: string;
  onInputChange: (val: string) => void;
  onSend: () => void;
  onStop: () => void;
  isSending: boolean;
  modelGroups: ModelPickerGroup[];
  selectedAgentId: string;
  selectedModelId: string;
  onModelChange: (id: string, targetAgentId?: string) => void;
  modeOptions: DropdownOption[];
  selectedModeId: string;
  onModeChange: (id: string) => void;
  effortOptions: DropdownOption[];
  selectedVariantId: string;
  onEffortChange: (id: string) => void;
  hasSelectedAgent: boolean;
  availableCommands: AvailableCommand[];
  attachments: ChatAttachment[];
  onAttachmentsChange: (items: ChatAttachment[]) => void;
  onImageClick: (src: string) => void;
  onHeightChange?: (contentHeight: number) => void;
  customHeight?: number;
  autoFocus?: boolean;
  isActive?: boolean;
}

export const emptyTranscriptionFeature: AudioTranscriptionFeatureState = {
  id: 'whisper-transcription',
  title: 'Whisper',
  installed: false,
  installing: false,
  supported: false,
  status: 'Loading',
  detail: '',
  installPath: '',
};

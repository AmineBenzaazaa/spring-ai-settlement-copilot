/** Shapes of the copilot API (mirrors the Java records in com.benzaazaa.copilot.chat / tools). */

export type Lang = 'en' | 'fr';

export interface ToolCallTrace {
  index: number;
  tool: string;
  arguments: string;
  status: 'OK' | 'NOT_FOUND' | 'DENIED' | 'UNAVAILABLE' | 'ERROR';
  rows: number;
  durationMs: number;
  flagged: number;
}

export interface SourceRef {
  kind: 'statement' | 'dispute' | 'courier' | 'order' | 'overview' | 'doc';
  id: string;
  label: string;
  url: string | null;
}

export interface ChartSpec {
  type: 'bar' | 'doughnut';
  title: string;
  labels: string[];
  datasets: { label: string; data: number[] }[];
  unit: string | null;
}

export interface Verification {
  grounded: boolean;
  checkedAmounts: number;
  ungrounded: string[];
}

export type CopilotEvent =
  | { type: 'meta'; conversationId: string; mode: string; model: string; language: Lang }
  | { type: 'tool'; trace: ToolCallTrace }
  | { type: 'token'; text: string }
  | { type: 'chart'; chart: ChartSpec }
  | { type: 'sources'; sources: SourceRef[] }
  | { type: 'verification'; verification: Verification }
  | { type: 'done'; conversationId: string; answer: string; durationMs: number }
  | { type: 'error'; code: string; message: string };

export interface CopilotInfo {
  mode: 'demo' | 'anthropic';
  model: string;
  description: string;
  languages: Lang[];
  tools: string[];
  deskUrl: string;
}

export interface ChatMessage {
  id: number;
  role: 'user' | 'assistant';
  text: string;
  streaming: boolean;
  traces: ToolCallTrace[];
  sources: SourceRef[];
  chart?: ChartSpec;
  verification?: Verification;
  error?: string;
  durationMs?: number;
}

export interface SessionUser {
  username: string;
  role: 'MERCHANT' | 'STAFF';
  merchantId: number | null;
  expiresAt: number;
}

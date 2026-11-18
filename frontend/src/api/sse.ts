import { API_BASE, authHeaders, toApiError } from './client';
import type { InterviewEvent } from './types';

export interface SseMessage {
  event: string;
  data: string;
}

/**
 * Incremental text/event-stream parser. Network chunks can split a message (or a line, or a CRLF) anywhere,
 * so input is buffered until a blank line ends a message.
 */
export class SseParser {
  private buffer = '';

  push(chunk: string): SseMessage[] {
    this.buffer += chunk;
    // Normalise line endings, but keep a trailing \r: its \n may arrive in the next chunk.
    const keepCr = this.buffer.endsWith('\r');
    let text = (keepCr ? this.buffer.slice(0, -1) : this.buffer).replace(/\r\n?/g, '\n');
    const messages: SseMessage[] = [];
    let boundary: number;
    while ((boundary = text.indexOf('\n\n')) !== -1) {
      const message = parseBlock(text.slice(0, boundary));
      if (message) messages.push(message);
      text = text.slice(boundary + 2);
    }
    this.buffer = keepCr ? `${text}\r` : text;
    return messages;
  }
}

function parseBlock(block: string): SseMessage | null {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line === '' || line.startsWith(':')) continue; // blank or comment (keep-alive)
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') event = value;
    else if (field === 'data') data.push(value);
  }
  return data.length > 0 ? { event, data: data.join('\n') } : null;
}

export function toInterviewEvent(message: SseMessage): InterviewEvent {
  return { type: message.event, ...JSON.parse(message.data) } as InterviewEvent;
}

/**
 * Opens the interview event stream. Uses fetch rather than EventSource because EventSource cannot send the
 * Authorization header. Resolves when the server closes the stream.
 */
export async function streamInterview(
  sessionId: number,
  onEvent: (event: InterviewEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${API_BASE}/api/interviews/${sessionId}/stream`, {
    headers: authHeaders({ Accept: 'text/event-stream' }),
    signal,
  });
  if (!response.ok || !response.body) throw await toApiError(response);

  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  const parser = new SseParser();
  for (;;) {
    const { value, done } = await reader.read();
    if (done) return;
    for (const message of parser.push(value)) onEvent(toInterviewEvent(message));
  }
}

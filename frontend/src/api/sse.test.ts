import { describe, expect, it } from 'vitest';
import { SseParser, toInterviewEvent } from './sse';

describe('SseParser', () => {
  it('parses complete messages in Spring SseEmitter format', () => {
    const parser = new SseParser();
    const messages = parser.push('event:token\ndata:{"text":"Hel"}\n\nevent:token\ndata:{"text":"lo"}\n\n');
    expect(messages).toEqual([
      { event: 'token', data: '{"text":"Hel"}' },
      { event: 'token', data: '{"text":"lo"}' },
    ]);
  });

  it('buffers messages split across network chunks', () => {
    const parser = new SseParser();
    expect(parser.push('event: quest')).toEqual([]);
    expect(parser.push('ion\ndata: {"questionNo":1,')).toEqual([]);
    expect(parser.push('"question":"Why?","questionTarget":3}\n')).toEqual([]);
    expect(parser.push('\n')).toEqual([
      { event: 'question', data: '{"questionNo":1,"question":"Why?","questionTarget":3}' },
    ]);
  });

  it('handles CRLF split between chunks, comments and multi-line data', () => {
    const parser = new SseParser();
    expect(parser.push(': keep-alive\r\n\r\nevent: status\r')).toEqual([]);
    expect(parser.push('\ndata: line one\r\ndata: line two\r\n\r\n')).toEqual([
      { event: 'status', data: 'line one\nline two' },
    ]);
  });

  it('defaults the event name to "message"', () => {
    expect(new SseParser().push('data: {}\n\n')).toEqual([{ event: 'message', data: '{}' }]);
  });
});

describe('toInterviewEvent', () => {
  it('merges the event name into the JSON payload', () => {
    expect(toInterviewEvent({ event: 'feedback', data: '{"questionNo":2,"score":7}' })).toEqual({
      type: 'feedback',
      questionNo: 2,
      score: 7,
    });
  });
});

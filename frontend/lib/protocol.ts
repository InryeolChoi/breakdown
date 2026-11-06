export type Room = { id: number; name: string; openAt: string; closeAt: string };
export type User = { id: number; nickname: string; status: 'ACTIVE' | 'BANNED' };
export type ChatMessage = { id: string; roomId: number; authorId: number; nickname: string; content: string; sentAt: string; expiresAt: string };
export type ChatError = { type: 'ERROR'; status: number; message: string };
export function isRoom(value: unknown): value is Room {
  if (!value || typeof value !== 'object') return false;
  const r = value as Room;
  return Number.isInteger(r.id) && typeof r.name === 'string' && typeof r.openAt === 'string' && typeof r.closeAt === 'string';
}
export function parseChat(raw: string): ChatMessage | ChatError {
  const value = JSON.parse(raw);
  if (value?.type === 'ERROR' && Number.isInteger(value.status) && typeof value.message === 'string') return value;
  if (value && typeof value.id === 'string' && Number.isInteger(value.roomId) && Number.isInteger(value.authorId)
    && ['nickname', 'content', 'sentAt', 'expiresAt'].every(key => typeof value[key] === 'string')) return value;
  throw new Error('서버 메시지 형식을 확인할 수 없어요.');
}
export function validateContent(content: string): string | null {
  const trimmed = content.trim();
  if (!trimmed) return '메시지를 입력해 주세요.';
  if (trimmed.includes('\0')) return '사용할 수 없는 문자가 들어 있어요.';
  if ([...trimmed].length > 140) return '메시지는 140자 이내로 입력해 주세요.';
  return null;
}
export function displayTime(time: string) { return time.match(/T(\d{2}:\d{2})/)?.[1] || time.slice(0, 5); }

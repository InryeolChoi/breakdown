import test from 'node:test';
import assert from 'node:assert/strict';
import { parseChat, validateContent, isRoom } from '../lib/protocol.ts';
test('accepts the server success envelope without an invented MESSAGE type', () => {
  const data = { id: 'uuid', roomId: 1, authorId: 2, nickname: '오리', content: '안녕', sentAt: '2026-11-05T10:30:00', expiresAt: '2026-11-05T10:45:00' };
  assert.deepEqual(parseChat(JSON.stringify(data)), data);
  assert.deepEqual(parseChat('{"type":"ERROR","status":403,"message":"방이 닫혔어요"}'), { type: 'ERROR', status: 403, message: '방이 닫혔어요' });
});
test('rejects malformed envelopes instead of rendering them as chat', () => {
  for (const value of ['null', '{}', '{"content":"x"}', '{"type":"ERROR","status":"400","message":"x"}', 'not-json']) assert.throws(() => parseChat(value));
});
test('uses Unicode code points for the 140 character boundary', () => {
  assert.equal(validateContent('😀'.repeat(140)), null);
  assert.equal(validateContent('😀'.repeat(141)), '메시지는 140자 이내로 입력해 주세요.');
  assert.ok(validateContent(' \n ')); assert.ok(validateContent('a\0b'));
});
test('rejects invalid room arrays at the public HTTP boundary', () => {
  assert.equal(isRoom({ id: 1, name: '점심방', openAt: '10:00', closeAt: '11:30' }), true);
  assert.equal(isRoom({ id: '1', name: '방', openAt: '10:00', closeAt: '11:30' }), false);
});

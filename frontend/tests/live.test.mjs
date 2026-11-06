import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import WebSocket from 'ws';
const origin = process.env.COMPARE_URL || 'http://127.0.0.1:3000';
const enabled = process.env.RUN_LIVE_TESTS === '1';
async function user(nickname) {
  const response = await fetch(`${origin}/api/anonymoususer`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ nickname }) });
  assert.equal(response.status, 200);
  const cookie = response.headers.get('set-cookie');
  assert.match(cookie, /HttpOnly/i);
  return { cookie: cookie.split(';')[0], data: await response.json() };
}
async function connect(roomId, cookie, customOrigin = origin) {
  const ws = new WebSocket(`${origin.replace(/^http/, 'ws')}/ws/rooms/${roomId}`, { origin: customOrigin, headers: cookie ? { Cookie: cookie } : {}, handshakeTimeout: 5000 });
  await once(ws, 'open'); return ws;
}
function receive(ws) {
  return once(ws, 'message', { signal: AbortSignal.timeout(5000) }).then(([raw]) => JSON.parse(raw.toString()));
}
test('live: proxy preserves HttpOnly cookie, rejects unauthenticated/cross-origin WS, broadcasts after save and accepts a report', { skip: !enabled, timeout: 25000 }, async t => {
  const roomsResponse = await fetch(`${origin}/api/rooms`);
  assert.equal(roomsResponse.status, 200);
  const rooms = await roomsResponse.json();
  assert.ok(rooms.length, 'No currently open room: run during an existing operating interval. Do not seed a permanent test room.');
  const room = rooms[0];
  const [alice, bob] = await Promise.all([user('디자인검증A'), user('디자인검증B')]);
  await assert.rejects(connect(room.id, null), /401/);
  await assert.rejects(connect(room.id, alice.cookie, 'https://invalid.example'), /403/);
  const a = await connect(room.id, alice.cookie);
  const b = await connect(room.id, bob.cookie);
  t.after(() => { a.close(); b.close(); });
  const arrivalA = receive(a), arrivalB = receive(b);
  const content = `디자인 연동 확인 ${Date.now()}`;
  a.send(JSON.stringify({ content }));
  const [first, second] = await Promise.all([arrivalA, arrivalB]);
  assert.equal(first.content, content); assert.equal(second.id, first.id);
  assert.equal(first.authorId, alice.data.id); assert.equal(first.roomId, room.id);
  const rejected = receive(a); a.send(JSON.stringify({ content: '   ' }));
  assert.equal((await rejected).status, 400);
  assert.equal(a.readyState, WebSocket.OPEN);
  const report = () => fetch(`${origin}/api/rooms/${room.id}/reports`, { method: 'POST', headers: { 'Content-Type': 'application/json', Cookie: bob.cookie }, body: JSON.stringify({ messageId: first.id, reason: '로컬 디자인 비교 연동 검증용 신고' }) });
  assert.equal((await report()).status, 201);
  assert.equal((await report()).status, 409);
});

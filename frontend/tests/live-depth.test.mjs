import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import WebSocket from 'ws';

const origin = process.env.COMPARE_URL || 'http://127.0.0.1:3000';
const enabled = process.env.RUN_LIVE_DEPTH_TESTS === '1';

async function prepareUser(nickname, cookie) {
  const response = await fetch(`${origin}/api/anonymoususer`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) },
    body: JSON.stringify({ nickname }),
    signal: AbortSignal.timeout(5000),
  });
  assert.equal(response.status, 200);
  const header = response.headers.get('set-cookie');
  assert.match(header, /HttpOnly/i);
  assert.match(header, /SameSite=Lax/i);
  return { cookie: header.split(';')[0], data: await response.json() };
}

function trackMessages(socket, belongsToRun) {
  const received = [];
  const waiters = new Set();
  let failure;
  socket.on('message', raw => {
    try {
      const message = JSON.parse(raw.toString());
      if (message.type === 'ERROR' || belongsToRun(message)) received.push(message);
    }
    catch (error) { failure = error; }
    for (const check of waiters) check();
  });
  const fail = () => {
    failure = new Error('Connection closed before all expected messages arrived');
    for (const check of waiters) check();
  };
  socket.on('error', fail);
  socket.on('close', fail);
  return {
    received,
    until(count) {
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => finish(new Error(`Expected ${count}, received ${received.length}`)), 10000);
        function finish(error) {
          clearTimeout(timer);
          waiters.delete(check);
          if (error) reject(error);
          else resolve();
        }
        function check() {
          if (failure) finish(failure);
          else if (received.length >= count) finish();
        }
        waiters.add(check);
        check();
      });
    },
  };
}

test('live depth: session reuse, input recovery, concurrent delivery, and duplicate report race',
  { skip: !enabled, timeout: 60000 }, async t => {
    const response = await fetch(`${origin}/api/rooms`, { signal: AbortSignal.timeout(5000) });
    assert.equal(response.status, 200);
    const rooms = await response.json();
    assert.ok(rooms.length, 'Run during an existing room operating interval');
    const room = rooms[0];
    const clients = [];
    t.after(() => clients.forEach(client => client.socket.terminate()));
    const runId = Date.now().toString(36);
    const boundaryContent = runId + '😀'.repeat(140 - runId.length);

    for (let index = 0; index < 8; index++) {
      const user = await prepareUser(`깊은검증${runId}${index}`);
      const socket = new WebSocket(`${origin.replace(/^http/, 'ws')}/ws/rooms/${room.id}`, {
        origin, headers: { Cookie: user.cookie }, handshakeTimeout: 5000,
      });
      const tracker = trackMessages(socket, message =>
        message.content === boundaryContent || message.content?.startsWith(`깊은검증 ${runId} `));
      clients.push({ user, socket, tracker });
      await once(socket, 'open');
    }

    await t.test('a returning cookie keeps its identity and token', async () => {
      const first = clients[0].user;
      const revisit = await prepareUser('이름변경시도', first.cookie);
      assert.equal(revisit.data.id, first.data.id);
      assert.equal(revisit.data.nickname, first.data.nickname);
      assert.equal(revisit.cookie, first.cookie);
    });

    await t.test('invalid JSON, content types, empty content, NUL, and 141 emoji recover on the same socket', async () => {
      const { socket, tracker } = clients[0];
      const invalid = ['{', 'null', '[]', '{}', '{"content":3}', JSON.stringify({ content: ' \n ' }),
        JSON.stringify({ content: 'a\0b' }), JSON.stringify({ content: '😀'.repeat(141) })];
      for (const payload of invalid) {
        const count = tracker.received.length;
        socket.send(payload);
        await tracker.until(count + 1);
        assert.equal(tracker.received.at(-1).status, 400);
        assert.equal(socket.readyState, WebSocket.OPEN);
      }
      assert.ok(clients.slice(1).every(client => client.tracker.received.length === 0));
      const waits = clients.map(client => client.tracker.until(client.tracker.received.length + 1));
      socket.send(JSON.stringify({ content: boundaryContent, authorId: -1, roomId: -1 }));
      await Promise.all(waits);
      const saved = tracker.received.at(-1);
      assert.equal(saved.content, boundaryContent);
      assert.equal(saved.authorId, clients[0].user.data.id);
      assert.equal(saved.roomId, room.id);
      assert.ok(clients.every(client => client.tracker.received.at(-1).id === saved.id));
    });

    let evidence;
    await t.test('eight simultaneous senders deliver all 80 distinct messages to all eight clients', async () => {
      const offsets = clients.map(client => client.tracker.received.length);
      const expected = new Set();
      const waits = clients.map((client, index) => client.tracker.until(offsets[index] + 80));
      clients.forEach((client, sender) => {
        for (let sequence = 0; sequence < 10; sequence++) {
          const content = `깊은검증 ${runId} ${sender}-${sequence}`;
          expected.add(content);
          client.socket.send(JSON.stringify({ content }));
        }
      });
      await Promise.all(waits);
      const batches = clients.map((client, index) => client.tracker.received.slice(offsets[index]));
      for (const batch of batches) {
        assert.equal(batch.length, 80);
        assert.deepEqual(new Set(batch.map(message => message.content)), expected);
        assert.equal(new Set(batch.map(message => message.id)).size, 80);
        for (const message of batch) {
          const sender = Number(message.content.split(' ').at(-1).split('-')[0]);
          assert.equal(message.authorId, clients[sender].user.data.id);
          assert.equal(message.roomId, room.id);
        }
      }
      const ids = new Set(batches[0].map(message => message.id));
      for (const batch of batches.slice(1)) assert.deepEqual(new Set(batch.map(message => message.id)), ids);
      evidence = batches[0].find(message => message.authorId === clients[0].user.data.id);
    });

    await t.test('12 concurrent duplicate reports produce one 201 and eleven 409 responses', async () => {
      const statuses = await Promise.all(Array.from({ length: 12 }, async () => {
        const result = await fetch(`${origin}/api/rooms/${room.id}/reports`, {
          method: 'POST', headers: { 'Content-Type': 'application/json', Cookie: clients[1].user.cookie },
          body: JSON.stringify({ messageId: evidence.id, reason: `깊은검증 동시 신고 ${runId}` }),
          signal: AbortSignal.timeout(10000),
        });
        await result.arrayBuffer();
        return result.status;
      }));
      assert.deepEqual(statuses.sort(), [201, ...Array(11).fill(409)]);
    });

    await t.test('self report and missing authentication are rejected after the report race', async () => {
      const send = cookie => fetch(`${origin}/api/rooms/${room.id}/reports`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) },
        body: JSON.stringify({ messageId: evidence.id, reason: '깊은검증 거절 경계' }),
        signal: AbortSignal.timeout(5000),
      });
      assert.equal((await send(clients[0].user.cookie)).status, 400);
      assert.equal((await send(null)).status, 401);
    });
  });

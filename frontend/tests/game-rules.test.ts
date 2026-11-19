import test from 'node:test';
import assert from 'node:assert/strict';
import { arrivals, cafeRound, menu, patience, runCafe } from '../lib/cafe-rush.ts';
import { chairPoints, deskGames, GOAL, launchDeskShot, makeDeskBoard, stepDeskBoard, FOLDER_LEVELS } from '../lib/desk-games.ts';

test('cafe serves only a ready order and cannot charge twice for one serving', () => {
  let round = runCafe(cafeRound(), { type: 'start' });
  round = runCafe(round, { type: 'prepare', item: 'coffee' });
  assert.equal(runCafe(round, { type: 'serve', index: 0 }).sales, 0);
  for (let second = 0; second < menu.coffee.seconds; second++) round = runCafe(round, { type: 'tick' });
  assert.equal(runCafe(round, { type: 'serve', index: 1 }).sales, 0);
  const served = runCafe(round, { type: 'serve', index: 0 });
  assert.equal(served.sales, menu.coffee.price + 500);
  assert.equal(served.orders[0], 'served');
  assert.equal(served.readyAt.coffee, null);
  assert.deepEqual(runCafe(served, { type: 'serve', index: 0 }), served);
});

test('cafe order expires exactly at its patience deadline', () => {
  let round = runCafe(cafeRound(), { type: 'start' });
  round = runCafe(round, { type: 'prepare', item: 'coffee' });
  for (let second = 0; second < patience - 1; second++) round = runCafe(round, { type: 'tick' });
  assert.equal(round.orders[0], 'waiting');
  round = runCafe(round, { type: 'tick' });
  assert.equal(round.orders[0], 'left');
  assert.equal(runCafe(round, { type: 'serve', index: 0 }).sales, 0);
});

test('cafe pauses its clock, finishes after all customers leave, and restarts cleanly', () => {
  let round = runCafe(cafeRound(), { type: 'start' });
  const paused = runCafe(round, { type: 'pause' });
  assert.deepEqual(runCafe(paused, { type: 'tick' }), paused);
  assert.deepEqual(runCafe(paused, { type: 'prepare', item: 'coffee' }), paused);
  round = runCafe(paused, { type: 'resume' });
  for (let second = 0; second < arrivals.at(-1)!.second + patience; second++) round = runCafe(round, { type: 'tick' });
  assert.equal(round.phase, 'finished');
  assert.ok(round.orders.every(status => status === 'left'));
  assert.deepEqual(runCafe(round, { type: 'tick' }), round);
  assert.deepEqual(runCafe(round, { type: 'start' }), cafeRound('playing'));
});

test('desk simulations finish with finite coordinates across supported games, layouts, aim and power', () => {
  for (const game of Object.keys(deskGames) as (keyof typeof deskGames)[]) {
    for (let level = 0; level < (game === 'folder-domino' ? FOLDER_LEVELS : 1); level++) {
      for (const angle of [-Math.PI, -Math.PI / 4, 0, Math.PI / 4, Math.PI]) {
        for (const power of [0, 50, 100]) {
          const board = makeDeskBoard(game, level);
          launchDeskShot(board, angle, power);
          let ended = false;
          for (let frame = 0; frame < 900 && !ended; frame++) ended = stepDeskBoard(game, board, 1 / 120);
          assert.equal(ended, true, `${game} level=${level} angle=${angle} power=${power}`);
          for (const body of [board.striker, ...board.notes]) {
            assert.ok([body.x, body.y, body.vx, body.vy].every(Number.isFinite));
          }
          assert.ok(board.folders.every(folder => Number.isFinite(folder.progress) && folder.progress >= 0 && folder.progress <= 1));
        }
      }
    }
  }
});

test('new desk rounds do not share mutable objects and chair score stays within zero and 100', () => {
  const first = makeDeskBoard('rubber-billiards');
  const fresh = makeDeskBoard('rubber-billiards');
  first.notes[0].out = true;
  first.striker.x = -1;
  assert.equal(fresh.notes[0].out, false);
  assert.notEqual(fresh.striker.x, first.striker.x);
  assert.equal(chairPoints(GOAL), 100);
  assert.equal(chairPoints({ x: -1000, y: -1000 }), 0);
});

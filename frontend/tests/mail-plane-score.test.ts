import test from 'node:test';
import assert from 'node:assert/strict';
import { scoreDelivery } from '../lib/mail-plane-score.ts';

test('mail delivery rewards matching the force to the distance', () => {
  assert.equal(scoreDelivery(0, 0), 100);
  assert.equal(scoreDelivery(0, -.2), 87);
  assert.equal(scoreDelivery(0, .2), 87);
  assert.equal(scoreDelivery(0, -.41), 0);
  assert.equal(scoreDelivery(0, .41), 0);
});

test('mail delivery still depends on aim', () => {
  assert.equal(scoreDelivery(.8, .2), 67);
  assert.equal(scoreDelivery(1.01, 0), 0);
});

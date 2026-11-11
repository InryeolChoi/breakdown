export type DeskGame = 'chair-curling' | 'folder-domino' | 'rubber-billiards';
export type Point = { x: number; y: number };
type Disc = Point & { vx: number; vy: number; radius: number; out: boolean };
type Folder = Point & { heavy: boolean; angle: number; progress: number; energy: number };
export type DeskBoard = {
  striker: Disc;
  notes: Disc[];
  folders: Folder[];
  elapsed: number;
};
export const WIDTH = 640;
export const HEIGHT = 360;
export const START = { x: 68, y: 180 };
export const GOAL = { x: 535, y: 180 };
export const CUP = { x: 410, y: 226, radius: 26 };
export const deskGames = {
  'chair-curling': {
    title: '의자 컬링', action: '의자 밀기', shots: 5, power: 56,
    rule: '목표 원에 가깝게 멈추면 점수가 올라가요. 카펫 위에서는 의자가 더 빨리 멈춰요.',
  },
  'folder-domino': {
    title: '서류 도미노', action: '밀어 넘기기', shots: 3, power: 70,
    rule: '첫 서류철을 밀어 연쇄를 시작하세요. 노란 서류철까지 넘어뜨리면 다음 배치가 열려요.',
  },
  'rubber-billiards': {
    title: '고무줄 당구', action: '지우개 튕기기', shots: 3, power: 67,
    rule: '지우개로 메모를 책상 밖으로 밀어내세요. 컵과 책상 가장자리에서 지우개가 튕겨요.',
  },
} as const;

const folderLayouts: Point[][] = [
  [{ x: 196, y: 180 }, { x: 247, y: 180 }, { x: 298, y: 180 }, { x: 347, y: 157 }, { x: 397, y: 139 }, { x: 449, y: 139 }, { x: 501, y: 158 }, { x: 553, y: 180 }],
  [{ x: 192, y: 130 }, { x: 241, y: 149 }, { x: 288, y: 175 }, { x: 335, y: 202 }, { x: 385, y: 224 }, { x: 437, y: 226 }, { x: 488, y: 208 }, { x: 538, y: 181 }],
  [{ x: 188, y: 229 }, { x: 238, y: 213 }, { x: 284, y: 185 }, { x: 330, y: 156 }, { x: 378, y: 130 }, { x: 430, y: 123 }, { x: 480, y: 141 }, { x: 525, y: 171 }, { x: 566, y: 205 }],
];

function disc(point: Point, radius: number): Disc {
  return { ...point, radius, vx: 0, vy: 0, out: false };
}

export function makeDeskBoard(game: DeskGame, level = 0): DeskBoard {
  return {
    striker: disc(START, game === 'chair-curling' ? 19 : 12),
    notes: game === 'rubber-billiards'
      ? [{ x: 543, y: 69 }, { x: 572, y: 141 }, { x: 540, y: 225 }, { x: 530, y: 300 }].map(point => disc(point, 15))
      : [],
    folders: game === 'folder-domino'
      ? folderLayouts[level].map((point, index) => ({
        ...point, heavy: index === 2 || index === 5, angle: 0, progress: 0, energy: 0,
      }))
      : [],
    elapsed: 0,
  };
}

export const FOLDER_LEVELS = folderLayouts.length;

export function aimAngle(point: Point) {
  return Math.atan2(point.y - START.y, point.x - START.x);
}

export function launchDeskShot(board: DeskBoard, angle: number, power: number) {
  board.elapsed = 0;
  board.striker.x = START.x;
  board.striker.y = START.y;
  const speed = 190 + power * 7.6;
  board.striker.vx = Math.cos(angle) * speed;
  board.striker.vy = Math.sin(angle) * speed;
}

function moveDisc(body: Disc, delta: number, friction: number) {
  const speed = Math.hypot(body.vx, body.vy);
  const remaining = Math.max(0, speed - friction * delta);
  const ratio = speed > 0 ? remaining / speed : 0;
  body.x += (body.vx + body.vx * ratio) * .5 * delta;
  body.y += (body.vy + body.vy * ratio) * .5 * delta;
  body.vx *= ratio;
  body.vy *= ratio;
}

function rebound(body: Disc) {
  const { radius } = body;
  if (body.x < radius || body.x > WIDTH - radius) {
    body.x = Math.min(WIDTH - radius, Math.max(radius, body.x));
    body.vx *= -.7;
  }
  if (body.y < radius || body.y > HEIGHT - radius) {
    body.y = Math.min(HEIGHT - radius, Math.max(radius, body.y));
    body.vy *= -.7;
  }
}

function collide(first: Disc, second: Disc) {
  const dx = second.x - first.x;
  const dy = second.y - first.y;
  const distance = Math.hypot(dx, dy);
  const overlap = first.radius + second.radius - distance;
  if (overlap <= 0) return;
  const nx = distance ? dx / distance : 1;
  const ny = distance ? dy / distance : 0;
  first.x -= nx * overlap / 2;
  first.y -= ny * overlap / 2;
  second.x += nx * overlap / 2;
  second.y += ny * overlap / 2;
  const closing = (first.vx - second.vx) * nx + (first.vy - second.vy) * ny;
  if (closing <= 0) return;
  const impulse = closing * .88;
  first.vx -= impulse * nx;
  first.vy -= impulse * ny;
  second.vx += impulse * nx;
  second.vy += impulse * ny;
}

function bounceOffCup(body: Disc) {
  const dx = body.x - CUP.x;
  const dy = body.y - CUP.y;
  const distance = Math.hypot(dx, dy);
  const edge = CUP.radius + body.radius;
  if (distance >= edge) return;
  const nx = distance ? dx / distance : 1;
  const ny = distance ? dy / distance : 0;
  body.x = CUP.x + nx * edge;
  body.y = CUP.y + ny * edge;
  const approach = body.vx * nx + body.vy * ny;
  if (approach < 0) {
    body.vx -= 1.7 * approach * nx;
    body.vy -= 1.7 * approach * ny;
  }
}

function topple(folder: Folder, angle: number, energy: number) {
  folder.angle = angle;
  folder.progress = .01;
  folder.energy = energy;
}

function stepFolders(board: DeskBoard, delta: number) {
  const striker = board.striker;
  const speed = Math.hypot(striker.vx, striker.vy);
  const contacted = board.folders.find(folder => !folder.progress && Math.hypot(folder.x - striker.x, folder.y - striker.y) < 23);
  if (contacted) {
    if (speed >= (contacted.heavy ? 245 : 130)) topple(contacted, Math.atan2(striker.vy, striker.vx), speed);
    striker.vx = 0;
    striker.vy = 0;
  }
  const falling = board.folders.filter(folder => folder.progress > 0 && folder.progress < 1);
  for (const folder of falling) {
    const previous = folder.progress;
    folder.progress = Math.min(1, previous + delta * 3);
    if (previous >= .65 || folder.progress < .65) continue;
    for (const next of board.folders) {
      if (next.progress) continue;
      const dx = next.x - folder.x;
      const dy = next.y - folder.y;
      const distance = Math.hypot(dx, dy);
      const alignment = (dx * Math.cos(folder.angle) + dy * Math.sin(folder.angle)) / distance;
      const energy = folder.energy * .93;
      if (distance <= 61 && alignment > .43 && energy >= (next.heavy ? 185 : 100)) {
        topple(next, Math.atan2(dy, dx), energy);
      }
    }
  }
}

export function stepDeskBoard(game: DeskGame, board: DeskBoard, delta: number) {
  board.elapsed += delta;
  const striker = board.striker;
  const onCarpet = striker.x > 273 && striker.x < 365 && striker.y > 79 && striker.y < 281;
  moveDisc(striker, delta, game === 'chair-curling' ? (onCarpet ? 520 : 380) : 172);
  rebound(striker);
  if (game === 'folder-domino') stepFolders(board, delta);
  if (game === 'rubber-billiards') {
    const bodies = [striker, ...board.notes.filter(note => !note.out)];
    for (const note of board.notes) {
      if (note.out) continue;
      moveDisc(note, delta, 112);
      if (note.x < 0 || note.x > WIDTH || note.y < 0 || note.y > HEIGHT) note.out = true;
    }
    const active = bodies.filter(body => !body.out);
    for (const [index, body] of active.entries()) {
      bounceOffCup(body);
      for (const other of active.slice(index + 1)) collide(body, other);
    }
  }
  const moving = [striker, ...board.notes].some(body => !body.out && Math.hypot(body.vx, body.vy) > 1);
  const falling = board.folders.some(folder => folder.progress > 0 && folder.progress < 1);
  return board.elapsed >= 7 || (!moving && !falling);
}

export function chairPoints(position: Point) {
  const distance = Math.hypot(position.x - GOAL.x, position.y - GOAL.y);
  return Math.max(0, Math.round(100 - distance * 1.5));
}

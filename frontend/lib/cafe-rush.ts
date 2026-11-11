export const menu = {
  coffee: { name: '커피', seconds: 3, price: 3000 },
  sandwich: { name: '샌드위치', seconds: 5, price: 4500 },
} as const;
export type MenuItem = keyof typeof menu;
type Phase = 'idle' | 'playing' | 'paused' | 'finished';
type OrderStatus = 'waiting' | 'served' | 'left';

export const patience = 14;
export const arrivals: { item: MenuItem; second: number }[] = [
  { item: 'coffee', second: 0 },
  { item: 'sandwich', second: 5 },
  { item: 'coffee', second: 9 },
  { item: 'sandwich', second: 13 },
  { item: 'coffee', second: 16 },
  { item: 'coffee', second: 19 },
  { item: 'sandwich', second: 22 },
  { item: 'sandwich', second: 26 },
];

export type CafeRound = {
  phase: Phase;
  second: number;
  orders: OrderStatus[];
  readyAt: Record<MenuItem, number | null>;
  sales: number;
  message: string;
};
export type CafeAction =
  | { type: 'start' | 'pause' | 'resume' | 'tick' }
  | { type: 'prepare'; item: MenuItem }
  | { type: 'serve'; index: number };

export function cafeRound(phase: Phase = 'idle'): CafeRound {
  return {
    phase, second: 0, orders: arrivals.map(() => 'waiting'),
    readyAt: { coffee: null, sandwich: null },
    sales: 0, message: phase === 'idle' ? '영업을 시작하면 첫 손님이 와요.' : '첫 주문이 들어왔어요.',
  };
}

function settle(round: CafeRound): CafeRound {
  return round.orders.every(status => status !== 'waiting')
    ? { ...round, phase: 'finished', message: '점심 영업이 끝났어요.' }
    : round;
}

export function runCafe(round: CafeRound, action: CafeAction): CafeRound {
  if (action.type === 'start') return cafeRound('playing');
  if (action.type === 'resume' && round.phase === 'paused') return { ...round, phase: 'playing' };
  if (round.phase !== 'playing') return round;
  if (action.type === 'pause') return { ...round, phase: 'paused' };
  if (action.type === 'tick') {
    const second = round.second + 1;
    const orders = round.orders.map((status, index) =>
      status === 'waiting' && second >= arrivals[index].second + patience ? 'left' : status);
    const someoneLeft = orders.some((status, index) => status !== round.orders[index]);
    return settle({ ...round, second, orders, message: someoneLeft ? '기다리던 손님이 돌아갔어요.' : round.message });
  }
  if (action.type === 'prepare') {
    if (round.readyAt[action.item] !== null) return round;
    const recipe = menu[action.item];
    return { ...round, readyAt: { ...round.readyAt, [action.item]: round.second + recipe.seconds }, message: recipe.name + ' 준비를 시작했어요.' };
  }
  if (action.type === 'serve') {
    const order = arrivals[action.index];
    if (!order || order.second > round.second || round.orders[action.index] !== 'waiting') return round;
    const readyAt = round.readyAt[order.item];
    if (readyAt === null || readyAt > round.second) return round;
    const tip = round.second - order.second < patience / 2 ? 500 : 0;
    return settle({
      ...round,
      orders: round.orders.map((status, index) => index === action.index ? 'served' : status),
      readyAt: { ...round.readyAt, [order.item]: null },
      sales: round.sales + menu[order.item].price + tip,
      message: tip ? '빨리 내줘서 팁 500원을 받았어요.' : '주문을 내줬어요.',
    });
  }
  return round;
}

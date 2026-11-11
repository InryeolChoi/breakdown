'use client';

import Link from 'next/link';
import { useEffect, useReducer } from 'react';
import { arrivals, cafeRound, menu, patience, runCafe, type MenuItem } from '../lib/cafe-rush';
import styles from './MiniArcade.module.css';

const menuItems: MenuItem[] = ['coffee', 'sandwich'];

export default function CafeRush() {
  const [round, dispatch] = useReducer(runCafe, undefined, () => cafeRound());

  useEffect(() => {
    if (round.phase !== 'playing') return;
    const timer = window.setInterval(() => dispatch({ type: 'tick' }), 1000);
    return () => window.clearInterval(timer);
  }, [round.phase]);

  const playing = round.phase === 'playing';
  const served = round.orders.filter(order => order === 'served').length;
  const left = round.orders.filter(order => order === 'left').length;
  const waiting = arrivals.map((order, index) => ({ ...order, index }))
    .filter(order => order.second <= round.second && round.orders[order.index] === 'waiting');
  const nextArrival = arrivals.find(order => order.second > round.second);

  return <main className={styles.arcade}>
    <header className={styles.bar}><Link href="/games">게임 목록</Link><Link href="/minimal">채팅</Link></header>
    <div className={styles.gameTitle}>
      <div><h1>점심 카페 러시</h1><p>커피와 샌드위치를 동시에 준비해 손님이 떠나기 전에 내주세요.</p></div>
      <div className={styles.counter}><strong>{round.sales.toLocaleString('ko-KR')}</strong><span>원</span></div>
    </div>
    <section className={styles.cafeCounter} aria-label="주방">
      {menuItems.map(item => {
        const recipe = menu[item];
        const readyAt = round.readyAt[item];
        const ready = readyAt !== null && readyAt <= round.second;
        return <div className={styles.station} key={item} data-ready={ready}>
          <div className={styles.stationIcon} aria-hidden="true">{item === 'coffee' ? '☕' : '▱'}</div>
          <div><h2>{recipe.name}</h2><span>{ready ? '준비 완료' : readyAt !== null ? `준비 중 · ${readyAt - round.second}초` : `준비 시간 ${recipe.seconds}초`}</span></div>
          <button className={styles.primary} onClick={() => dispatch({ type: 'prepare', item })}
            disabled={!playing || readyAt !== null}>{recipe.name} 준비</button>
        </div>;
      })}
    </section>
    <section className={styles.orders} aria-label="주문">
      <div className={styles.boardCaption}><strong>주문표</strong><span>완료 {served}/{arrivals.length} · 돌아감 {left}</span></div>
      {round.phase === 'idle' && <p className={styles.gameEmpty}>가게를 열 준비가 됐어요.</p>}
      {round.phase === 'finished' && <p className={styles.gameEmpty}>{arrivals.length}명 중 {served}명에게 내줬어요.</p>}
      {round.phase !== 'idle' && round.phase !== 'finished' && waiting.length === 0 && <p className={styles.gameEmpty}>
        {nextArrival ? `다음 손님까지 ${nextArrival.second - round.second}초` : '남은 주문이 없어요.'}
      </p>}
      {round.phase !== 'idle' && round.phase !== 'finished' && waiting.map(order => {
        const remaining = order.second + patience - round.second;
        const readyAt = round.readyAt[order.item];
        return <div className={styles.order} key={order.index}>
          <div><strong>{order.index + 1}번 · {menu[order.item].name}</strong>
            <div className={styles.waitTime}><meter min={0} max={patience} low={4} value={remaining} aria-label="남은 대기 시간"/><span>{remaining}초</span></div>
          </div>
          <button className={styles.secondary} aria-label={`${order.index + 1}번 손님 ${menu[order.item].name} 내주기`}
            disabled={!playing || readyAt === null || readyAt > round.second}
            onClick={() => dispatch({ type: 'serve', index: order.index })}>내주기</button>
        </div>;
      })}
    </section>
    <p className={styles.feedback} role="status">{round.phase === 'paused' ? '잠깐 멈췄어요.' : round.message}</p>
    <div className={styles.controls}>
      {round.phase === 'idle' || round.phase === 'finished'
        ? <button className={styles.primary} onClick={() => dispatch({ type: 'start' })}>{round.phase === 'idle' ? '영업 시작' : '다시 하기'}</button>
        : <button className={styles.secondary} onClick={() => dispatch({ type: playing ? 'pause' : 'resume' })}>{playing ? '잠깐 멈춤' : '계속하기'}</button>}
    </div>
    <p className={styles.help}>주방마다 한 개씩 준비할 수 있어요. 빠르게 내주면 팁 500원을 받아요.</p>
  </main>;
}

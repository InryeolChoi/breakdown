'use client';

import Link from 'next/link';
import { useState } from 'react';
import styles from './MiniArcade.module.css';

const departments = {
  plan: { name: '기획', mark: '▤' },
  design: { name: '디자인', mark: '◈' },
  build: { name: '개발', mark: '⌘' },
  finance: { name: '재무', mark: '◎' },
} as const;
type Department = keyof typeof departments;
const cards: Department[] = ['plan', 'design', 'plan', 'build', 'design', 'build', 'plan', 'finance', 'design', 'build', 'finance', 'finance'];
const capacity = 3;

function initialRound() {
  return { next: 0, piles: [[], [], []] as Department[][], cleared: 0, message: '첫 업무 카드를 놓을 서류함을 골라보세요.' };
}

export default function DeskSort() {
  const [round, setRound] = useState(initialRound);
  const current = cards[round.next];
  const won = round.cleared === cards.length;
  const stuck = current === undefined || round.piles.every(pile => pile.length >= capacity);
  const done = won || stuck;

  function place(index: number) {
    setRound(previous => {
      const card = cards[previous.next];
      const pile = previous.piles[index];
      if (card === undefined || pile.length >= capacity) return previous;
      const nextPile = [...pile, card];
      const match = nextPile.length === capacity && nextPile.every(entry => entry === card);
      return {
        next: previous.next + 1,
        piles: previous.piles.map((entry, i) => i === index ? (match ? [] : nextPile) : entry),
        cleared: previous.cleared + (match ? capacity : 0),
        message: match ? departments[card].name + ' 카드 세 장을 정리했어요!' : `${index + 1}번 서류함에 넣었어요.`,
      };
    });
  }

  return <main className={styles.arcade}>
    <header className={styles.bar}><Link href="/games">게임 목록</Link><Link href="/minimal">채팅</Link></header>
    <div className={styles.gameTitle}>
      <div><h1>퇴근 정리</h1><p>업무 카드를 세 서류함에 나눠 담으세요. 같은 종류 세 장이 모이면 서류함이 비어요.</p></div>
      <div className={styles.counter}><strong>{round.cleared}</strong><span>/{cards.length}장</span></div>
    </div>
    <section className={styles.sortTable} aria-label="업무 카드와 서류함">
      <div className={styles.sortIncoming}>
        <span>남은 카드 {cards.length - round.next}장</span>
        {current !== undefined && !done && <span className={styles.jobCard} data-dept={current}>
          <b aria-hidden="true">{departments[current].mark}</b> {departments[current].name}
        </span>}
      </div>
      <div className={styles.sortPiles}>
        {round.piles.map((pile, index) => <button key={index} className={styles.sortPile} disabled={done || pile.length >= capacity}
          aria-label={`${index + 1}번 서류함, ${pile.length}장${pile.length ? ', ' + pile.map(card => departments[card].name).join(', ') : ''}`}
          onClick={() => place(index)}>
          <span>{index + 1}번 서류함</span>
          {Array.from({ length: capacity }, (_, position) => {
            const card = pile[position];
            return card !== undefined
              ? <span key={position} className={styles.jobCard} data-dept={card}><b aria-hidden="true">{departments[card].mark}</b> {departments[card].name}</span>
              : <span key={position} className={styles.jobSlot}>빈칸</span>;
          })}
        </button>)}
      </div>
    </section>
    <p className={styles.feedback} role="status">{won ? '업무 카드 12장 정리 완료. 이제 퇴근!' : stuck
      ? (current === undefined ? '카드는 다 넣었지만 서류가 남았어요. 다른 순서로 놓아보세요.' : '서류함이 모두 찼어요. 다른 배치로 다시 해보세요.')
      : round.message}</p>
    <div className={styles.controls}><button className={styles.primary} onClick={() => setRound(initialRound())}>{done ? '다시 하기' : '처음부터'}</button></div>
    <p className={styles.help}>카드를 놓을 서류함을 누르세요. 같은 종류끼리 모을 자리를 남겨두는 게 좋아요.</p>
  </main>;
}

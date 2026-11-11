'use client';

import Link from 'next/link';
import { useState } from 'react';
import styles from './MiniArcade.module.css';

const suits = ['♠', '♥', '♣', '♦'];
const suitNames = ['스페이드', '하트', '클로버', '다이아몬드'];
const ranks = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];
const rank = (card: number) => card % 13;

function deal(seed: number) {
  const deck = Array.from({ length: 52 }, (_, card) => card);
  let random = seed >>> 0;
  for (let i = deck.length - 1; i > 0; i--) {
    random = (Math.imul(random, 1664525) + 1013904223) >>> 0;
    const j = random % (i + 1);
    [deck[i], deck[j]] = [deck[j], deck[i]];
  }
  return {
    columns: Array.from({ length: 5 }, (_, index) => deck.slice(index * 3, index * 3 + 3)),
    current: deck[15],
    stock: deck.slice(16, 34),
    streak: 0,
    longest: 0,
    message: '기준 카드와 숫자가 하나 차이 나는 카드를 골라보세요.',
  };
}

function CardFace({ card }: { card: number }) {
  const suit = Math.floor(card / 13);
  return <span className={suit % 2 ? styles.redCard : styles.blackCard}>
    <b>{ranks[rank(card)]}</b><span>{suits[suit]}</span>
  </span>;
}

export default function GolfSolitaire() {
  const [round, setRound] = useState(() => deal(77));
  const remaining = round.columns.reduce((total, cards) => total + cards.length, 0);
  const canPlay = (card: number) => Math.abs(rank(card) - rank(round.current)) === 1;
  const hasMatch = round.columns.some(cards => cards.length && canPlay(cards[cards.length - 1]));
  const finished = remaining === 0 || (!round.stock.length && !hasMatch);

  function take(column: number) {
    if (finished) return;
    const card = round.columns[column].at(-1);
    if (card === undefined) return;
    if (!canPlay(card)) {
      setRound(previous => ({ ...previous, message: '기준 카드보다 하나 높거나 낮은 숫자를 골라주세요.' }));
      return;
    }
    const streak = round.streak + 1;
    setRound({
      ...round,
      columns: round.columns.map((cards, index) => index === column ? cards.slice(0, -1) : cards),
      current: card,
      streak,
      longest: Math.max(round.longest, streak),
      message: streak > 1 ? `${streak}장 연속!` : '한 장을 지웠어요. 다음 숫자를 찾아보세요.',
    });
  }

  function draw() {
    if (finished || !round.stock.length) return;
    setRound({
      ...round,
      current: round.stock[0],
      stock: round.stock.slice(1),
      streak: 0,
      message: '기준 카드를 바꿨어요.',
    });
  }

  return <main className={styles.arcade}>
    <header className={styles.bar}><Link href="/games">게임 목록</Link><Link href="/minimal">채팅</Link></header>
    <div className={styles.gameTitle}>
      <div><h1>골프 솔리테어</h1><p>기준 카드보다 하나 높거나 낮은 카드로 이어가며 15장을 지워보세요.</p></div>
      <div className={styles.counter}><strong>{15 - remaining}</strong><span>/15장</span></div>
    </div>
    <section className={styles.cardTable} aria-label="카드판">
      <div className={styles.cardColumns}>
        {round.columns.map((cards, column) => <div key={column} className={styles.cardColumn}>
          {cards.length === 0 && <span className={styles.emptyCard}>✓</span>}
          {cards.map((card, index) => index === cards.length - 1
            ? <button type="button" className={styles.card} key={card} onClick={() => take(column)}
              disabled={finished} data-match={canPlay(card)}
              aria-label={`${column + 1}열 ${suitNames[Math.floor(card / 13)]} ${ranks[rank(card)]}`}>
              <CardFace card={card}/>
            </button>
            : <span className={styles.card} key={card}><CardFace card={card}/></span>)}
        </div>)}
      </div>
      <div className={styles.cardBottom}>
        <div className={styles.cardPile}><span>기준 카드</span><div className={styles.card}><CardFace card={round.current}/></div></div>
        <div className={styles.cardPile}><span>보충 덱</span>
          <button className={styles.cardBack} onClick={draw} disabled={finished || round.stock.length === 0}
            aria-label={`덱에서 뽑기, ${round.stock.length}장 남음`}>{round.stock.length}<span>뽑기</span></button>
        </div>
      </div>
    </section>
    <p className={styles.feedback} role="status">
      {remaining === 0 ? '전부 지웠어요!' : finished ? `${remaining}장을 남겼어요. 한 판 더 해볼까요?` : round.message}
    </p>
    <div className={styles.controls}>
      <span>최장 연속 {round.longest}장</span>
      <button className={styles.primary} onClick={() => setRound(deal(Date.now()))}>{finished ? '다시 하기' : '새 판'}</button>
    </div>
    <p className={styles.help}>열의 맨 아래 카드부터 고를 수 있어요. 막히면 덱에서 뽑으세요. A와 K는 이어지지 않아요.</p>
  </main>;
}

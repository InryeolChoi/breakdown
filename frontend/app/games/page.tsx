import Link from 'next/link';
import styles from '../../components/MiniArcade.module.css';

const games = [
  { id: 'mail-plane', mark: '↗', tone: 'paper', title: '종이비행기', detail: '메일을 접어서 날리기' },
  { id: 'chair-curling', mark: '◎', tone: 'sand', title: '의자 컬링', detail: '밀어서 목표 원에 세우기' },
  { id: 'folder-domino', mark: '▥', tone: 'lilac', title: '서류 도미노', detail: '한 번 밀어 서류철 줄줄이 넘기기' },
  { id: 'rubber-billiards', mark: '◈', tone: 'rose', title: '고무줄 당구', detail: '지우개로 메모를 책상 밖으로' },
  { id: 'cafe-rush', mark: '☕', tone: 'sand', title: '점심 카페 러시', detail: '커피와 샌드위치 준비하고 내주기' },
  { id: 'desk-sort', mark: '▤', tone: 'lilac', title: '퇴근 정리', detail: '업무 카드 세 장을 모아 서류함 비우기' },
  { id: 'golf', mark: '♠', tone: 'leaf', title: '골프 솔리테어', detail: '숫자를 이어 카드 지우기' },
  { id: 'block-office', mark: '▦', tone: 'sand', title: '미니어처 철거', detail: '35초 안에 블록 사옥 무너뜨리기' },

];

export default function GamesPage() {
  return <main className={styles.arcade}>
    <header className={styles.bar}><Link href="/minimal">breakground</Link><Link href="/minimal">채팅</Link></header>
    <div className={styles.gameTitle}><h1>잠깐, 한 판</h1></div>
    <ul className={styles.gameList}>
      {games.map(game => <li key={game.id}>
        <Link href={`/games/${game.id}`} prefetch={false}>
          <span className={styles.gameMark} data-tone={game.tone} aria-hidden="true">{game.mark}</span>
          <span><strong>{game.title}</strong><small>{game.detail}</small></span>
          <span className={styles.playMark} aria-hidden="true">▶</span>
        </Link>
      </li>)}
    </ul>
  </main>;
}

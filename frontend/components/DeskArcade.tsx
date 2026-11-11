'use client';

import Link from 'next/link';
import { useEffect, useRef, useState, type PointerEvent } from 'react';
import {
  aimAngle, chairPoints, CUP, deskGames, FOLDER_LEVELS, GOAL, HEIGHT,
  launchDeskShot, makeDeskBoard, START, stepDeskBoard, WIDTH,
  type DeskGame, type Point,
} from '../lib/desk-games';
import styles from './MiniArcade.module.css';

type Phase = 'ready' | 'moving' | 'result' | 'cleared' | 'finished';

export default function DeskArcade({ game }: { game: DeskGame }) {
  const config = deskGames[game];
  const liveBoard = useRef(makeDeskBoard(game));
  const frame = useRef<number | null>(null);
  const activePointer = useRef<number | null>(null);
  const [board, setBoard] = useState(() => makeDeskBoard(game));
  const [aim, setAim] = useState<Point>(GOAL);
  const [power, setPower] = useState<number>(config.power);
  const [level, setLevel] = useState(0);
  const [shots, setShots] = useState(0);
  const [score, setScore] = useState(0);
  const [phase, setPhase] = useState<Phase>('ready');
  const [message, setMessage] = useState('방향과 힘을 정해 한 번 밀어보세요.');

  useEffect(() => () => {
    if (frame.current !== null) cancelAnimationFrame(frame.current);
  }, []);

  function publishBoard() {
    setBoard(structuredClone(liveBoard.current));
  }

  function shoot(point = aim) {
    if (phase !== 'ready' || frame.current !== null) return;
    if (Math.hypot(point.x - START.x, point.y - START.y) < 12) {
      setMessage('출발점에서 조금 떨어진 곳을 겨냥해 주세요.');
      return;
    }
    if (game !== 'rubber-billiards') liveBoard.current = makeDeskBoard(game, level);
    const shotBoard = liveBoard.current;
    launchDeskShot(shotBoard, aimAngle(point), power);
    const used = shots + 1;
    setShots(used);
    setPhase('moving');
    setMessage('움직이는 중…');
    let last = performance.now();
    function animate(now: number) {
      const elapsed = Math.min(.05, Math.max(0, (now - last) / 1000));
      last = now;
      const steps = Math.max(1, Math.ceil(elapsed * 120));
      let done = false;
      for (let step = 0; step < steps && !done; step++) {
        done = stepDeskBoard(game, shotBoard, elapsed / steps);
      }
      publishBoard();
      if (!done) {
        frame.current = requestAnimationFrame(animate);
        return;
      }
      frame.current = null;
      if (game === 'chair-curling') {
        const points = chairPoints(shotBoard.striker);
        setScore(value => value + points);
        setMessage(points ? `이번 밀기 +${points}점` : '목표 원과 멀리 멈췄어요. 힘과 방향을 조절해 보세요.');
      } else if (game === 'folder-domino') {
        const fallen = shotBoard.folders.filter(folder => folder.progress > 0).length;
        if (shotBoard.folders.at(-1)?.progress) {
          setScore(value => value + fallen * 10);
          setMessage(`배치 ${level + 1} 성공! ${fallen}개를 넘어뜨렸어요.`);
          setPhase(level + 1 === FOLDER_LEVELS ? 'finished' : 'cleared');
          return;
        }
        setMessage(`${fallen}개가 넘어졌어요. 각도나 힘을 바꿔보세요.`);
      } else {
        const pushed = shotBoard.notes.filter(note => note.out).length;
        setScore(pushed * 100);
        setMessage(`메모 ${pushed}/${shotBoard.notes.length}장을 밀어냈어요.`);
        if (pushed === shotBoard.notes.length) {
          setPhase('finished');
          return;
        }
      }
      setPhase(used >= config.shots ? 'finished' : 'result');
    }
    frame.current = requestAnimationFrame(animate);
  }

  function prepare(nextLevel: number, resetRound: boolean) {
    if (frame.current !== null) cancelAnimationFrame(frame.current);
    frame.current = null;
    activePointer.current = null;
    if (resetRound || game !== 'rubber-billiards') {
      liveBoard.current = makeDeskBoard(game, nextLevel);
    } else {
      const fresh = makeDeskBoard(game);
      liveBoard.current.striker = fresh.striker;
      for (const note of liveBoard.current.notes) { note.vx = 0; note.vy = 0; }
    }
    setLevel(nextLevel);
    if (resetRound || nextLevel !== level) setShots(0);
    if (resetRound) { setScore(0); setPower(config.power); }
    setAim(GOAL);
    setPhase('ready');
    setMessage('방향과 힘을 정해 한 번 밀어보세요.');
    publishBoard();
  }

  function pointerPosition(event: PointerEvent<SVGSVGElement>) {
    const matrix = event.currentTarget.getScreenCTM();
    if (!matrix) return null;
    const point = new DOMPoint(event.clientX, event.clientY).matrixTransform(matrix.inverse());
    return { x: Math.min(WIDTH, Math.max(0, point.x)), y: Math.min(HEIGHT, Math.max(0, point.y)) };
  }

  const striker = board.striker;
  const folderGame = game === 'folder-domino';
  const chairGame = game === 'chair-curling';

  return <main className={styles.arcade}>
    <header className={styles.bar}><Link href="/games">게임 목록</Link><Link href="/minimal">채팅</Link></header>
    <div className={styles.gameTitle}>
      <div><h1>{config.title}</h1><p>{config.rule}</p></div>
      <div className={styles.counter}><strong>{score}</strong><span>점</span></div>
    </div>
    <div className={styles.boardCaption}>
      <span>{folderGame ? `배치 ${level + 1}/${FOLDER_LEVELS}` : chairGame ? '엘리베이터 앞' : '내 책상'}</span>
      <span>남은 기회 {config.shots - shots}</span>
    </div>
    <svg className={styles.deskBoard} viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      tabIndex={0} role="group" aria-label={`${config.title} 경기판. 방향키로 조준하고 스페이스로 발사`}
      onPointerDown={event => {
        if (phase !== 'ready' || !event.isPrimary || event.button !== 0) return;
        activePointer.current = event.pointerId;
        event.currentTarget.setPointerCapture(event.pointerId);
        const point = pointerPosition(event);
        if (point) setAim(point);
      }}
      onPointerMove={event => {
        if (phase !== 'ready' || (event.pointerType !== 'mouse' && activePointer.current !== event.pointerId)) return;
        const point = pointerPosition(event);
        if (point) setAim(point);
      }}
      onPointerUp={event => {
        if (activePointer.current !== event.pointerId) return;
        activePointer.current = null;
        const point = pointerPosition(event);
        if (point) shoot(point);
      }}
      onPointerCancel={() => { activePointer.current = null; }}
      onKeyDown={event => {
        if (phase !== 'ready') return;
        if (event.code === 'Space' || event.key === 'Enter') {
          event.preventDefault();
          shoot();
          return;
        }
        const dx = event.key === 'ArrowLeft' ? -18 : event.key === 'ArrowRight' ? 18 : 0;
        const dy = event.key === 'ArrowUp' ? -18 : event.key === 'ArrowDown' ? 18 : 0;
        if (dx || dy) {
          event.preventDefault();
          setAim(point => ({ x: Math.max(0, Math.min(WIDTH, point.x + dx)), y: Math.max(0, Math.min(HEIGHT, point.y + dy)) }));
        }
      }}>
      <rect width={WIDTH} height={HEIGHT} fill={chairGame ? '#d7c09c' : '#244e45'}/>
      {Array.from({ length: 11 }, (_, index) => <path key={index} d={`M0 ${index * 36}H640`} stroke={chairGame ? '#8b704c' : '#bed9bf'} strokeOpacity=".13"/>)}
      <rect x="5" y="5" width="630" height="350" rx="11" fill="none" stroke={chairGame ? '#735a3d' : '#90b5a1'} strokeOpacity=".55" strokeWidth="3"/>
      {chairGame && <>
        <rect x="273" y="79" width="92" height="202" rx="6" fill="#869789" stroke="#f0e7c9" strokeWidth="3" strokeDasharray="4 5"/>
        <text x="319" y="96" textAnchor="middle" fill="#193c32" fontSize="15" fontWeight="700">카펫</text>
        {[46, 31, 16].map(radius => <circle key={radius} cx={GOAL.x} cy={GOAL.y} r={radius} fill={radius === 16 ? '#edd783' : '#d9e7d5'} stroke="#426e5c" strokeWidth="2"/>)}
      </>}
      {game === 'rubber-billiards' && <>
        <circle cx={CUP.x + 3} cy={CUP.y + 5} r={CUP.radius + 3} fill="#132e2a"/>
        <circle cx={CUP.x} cy={CUP.y} r={CUP.radius} fill="#efe7cf"/>
        <circle cx={CUP.x} cy={CUP.y} r="17" fill="#a87a52"/>
        {board.notes.map((note, index) => !note.out && <g key={index} transform={`translate(${note.x} ${note.y}) rotate(-10)`}>
          <rect x="-14" y="-14" width="29" height="30" rx="2" fill="#172f2a" opacity=".4" transform="translate(3 4)"/>
          <rect x="-14" y="-14" width="29" height="30" rx="2" fill={index % 2 ? '#f1df9e' : '#dfebc1'}/>
          <path d="M-7 -4H7M-7 3H4" stroke="#737b54" strokeWidth="2"/>
        </g>)}
      </>}
      {board.folders.map((folder, index) => {
        const final = index === board.folders.length - 1;
        const offset = folder.progress * 15;
        const color = final ? '#e9c568' : folder.heavy ? '#b6a4ce' : '#8bb9a6';
        return <g key={index} transform={`translate(${folder.x} ${folder.y}) rotate(${folder.angle * 180 / Math.PI})`}>
          <rect x="-7" y="-20" width="14" height="40" rx="3" fill="none" stroke="#92b8a4" strokeOpacity=".35"/>
          <rect x={-7 + offset} y="-19" width={14 + offset} height="38" rx="2" fill={color} stroke="#183b32" strokeWidth="2"/>
          <path d={`M${-3 + offset} -14v9`} stroke="#ffffff" strokeOpacity=".65" strokeWidth="3"/>
        </g>;
      })}
      {phase === 'ready' && <>
        <path d={`M${START.x} ${START.y}L${aim.x} ${aim.y}`} stroke={chairGame ? '#385c4d' : '#e4deb4'} strokeWidth="2" strokeDasharray="6 7"/>
        <circle cx={aim.x} cy={aim.y} r="8" fill="none" stroke={chairGame ? '#385c4d' : '#e4deb4'} strokeWidth="2"/>
      </>}
      <g transform={`translate(${striker.x} ${striker.y})`}>
        {chairGame ? <>
          <path d="M-15 -15L15 15M15 -15L-15 15" stroke="#273d38" strokeWidth="6" strokeLinecap="round"/>
          <rect x="-13" y="-13" width="26" height="27" rx="7" fill="#4a796c" stroke="#294b40" strokeWidth="3"/>
          <path d="M-15 -8V-14Q0 -22 15 -14V-8" fill="#365e53"/>
        </> : <rect x="-12" y="-9" width="24" height="18" rx="4" fill={folderGame ? '#dda978' : '#e7a3a0'} stroke="#fff5dc" strokeWidth="2"/>}
      </g>
    </svg>
    <p className={styles.feedback} role="status">{message}</p>
    <div className={styles.controls}>
      <label className={styles.power}>힘 <input aria-label="힘" type="range" min="10" max="100" value={power}
        disabled={phase !== 'ready'} onChange={event => setPower(Number(event.target.value))}/><output>{power}</output></label>
      {phase === 'ready' && <button className={styles.primary} onClick={() => shoot()}>{config.action}</button>}
      {phase === 'moving' && <button className={styles.primary} disabled>기다리기</button>}
      {phase === 'result' && <button className={styles.primary} onClick={() => prepare(level, false)}>다음 시도</button>}
      {phase === 'cleared' && <button className={styles.primary} onClick={() => prepare(level + 1, false)}>다음 배치</button>}
      {phase === 'finished' && <button className={styles.primary} onClick={() => prepare(0, true)}>다시 하기</button>}
      {phase !== 'finished' && <button className={styles.secondary} onClick={() => prepare(0, true)}>처음부터</button>}
    </div>
    <p className={styles.help}>경기판을 누르면 그 방향으로 밀어요. 키보드는 방향키로 조준하고 스페이스로 발사해요.</p>
  </main>;
}

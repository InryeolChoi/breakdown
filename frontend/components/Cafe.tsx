'use client';
import Link from 'next/link';
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { displayTime, isRoom, parseChat, validateContent, type ChatMessage, type Room, type User } from '../lib/protocol';

import { games } from '../lib/game-catalog';
import GlacierAtmosphere from './GlacierAtmosphere';

type Mode = 'demo' | 'live';
type Phase = 'entry' | 'connecting' | 'connected' | 'closed';
const activities = [
  { id: 'chat', label: '채팅', icon: '🍵' },
  { id: 'game', label: '게임', icon: '🍪' },
  { id: 'meditation', label: '명상', icon: '🍬' },
] as const;
type Activity = typeof activities[number]['id'];
type Theme = 'ice' | 'warm' | 'bamboo' | 'black';
const THEMES: {id: Theme; label: string; description: string}[] = [
  { id: 'ice', label: '빙하', description: '시원한 공기가 스쳐가는 자리' },
  { id: 'warm', label: '온기', description: '햇살처럼 포근한 공기' },
  { id: 'bamboo', label: '대나무숲', description: '초록 그늘 사이로 스며드는 빛' },
  { id: 'black', label: '블랙', description: '빛을 낮춘 조용한 밤' },
];
const DEMO_ROOM: Room = { id: -1, name: '점심메뉴방', openAt: '10:00', closeAt: '11:30' };
const DEMO_REPLIES = ['저는 오늘 따뜻한 국수가 당기네요.', '잠깐 쉬니까 조금 살 것 같아요.', '이런 얘기하면서 메뉴 정하는 게 좋네요.'];
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, { ...options, cache: 'no-store', credentials: 'same-origin', signal: options?.signal || AbortSignal.timeout(10000) });
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.message || `요청을 처리하지 못했어요. (${response.status})`);
  return data as T;
}
function sampleMessage(roomId: number, authorId: number, nickname: string, content: string): ChatMessage {
  const now = new Date();
  const seoul = (date: Date) => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul', dateStyle: 'short', timeStyle: 'medium' }).format(date).replace(' ', 'T');
  return { id: crypto.randomUUID(), roomId, authorId, nickname, content, sentAt: seoul(now), expiresAt: seoul(new Date(now.getTime() + 900000)) };
}
export default function Cafe({ variant, initialActivity = null }: {
  variant: 'spatial' | 'minimal';
  initialActivity?: Activity | null;
}) {
  const minimal = variant === 'minimal';
  const [activity, setActivity] = useState<Activity | null>(initialActivity);
  const [mode, setMode] = useState<Mode>(minimal ? 'live' : 'demo');
  const [rooms, setRooms] = useState<Room[]>(minimal ? [] : [DEMO_ROOM]);
  const [selected, setSelected] = useState<number | null>(minimal ? null : DEMO_ROOM.id);
  const [loading, setLoading] = useState(minimal);
  const [phase, setPhase] = useState<Phase>('entry');
  const [nickname, setNickname] = useState('');
  const [user, setUser] = useState<User | null>(null);
  const [activeRoom, setActiveRoom] = useState<Room | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [notice, setNotice] = useState('');
  const [roomError, setRoomError] = useState('');
  const [quiet, setQuiet] = useState(variant === 'spatial');
  const [theme, setTheme] = useState<Theme>('warm');
  const [reportTarget, setReportTarget] = useState<ChatMessage | null>(null);
  const [reason, setReason] = useState('');
  const [reportError, setReportError] = useState('');
  const [reportBusy, setReportBusy] = useState(false);
  const [reported, setReported] = useState<string[]>([]);
  const [pending, setPending] = useState(0);
  const socket = useRef<WebSocket | null>(null);
  const generation = useRef(0);
  const roomRequest = useRef(0);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  const transcript = useRef<HTMLDivElement>(null);
  const entering = phase === 'entry' || phase === 'connecting';
  const showActivities = minimal && entering;
  const showChat = !showActivities || activity === 'chat';
  const shelfRef = useRef<HTMLDivElement>(null);
  const appearanceRef = useRef<HTMLDetailsElement>(null);
  const activityHeading = useRef<HTMLHeadingElement>(null);
  const focusAfterSelection = useRef(false);

  useEffect(() => {
    if (!minimal) return;

    function dismissOutside(event: MouseEvent) {
      const settings = appearanceRef.current;
      if (settings?.open && event.target instanceof Node && !settings.contains(event.target)) {
        settings.open = false;
      }
    }

    function dismissWithEscape(event: KeyboardEvent) {
      const settings = appearanceRef.current;
      if (event.key === 'Escape' && settings?.open) {
        settings.open = false;
        if (settings.contains(document.activeElement)) settings.querySelector('summary')?.focus();
      }
    }

    document.addEventListener('click', dismissOutside);
    document.addEventListener('keydown', dismissWithEscape);
    return () => {
      document.removeEventListener('click', dismissOutside);
      document.removeEventListener('keydown', dismissWithEscape);
    };
  }, [minimal]);

  function chooseActivity(next: Activity | null) {
    focusAfterSelection.current = true;
    setActivity(next);
  }

  useEffect(() => {
    if (!focusAfterSelection.current) return;
    focusAfterSelection.current = false;
    if (activity === null) shelfRef.current?.querySelector('button')?.focus({ preventScroll: true });
    else activityHeading.current?.focus({ preventScroll: true });
  }, [activity]);

  const disconnect = useCallback(() => {
    generation.current += 1;
    if (socket.current) { const ws = socket.current; socket.current = null; ws.close(1000, 'leaving'); }
    timers.current.forEach(clearTimeout); timers.current = [];
    setPending(0);
  }, []);
  const loadRooms = useCallback(async (nextMode: Mode, signal?: AbortSignal) => {
    const current = ++roomRequest.current;
    setRoomError('');
    if (nextMode === 'demo') { setRooms([DEMO_ROOM]); setSelected(DEMO_ROOM.id); setLoading(false); return; }
    setLoading(true); setRooms([]); setSelected(null);
    try {
      const result = await request<unknown>('/rooms', { signal });
      if (!Array.isArray(result) || !result.every(isRoom)) throw new Error('방 목록의 형식을 확인할 수 없어요.');
      if (!signal?.aborted && roomRequest.current === current) { setRooms(result); setSelected(result[0]?.id ?? null); }
    } catch (error) { if (!signal?.aborted && roomRequest.current === current) setRoomError(error instanceof Error ? error.message : '방 목록을 불러오지 못했어요.'); }
    finally { if (!signal?.aborted && roomRequest.current === current) setLoading(false); }
  }, []);
  useEffect(() => { const controller = new AbortController(); void loadRooms(mode, controller.signal); return () => controller.abort(); }, [mode, loadRooms]);
  useEffect(() => () => { generation.current += 1; socket.current?.close(); timers.current.forEach(clearTimeout); }, []);
  useEffect(() => { if (transcript.current) transcript.current.scrollTop = transcript.current.scrollHeight; }, [messages]);
  useEffect(() => {
    if (!reportTarget) return;
    const previous = document.activeElement as HTMLElement | null;
    const dialog = document.querySelector('.report-dialog');
    dialog?.querySelector('textarea')?.focus();
    function keyboard(event: KeyboardEvent) {
      if (event.key === 'Escape' && !reportBusy) setReportTarget(null);
      if (event.key !== 'Tab') return;
      const controls = dialog?.querySelectorAll<HTMLElement>('button:not(:disabled), textarea');
      if (!controls?.length) return;
      const first = controls[0], last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
    document.addEventListener('keydown', keyboard);
    return () => { document.removeEventListener('keydown', keyboard); previous?.focus(); };
  }, [reportTarget, reportBusy]);

  function changeMode(next: Mode) { disconnect(); setMode(next); setPhase('entry'); setUser(null); setMessages([]); setNotice(''); setReportTarget(null); setReported([]); }
  function append(message: ChatMessage) {
    setMessages(old => old.some(item => item.id === message.id) ? old : [...old.slice(-199), message]);
  }
  async function enter(event: FormEvent) {
    event.preventDefault(); if (loading || phase === 'connecting' || selected === null) return;
    const room = rooms.find(item => item.id === selected); if (!room) return;
    if (nickname.trim().length > 30) { setNotice('닉네임은 30자 이내로 입력해 주세요.'); return; }
    disconnect(); const current = generation.current;
    setNotice(''); setPhase('connecting'); setMessages([]); setReported([]); setActiveRoom(room);
    if (mode === 'demo') {
      const me: User = { id: 1, nickname: nickname.trim() || '느긋한오리', status: 'ACTIVE' };
      setUser(me); setPhase('connected');
      append(sampleMessage(room.id, 2, '체험 도우미', '어서 와요. 오늘 점심은 뭐 드실 거예요?'));
      return;
    }
    try {
      const me = await request<User>('/anonymoususer', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ nickname: nickname.trim() || null }) });
      if (generation.current !== current) return;
      if (me.status === 'BANNED') throw new Error('현재 차단 상태라 입장할 수 없어요.');
      if (!Number.isInteger(me.id) || typeof me.nickname !== 'string' || me.status !== 'ACTIVE') throw new Error('사용자 응답을 확인할 수 없어요.');
      setUser(me);
      const ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/rooms/${room.id}`);
      socket.current = ws;
      const timeout = setTimeout(() => { if (generation.current === current && ws.readyState !== WebSocket.OPEN) { setNotice('연결 시간이 초과됐어요. 다시 입장해 주세요.'); ws.close(); setPhase('closed'); } }, 10000);
      ws.onopen = () => { clearTimeout(timeout); if (generation.current !== current) { ws.close(); return; } setPhase('connected'); };
      ws.onmessage = event => {
        if (generation.current !== current) return;
        try {
          const data = parseChat(event.data);
          if ('type' in data) {
            setNotice(data.message); setPending(0);
            if (data.status === 401 || data.status === 403) { setPhase('closed'); ws.close(1008, 'policy'); }
          } else if (data.roomId === room.id) { append(data); if (data.authorId === me.id) setPending(value => Math.max(0, value - 1)); }
        } catch { setNotice('받은 메시지를 표시하지 못했어요.'); }
      };
      ws.onerror = () => { if (generation.current === current) setNotice('채팅에 연결하지 못했어요. 방 운영 상태와 서버 연결을 확인해 주세요.'); };
      ws.onclose = event => { clearTimeout(timeout); if (generation.current !== current) return; setPhase('closed'); setPending(0); setNotice(old => old || (event.code === 1008 ? '서버 정책에 따라 연결이 종료됐어요.' : '연결이 종료됐어요. 다시 입장할 수 있어요.')); };
    } catch (error) { if (generation.current === current) { setNotice(error instanceof Error ? error.message : '입장하지 못했어요.'); setPhase('entry'); } }
  }
  function leave() { disconnect(); setPhase('entry'); setMessages([]); setNotice(''); setReportTarget(null); void loadRooms(mode); }
  function send(draft: string): boolean {
    if (phase !== 'connected' || !user || !activeRoom) return false;
    const error = validateContent(draft); if (error) { setNotice(error); return false; }
    const content = draft.trim(); setNotice('');
    if (mode === 'demo') {
      append(sampleMessage(activeRoom.id, user.id, user.nickname, content));
      const current = generation.current;
      const reply = DEMO_REPLIES[messages.filter(item => item.authorId === user.id).length % DEMO_REPLIES.length];
      timers.current.push(setTimeout(() => { if (generation.current === current) append(sampleMessage(activeRoom.id, 2, '체험 도우미', reply)); }, 650));
    } else {
      if (socket.current?.readyState !== WebSocket.OPEN) { setNotice('연결이 끊겼어요. 다시 입장해 주세요.'); setPhase('closed'); return false; }
      try { socket.current.send(JSON.stringify({ content })); setPending(value => value + 1); }
      catch { setNotice('전송하지 못했어요. 연결을 확인해 주세요.'); return false; }
    }
    return true;
  }
  async function report(event: FormEvent) {
    event.preventDefault(); if (!reportTarget || !activeRoom || reportBusy) return;
    if (!reason.trim()) return;
    setReportError('');
    setReportBusy(true); const current = generation.current; const id = reportTarget.id;
    try {
      if (mode === 'live') await request(`/rooms/${activeRoom.id}/reports`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ messageId: id, reason: reason.trim() }) });
      if (generation.current !== current) return;
      setReported(old => [...old, id]); setReportTarget(null); setReason(''); setNotice(mode === 'demo' ? '신고 화면 체험을 마쳤어요. 실제 신고는 접수되지 않았어요.' : '신고가 접수됐어요.');
    } catch (error) { if (generation.current === current) setReportError(error instanceof Error ? error.message : '신고를 접수하지 못했어요.'); }
    finally { setReportBusy(false); }
  }
  return <main className={`cafe ${variant}${minimal && entering ? " entry-screen" : ""}`} data-theme={variant === 'minimal' ? theme : undefined} data-phase={phase}>
    <div className="cafe-scene" aria-hidden="true" />
    {minimal && theme === 'ice' && <GlacierAtmosphere />}
    {variant === 'spatial' && <div className="table-depth" aria-hidden="true"/>}
    <header className="cafe-header">
      <Link href="/minimal" className="wordmark" onNavigate={() => {
        if (!minimal) return;
        setActivity(null);
        leave();
        if (mode !== 'live') setMode('live');
        window.scrollTo({ top: 0, behavior: 'instant' });
      }}>breakground</Link>
      {!minimal && <nav aria-label="게임"><Link href="/games">게임</Link></nav>}
    </header>
    {!minimal && <div className="mode-bar">
      <span className="mode-status"><span className={`status-dot ${mode}`}/>{mode === 'demo' ? '체험 모드' : '실제 모드'}</span>
      <button className="mode-switch" onClick={() => changeMode(mode === 'demo' ? 'live' : 'demo')}>
        {mode === 'demo' ? '실제 서버로 바꾸기' : '체험 모드로 바꾸기'}
      </button>
    </div>}
    {!minimal && <div className="scene-actions">
      <button onClick={() => setQuiet(value => !value)}>{quiet ? '입장 · 대화 열기' : '공간만 보기'}</button>
    </div>}
    <section className={`conversation-console ${quiet ? 'console-hidden' : ''}`} aria-label={showActivities ? '쉬는 방법 고르기' : entering ? '방 입장' : '채팅'}>
      {showActivities && <div className="shelf-curtain" data-open={activity === null} id="activity-shelf"
        ref={shelfRef} inert={activity !== null} aria-hidden={activity !== null}>
        <div className="shelf-curtain-inner">
          <h1 className="entry-title">잠깐 쉬어요.</h1>
          <div className="activity-shelf" role="group" aria-label="쉬는 방법">
            {activities.map(item => <button key={item.id} type="button" onClick={() => chooseActivity(item.id)}>
              <span className="activity-icon" aria-hidden="true">{item.icon}</span>
              {item.label}
            </button>)}
          </div>
        </div>
      </div>}
      {showActivities && activity !== null && <h1 className="activity-heading" ref={activityHeading} tabIndex={-1}>
        {activity === 'chat' ? '채팅방' : activity === 'game' ? '게임' : '명상'}
      </h1>}
      {showActivities && activity === 'game' && <ul className="lobby-game-list" aria-label="게임 목록">
        {games.map(game => <li key={game.id}>
          <Link href={`/games/${game.id}`} prefetch={false}>
            <span className="lobby-game-mark" data-tone={game.tone} aria-hidden="true">{game.mark}</span>
            <span><strong>{game.title}</strong><small>{game.detail}</small></span>
            <span className="lobby-game-arrow" aria-hidden="true">→</span>
          </Link>
        </li>)}
      </ul>}
      {showActivities && activity === 'meditation' && <MeditationTimer />}
      {showChat && <div className="console-bezel">
      {!entering && <div className="console-topline"><span className="console-state" role="status">
        {phase === 'closed' ? '연결 종료' : mode === 'demo' ? '체험 중' : '연결됨'}
      </span></div>}
      {entering ? <form className="entry-form" onSubmit={enter}>
        <fieldset disabled={loading || phase === 'connecting'}>
          <legend>{mode === 'demo' && minimal ? '채팅 체험' : '지금 열린 방'}</legend>
          {loading ? <p className="room-empty" role="status">방을 찾아보고 있어요…</p>
            : roomError ? <div className="room-empty"><p role="status">{roomError}</p>
                <button type="button" onClick={() => void loadRooms(mode)}>다시 시도</button></div>
            : rooms.length ? <div className={minimal ? "room-options lobby-rooms" : "room-options"}>
                {rooms.map(room => <label key={room.id} className={`room-option ${selected === room.id ? 'selected' : ''}`}>
                  <input type="radio" name="room" value={room.id} checked={selected === room.id} onChange={() => setSelected(room.id)}/>
                  <span className="room-name"><strong>{room.name}</strong><small>{room.openAt.slice(0, 5)}–{room.closeAt.slice(0, 5)}</small></span>
                  {minimal && <span className="room-signal" aria-hidden="true" />}
                </label>)}
              </div>
            : <div className="room-empty"><strong>지금은 열린 방이 없어요.</strong>
                <p>평일에 채팅방이 열려요.</p>
                <button type="button" onClick={() => void loadRooms(mode)}>방 목록 새로고침</button></div>}
        </fieldset>
        {(!minimal || rooms.length > 0) && <div className="entry-bottom">
          <label className="nickname-label" htmlFor="nickname">닉네임 <span>선택</span>
            <input id="nickname" value={nickname} onChange={event => setNickname(event.target.value)}
              placeholder="비워두면 자동 생성" maxLength={30} autoComplete="off" disabled={phase === 'connecting'}/>
          </label>
          <button className="primary-action" type="submit" disabled={selected === null || loading || !!roomError || phase === 'connecting'}>
            {phase === 'connecting' ? '입장하는 중…' : '입장하기'}
          </button>
        </div>}
        {minimal && mode === 'demo' && <p className="entry-note">예시 대화예요. 입력한 내용은 저장하지 않아요.</p>}
      </form> : <div className="chat-panel"><div className="chat-heading"><div><h2>{activeRoom?.name}</h2><p><strong>{user?.nickname}</strong>으로 쉬는 중</p></div><button className="leave" onClick={leave}>{phase === 'closed' ? '다시 입장' : '나가기'}</button></div>
        <div className="transcript" ref={transcript} role="log" aria-label="대화 내용" aria-live="polite" aria-relevant="additions text">
          <p className="chat-welcome">{mode === 'demo' ? '체험 도우미가 예시 답변을 보내요.' : '입장 후 도착하는 대화부터 보여요.'}</p>
          {messages.map(message => <article className={`message ${message.authorId === user?.id ? 'mine' : ''}`} key={message.id}><div className="message-meta"><strong>{message.authorId === user?.id ? `${message.nickname} (나)` : message.nickname}</strong><time>{displayTime(message.sentAt)}</time>{message.authorId !== user?.id && <button disabled={reported.includes(message.id) || phase !== 'connected'} onClick={() => { setReportTarget(message); setReason(''); setReportError(''); setNotice(''); }}>{reported.includes(message.id) ? '신고됨' : '신고'}</button>}</div><p>{message.content}</p></article>)}
        </div>
        <ChatComposer phase={phase} pending={pending} onSend={send} />
      </div>}
      {notice && <p className="notice" role="status">{notice}</p>}
      </div>}
      {showActivities && showChat && <button className="entry-mode-button" type="button" disabled={phase === 'connecting'}
        onClick={() => changeMode(mode === 'demo' ? 'live' : 'demo')}>
        {mode === 'demo' ? '실제 방 목록 보기' : '채팅 체험하기'}
      </button>}
    </section>
      {showActivities && activity !== null && <button type="button" className="show-shelf"
        aria-controls="activity-shelf" aria-expanded={false} disabled={phase === 'connecting'}
        onClick={() => {
          window.scrollTo({ top: 0, behavior: 'instant' });
          chooseActivity(null);
        }}>선반 열기 <span aria-hidden="true">↓</span></button>}
    {minimal && <footer className="theme-dock">
      <details className="appearance-settings" ref={appearanceRef}>
        <summary aria-label="화면 분위기">
          <span className="appearance-label-start" aria-hidden="true">화면</span>
          <span className="appearance-label-end" aria-hidden="true">분위기</span>
        </summary>
        <div className="theme-switcher" role="group" aria-label="분위기 선택">
          {THEMES.map(item => <button key={item.id} type="button" aria-pressed={theme === item.id} onClick={() => setTheme(item.id)}>
            <span className={`theme-dot theme-${item.id}`} aria-hidden="true"/>{item.label}
          </button>)}
        </div>
      </details>
    </footer>}
    {reportTarget && <div className="modal-backdrop"><section className="report-dialog" role="dialog" aria-modal="true" aria-labelledby="report-title"><h2 id="report-title">이 메시지로 신고할까요?</h2><blockquote>{reportTarget.content}</blockquote><form onSubmit={report}><label htmlFor="reason">신고 사유</label><textarea autoFocus id="reason" maxLength={500} value={reason} onChange={event => setReason(event.target.value)} placeholder="어떤 점이 문제였는지 적어주세요." required/><p>{mode === 'demo' ? '체험 모드예요. 실제 신고는 접수되지 않아요.' : '메시지 원본과 신고 사유를 서버에 보내요.'}</p>{reportError && <p className="report-error" role="alert">{reportError}</p>}<div><button type="button" onClick={() => setReportTarget(null)} disabled={reportBusy}>취소</button><button className="primary-action" disabled={!reason.trim() || reportBusy}>{reportBusy ? '접수 중…' : '신고하기'}</button></div></form></section></div>}
  </main>;
}

function ChatComposer({ phase, pending, onSend }: {
  phase: Phase;
  pending: number;
  onSend: (draft: string) => boolean;
}) {
  const [draft, setDraft] = useState('');
  const connected = phase === 'connected';
  const trimmed = draft.trim();

  return <>
    <form className="composer" onSubmit={event => {
      event.preventDefault();
      if (onSend(draft)) setDraft('');
    }}>
      <label className="sr-only" htmlFor="message">메시지</label>
      <textarea
        id="message"
        value={draft}
        onChange={event => setDraft(event.target.value)}
        placeholder={phase === 'closed' ? '연결이 종료됐어요.' : '가볍게 한마디 남겨보세요'}
        disabled={!connected}
        rows={1}
        onKeyDown={event => {
          if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
            event.preventDefault();
            event.currentTarget.form?.requestSubmit();
          }
        }}
      />
      <button className="primary-action" type="submit" disabled={!connected || !trimmed}>보내기</button>
    </form>
    <div className="composer-meta">
      <span>{[...trimmed].length}/140</span>
      <span>{pending ? `전송 확인 중 ${pending}건` : 'Enter 전송 · Shift + Enter 줄바꿈'}</span>
    </div>
  </>;
}

const MEDITATION_SECONDS = 180;

function MeditationTimer() {
  const [seconds, setSeconds] = useState(MEDITATION_SECONDS);
  const [deadline, setDeadline] = useState<number | null>(null);

  useEffect(() => {
    if (deadline === null) return;
    const interval = window.setInterval(() => {
      const remaining = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
      setSeconds(remaining);
      if (remaining === 0) setDeadline(null);
    }, 200);
    return () => window.clearInterval(interval);
  }, [deadline]);

  function toggle() {
    if (deadline !== null) {
      setSeconds(Math.max(0, Math.ceil((deadline - Date.now()) / 1000)));
      setDeadline(null);
      return;
    }
    const duration = seconds || MEDITATION_SECONDS;
    setSeconds(duration);
    setDeadline(Date.now() + duration * 1000);
  }

  return <div className="activity-panel">
    <h2>3분 쉬기</h2>
    <p>{seconds === 0 ? '잠깐 쉬었어요. 천천히 돌아와요.' : '어깨에 힘을 빼고 숨을 천천히 쉬어요.'}</p>
    <div className="activity-links">
      <span className="meditation-clock" role="timer" aria-label="명상 남은 시간">
        {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}
      </span>
      <button className="primary-action" type="button" onClick={toggle}>
        {deadline !== null ? '잠시 멈춤' : seconds === 0 ? '다시 시작' : seconds === MEDITATION_SECONDS ? '시작' : '계속하기'}
      </button>
    </div>
  </div>;
}

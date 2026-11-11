'use client';

import Link from 'next/link';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import * as THREE from 'three';
import arcade from './MiniArcade.module.css';
import styles from './BlockOffice.module.css';

const ROUND_SECONDS = 35;
const DEFAULT_SITE = '브레이크그라운드 사옥';
const tools = {
  ball: { name: '고무공', detail: '블록 하나', delay: 220 },
  stamp: { name: '도장', detail: '같은 높이의 주변 블록', delay: 1050 },
  magnet: { name: '자석', detail: '한 줄의 블록', delay: 1500 },
};
type Tool = keyof typeof tools;
type Phase = 'ready' | 'playing' | 'won' | 'over';
type Block = { mesh: THREE.Mesh; x: number; y: number; z: number };
type Debris = { mesh: THREE.Mesh; velocity: THREE.Vector3; life: number };
type Board = { reset(site: string): number; hit(tool: Tool, point?: { x: number; y: number }): number };

function createBoard(host: HTMLDivElement) {
  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFShadowMap;
  host.append(renderer.domElement);

  const scene = new THREE.Scene();
  const camera = new THREE.OrthographicCamera();
  camera.position.set(11, 13, 12);
  camera.lookAt(0, 1, 0);

  const sun = new THREE.DirectionalLight(0xffefd2, 2.3);
  sun.position.set(-4, 12, 6);
  sun.castShadow = true;
  sun.shadow.mapSize.set(1024, 1024);
  Object.assign(sun.shadow.camera, { left: -9, right: 9, top: 9, bottom: -9, far: 30 });
  sun.shadow.camera.updateProjectionMatrix();
  sun.shadow.normalBias = 0.035;
  scene.add(sun, new THREE.HemisphereLight(0xf9f7e6, 0x48695f, 2));

  const cube = new THREE.BoxGeometry(1, 1, 1);
  const colors = [0x2e5550, 0xa7bb94, 0x526e70, 0xd2bb90, 0x86a9a0, 0xeee2bd, 0xd87037, 0xc1552d, 0xea9851];
  const materials = colors.map(color => new THREE.MeshStandardMaterial({ color, roughness: 0.88 }));
  const town = new THREE.Group();
  scene.add(town);
  let blocks: Block[] = [];
  let debris: Debris[] = [];
  let settling = false;
  let dirty = true;
  let frame = 0;
  let previous = performance.now();

  function box(material: number, x: number, y: number, z: number, w: number, h: number, d: number, cast = true) {
    const mesh = new THREE.Mesh(cube, materials[material]);
    mesh.position.set(x, y, z);
    mesh.scale.set(w, h, d);
    mesh.castShadow = cast;
    mesh.receiveShadow = true;
    town.add(mesh);
    return mesh;
  }

  function reset(site: string) {
    const seed = Array.from(site).reduce((sum, char) => (Math.imul(sum, 31) + char.codePointAt(0)!) >>> 0, 7);
    town.clear();
    blocks = [];
    debris = [];
    settling = false;
    dirty = true;
    box(0, 0, -0.37, 0, 11.4, 0.6, 9.9);
    box(1, 0, -0.08, 0, 11.3, 0.16, 9.8, false);
    box(2, 0, 0.012, 2.7, 11.3, 0.025, 0.83, false);
    box(2, -2.8, 0.015, 0, 0.83, 0.03, 9.8, false);
    for (let i = -5; i <= 5; i++) {
      box(5, i, 0.028, 2.7, 0.32, 0.015, 0.028, false);
    }
    const lots = [[-4.3, -3.5], [-4.3, -1.25], [3.6, -3], [4, 0.35], [-4.25, 3.85], [3.5, 3.8]];
    lots.forEach(([x, z], index) => {
      const height = 0.65 + ((seed >>> (index * 3)) & 7) * 0.18;
      const color = 3 + (seed + index) % 3;
      box(5, x, 0.045, z, 1.78, 0.09, 1.52);
      box(color, x, 0.09 + height / 2, z, 1.39, height, 1.14);
      box(5, x, 0.13 + height, z, 1.47, 0.08, 1.22);
      for (let level = 0.42; level < height; level += 0.38) {
        box(2, x, level, z + 0.579, 0.96, 0.1, 0.02, false);
        box(2, x + 0.705, level, z, 0.02, 0.1, 0.72, false);
      }
    });
    box(5, 0, 0.05, 0, 3.24, 0.1, 3.24);
    for (let y = 0; y < 3 + seed % 2; y++) {
      for (let x = -1; x <= 1; x++) {
        for (let z = -1; z <= 1; z++) {
          const mesh = box(6 + (x + z + y + 3) % 3, x * 0.93, 0.48 + y * 0.79, z * 0.93, 0.875, 0.75, 0.875);
          blocks.push({ mesh, x, y, z });
        }
      }
    }
    return blocks.length;
  }

  const ray = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  function hit(tool: Tool, point?: { x: number; y: number }) {
    let target = blocks.at(-1);
    if (point) {
      const bounds = renderer.domElement.getBoundingClientRect();
      pointer.set((point.x - bounds.left) / bounds.width * 2 - 1, 1 - (point.y - bounds.top) / bounds.height * 2);
      ray.setFromCamera(pointer, camera);
      const [intersection] = ray.intersectObjects(blocks.map(block => block.mesh), false);
      target = blocks.find(block => block.mesh === intersection?.object);
    }
    if (!target) return blocks.length;

    const { x, y, z } = target;
    const selected = (block: Block) => {
      if (tool === 'magnet') return block.x === x && block.z === z;
      if (tool === 'stamp') return block.y === y && Math.abs(block.x - x) + Math.abs(block.z - z) <= 1;
      return block === target;
    };
    blocks = blocks.filter(block => {
      if (!selected(block)) return true;
      debris.push({
        mesh: block.mesh,
        velocity: new THREE.Vector3(1.8 * (block.x + Math.random() - 0.5), 2.8 + Math.random(), 1.8 * (block.z + Math.random() - 0.5)),
        life: 0.9,
      });
      return false;
    });
    const columnHeights = new Map<number, number>();
    for (const block of blocks) {
      const column = (block.x + 1) * 3 + block.z + 1;
      block.y = columnHeights.get(column) ?? 0;
      columnHeights.set(column, block.y + 1);
    }
    settling = true;
    return blocks.length;
  }

  function resize() {
    const { clientWidth: width, clientHeight: height } = host;
    if (!width || !height) return;
    const aspect = width / height;
    const halfHeight = Math.max(5.5, 7.3 / aspect);
    camera.left = -halfHeight * aspect;
    camera.right = halfHeight * aspect;
    camera.top = halfHeight;
    camera.bottom = -halfHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(width, height);
    dirty = true;
  }

  function animate(now: number) {
    const dt = Math.min(0.04, (now - previous) / 1000);
    previous = now;
    if (dirty || debris.length || settling) {
      settling = false;
      for (const block of blocks) {
        const restingHeight = 0.48 + block.y * 0.79;
        block.mesh.position.y = Math.max(restingHeight, block.mesh.position.y - 4 * dt);
        settling ||= block.mesh.position.y > restingHeight;
      }
      debris = debris.filter(piece => {
        piece.life -= dt;
        if (piece.life <= 0) {
          town.remove(piece.mesh);
          return false;
        }
        piece.velocity.y -= 12 * dt;
        piece.mesh.position.addScaledVector(piece.velocity, dt);
        piece.mesh.position.y = Math.max(0.38, piece.mesh.position.y);
        piece.mesh.rotation.x += dt * 3.1;
        piece.mesh.rotation.z += dt * 2.4;
        piece.mesh.scale.setScalar(Math.min(1, piece.life * 5) * 0.85);
        return true;
      });
      renderer.render(scene, camera);
      dirty = false;
    }
    frame = requestAnimationFrame(animate);
  }

  const observer = new ResizeObserver(resize);
  observer.observe(host);
  resize();
  frame = requestAnimationFrame(animate);
  return {
    reset,
    hit,
    dispose() {
      cancelAnimationFrame(frame);
      observer.disconnect();
      cube.dispose();
      materials.forEach(material => material.dispose());
      sun.dispose();
      renderer.dispose();
      renderer.domElement.remove();
    },
  };
}

export default function BlockOffice() {
  const host = useRef<HTMLDivElement>(null);
  const board = useRef<Board | null>(null);
  const round = useRef({ deadline: 0, nextHit: 0, remaining: 0 });
  const [draft, setDraft] = useState('');
  const [address, setAddress] = useState('');
  const [tool, setTool] = useState<Tool>('ball');
  const [phase, setPhase] = useState<Phase>('ready');
  const [seconds, setSeconds] = useState(ROUND_SECONDS);
  const [count, setCount] = useState({ remaining: 0, total: 0 });
  const [unavailable, setUnavailable] = useState(false);
  const [cooldown, setCooldown] = useState(false);

  useEffect(() => {
    if (!host.current) return;
    let scene: ReturnType<typeof createBoard>;
    try {
      scene = createBoard(host.current);
    } catch {
      setUnavailable(true);
      return;
    }
    board.current = scene;
    const total = scene.reset(DEFAULT_SITE);
    setCount({ remaining: total, total });
    return () => {
      board.current = null;
      scene.dispose();
    };
  }, []);

  useEffect(() => {
    if (phase !== 'playing') return;
    const timer = window.setInterval(() => {
      const now = performance.now();
      const remaining = Math.max(0, Math.ceil((round.current.deadline - now) / 1000));
      setSeconds(remaining);
      setCooldown(now < round.current.nextHit);
      if (remaining === 0) setPhase('over');
    }, 75);
    return () => window.clearInterval(timer);
  }, [phase]);

  function reset(site: string, play: boolean) {
    if (!board.current) return;
    const total = board.current.reset(site || DEFAULT_SITE);
    round.current = { deadline: play ? performance.now() + ROUND_SECONDS * 1000 : 0, nextHit: 0, remaining: total };
    setCount({ remaining: total, total });
    setSeconds(ROUND_SECONDS);
    setCooldown(false);
    setPhase(play ? 'playing' : 'ready');
  }

  function selectAddress(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = draft.trim();
    setAddress(value);
    reset(value, false);
  }

  function hit(point?: { x: number; y: number }) {
    if (!board.current || phase !== 'playing' || round.current.remaining === 0) return;
    const now = performance.now();
    if (now >= round.current.deadline || now < round.current.nextHit) return;
    const remaining = board.current.hit(tool, point);
    if (remaining === round.current.remaining) return;
    round.current.remaining = remaining;
    round.current.nextHit = now + tools[tool].delay;
    setCount(previous => ({ ...previous, remaining }));
    setCooldown(true);
    if (remaining === 0) setPhase('won');
  }

  const score = (count.total - count.remaining) * 100;
  const feedback = {
    ready: '시작한 뒤 주황색 블록을 눌러.',
    playing: cooldown ? '도구 준비 중' : '블록을 골라.',
    won: '철거 완료!',
    over: '시간 종료!',
  }[phase];

  return <main className={styles.page}>
    <header className={arcade.bar}><Link href="/games">게임 목록</Link><Link href="/minimal">채팅</Link></header>
    <div className={styles.heading}>
      <h1>미니어처 철거</h1>
      <div className={styles.score}>
        <span>남은 시간 <strong>{seconds}초</strong></span>
        <span>점수 <strong>{score.toLocaleString('ko-KR')}</strong></span>
      </div>
    </div>
    <form className={styles.address} onSubmit={selectAddress}>
      <label htmlFor="block-office-address">회사 주소</label>
      <input id="block-office-address" value={draft} maxLength={160} placeholder="도로명 주소 입력" onChange={event => setDraft(event.target.value)} />
      <button type="submit">주소 적용</button>
    </form>
    <div className={styles.site}>
      <span>{address || DEFAULT_SITE} <small>가상 모형</small></span>
      {address && <a href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`} target="_blank" rel="noreferrer">Google 지도에서 실제 위치 보기 ↗</a>}
    </div>
    <div className={styles.stage}>
      <div
        ref={host}
        className={styles.board}
        aria-label="3D 게임판. 블록을 클릭하거나 Enter를 눌러 철거"
        role="group"
        tabIndex={0}
        onPointerDown={event => { if (event.button === 0) hit({ x: event.clientX, y: event.clientY }); }}
        onKeyDown={event => {
          if ((event.key === 'Enter' || event.key === ' ') && !event.repeat) {
            event.preventDefault();
            hit();
          }
        }}
      />
      {unavailable && <p className={styles.unavailable} role="status">이 브라우저에서 3D 화면을 열 수 없어.</p>}
      <span className={styles.blocks}>남은 블록 {count.remaining} / {count.total}</span>
    </div>
    <div className={styles.tools} aria-label="철거 도구" role="group">
      {(Object.keys(tools) as Tool[]).map(key => <button key={key} type="button" aria-pressed={tool === key} onClick={() => setTool(key)}>
        <strong>{tools[key].name}</strong><small>{tools[key].detail}</small>
      </button>)}
    </div>
    <div className={styles.actions}>
      <p role="status">{feedback}</p>
      <button className={arcade.primary} type="button" onClick={() => reset(address, true)} disabled={unavailable}>
        {phase === 'ready' ? '35초 시작' : '다시 시작'}
      </button>
    </div>
  </main>;
}

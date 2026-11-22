'use client';

import { useEffect, useRef } from 'react';

const PUFF_COUNT = 26;

export default function GlacierAtmosphere() {
  const back = useRef<HTMLCanvasElement>(null);
  const front = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const rear = back.current;
    const near = front.current;
    const rearContext = rear?.getContext('2d');
    const nearContext = near?.getContext('2d');
    if (!rear || !near || !rearContext || !nearContext) return;

    const sprites = [3, 11, 27].map(makeMistTexture);

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
    let width = 0;
    let height = 0;
    let frame = 0;
    let previous = performance.now();
    let elapsed = 0;
    const puffs = Array.from({ length: PUFF_COUNT }, (_, index) => ({
      texture: sprites[index % sprites.length],
      phase: index / PUFF_COUNT,
      span: .09 + (index % 9) * .047,
      period: 18 + (index % 7) * 1.8,
      near: index % 4 === 0,
      direction: index % 2 === 0 ? -1 : 1,
    }));

    function paint() {
      rearContext!.clearRect(0, 0, width, height);
      nearContext!.clearRect(0, 0, width, height);
      const scale = Math.min(width, 1000);
      for (const puff of puffs) {
        const progress = (puff.phase + elapsed / puff.period) % 1;
        const floor = Math.max(0, (progress - .64) / .36);
        const x = width * (.5 + puff.direction * (puff.span + floor * .35))
          + Math.sin(progress * 11 + puff.phase * 21) * scale * (.053 + floor * .1);
        const y = height * (progress < .64 ? -.14 + progress * 1.46 : .79 + floor * .12)
          + Math.sin(floor * 10 + puff.phase * 14) * height * .035;
        const w = scale * (.48 + floor * .73);
        const h = height * (.56 - floor * .22);
        const surface = puff.near ? nearContext! : rearContext!;
        surface.globalAlpha = Math.sin(Math.PI * progress) * (puff.near ? .24 : .62);
        surface.drawImage(puff.texture, x - w / 2, y - h / 2, w, h);
      }
    }

    function resize() {
      width = Math.max(1, rear!.clientWidth);
      height = Math.max(1, rear!.clientHeight);
      const ratio = Math.min(window.devicePixelRatio, 1.5);
      for (const [canvas, context] of [[rear!, rearContext!], [near!, nearContext!]] as const) {
        canvas.width = Math.round(width * ratio);
        canvas.height = Math.round(height * ratio);
        context.setTransform(ratio, 0, 0, ratio, 0, 0);
      }
      paint();
    }

    function tick(now: number) {
      elapsed += Math.min(.05, (now - previous) / 1000);
      previous = now;
      paint();
      frame = requestAnimationFrame(tick);
    }

    function syncMotion() {
      cancelAnimationFrame(frame);
      if (!reduced.matches) {
        previous = performance.now();
        frame = requestAnimationFrame(tick);
      } else paint();
    }

    const observer = new ResizeObserver(resize);
    observer.observe(rear);
    resize();
    syncMotion();
    reduced.addEventListener('change', syncMotion);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      reduced.removeEventListener('change', syncMotion);
    };
  }, []);

  return <>
    <canvas className="glacier-fog glacier-fog-back" ref={back} aria-hidden="true" />
    <canvas className="glacier-fog glacier-fog-front" ref={front} aria-hidden="true" />
  </>;
}

// Smooth noise at several scales gives each wisp both broad billows and wispy edges.
function makeMistTexture(seed: number) {
  const size = 192;
  const texture = document.createElement('canvas');
  texture.width = texture.height = size;
  const context = texture.getContext('2d');
  if (!context) return texture;
  const image = context.createImageData(size, size);

  function sample(x: number, y: number) {
    const hash = Math.imul(x + seed, 1597334677) ^ Math.imul(y, 3812015801);
    return ((Math.imul(hash ^ hash >>> 16, 2246822519) ^ hash >>> 13) >>> 0) / 4294967295;
  }

  function noise(x: number, y: number) {
    const ix = Math.floor(x), iy = Math.floor(y);
    const fx = x - ix, fy = y - iy;
    const u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
    const top = sample(ix, iy) * (1 - u) + sample(ix + 1, iy) * u;
    const bottom = sample(ix, iy + 1) * (1 - u) + sample(ix + 1, iy + 1) * u;
    return top * (1 - v) + bottom * v;
  }

  for (let row = 0; row < size; row++) {
    for (let column = 0; column < size; column++) {
      const x = column / size, y = row / size;
      const dx = x + (noise(x * 3 + 8, y * 4) - .5) * .25;
      const dy = y + (noise(x * 4, y * 3 + 9) - .5) * .2;
      const density = .58 * noise(dx * 5, dy * 6) + .27 * noise(dx * 12, dy * 13)
        + .1 * noise(dx * 26, dy * 28) + .05 * noise(dx * 57, dy * 62);
      const edge = Math.max(0, 1 - Math.hypot((x - .5) * 2, (y - .5) * 2));
      const index = (row * size + column) * 4;
      image.data[index] = 225 + density * 29;
      image.data[index + 1] = 244 + density * 11;
      image.data[index + 2] = 255;
      image.data[index + 3] = Math.min(255, (Math.max(0, density - .26) ** 1.3) * edge ** .82 * 1050);
    }
  }
  context.putImageData(image, 0, 0);
  return texture;
}

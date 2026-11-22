'use client';
import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import * as T from 'three';
import { GLTFLoader, type GLTF } from 'three/addons/loaders/GLTFLoader.js';
import { KTX2Loader } from 'three/addons/loaders/KTX2Loader.js';
import { MeshoptDecoder } from 'three/addons/libs/meshopt_decoder.module.js';
import { clone } from 'three/addons/utils/SkeletonUtils.js';
import styles from './PaperPlanePrototype.module.css';

type Phase = 'mail' | 'folding' | 'ready' | 'flying' | 'result' | 'finished' | 'caught';
type View = { phase: Phase; score: number; shots: number; feedback: string; watched: boolean; working: boolean };
type Controls = { start: () => void; fire: () => void; work: () => void; pause: () => void };
const initial: View = {phase: 'mail', score: 0, shots: 0, feedback: '', watched: false, working: false};
const emails = ['퇴근 전에 이것만 부탁드려요.', '아까 자료, 처음부터 다시 해주세요.', '회의를 위한 사전 회의를 잡았습니다.', '참고로 급한 건입니다. 방금 보냈어요.', '수고하셨고, 한 가지만 더요.'];
const clamp = T.MathUtils.clamp;
const assetRoot = '/games/mail-plane-assets/';
const seatX = -5.6;

function walkingLoop(x: number, z: number, width: number, depth: number) {
  return new T.CatmullRomCurve3([
    [-.7, 1], [.7, 1], [1, .7], [1, -.7],
    [.7, -1], [-.7, -1], [-1, -.7], [-1, .7],
  ].map(([u, v]) => new T.Vector3(x + u * width, 0, z + v * depth)), true, 'centripetal');
}

export default function PaperPlanePrototype() {
  const host = useRef<HTMLDivElement>(null);
  const targetButton = useRef<HTMLButtonElement>(null);
  const controls = useRef<Controls | null>(null);
  const [view,setView] = useState<View>(initial);
  const [paused,setPaused] = useState(false);
  const [loadState, setLoadState] = useState<"loading" | "ready" | "webgl-error" | "asset-error">("loading");
  const unavailable = loadState !== "ready";
  useEffect(()=>{
    const mount = host.current;
    if (!mount) return;
    let renderer: T.WebGLRenderer;
    try { renderer = new T.WebGLRenderer({antialias:true,alpha:false}); }
    catch { setLoadState("webgl-error"); return; }
    renderer.setPixelRatio(Math.min(devicePixelRatio,1.5));
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = T.PCFShadowMap;
    renderer.setClearColor('#e5e9e8');
    renderer.domElement.setAttribute('aria-hidden', 'true');
    mount.appendChild(renderer.domElement);
    const scene = new T.Scene();
    scene.fog = new T.Fog('#e5e9e8',20,49);
    const camera = new T.PerspectiveCamera(48,1,.1,65);
    camera.position.set(seatX, 2.16, 6.7);
    const sunlight = new T.DirectionalLight('#fff4d9',2.4);
    sunlight.position.set(0,13,-3); sunlight.target.position.set(-3,0,-10); scene.add(sunlight.target); sunlight.castShadow=true;
    sunlight.shadow.mapSize.set(1024,1024);
    Object.assign(sunlight.shadow.camera,{left:-12,right:12,top:12,bottom:-15,near:.5,far:40});
    sunlight.shadow.bias=-.0005; scene.add(sunlight);
    scene.add(new T.HemisphereLight('#e4f3ff','#b7b09d',1.6));
    const faceLight = new T.DirectionalLight('#fff0de', 1.15);
    faceLight.position.set(seatX - 3, 3.6, 8);
    scene.add(faceLight);
    const material = (color: T.ColorRepresentation) => new T.MeshStandardMaterial({color,roughness:.85});
    const mailOrigin = new T.Vector3(seatX + .18, 1.49, 3.63);
    const cream=material('#e7e7de'), wood=material('#b9946f'), charcoal=material('#334247');
    const solidObjects: T.Mesh[] = [];
    function box(w:number,h:number,d:number,m:T.Material,x:number,y:number,z:number) {
      const mesh = new T.Mesh(new T.BoxGeometry(w,h,d),m); mesh.position.set(x,y,z);
      mesh.castShadow=true;mesh.receiveShadow=true;scene.add(mesh);solidObjects.push(mesh);return mesh;
    }
    const carpet = material('#a9b7b0');
    const partitions = material('#8ea29b');
    const steel = material('#5e6e70');
    const inactiveScreen = material('#6e888a');
    const chairFabric = material('#566563');
    const glass = new T.MeshStandardMaterial({color:'#cae7ee', transparent:true, opacity:.28, roughness:.27, depthWrite:false});
    const ceilingLight = new T.MeshBasicMaterial({color:'#fffbea'});
    box(26, .2, 52, carpet, 0, -.12, -15);
    box(.25, 5.6, 50, cream, -13, 2.7, -15);
    box(26, 5.6, .25, cream, 0, 2.7, -39);
    box(.2, 5.6, 50, cream, 13, 2.7, -15);
    box(26, .15, 52, cream, 0, 5.55, -15).castShadow = false;
    box(.12, 3.25, 38, glass, 12.81, 3.34, -14);
    for (let z = -33; z < 8; z += 5.5) {
      box(.13, 3.3, .12, steel, 12.7, 3.34, z);
      box(2.8, .065, .52, ceilingLight, -4.8, 5.44, z);
      box(2.8, .065, .52, ceilingLight, 4.8, 5.44, z);
    }
    for (const side of [-1, 1]) {
      for (const x of [5.6, 9.3]) {
        for (let z = -28; z <= 0; z += 6) {
          const deskX = side * x;
          box(2.55, .12, 1.5, wood, deskX, 1.02, z);
          for (const offset of [-1.06, 1.06]) {
            box(.09, .99, 1.18, steel, deskX + offset, .5, z);
          }
          box(2.49, .52, .07, partitions, deskX, 1.34, z - .75);
          box(.92, .58, .075, charcoal, deskX, 1.52, z - .32);
          box(.81, .48, .025, inactiveScreen, deskX, 1.52, z - .277);
          box(.82, .035, .29, charcoal, deskX, 1.1, z + .27);

          box(.61, .12, .58, chairFabric, deskX, .65, z + 1.35);
          box(.57, .56, .09, chairFabric, deskX, .94, z + 1.59);
          box(.075, .56, .075, steel, deskX, .35, z + 1.35);
          box(.55, .055, .12, steel, deskX, .08, z + 1.35);
          if (x === 9.3 && z % 12 === -4) box(.4, 5.5, .4, cream, side * 7.45, 2.72, z - 2.2);
        }
      }
    }
    // A glass meeting room closes the far end of the main aisle.
    box(8.5, 3.3, .09, glass, -3.7, 1.68, -31.8);
    for (const x of [-8, -5.4, -2.8, .5]) box(.095, 3.4, .14, steel, x, 1.7, -31.75);
    box(8.6, .09, .14, steel, -3.7, 3.38, -31.75);
    box(5.3, .12, 2.1, wood, -3.6, 1.05, -35.1);
    box(.26, 1, .94, steel, -3.6, .5, -35.1);
    for (const x of [-5, -3.5, -2]) {
      box(.64, .14, .62, chairFabric, x, .65, -33.6);
      box(.62, .57, .12, chairFabric, x, .92, -33.3);
    }
    box(2.5, 2.35, .58, partitions, 5.3, 1.17, -32.4);
    for (const y of [.6, 1.25, 1.9]) box(2.45, .055, .63, wood, 5.3, y, -32.33);
    // The player sits at the near end of the same desk row as their colleagues.
    box(2.55, .12, 1.5, wood, seatX, 1.02, 3.85);
    for (const offset of [-1.06, 1.06]) box(.09, .99, 1.18, steel, seatX + offset, .5, 3.85);
    const frameX = seatX - .26;
    box(1.18, .69, .042, charcoal, frameX, 1.60, 3.54);
    box(.085, .26, .065, charcoal, frameX, 1.20, 3.54);
    box(.44, .028, .25, charcoal, frameX, 1.095, 3.61);
    box(.73, .028, .26, charcoal, frameX + .08, 1.097, 4.2);
    box(.13, .045, .23, charcoal, frameX + .58, 1.1, 4.18);
    const mug = new T.Mesh(new T.CylinderGeometry(.08, .065, .14, 20, 1, true), material('#e1e6d7'));
    mug.position.set(seatX + .9, 1.15, 3.67);
    mug.material.side = T.DoubleSide;
    scene.add(mug);

    const screenCanvas = document.createElement('canvas');
    screenCanvas.width = 960;
    screenCanvas.height = 540;
    const ctx = screenCanvas.getContext('2d')!;
    const screenTexture = new T.CanvasTexture(screenCanvas);
    screenTexture.colorSpace = T.SRGBColorSpace;
    const screen = new T.Mesh(new T.PlaneGeometry(1.12, .63), new T.MeshBasicMaterial({map: screenTexture}));
    screen.position.set(frameX, 1.61, 3.564);
    scene.add(screen);

    function paintMail(index: number) {
      ctx.fillStyle = '#f8fbff';
      ctx.fillRect(0, 0, 960, 540);
      ctx.fillStyle = '#15498b';
      ctx.fillRect(0, 0, 960, 72);
      ctx.fillStyle = '#f3f7ff';
      ctx.font = '600 34px sans-serif';
      ctx.fillText('사내 메일', 30, 48);
      ctx.fillStyle = '#dfeaf8';
      ctx.fillRect(0, 72, 200, 468);
      ctx.fillStyle = '#174b88';
      ctx.font = '600 32px sans-serif';
      ctx.fillText('받은 편지함', 228, 130);
      ctx.font = '500 22px sans-serif';
      ['받은 편지함', '보낸 편지함', '임시 보관함'].forEach((label, row) => {
        ctx.fillText(label, 22, 131 + row * 57);
      });
      ctx.fillStyle = '#dcecff';
      ctx.fillRect(216, 155, 725, 104);
      ctx.fillStyle = '#246bbe';
      ctx.fillRect(216, 155, 6, 104);
      ctx.font = '600 27px sans-serif';
      ctx.fillText(emails[index % emails.length], 246, 202, 660);
      ctx.fillStyle = '#6b7b8d';
      ctx.font = '500 20px sans-serif';
      ctx.fillText('새 메일', 246, 234);
      for (let row = 0; row < 3; row++) {
        ctx.fillStyle = '#e1e9f3';
        ctx.fillRect(229, 300 + row * 67, 700, 2);
        ctx.fillStyle = '#c8d6e6';
        ctx.fillRect(246, 320 + row * 67, 228 + row * 39, 9);
      }
      screenTexture.needsUpdate = true;
    }
    paintMail(0);

    function paintWork() {
      ctx.fillStyle = '#f8faf7';
      ctx.fillRect(0, 0, 960, 540);
      ctx.fillStyle = '#285d47';
      ctx.fillRect(0, 0, 960, 72);
      ctx.fillStyle = '#fff';
      ctx.font = '600 34px sans-serif';
      ctx.fillText('주간 업무 현황', 30, 48);
      ctx.font = '600 26px sans-serif';
      const rows = [['업무', '담당', '상태'], ['자료 검토', '기획팀', '완료'], ['회의록 정리', '운영팀', '진행 중'], ['주간 보고', '운영팀', '진행 중']];
      rows.forEach((cells, row) => {
        ctx.fillStyle = row === 0 ? '#c9e2d3' : row % 2 ? '#e9f1eb' : '#f2f6f1';
        ctx.fillRect(20, 91 + row * 75, 920, 72);
        ctx.fillStyle = '#263f34';
        cells.forEach((cell, column) => ctx.fillText(cell, 45 + column * 315, 136 + row * 75));
      });
      screenTexture.needsUpdate = true;
    }

    const plane = new T.Group();
    const vertices = new Float32Array([
      0, .08, -.67, -.46, .12, .38, -.035, .09, .29,
      0, .08, -.67, .035, .09, .29, .46, .12, .38,
      0, .08, -.67, -.035, .09, .29, 0, -.105, .47,
      0, .08, -.67, 0, -.105, .47, .035, .09, .29,
    ]);
    const geometry = new T.BufferGeometry();
    geometry.setAttribute('position', new T.BufferAttribute(vertices, 3));
    geometry.addGroup(0, 6, 0);
    geometry.addGroup(6, 6, 1);
    geometry.computeVertexNormals();
    const paper = new T.Mesh(geometry, [
      new T.MeshStandardMaterial({color: '#fffefa', side: T.DoubleSide, roughness: .8}),
      new T.MeshStandardMaterial({color: '#d9e2e6', side: T.DoubleSide, roughness: .8}),
    ]);
    paper.castShadow = true;
    plane.add(paper);
    scene.add(plane);
    plane.visible = false;

    const routes = [
      walkingLoop(-2.55, -1.5, 1.07, 5.28),
      walkingLoop(.55, -3.1, 1.45, 6.1),
      walkingLoop(-.65, -15.8, 2.95, 6.9),
      walkingLoop(7.45, -13.8, .26, 17.65),
      walkingLoop(-7.48, -14, .22, 15.6),
    ];
    const routeLengths = routes.map(route => route.getLength());
    const seatProgress = .9;
    const seatPath = routes[0].getPointAt(seatProgress);
    const officeSeat = seatPath.clone().add(new T.Vector3(-.5, 0, 0));
    box(.53, .09, .55, chairFabric, officeSeat.x, .52, officeSeat.z);
    box(.09, .53, .51, chairFabric, officeSeat.x - .26, .81, officeSeat.z);
    box(.085, .44, .085, steel, officeSeat.x, .26, officeSeat.z);
    box(.53, .045, .39, steel, officeSeat.x, .07, officeSeat.z);
    const targets = Array.from({length: 20}, (_, index) => {
      const routeIndex = index % routes.length;
      const route = routes[routeIndex];
      const progress = index === 0 ? .78 : (Math.floor(index / routes.length) * .25 + routeIndex * .08) % 1;
      const group = new T.Group();
      const heightScale = .96 + (index % 5) * .025;
      group.scale.setScalar(heightScale);
      route.getPointAt(progress, group.position);
      const tangent = route.getTangentAt(progress);
      group.rotation.y = Math.atan2(tangent.x, tangent.z);
      scene.add(group);
      return {
        group, route, routeLength: routeLengths[routeIndex], progress, heightScale,
        speed: [1.28, 1.15, 1.37, 1.06][Math.floor(index / routes.length)],
        mixer: null as T.AnimationMixer | null,
        walk: null as T.AnimationAction | null, idle: null as T.AnimationAction | null,
        head: undefined as T.Object3D | undefined,
        legs: [] as {thigh: T.Object3D; calf: T.Object3D; foot: T.Object3D; toe: T.Object3D}[],
        seatedAt: -Infinity,
      };
    });

    const ktx = new KTX2Loader().setTranscoderPath(assetRoot).detectSupport(renderer);
    const loader = new GLTFLoader().setMeshoptDecoder(MeshoptDecoder).setKTX2Loader(ktx);
    const loadedModels: T.Object3D[] = [];
    let active = true;

    function prepareAssets() {
      const files = ['hoodie.glb', 'jacket.glb', 'hoodie-aisle.glb', 'jacket-aisle.glb'];
      void Promise.allSettled(files.map(async file => {
        const model = await loader.loadAsync(assetRoot + file);
        loadedModels.push(model.scene);
        return model;
      })).then(results => {
        ktx.dispose();
        if (!active) {
          disposeResources(loadedModels);
          return;
        }
        if (results.some(result => result.status === 'rejected')) {
          setLoadState("asset-error");
          return;
        }
        const models = results.map(result => (result as PromiseFulfilledResult<GLTF>).value);
        targets.forEach((target, index) => {
          const near = index % routes.length < 2;
          const model = models[(near ? 0 : 2) + index % 2];
          const avatar = clone(model.scene);
          const tint = ['#ffffff', '#ede9e7', '#e1e8f4', '#eeeece', '#dfefeb'][Math.floor(index / 4)];
          avatar.traverse(object => {
            if (object instanceof T.SkinnedMesh && object.material instanceof T.MeshStandardMaterial) {
              const finish = object.material.clone();
              finish.color.set(tint);
              object.material = finish;
              finish.normalScale.multiplyScalar(.8);
              object.castShadow = near;
              object.frustumCulled = false;
            }
          });
          target.group.add(avatar);
          target.head = avatar.getObjectByName('head');
          if (index === 0) {
            for (const side of ['L', 'R']) {
              const thigh = avatar.getObjectByName('thigh_' + side);
              const calf = avatar.getObjectByName('calf_' + side);
              const foot = avatar.getObjectByName('foot_' + side);
              const toe = avatar.getObjectByName('toe_' + side);
              if (thigh && calf && foot && toe) target.legs.push({thigh, calf, foot, toe});
            }
          }
          const mixer = new T.AnimationMixer(avatar);
          const walk = T.AnimationClip.findByName(model.animations, 'walk');
          const idle = T.AnimationClip.findByName(model.animations, 'idle');
          target.mixer = mixer;
          if (walk) {
            target.walk = mixer.clipAction(walk).play();
            target.walk.time = walk.duration * ((index * .37) % 1);
            target.walk.setEffectiveTimeScale(target.speed / 1.25);
          }
          if (idle && index === 0) {
            target.idle = mixer.clipAction(idle).play();
            target.idle.setEffectiveWeight(0);
          }
        });
        setLoadState("ready");
      });
    }

    function disposeResources(roots: T.Object3D[]) {
      const geometries = new Set<T.BufferGeometry>();
      const materials = new Set<T.Material>();
      const textures = new Set<T.Texture>();
      for (const root of roots) root.traverse(object => {
        if (!(object instanceof T.Mesh || object instanceof T.Line)) return;
        geometries.add(object.geometry);
        for (const finish of Array.isArray(object.material) ? object.material : [object.material]) {
          materials.add(finish);
          for (const value of Object.values(finish)) if (value instanceof T.Texture) textures.add(value);
        }
        if (object instanceof T.SkinnedMesh) object.skeleton.dispose();
      });
      geometries.forEach(geometry => geometry.dispose());
      materials.forEach(finish => finish.dispose());
      textures.forEach(texture => texture.dispose());
    }

    const impactMaterial = new T.MeshBasicMaterial({color: '#e6a653', side: T.DoubleSide, transparent: true, opacity: 0});
    const impact = new T.Mesh(new T.RingGeometry(.18, .23, 32), impactMaterial);
    impact.visible = false;
    scene.add(impact);

    let state = {...initial}, paused = false, time = 0, last = 0;
    let foldStart = 0, readyAt = 0, flightStart = 0, flightDuration = 0, impactAt = -Infinity;
    let targetIndex = -1, previousTargetIndex = -1;
    const launch = new T.Vector3(seatX + .64, 1.38, 4.19);
    const tangent = new T.Vector3(), projected = new T.Vector3();
    const landing = new T.Vector3(), flightDirection = new T.Vector3(), previousFlightPosition = new T.Vector3();
    const seatedForward = new T.Vector3(1, -.08, 0), seatedDown = new T.Vector3(.12, -1, 0);
    const seatedToe = new T.Vector3(1, -.25, 0);
    const poseDirection = new T.Vector3(), poseSource = new T.Vector3();
    const poseTurn = new T.Quaternion(), parentRotation = new T.Quaternion();
    const worldUp = new T.Vector3(0, 1, 0);
    const raycaster = new T.Raycaster();
    const publish = () => setView({...state});

    function projectHead(index: number) {
      const {group, head, heightScale} = targets[index];
      if (head) {
        head.getWorldPosition(projected);
        projected.y += .19 * heightScale;
      } else {
        projected.copy(group.position);
        projected.y += 1.82 * heightScale;
      }
      return projected;
    }

    function inView(point: T.Vector3) {
      return Math.abs(point.x) <= .82 && Math.abs(point.y) <= .68 && point.z >= -1 && point.z <= 1;
    }

    function findTarget() {
      let nearest = Infinity;
      let chosen = -1;
      for (const [index, target] of targets.entries()) {
        const {group} = target;
        projectHead(index);
        tangent.copy(projected).sub(camera.position);
        const distance = tangent.length();
        projected.project(camera);
        if (group.position.z >= launch.z - .1 || !inView(projected) || Math.abs(projected.x) > .74) continue;
        raycaster.set(camera.position, tangent.normalize());
        const obstacle = raycaster.intersectObjects(solidObjects, false)[0];
        if (obstacle && obstacle.distance < distance - .2) continue;
        // Give the nearest detailed models priority and vary the target across deliveries.
        const priority = distance + (index % routes.length < 2 ? 0 : 15) + (index === previousTargetIndex ? 13 : 0);
        if (priority < nearest) {
          chosen = index;
          nearest = priority;
        }
      }
      return chosen;
    }

    function positionMarker() {
      const marker = targetButton.current;
      if (!marker) return;
      if (targetIndex === -1) {
        marker.style.visibility = 'hidden';
        return;
      }
      projectHead(targetIndex).project(camera);
      marker.style.visibility = inView(projected) ? '' : 'hidden';
      marker.style.left = `${(projected.x + 1) * 50}%`;
      marker.style.top = `${(1 - projected.y) * 50}%`;
    }

    function updateLanding() {
      const {group, heightScale} = targets[targetIndex];
      landing.copy(group.position);
      landing.y += 1.31 * heightScale;
    }

    function alignJoint(joint: T.Object3D, child: T.Object3D, worldDirection: T.Vector3, weight: number) {
      if (!joint.parent) return;
      joint.parent.getWorldQuaternion(parentRotation).invert();
      poseDirection.copy(worldDirection).applyQuaternion(parentRotation).normalize();
      poseSource.copy(child.position).applyQuaternion(joint.quaternion).normalize();
      poseTurn.setFromUnitVectors(poseSource, poseDirection);
      poseTurn.multiply(joint.quaternion);
      joint.quaternion.slerp(poseTurn, weight);
    }

    function updateCoworker(target: (typeof targets)[number], index: number, dt: number) {
      let seatAge = time - target.seatedAt;
      const sitting = index === 0 && seatAge < 5.8;
      if (!sitting) {
        const next = (target.progress + target.speed * dt / target.routeLength) % 1;
        if (index === 0 && target.progress < seatProgress && next >= seatProgress) {
          target.progress = seatProgress;
          target.seatedAt = time;
          seatAge = 0;
        } else target.progress = next;
      }
      target.route.getPointAt(target.progress, target.group.position);
      target.route.getTangentAt(target.progress, tangent);
      const sit = clamp(Math.min((seatAge - .55) / .46, (5.25 - seatAge) / .48), 0, 1);
      if (seatAge < 5.8) {
        const step = clamp(Math.min(seatAge / .55, (5.8 - seatAge) / .55), 0, 1);
        target.group.position.lerp(officeSeat, step);
        target.group.position.y -= .36 * sit;
        tangent.lerp(seatedForward, step);
      }
      const turn = Math.atan2(tangent.x, tangent.z) - target.group.rotation.y;
      target.group.rotation.y += Math.atan2(Math.sin(turn), Math.cos(turn)) * Math.min(1, dt * 7);
      target.walk?.setEffectiveWeight(1 - sit);
      target.idle?.setEffectiveWeight(sit);
      target.mixer?.update(dt);
      if (sit > 0) {
        for (const {thigh, calf, foot, toe} of target.legs) {
          alignJoint(thigh, calf, seatedForward, sit);
          alignJoint(calf, foot, seatedDown, sit);
          alignJoint(foot, toe, seatedToe, sit);
        }
      }
      if (index === targetIndex && state.watched && target.head?.parent) {
        target.head.parent.getWorldQuaternion(parentRotation).invert();
        poseDirection.copy(worldUp).applyQuaternion(parentRotation);
        tangent.copy(camera.position).sub(target.group.position);
        const gaze = Math.atan2(tangent.x, tangent.z) - target.group.rotation.y;
        poseTurn.setFromAxisAngle(poseDirection, clamp(Math.atan2(Math.sin(gaze), Math.cos(gaze)), -1.2, 1.2));
        target.head.quaternion.premultiply(poseTurn);
      }
    }

    function foldMail() {
      state.phase = 'folding';
      state.watched = false;
      state.working = false;
      foldStart = time;
      previousTargetIndex = targetIndex;
      targetIndex = -1;
      plane.visible = true;
      plane.position.copy(mailOrigin);
      plane.scale.setScalar(.1);
      paintMail(state.shots);
      publish();
    }

    function start() {
      state = {...initial};
      targetIndex = -1;
      impactAt = -Infinity;
      paused = false;
      setPaused(false);
      foldMail();
    }

    function fire() {
      if (paused || state.phase !== 'ready' || state.working || targetIndex < 0) return;
      if (!inView(projectHead(targetIndex).project(camera))) return;
      updateLanding();
      flightDirection.copy(landing).sub(launch);
      flightDuration = clamp(flightDirection.length() / 12, .45, 1.35);
      flightStart = time;
      state.phase = 'flying';
      state.watched = false;
      state.shots++;
      publish();
    }

    function work() {
      if (paused || state.phase !== 'ready' || !state.watched || state.working) return;
      state.working = true;
      plane.visible = false;
      paintWork();
      publish();
    }

    function completeDelivery() {
      const points = Math.max(65, Math.round(100 - (flightStart - readyAt) * 5));
      state.score += points;
      state.feedback = `배달 성공! +${points}`;
      state.phase = state.shots === 5 ? 'finished' : 'result';
      impactAt = time;
      impact.position.copy(landing);
      impact.lookAt(camera.position);
      plane.visible = false;
      publish();
    }

    function pause() {
      paused = !paused;
      setPaused(paused);
      last = 0;
    }
    controls.current = {start, fire, work, pause};
    const canvas = renderer.domElement;

    function visibility() {
      if (document.hidden && !paused && !['mail', 'finished', 'caught'].includes(state.phase)) pause();
    }
    document.addEventListener('visibilitychange', visibility);
    function resize() {
      const width = mount!.clientWidth, height = Math.max(mount!.clientHeight, 1);
      renderer.setSize(width, height);
      camera.aspect = width / height;
      const narrow = camera.aspect < .8;
      camera.fov = narrow ? 65 : 49;
      camera.lookAt(narrow ? -4.1 : -2.25, 1.54, -6.8);
      camera.updateProjectionMatrix();
      camera.updateMatrixWorld();
      if (state.phase === 'ready' && targetIndex !== -1) positionMarker();
    }
    const observer=new ResizeObserver(resize);observer.observe(mount);resize();
    prepareAssets();
    renderer.setAnimationLoop((now:number)=>{
      const dt=last?Math.min((now-last)/1000,.034):0;last=now;
      if(!paused) {
        time+=dt;
        if (state.phase === 'result' && time - impactAt >= .82) foldMail();
        targets.forEach((target, index) => updateCoworker(target, index, dt));
        if (state.phase === 'ready') {
          if (targetIndex === -1 || !inView(projectHead(targetIndex).project(camera))) {
            const replacement = findTarget();
            targetIndex = replacement;
          }
          positionMarker();
          const age = time - readyAt;
          if (state.working && age >= 8.55) {
            readyAt = time;
            state.watched = state.working = false;
            plane.visible = true;
            paintMail(state.shots);
            publish();
          } else if (!state.working && targetIndex !== -1) {
            if (age >= 7) {
              state.phase = 'caught';
              state.watched = true;
              state.feedback = '동료와 눈이 마주쳤어요.';
              plane.visible = false;
              publish();
            } else if (age >= 5.6 && !state.watched) {
              state.watched = true;
              publish();
            }
          }
        }
        if (state.phase === 'folding') {
          const fraction = clamp((time - foldStart) / .65, 0, 1);
          const eased = fraction * fraction * (3 - 2 * fraction);
          plane.position.lerpVectors(mailOrigin, launch, eased);
          plane.position.y += Math.sin(Math.PI * eased) * .16;
          plane.rotation.set(.16 * (1 - eased), -.53 * (1 - eased), .28 * (1 - eased));
          plane.scale.setScalar(.1 + .9 * eased);
          if (fraction === 1) {
            targetIndex = findTarget();
            if (targetIndex !== -1) {
              state.phase = 'ready';
              readyAt = time;
              positionMarker();
              publish();
            }
          }
        }
        if (state.phase === 'flying') {
          const fraction = clamp((time - flightStart) / flightDuration, 0, 1);
          updateLanding();
          previousFlightPosition.copy(plane.position);
          plane.position.lerpVectors(launch, landing, fraction);
          plane.position.y += Math.sin(fraction * Math.PI) * .56;
          flightDirection.copy(plane.position).sub(previousFlightPosition);
          if (flightDirection.lengthSq() > .000001) {
            plane.rotation.y = Math.atan2(-flightDirection.x, -flightDirection.z);
            plane.rotation.x = Math.atan2(flightDirection.y, Math.hypot(flightDirection.x, flightDirection.z));
          }
          if (fraction === 1) completeDelivery();
        }
        const impactAge = time - impactAt;
        impact.visible = impactAge < .5;
        impact.scale.setScalar(1 + Math.max(0, impactAge) * 4);
        impactMaterial.opacity = Math.max(0, 1 - impactAge * 2);
      }
      renderer.render(scene,camera);
    });
    return ()=>{
      active = false;
      controls.current=null;renderer.setAnimationLoop(null);observer.disconnect();document.removeEventListener('visibilitychange',visibility);
      targets.forEach(({mixer}) => mixer?.stopAllAction());
      disposeResources([scene, ...loadedModels]);
      renderer.dispose();
      canvas.remove();
    };
  },[]);
  const over = view.phase === 'finished' || view.phase === 'caught';
  const status = paused ? '잠깐 멈췄어요.'
    : view.phase === 'mail' ? '동료가 돌아보기 전에 화살표를 눌러요.'
    : view.phase === 'folding' ? '다음 메일이 도착했어요.'
    : view.phase === 'flying' ? '날아가는 중…'
    : view.phase === 'result' || over ? view.feedback
    : view.working ? '일하는 척하는 중. 동료가 지나갈 때까지 기다려요.'
    : view.watched ? '곧 이쪽을 봐요! 지금 날리거나 일하는 척해요.'
    : '화살표가 있는 동료를 눌러요.';

  return <main className={styles.game}>
    <header className={styles.header}>
      <Link href="/games">게임 목록</Link>
      <button onClick={() => controls.current?.pause()} disabled={unavailable || view.phase === 'mail' || over}>
        {paused ? '계속하기' : '잠깐 멈춤'}
      </button>
    </header>
    <div className={styles.title}>
      <h1>메일비행기</h1>
      <div className={styles.score}><strong>{view.score}</strong><span>점수 · {view.shots}/5번</span></div>
    </div>
    <section className={styles.stage} aria-label="메일비행기 게임" tabIndex={0}
      onKeyDown={event => {
        if (event.key.toLowerCase() === 'f' && !event.repeat) {
          controls.current?.work();
          return;
        }
        if (event.target !== event.currentTarget || (event.key !== 'Enter' && event.key !== ' ')) return;
        event.preventDefault();
        if (!event.repeat) controls.current?.fire();
      }}>
      <div ref={host} className={styles.canvas}/>
      <button ref={targetButton} className={styles.targetButton} type="button"
        aria-label="표시된 동료에게 메일 날리기" aria-disabled={paused} data-warning={view.watched}
        onClick={event => {
          if (paused) return;
          if (event.detail === 0) event.currentTarget.closest('section')?.focus();
          controls.current?.fire();
        }} hidden={view.phase !== 'ready' || view.working}>
        <span aria-hidden="true">▼</span>
      </button>
      <div className={styles.instructions} data-danger={view.watched && !paused} aria-live="polite">{status}</div>
      {over && <div className={styles.result}>
        <span>{view.phase === 'caught' ? '들켰어요 · 게임 오버' : '메일 5통 배달 완료'}</span>
        <strong>{view.score}점</strong>
      </div>}
      {loadState === 'loading' && <div className={styles.result} role="status"><p>사무실을 준비하는 중이에요.</p></div>}
      {loadState === 'webgl-error' && <div className={styles.result}><p>WebGL을 지원하는 브라우저에서 열어주세요.</p></div>}
      {loadState === 'asset-error' && <div className={styles.result} role="alert"><p>동료 모델을 불러오지 못했어요. 새로고침해 다시 시도해 주세요.</p></div>}
      <div className={styles.actions}>
        {view.phase === 'ready' && view.watched && !view.working && <button type="button" disabled={paused}
          onClick={event => {
            if (event.detail === 0) event.currentTarget.closest('section')?.focus();
            controls.current?.work();
          }}>
          일하는 척
        </button>}
        {(view.phase === 'mail' || over) && <button disabled={unavailable} onClick={() => controls.current?.start()}>
          {over ? '다시 하기' : '시작'}
        </button>}
      </div>
    </section>
    <footer className={styles.footer}>
      <span>화살표로 발사 · 키보드 Enter로 발사, F로 일하는 척</span>
      <button onClick={() => controls.current?.start()} disabled={unavailable}>처음부터</button>
    </footer>
  </main>;
}

'use client';
import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import * as T from 'three';
import styles from './PaperPlanePrototype.module.css';

type Phase = 'mail' | 'folding' | 'ready' | 'flying' | 'result' | 'finished';
type View = { phase: Phase; score: number; shots: number; combo: number; feedback: string };
type Controls = { take: () => void; throw: () => void; reset: () => void; pause: () => void };
const initial: View = {phase:'mail',score:0,shots:0,combo:0,feedback:''};
const emails = ['퇴근 전에 이것만 부탁드려요.', '아까 자료, 처음부터 다시 해주세요.', '회의를 위한 사전 회의를 잡았습니다.', '참고로 급한 건입니다. 방금 보냈어요.', '수고하셨고, 한 가지만 더요.'];
const clamp = T.MathUtils.clamp;

export default function PaperPlanePrototype() {
  const host = useRef<HTMLDivElement>(null);
  const controls = useRef<Controls | null>(null);
  const [view,setView] = useState<View>(initial);
  const [paused,setPaused] = useState(false);
  const [unavailable,setUnavailable] = useState(false);
  useEffect(()=>{
    const mount = host.current;
    if (!mount) return;
    let renderer: T.WebGLRenderer;
    try { renderer = new T.WebGLRenderer({antialias:true,alpha:false}); }
    catch { setUnavailable(true); return; }
    renderer.setPixelRatio(Math.min(devicePixelRatio,1.5));
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = T.PCFSoftShadowMap;
    renderer.setClearColor('#e5e9e8');
    renderer.domElement.tabIndex = 0;
    renderer.domElement.setAttribute('aria-label','종이비행기 사무실. 방향키로 조준하고 스페이스로 던지기');
    mount.appendChild(renderer.domElement);
    const scene = new T.Scene();
    scene.fog = new T.Fog('#e5e9e8',18,36);
    const camera = new T.PerspectiveCamera(48,1,.1,65);
    camera.position.set(0,2.9,7.7); camera.lookAt(0,1.55,-6);
    const sunlight = new T.DirectionalLight('#fff4d9',3);
    sunlight.position.set(7,12,5); sunlight.castShadow=true;
    sunlight.shadow.mapSize.set(1024,1024);
    Object.assign(sunlight.shadow.camera,{left:-12,right:12,top:12,bottom:-15,near:.5,far:40});
    sunlight.shadow.bias=-.0005; scene.add(sunlight);
    scene.add(new T.HemisphereLight('#e4f3ff','#b7b09d',2.2));
    const material = (color: T.ColorRepresentation) => new T.MeshStandardMaterial({color,roughness:.85});
    const cream=material('#e7e7de'), wood=material('#b9946f'), charcoal=material('#334247');
    function box(w:number,h:number,d:number,m:T.Material,x:number,y:number,z:number) {
      const mesh = new T.Mesh(new T.BoxGeometry(w,h,d),m); mesh.position.set(x,y,z);
      mesh.castShadow=true;mesh.receiveShadow=true;scene.add(mesh);return mesh;
    }
    box(17,.2,36,material('#c7cec9'),0,-.12,-8);
    box(.25,5.4,34,cream,-8.5,2.6,-8);
    box(17,5.4,.3,cream,0,2.6,-24);
    box(.2,5.4,34,material('#d7ddd8'),8.5,2.6,-8);
    box(.13,2.8,25,new T.MeshStandardMaterial({color:'#d4eaf0',emissive:'#8baebc',emissiveIntensity:.3,roughness:.35}),8.32,3,-8);
    for(let z=-19;z<5;z+=5) box(.16,3,.09,charcoal,8.15,3,z);
    for(let z=-18;z<0;z+=5) {
      for(const side of [-1,1]) {
        box(2.5,.12,1.45,wood,side*6,1.02,z);
        box(.1,1,.1,charcoal,side*6-1, .5,z);
        box(.1,1,.1,charcoal,side*6+1, .5,z);
        box(.8,.54,.06,charcoal,side*6,1.47,z-.22);
        box(.7,.15,.65,material('#697b75'),side*6,.7,z+1.15);
      }
    }
    // Foreground desk gives a seated point of view and a place to pull the mail from.
    box(6.1,.16,2.1,wood,0,1.12,4.25);
    box(.14,1.1,.14,charcoal,-2.7,.53,4.8);box(.14,1.1,.14,charcoal,2.7,.53,4.8);
    const monitor=box(1.8,1.12,.12,charcoal,-1.65,2,3.85);
    box(.12,.5,.12,charcoal,-1.65,1.4,3.85);box(.7,.06,.4,charcoal,-1.65,1.24,3.85);
    const screenCanvas=document.createElement('canvas');screenCanvas.width=640;screenCanvas.height=360;
    const ctx=screenCanvas.getContext('2d')!;
    const screenTexture=new T.CanvasTexture(screenCanvas);
    screenTexture.colorSpace=T.SRGBColorSpace;
    const screen=new T.Mesh(new T.PlaneGeometry(1.65,.95),new T.MeshBasicMaterial({map:screenTexture}));
    screen.position.set(-1.65,2,3.922);scene.add(screen);
    function paintMail(index:number) {
      ctx.fillStyle='#ecf0e6';ctx.fillRect(0,0,640,360);
      ctx.fillStyle='#365951';ctx.fillRect(0,0,640,64);
      ctx.fillStyle='#fff';ctx.font='bold 28px sans-serif';ctx.fillText('받은 메일',30,43);
      ctx.fillStyle='#29443d';ctx.font='bold 25px sans-serif';ctx.fillText(emails[index%emails.length],30,160,580);
      ctx.fillStyle='#768880';ctx.font='21px sans-serif';ctx.fillText('클릭해서 꺼내기',30,290);
      screenTexture.needsUpdate=true;
    }
    paintMail(0);
    box(1.35,.04,.45,charcoal,-.8,1.24,4.7);
    const mug=new T.Mesh(new T.CylinderGeometry(.15,.13,.25,24),material('#f2eee4'));mug.position.set(1.7,1.33,4.65);scene.add(mug);
    const plane=new T.Group();
    const vertices=new Float32Array([0,0,-.72,-.58,-.03,.48,0,.15,.3, 0,0,-.72,0,.15,.3,.58,-.03,.48, -.58,-.03,.48,0,-.06,.55,0,.15,.3, .58,-.03,.48,0,.15,.3,0,-.06,.55]);
    const geometry=new T.BufferGeometry();geometry.setAttribute('position',new T.BufferAttribute(vertices,3));geometry.computeVertexNormals();
    const paper=new T.Mesh(geometry,new T.MeshStandardMaterial({color:'#fffaf0',side:T.DoubleSide,roughness:.75}));paper.castShadow=true;plane.add(paper);scene.add(plane);plane.visible=false;
    const sheet=new T.Mesh(new T.PlaneGeometry(1,1.35),new T.MeshStandardMaterial({color:'#fffdf5',side:T.DoubleSide}));scene.add(sheet);sheet.visible=false;
    const palettes=['#5d7c87','#b38a68','#68836b','#8b7590'];
    const targets=palettes.map((color,i)=>{
      const group=new T.Group();const shirt=material(color);
      const torso=new T.Mesh(new T.CapsuleGeometry(.23,.55,4,10),shirt);torso.position.y=1.23;torso.castShadow=true;group.add(torso);
      const head=new T.Mesh(new T.SphereGeometry(.23,14,12),material('#d7b89e'));head.position.y=1.93;head.castShadow=true;group.add(head);
      const hair=new T.Mesh(new T.SphereGeometry(.235,12,8,0,Math.PI*2,0,Math.PI/2),charcoal);hair.position.y=2.01;group.add(hair);
      for(const side of [-1,1]) {
        const leg=new T.Mesh(new T.BoxGeometry(.16,.65,.19),charcoal);leg.position.set(side*.15,.42,0);leg.castShadow=true;group.add(leg);
        const arm=new T.Mesh(new T.CapsuleGeometry(.075,.48,3,8),shirt);arm.position.set(side*.32,1.23,0);arm.rotation.z=side*.15;group.add(arm);
      }
      const badge=new T.Mesh(new T.BoxGeometry(.1,.13,.025),material('#f4edcc'));badge.position.set(.12,1.43,.245);group.add(badge);
      group.position.z=-3.5-i*3.3;scene.add(group);
      return {group,shirt,z:group.position.z,phase:i*1.8,hitAt:-100};
    });
    const previewGeometry=new T.BufferGeometry();const preview=new T.Line(previewGeometry,new T.LineDashedMaterial({color:'#436f7c',dashSize:.14,gapSize:.12,transparent:true,opacity:.55}));scene.add(preview);preview.visible=false;
    const impact=new T.Mesh(new T.RingGeometry(.25,.31,32),new T.MeshBasicMaterial({color:'#e6a653',side:T.DoubleSide,transparent:true,opacity:0}));scene.add(impact);
    const raycaster=new T.Raycaster(); const mouse=new T.Vector2(0,-.1);
    const aimPoint=new T.Vector3(0,1.55,-8);
    const aimSurface=new T.Plane(new T.Vector3(0,0,1),8);
    const reticle=new T.Mesh(new T.RingGeometry(.18,.23,32),new T.MeshBasicMaterial({color:'#2f725b',side:T.DoubleSide,depthTest:false}));
    reticle.renderOrder=3;reticle.visible=false;scene.add(reticle);
    let state={...initial}, paused=false,time=0,last=0,foldStart=0,impactAt=-100,flightStart=0;
    let drag: {id:number}|null=null;
    const launch=new T.Vector3(0,1.65,3.2),velocity=new T.Vector3(),previous=new T.Vector3();
    const segment=new T.Line3(),closest=new T.Vector3(),targetPoint=new T.Vector3();
    const publish=()=>setView({...state});
    const isReduced=matchMedia('(prefers-reduced-motion: reduce)').matches;
    function speed() {
      // Fixed forward speed; compensate gravity so the arc passes the selected world point.
      const flightTime=Math.max(.25,(launch.z-aimPoint.z)/12);
      return new T.Vector3((aimPoint.x-launch.x)/flightTime,(aimPoint.y-launch.y+.45*flightTime*flightTime)/flightTime,-12);
    }
    function aimAtPointer() {
      raycaster.setFromCamera(mouse,camera);
      const hit=raycaster.intersectObjects(targets.map(t=>t.group),true)[0];
      if(hit)aimPoint.copy(hit.point);
      else raycaster.ray.intersectPlane(aimSurface,aimPoint);
      aimPoint.x=clamp(aimPoint.x,-7,7);aimPoint.y=clamp(aimPoint.y,.25,4);
      reticle.position.copy(aimPoint);reticle.lookAt(camera.position);reticle.visible=true;
      preview.visible=true;previewPath();
    }
    function aimFromEvent(e:PointerEvent) {
      const rect=canvas.getBoundingClientRect();
      mouse.set(clamp((e.clientX-rect.left)/rect.width*2-1,-1,1),clamp(-(e.clientY-rect.top)/rect.height*2+1,-1,1));
      aimAtPointer();
    }
    function previewPath() {
      const v=speed();const points=[];
      for(let t=0;t<2.3;t+=.09) points.push(new T.Vector3(launch.x+v.x*t,launch.y+v.y*t-.45*t*t,launch.z+v.z*t));
      previewGeometry.setFromPoints(points);preview.computeLineDistances();
    }
    function take() {
      if(paused||state.phase!=='mail') return;
      state.phase='folding';foldStart=time;sheet.visible=true;plane.visible=false;publish();
    }
    function throwPlane() {
      if(paused||state.phase!=='ready') return;
      state.phase='flying';state.shots++;velocity.copy(speed());plane.position.copy(launch);plane.rotation.set(0,0,0);flightStart=time;preview.visible=false;reticle.visible=false;publish();
    }
    function result(hit:boolean,distance=0,accuracy=0) {
      if(state.phase!=='flying') return;
      if(hit) {
        state.combo++;const points=Math.round(30+distance*3+accuracy*60)+Math.min(state.combo-1,4)*10;
        state.score+=points;state.feedback=`${accuracy>.78?'정확한 명중!':'명중!'} +${points}${state.combo>1?` · ${state.combo}연속`:''}`;
      } else {state.combo=0;state.feedback=state.shots>=5?'마지막 메일까지 날렸어요.':'아깝다! 다음 메일로 다시 조준해봐요.';}
      state.phase=state.shots>=5?'finished':'result';publish();
    }
    function next() { if(paused)return; if(state.phase==='result'){state.phase='mail';plane.visible=false;mouse.set(0,-.1);reticle.visible=false;paintMail(state.shots);publish();} else if(state.phase==='mail')take(); }
    function reset() {state={...initial};impactAt=-100;drag=null;mouse.set(0,-.1);reticle.visible=false;plane.visible=false;sheet.visible=false;preview.visible=false;paused=false;setPaused(false);targets.forEach(t=>t.hitAt=-100);paintMail(0);publish();}
    function pause() {paused=!paused;drag=null;setPaused(paused);last=0;}
    controls.current={take:next,throw:throwPlane,reset,pause};
    const canvas=renderer.domElement;
    function pointerDown(e:PointerEvent) {
      if(paused) return;
      canvas.focus();
      if(state.phase==='mail') {
        const rect=canvas.getBoundingClientRect();mouse.set((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1);raycaster.setFromCamera(mouse,camera);
        if(raycaster.intersectObject(monitor).length||raycaster.intersectObject(screen).length)take();return;
      }
      if(state.phase!=='ready')return;
      drag={id:e.pointerId};canvas.setPointerCapture(e.pointerId);aimFromEvent(e);
    }
    function pointerMove(e:PointerEvent) {
      if(paused||state.phase!=='ready'||(e.pointerType!=='mouse'&&drag?.id!==e.pointerId))return;
      aimFromEvent(e);
    }
    function pointerUp(e:PointerEvent) {if(drag?.id!==e.pointerId)return;aimFromEvent(e);drag=null;if(canvas.hasPointerCapture(e.pointerId))canvas.releasePointerCapture(e.pointerId);throwPlane();}
    function cancel(){drag=null;}
    function keyDown(e:KeyboardEvent) {
      if(e.key==='Escape'){pause();e.preventDefault();return;}
      if(paused)return;
      if(e.key==='Enter'&&(state.phase==='mail'||state.phase==='result')){next();e.preventDefault();return;}
      if(state.phase!=='ready')return;
      if(e.code==='Space'){throwPlane();e.preventDefault();return;}
      if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(e.key)) {
        mouse.x=clamp(mouse.x+(e.key==='ArrowLeft'?-.05:e.key==='ArrowRight'?.05:0),-1,1);
        mouse.y=clamp(mouse.y+(e.key==='ArrowUp'?.05:e.key==='ArrowDown'?-.05:0),-1,1);
        aimAtPointer();e.preventDefault();
      }
    }
    function visibility(){if(document.hidden&&!paused){paused=true;drag=null;setPaused(true);last=0;}}
    canvas.addEventListener('pointerdown',pointerDown);canvas.addEventListener('pointermove',pointerMove);canvas.addEventListener('pointerup',pointerUp);canvas.addEventListener('pointercancel',cancel);canvas.addEventListener('keydown',keyDown);document.addEventListener('visibilitychange',visibility);
    function resize(){const w=mount!.clientWidth,h=mount!.clientHeight;renderer.setSize(w,h);camera.aspect=w/h;camera.fov=w/h<.8?62:48;camera.updateProjectionMatrix();}
    const observer=new ResizeObserver(resize);observer.observe(mount);resize();
    renderer.setAnimationLoop((now:number)=>{
      const dt=last?Math.min((now-last)/1000,.034):0;last=now;
      if(!paused) {
        time+=dt;
        targets.forEach((target,i)=>{
          target.group.position.x=Math.sin(time*(.31+i*.045)+target.phase)*(2.6+i*.22);
          const hitAge=time-target.hitAt;target.group.rotation.z=hitAge<.55?Math.sin(hitAge*12)*.18*Math.exp(-hitAge*5):0;
          target.shirt.emissive.setHex(hitAge<.28?0x805122:0x000000);
        });
        if(state.phase==='folding') {
          const t=clamp((time-foldStart)/.85,0,1);sheet.position.set(-1.65*(1-t),2-(t*.35),3.9-t*.7);sheet.rotation.set(-t*Math.PI/2,t*.6,t*.2);sheet.scale.set(Math.max(.05,1-t),1,1);
          if(t===1){sheet.visible=false;plane.visible=true;plane.position.copy(launch);plane.rotation.set(0,0,0);plane.scale.setScalar(1);state.phase='ready';aimAtPointer();publish();}
        }
        if(state.phase==='ready'&&!drag)plane.position.y=launch.y+(isReduced?0:Math.sin(time*2)*.025);
        if(state.phase==='flying') {
          previous.copy(plane.position);velocity.y-=.9*dt;plane.position.addScaledVector(velocity,dt);
          plane.rotation.y=Math.atan2(-velocity.x,-velocity.z);plane.rotation.z=-velocity.x*.04;plane.rotation.x=Math.atan2(velocity.y,-velocity.z);
          segment.set(previous,plane.position);
          for(const target of targets) {
            targetPoint.set(target.group.position.x,1.55,target.z);segment.closestPointToPoint(targetPoint,true,closest);
            const distance=closest.distanceTo(targetPoint);
            if(distance<.62){target.hitAt=time;impactAt=time;impact.position.copy(closest);impact.lookAt(camera.position);result(true,launch.z-target.z,1-distance/.62);velocity.set(0,0,0);break;}
          }
          if(state.phase==='flying'&&(plane.position.z< -20||plane.position.y<.15||Math.abs(plane.position.x)>8||time-flightStart>4)) result(false);
        }
        const impactAge=time-impactAt;
        impact.visible=impactAge<.5;impact.scale.setScalar(1+Math.max(0,impactAge)*4);(impact.material as T.MeshBasicMaterial).opacity=Math.max(0,1-impactAge*2);
      }
      renderer.render(scene,camera);
    });
    return ()=>{
      controls.current=null;renderer.setAnimationLoop(null);observer.disconnect();document.removeEventListener('visibilitychange',visibility);
      canvas.removeEventListener('pointerdown',pointerDown);canvas.removeEventListener('pointermove',pointerMove);canvas.removeEventListener('pointerup',pointerUp);canvas.removeEventListener('pointercancel',cancel);canvas.removeEventListener('keydown',keyDown);
      scene.traverse(object=>{if(object instanceof T.Mesh||object instanceof T.Line){object.geometry.dispose();const materials=Array.isArray(object.material)?object.material:[object.material];materials.forEach(m=>m.dispose());}});screenTexture.dispose();renderer.dispose();canvas.remove();
    };
  },[]);
  const action = view.phase==='mail'?'메일 꺼내기':view.phase==='result'?'다음 메일':view.phase==='finished'?'다시 하기':view.phase==='ready'?'던지기':null;
  return <main className={styles.game}>
    <header className={styles.header}><Link href="/games">게임 목록</Link><span>종이비행기 · 첫 체험</span><button onClick={()=>controls.current?.pause()} disabled={unavailable}>{paused?'계속하기':'잠깐 멈춤'}</button></header>
    <div className={styles.title}><div><p>받은 메일, 날려버리기</p><h1>퇴근 전에 이것만요?</h1></div><div className={styles.score}><strong>{view.score}</strong><span>점수 · {view.shots}/5번</span></div></div>
    <section className={styles.stage} aria-label="종이비행기 게임">
      <div ref={host} className={styles.canvas}/>
      <div className={styles.instructions} aria-live="polite">{paused?'잠깐 멈췄어요.':view.phase==='mail'?'모니터의 메일을 꺼내세요.':view.phase==='folding'?'메일을 접는 중…':view.phase==='ready'?'맞힐 곳을 가리키고 클릭하세요.':view.phase==='flying'?'날아가는 중…':view.feedback}</div>
      {view.phase==='mail'&&<div className={styles.mail}><small>받은 메일</small><p>{emails[view.shots%emails.length]}</p></div>}
      {view.phase==='finished'&&<div className={styles.result}><span>메일함 비우기 완료</span><strong>{view.score}점</strong><p>각도를 바꿔 한 번 더 날려볼까요?</p></div>}
      {unavailable&&<div className={styles.result}><strong>3D 화면을 열 수 없어요.</strong><p>WebGL을 지원하는 브라우저에서 다시 열어주세요.</p></div>}
      <div className={styles.actions}>{action&&<button disabled={paused||unavailable} onClick={()=>view.phase==='finished'?controls.current?.reset():view.phase==='ready'?controls.current?.throw():controls.current?.take()}>{action}</button>}</div>
    </section>
    <footer className={styles.footer}><span>PC: 가리키고 클릭 · 모바일: 조준하고 손 떼기<br/>키보드: 화면 선택 후 방향키 조준 · Space 던지기</span><button onClick={()=>controls.current?.reset()}>처음부터</button><span>기록은 저장하지 않는 시제품이에요.</span></footer>
  </main>;
}

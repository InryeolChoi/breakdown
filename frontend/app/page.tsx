import Link from 'next/link';
export default function Home() {
  return <main className="comparison-home">
    <header className="comparison-header"><span className="wordmark">breakground<span>잠깐, 다른 공기.</span></span><span className="edition">디자인 체험실</span></header>
    <section className="comparison-intro"><p>일과 일 사이에</p><h1>어떤 화면에서<br/>쉬어가고 싶어요?</h1><div>같은 대화, 다른 분위기.<br/>직접 들어가서 이름을 정하고 한마디 남겨보세요.</div></section>
    <div className="comparison-options">
      <Link href="/spatial" className="design-option spatial-option"><div className="option-photo"/><div className="option-overlay"><span>창가의 자리</span><h2>공간형 카페</h2><p>탁자 앞에 앉은 듯한 깊이와 햇살.<br/>정지된 카페 풍경에서 대화를 나눠요.</p><strong>이 자리에서 쉬기 <span aria-hidden="true">↗</span></strong></div></Link>
      <Link href="/minimal" className="design-option minimal-option"><div className="option-photo"/><div className="option-overlay"><span>비워둔 화면</span><h2>미니멀 휴식</h2><p>빙하, 온기, 블랙. 세 가지 분위기.<br/>작은 대화와 넓은 여백만 남겨요.</p><strong>가볍게 쉬어가기 <span aria-hidden="true">↗</span></strong></div></Link>
    </div><footer className="comparison-footer"><span>체험 모드는 서버에 저장되지 않아요.</span><span>각 화면에서 실제 Spring 서버 연결로 바꿀 수 있어요.</span></footer>
  </main>;
}

import type { Metadata } from 'next';
import './styles.css';
export const metadata: Metadata = { title: 'Breakground — 잠깐 쉬어가는 곳', description: '잠깐 쉬면서 나누는 익명 대화.' };
export default function RootLayout({ children }: Readonly<{children: React.ReactNode}>) {
  return <html lang="ko"><body>{children}</body></html>;
}

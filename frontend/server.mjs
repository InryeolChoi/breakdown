import { createServer } from 'node:http';
import next from 'next';
import httpProxy from 'http-proxy';

const hostname = '127.0.0.1';
const port = Number(process.env.PORT || 3000);
const backend = process.env.BACKEND_URL || 'http://127.0.0.1:8080';
const target = new URL(backend);
if (!['http:', 'https:'].includes(target.protocol)) throw new Error('Invalid BACKEND_URL');
const app = next({ dev: process.env.NODE_ENV !== 'production', hostname, port });
await app.prepare();
const handle = app.getRequestHandler();
const upgrade = app.getUpgradeHandler();
// Preserve Host and Origin together: Spring sees the same browser origin for HTTP and WS.
// This is a loopback development gateway, not a production deployment configuration.
const proxy = httpProxy.createProxyServer({ target: backend, changeOrigin: false, xfwd: true });
proxy.on('error', (_error, _req, response) => {
  if ('writeHead' in response && !response.headersSent) {
    response.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    response.end(JSON.stringify({ message: 'Spring 서버에 연결할 수 없어요. 서버 실행 상태를 확인해 주세요.' }));
  } else if ('destroy' in response) response.destroy();
});
const server = createServer((req, res) => {
  if (req.url?.startsWith('/api/')) {
    req.url = req.url.slice(4);
    proxy.web(req, res, { proxyTimeout: 10000 });
  } else handle(req, res);
});
server.on('upgrade', (req, socket, head) => {
  if (/^\/ws\/rooms\/\d+(?:\?|$)/.test(req.url || '')) proxy.ws(req, socket, head);
  else upgrade(req, socket, head);
});
server.listen(port, hostname, () => console.log(`Breakground: http://${hostname}:${port} (Spring: ${backend})`));

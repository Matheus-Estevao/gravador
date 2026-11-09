// Guarda a casca do app pra abrir mesmo sem internet; a Mistral sempre vai direto pela rede.
const CACHE = 'gravador-v4';
const SHELL = ['./', 'index.html', 'manifest.webmanifest', 'icon.svg', 'icon-192.png', 'icon-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => Promise.all(SHELL.map(u => c.add(new Request(u, { cache: 'reload' })).catch(() => {})))));
  self.skipWaiting();
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE && k !== 'gravador-compartilhado').map(k => caches.delete(k)))));
  self.clients.claim();
});
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  // áudio compartilhado de outro app (ex.: gravador da Samsung): guarda e abre o app pra converter
  if (e.request.method === 'POST' && url.pathname.endsWith('/compartilhar')) {
    e.respondWith((async () => {
      const dados = await e.request.formData();
      const arq = dados.get('audio');
      if (arq) {
        const c = await caches.open('gravador-compartilhado');
        await c.put('./compartilhado', new Response(arq, { headers: { 'Content-Type': arq.type || 'audio/mp4', 'X-Nome': encodeURIComponent(arq.name || 'audio') } }));
      }
      return Response.redirect('./?compartilhado=1', 303);
    })());
    return;
  }
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  e.respondWith(
    fetch(e.request.url, { cache: 'no-cache' }).then(r => {
      const copia = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copia)); return r;
    }).catch(() => caches.match(e.request, { ignoreSearch: true }))
  );
});

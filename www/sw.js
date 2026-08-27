// 築雲預警 Service Worker — 只快取「殼」，絕不快取安全相關的即時資料。
//
// 這是防災 App，今天稽核已經修過好幾個「假裝知道答案」的錯誤（隨機座標、
// 中央山脈座標、把 dBZ 當雨量）。快取層絕不能再犯同一種錯——如果離線時
// Service Worker 拿一份舊的天氣/CCTV/SOS 回應充數，使用者會以為那是現況。
//
// 策略：
//   殼（index.html、manifest、圖示、vendor/leaflet）→ cache-first，
//     讓 App 離線也能打開、看到介面。
//   其他所有請求（後端 API、SSE、地圖圖磚）→ 完全不攔截，直接放行給網路。
//     連不上就讓它照原本的方式失敗，App 自己的 JS 已經有處理離線/逾時的
//     邏輯（佇列、toast、119 按鈕），不需要 Service Worker 插手假裝成功。
const CACHE = 'zhuyun-shell-v1';
const SHELL = [
  './',
  './index.html',
  './manifest.json',
  './vendor/leaflet/leaflet.css',
  './vendor/leaflet/leaflet.js',
  './icons/icon-192.png',
  './icons/icon-512.png',
];

self.addEventListener('install', (ev) => {
  ev.waitUntil(
    caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (ev) => {
  ev.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (ev) => {
  const req = ev.request;
  const url = new URL(req.url);

  // 只處理同源、GET、且在殼清單裡的請求。
  // 其餘一律不呼叫 respondWith——不攔截就是最安全的預設值，SSE 串流、
  // POST（回報/求救）、跨來源的 API/圖磚請求都完全不會經過這支腳本。
  const isShellAsset = url.origin === self.location.origin &&
    req.method === 'GET' &&
    SHELL.some((p) => url.pathname.endsWith(p.replace('./', '/')) || (p === './' && url.pathname === '/'));
  if (!isShellAsset) return;

  ev.respondWith(
    caches.match(req).then((cached) => cached || fetch(req))
  );
});

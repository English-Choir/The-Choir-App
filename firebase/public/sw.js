
const CACHE='choir-15aad125536f6dd4',FILES=["/","/icon.svg","/icon-192.png","/icon-512.png","/manifest.webmanifest","/assets/firebase-CQhuGDur.js","/assets/firestore-CgRgNz78.js","/assets/index-CNSsWT_d.css","/assets/index-hYCO_Azd.js","/assets/pdf-ui-DnV8m-qw.js","/assets/pdf-worker-BTRH5gxB.js"];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(FILES)));});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('choir-')&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
self.addEventListener('message',e=>{if(e.data==='ACTIVATE')self.skipWaiting();});
self.addEventListener('fetch',e=>{
 const u=new URL(e.request.url);
 if(e.request.method!=='GET'||u.origin!==location.origin)return;
 // Sites redirects /index.html to /. Cache the final URL so an offline navigation
 // never receives a redirected response, which browsers reject for navigations.
 if(e.request.mode==='navigate'){e.respondWith(fetch(e.request).catch(()=>caches.match('/',{ignoreVary:true})));return;}
 // Only fingerprinted application assets use this cache. Preview/CDN Vary: Origin
 // must not prevent a same-origin module request from finding its pre-cached bytes.
 if(FILES.includes(u.pathname))e.respondWith(caches.open(CACHE).then(c=>c.match(e.request,{ignoreVary:true})).then(c=>c||fetch(e.request)));
});

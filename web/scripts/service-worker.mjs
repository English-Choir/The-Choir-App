import { readdir, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
const assets=(await readdir('dist/assets')).map(f=>'/assets/'+f);
const files=['/','/icon.svg','/icon-192.png','/icon-512.png','/manifest.webmanifest',...assets];
const version=createHash('sha256').update(JSON.stringify(files)).digest('hex').slice(0,16);
await writeFile('dist/sw.js',`
const CACHE='choir-${version}',FILES=${JSON.stringify(files)};
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
`);

import {chromium,webkit,devices} from '@playwright/test';
import {mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const url=process.argv[2]||'http://127.0.0.1:5174/';
if(!['127.0.0.1','the-choir-library.bonnyloom256.chatgpt.site'].includes(new URL(url).hostname))throw Error('Unexpected smoke-test destination');
await mkdir('test-results',{recursive:true});
const results=[];
for(const [name,engine,options] of [['desktop',chromium,{viewport:{width:1440,height:1050}}],['iphone',webkit,{...devices['iPhone 13']}]]) {
  const browser=await engine.launch(),context=await browser.newContext(options),page=await context.newPage(),errors=[];
  const failures=[],expectedDisconnects=[];let deliberatelyOffline=false;
  const logs=[];page.on('console',m=>{if(['error','warning'].includes(m.type()))logs.push(m.text());});
  page.on('requestfailed',r=>failures.push({url:r.url(),failure:r.failure()}));
  page.on('pageerror',e=>{
    if(deliberatelyOffline&&e.message.includes('/google.firestore.v1.Firestore/Listen/channel'))expectedDisconnects.push(e.message);
    else errors.push(e.message);
  });
  try {
    await page.goto(url,{waitUntil:'domcontentloaded',timeout:60000});
    await page.waitForFunction(()=>document.querySelector('.connection')?.textContent==='Library synced',{timeout:30000});
    await page.evaluate(()=>document.fonts.ready);
    const count=await page.locator('.stats>div').first().innerText();
    assert.ok(count.includes('458'));
    const availability=await page.locator('.count-line').innerText();
    assert.ok(availability.includes('130'));
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
    await page.screenshot({path:`test-results/production-${name}-home.png`,fullPage:true});
    await page.goto(url+'#library');
    await page.getByRole('heading',{name:'All registrations',exact:true}).waitFor();
    await page.locator('#search').fill('Beautiful Sunday');
    await page.locator('#chord-filter').selectOption('with');
    assert.equal(await page.locator('.registration').count(),1);
    await page.screenshot({path:`test-results/production-${name}-registration.png`});
    await page.waitForFunction(()=>!!navigator.serviceWorker?.controller, {timeout:30000});
    deliberatelyOffline=true;await context.setOffline(true);
    await page.reload({waitUntil:'domcontentloaded'});
    await page.waitForSelector('.offline-notice');
    await page.goto(url+'#sections');
    await page.waitForFunction(()=>document.querySelector('.stats>div')?.textContent.includes('458'));
    assert.deepEqual(errors,[]);
    results.push({name,liveRegistrations:458,availability,offlineReload:true,pageErrors:errors,expectedOfflineDisconnects:expectedDisconnects.length});
  }catch(e){
    console.log(JSON.stringify({name,error:e.message,url:page.url(),page:await page.locator('body').innerText(),connection:await page.evaluate(()=>document.querySelector('.connection')?.textContent||null),online:await page.evaluate(()=>navigator.onLine),pageErrors:errors,logs,failures,cache:await page.evaluate(async()=>typeof caches==='undefined'?null:Object.fromEntries(await Promise.all((await caches.keys()).map(async k=>[k,(await (await caches.open(k)).keys()).map(r=>r.url)]))))}));
    await page.screenshot({path:`test-results/production-${name}-failure.png`});
    throw e;
  }finally{await browser.close();}
}
await writeFile('test-results/production-smoke.json',JSON.stringify(results,null,2));
console.log(JSON.stringify(results,null,2));


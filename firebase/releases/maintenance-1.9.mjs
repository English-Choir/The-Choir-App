import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {OAuth2Client} from 'google-auth-library';
import {googleRequest,firestoreUrl,ownerEmail} from '../admin-session.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const out=path.join(root,'outputs/choir-android');
const save=(name,data)=>fs.writeFileSync(path.join(out,name),JSON.stringify(data,null,2)+'\n');
async function drive() {
  const t=JSON.parse(fs.readFileSync(path.join(process.env.USERPROFILE,'.choir-app-clasprc.json'),'utf8')).tokens['english-choir'];
  const c=new OAuth2Client(t.client_id,t.client_secret);c.setCredentials(t);
  const a=await c.getAccessToken();
  if((await c.getTokenInfo(a.token)).email!==ownerEmail)throw Error('Wrong account');
  return c;
}
async function apkFiles(c) {
  const q="'me' in owners and trashed = false and (name contains '.apk')";
  const all=[];let page='';
  do {
    const url=new URL('https://www.googleapis.com/drive/v3/files');
    url.search=new URLSearchParams({q,pageSize:'100',fields:'files(id,name,size,parents,webViewLink,owners(emailAddress)),nextPageToken',...(page?{pageToken:page}:{})});
    const r=(await c.request({url:url.href})).data;all.push(...r.files);page=r.nextPageToken||'';
  }while(page);
  return all;
}
async function main(){
  const mode=process.argv[2];
  if(mode==='audit-drive') {
    const files=await apkFiles(await drive());save('version-1.9-drive-before-cleanup.json',files);console.log(JSON.stringify(files));
  } else if(mode==='deploy-sync') {
    const c=await drive();const dir=path.join(root,'firebase/chords');
    const id=JSON.parse(fs.readFileSync(path.join(dir,'.clasp.json'),'utf8')).scriptId;
    const url=`https://script.googleapis.com/v1/projects/${id}/content`;
    const remote=(await c.request({url})).data;
    const before=fs.readFileSync(path.join(dir,'deploy/Code.gs'),'utf8').replaceAll('\r\n','\n');
    const next=fs.readFileSync(path.join(dir,'Code.gs'),'utf8');
    const code=remote.files.find(f=>f.name==='Code'&&f.type==='SERVER_JS');
    if(!code || code.source.replaceAll('\r\n','\n')!==before)throw Error('Remote sync differs from the last deployed source; inspect before replacing.');
    const files=remote.files.map(({name,type,source})=>({name,type,source:name==='Code'?next:source}));
    await c.request({url,method:'PUT',data:{files}});
    const verified=(await c.request({url})).data.files.find(f=>f.name==='Code');
    if(verified.source.replaceAll('\r\n','\n')!==next.replaceAll('\r\n','\n'))throw Error('Sync readback differs');
    fs.copyFileSync(path.join(dir,'Code.gs'),path.join(dir,'deploy/Code.gs'));
    save('version-1.9-sync-deployment.json',{scriptId:id,verified:true,deployedAt:new Date().toISOString()});console.log('Chord sync updated and read back successfully.');
  } else if(mode==='cleanup-drive') {
    const release=JSON.parse(fs.readFileSync(path.join(out,'version-1.9-release.json'),'utf8'));
    const current=await googleRequest(`${firestoreUrl}/documents/appReleases/android`);
    if(Number(current.fields.versionCode.integerValue)!==10 || current.fields.downloadUrl.stringValue!==release.downloadUrl)throw Error('Publish the verified 1.9 release first');
    const c=await drive();const files=await apkFiles(c);
    const latest=files.filter(f=>/^The-Choir-1\.[89]\.apk$/.test(f.name));
    if(latest.length!==2)throw Error('Expected exactly one copy each of 1.8 and 1.9 before cleanup');
    const old=files.filter(f=>/^The-Choir-1\.[0-7]\.apk$/.test(f.name));
    for(const f of old){
      if(!f.owners.some(o=>o.emailAddress===ownerEmail))throw Error('Unexpected APK owner');
      await c.request({url:`https://www.googleapis.com/drive/v3/files/${f.id}`,method:'PATCH',data:{trashed:true}});
      const result=(await c.request({url:`https://www.googleapis.com/drive/v3/files/${f.id}?fields=id,trashed`})).data;
      if(!result.trashed)throw Error('Could not verify trash');
    }
    const remaining=await apkFiles(c);
    save('version-1.9-drive-cleanup.json',{trashed:old.map(f=>({id:f.id,name:f.name})),retained:remaining});
    console.log(JSON.stringify({trashed:old.map(f=>f.name),remaining:remaining.map(f=>f.name)}));
  } else if(mode==='cleanup-firebase') {
    const url=`${firestoreUrl}/documents/chordOverrides/e0b1701f48e889b25a57`;
    const d=await googleRequest(url);
    if(d.fields.mode.stringValue!=='automatic' || d.fields.appendPending.booleanValue!==false || d.fields.revision.integerValue!=='3')throw Error('The original test override changed; keep it.');
    await googleRequest(`${firestoreUrl}/documents:commit`,{method:'POST',body:JSON.stringify({writes:[{delete:d.name,currentDocument:{updateTime:d.updateTime}}]})});
    const r=await fetch(url);if(r.status!==404)throw Error('Deletion verification failed');
    save('version-1.9-firebase-cleanup.json',{removedDefaultOverride:'e0b1701f48e889b25a57',reason:'Original setup test returned to Automatic; same behavior as no override.',verified:true});
    console.log('Removed one redundant Automatic override left by the original setup test.');
  } else throw Error('Unknown maintenance mode');
}
main().catch(e=>{console.error(e.response?.data?.error?.message||e.message);process.exitCode=1;});


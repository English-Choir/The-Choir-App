import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {OAuth2Client} from 'google-auth-library';
import {googleRequest,firestoreUrl,firestoreFields,databasePath,ownerEmail} from '../admin-session.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const out = path.join(root, 'outputs/choir-android');
const apkPath = path.join(out, 'The-Choir-1.8.apk');
const manifestPath = path.join(out, 'version-1.8-drive.json');
const hash = (b,algo='sha256') => createHash(algo).update(b).digest('hex');
const write = (name,data) => fs.writeFileSync(path.join(out,name),JSON.stringify(data,null,2)+'\n');

async function main() {
  const apk = fs.readFileSync(apkPath);
  if (process.argv[2] === 'upload') {
    if (fs.existsSync(manifestPath)) throw new Error('Upload manifest already exists; inspect it instead of creating a duplicate.');
    const saved = JSON.parse(fs.readFileSync(path.join(process.env.USERPROFILE,'.choir-app-clasprc.json'),'utf8')).tokens['english-choir'];
    const client = new OAuth2Client(saved.client_id,saved.client_secret);
    client.setCredentials(saved);
    const token = await client.getAccessToken();
    if ((await client.getTokenInfo(token.token)).email !== ownerEmail) throw new Error('Wrong Drive account');
    const parent = '1sLM2zur6JvRQB5hX88WAA6CxNiDjujKy';
    const folder = (await client.request({url:`https://www.googleapis.com/drive/v3/files/${parent}?fields=id,name,owners(emailAddress),mimeType`})).data;
    if (!folder.owners.some(o=>o.emailAddress===ownerEmail) || folder.mimeType!=='application/vnd.google-apps.folder') throw new Error('Wrong release folder');
    const mime = 'application/vnd.android.package-archive';
    const session = await client.request({url:'https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name,size,md5Checksum,webViewLink,parents',method:'POST',
      headers:{'X-Upload-Content-Type':mime,'X-Upload-Content-Length':String(apk.length)},
      data:{name:'The-Choir-1.8.apk',mimeType:mime,parents:[parent]}});
    const location = session.headers.get?.('location') || session.headers.location;
    if (!location) throw new Error('Upload session missing');
    const uploaded = (await client.request({url:location,method:'PUT',headers:{'Content-Type':mime,'Content-Length':String(apk.length)},data:apk})).data;
    write('version-1.8-drive.json',{...uploaded,sha256:hash(apk)});
    if (uploaded.md5Checksum!==hash(apk,'md5') || Number(uploaded.size)!==apk.length) throw new Error('Upload checksum mismatch');
    await client.request({url:`https://www.googleapis.com/drive/v3/files/${uploaded.id}/permissions`,method:'POST',data:{type:'anyone',role:'reader',allowFileDiscovery:false}});
    const metadata = (await client.request({url:`https://www.googleapis.com/drive/v3/files/${uploaded.id}?fields=id,name,size,md5Checksum,webViewLink,owners(emailAddress),parents,permissions(type,role,allowFileDiscovery)`})).data;
    if (!metadata.owners.some(o=>o.emailAddress===ownerEmail) || !metadata.permissions.some(p=>p.type==='anyone'&&p.role==='reader'&&!p.allowFileDiscovery)) throw new Error('Sharing verification failed');
    write('version-1.8-drive.json',{...metadata,sha256:hash(apk)});
    console.log(JSON.stringify({id:metadata.id,name:metadata.name,url:metadata.webViewLink,size:metadata.size}));
  } else if (process.argv[2] === 'verify-download') {
    const metadata = JSON.parse(fs.readFileSync(manifestPath,'utf8'));
    let response = await fetch(`https://drive.usercontent.google.com/download?id=${metadata.id}&export=download`);
    if ((response.headers.get('content-type')||'').includes('text/html')) {
      const html = await response.text();
      const form = html.match(/<form[^>]*id="download-form"[^>]*action="([^"]+)"[^>]*>([\s\S]*?)<\/form>/);
      if (!form || form[1]!=='https://drive.usercontent.google.com/download') throw new Error('Unexpected public download page');
      const params = new URLSearchParams();
      for(const input of form[2].matchAll(/<input[^>]*name="([^"]+)"[^>]*value="([^"]*)"[^>]*>/g)) params.set(input[1],input[2]);
      if (params.get('id')!==metadata.id || params.get('confirm')!=='t') throw new Error('Unexpected download confirmation');
      response = await fetch(form[1]+'?'+params);
    }
    const downloaded = Buffer.from(await response.arrayBuffer());
    if (!response.ok || hash(downloaded)!==hash(apk) || downloaded.length!==apk.length) throw new Error('Anonymous download verification failed');
    write('version-1.8-public-download.json',{fileId:metadata.id,sizeBytes:apk.length,sha256:hash(apk),verifiedAt:new Date().toISOString()});
    console.log('Anonymous APK download verified by size and SHA-256.');
  } else if (process.argv[2] === 'publish') {
    const metadata = JSON.parse(fs.readFileSync(manifestPath,'utf8'));
    const verified = JSON.parse(fs.readFileSync(path.join(out,'version-1.8-public-download.json'),'utf8'));
    if (verified.sha256!==hash(apk) || verified.fileId!==metadata.id) throw new Error('Verify the current APK first');
    const previous = await googleRequest(`${firestoreUrl}/documents/appReleases/android`);
    if (Number(previous.fields.versionCode.integerValue)!==8) throw new Error('Release changed; inspect before publishing');
    const release = {enabled:true,packageName:'com.phantominnovation.choir',versionCode:9,versionName:'1.8',
      downloadUrl:`https://drive.google.com/file/d/${metadata.id}/view`,sha256:hash(apk),sizeBytes:apk.length,publishedAt:new Date(),
      notes:'Host rename and removal of Other registrations sections, preserving registrations and chord availability. Shared Firebase library is also available on the web.'};
    await googleRequest(`${firestoreUrl}/documents:commit`,{method:'POST',body:JSON.stringify({writes:[{
      update:{name:`${databasePath}/documents/appReleases/android`,fields:firestoreFields(release)},currentDocument:{updateTime:previous.updateTime}
    }]})});
    const publicRead = await fetch(`${firestoreUrl}/documents/appReleases/android`).then(r=>r.json());
    if (Number(publicRead.fields?.versionCode?.integerValue)!==9) throw new Error('Release readback failed');
    write('version-1.8-release.json',{...release,guestReadVerified:true});
    console.log(JSON.stringify({version:release.versionName,url:release.downloadUrl,guestReadVerified:true}));
  } else throw new Error('Choose upload, verify-download or publish');
}
main().catch(e=>{console.error(e.response?.data?.error?.message || e.message);process.exitCode=1;});


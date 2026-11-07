import fs from 'node:fs';
import {isDeepStrictEqual} from 'node:util';
import {googleRequest,projectId,ownerEmail} from '../admin-session.mjs';
const number='243157577573';
const api='https://serviceusage.googleapis.com/v1/projects/'+number+'/services/drive.googleapis.com';
if((await googleRequest(api)).state!=='ENABLED'){
  const op=await googleRequest(api+':enable',{method:'POST',body:'{}'});
  if(!op.done)console.log('Drive API enable requested.');
}
const base=`https://apikeys.googleapis.com/v2/projects/${number}/locations/global/keys`;
const title='The Choir public PDF downloads';
const list=await googleRequest(base);
let key=list.keys?.find(k=>k.displayName===title);
const restrictions={
  browserKeyRestrictions:{allowedReferrers:[
    'https://the-choir-library.bonnyloom256.chatgpt.site',
    'https://the-choir-library.bonnyloom256.chatgpt.site/*',
    'http://127.0.0.1:5173','http://127.0.0.1:5173/*',
    'http://127.0.0.1:5174','http://127.0.0.1:5174/*'
  ]},
  apiTargets:[{service:'drive.googleapis.com',methods:['google.apps.drive.v3.DriveFiles.Get','google.apps.drive.v3.DriveFiles.Export']}]
};
if(!key){
  let op=await googleRequest(base,{method:'POST',body:JSON.stringify({displayName:title,restrictions})});
  for(let i=0;!op.done&&i<25;i++){
    await new Promise(resolve=>setTimeout(resolve,1000));
    op=await googleRequest('https://apikeys.googleapis.com/v2/'+op.name);
  }
  if(!op.done||op.error)throw Error('Public download key creation did not complete.');
  key=op.response;
}
const verified=await googleRequest('https://apikeys.googleapis.com/v2/'+key.name);
if(!isDeepStrictEqual(verified.restrictions,restrictions))throw Error('Existing key restrictions differ; inspect before changing.');
const value=await googleRequest('https://apikeys.googleapis.com/v2/'+key.name+'/keyString');
// A website-restricted, public API key identifies requests; it grants no access to private Drive files.
fs.writeFileSync(new URL('../../web/src/drive-config.json',import.meta.url),JSON.stringify({publicApiKey:value.keyString},null,2)+'\n');
console.log(JSON.stringify({account:ownerEmail,projectId,keyName:key.name,restrictedToPublicReadMethods:true}));

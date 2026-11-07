import fs from 'node:fs';
import { googleRequest,projectId,ownerEmail } from '../admin-session.mjs';
const base='https://firebase.googleapis.com/v1beta1/projects/'+projectId;
const apps=await googleRequest(base+'/webApps');
let app=apps.apps?.find(a=>a.displayName==='The Choir Web');
if(!app){
  const operation=await googleRequest(base+'/webApps',{method:'POST',body:JSON.stringify({displayName:'The Choir Web'})});
  let result=operation;
  for(let i=0;i<20&&!result.done;i++){
    await new Promise(resolve=>setTimeout(resolve,1000));
    result=await googleRequest('https://firebase.googleapis.com/v1beta1/'+operation.name);
  }
  if(!result.done||result.error)throw Error('Web app registration did not complete');
  app=result.response;
}
const config=await googleRequest(base+'/webApps/'+encodeURIComponent(app.appId)+'/config');
fs.mkdirSync(new URL('../../web/src',import.meta.url),{recursive:true});
fs.writeFileSync(new URL('../../web/src/firebase-config.json',import.meta.url),JSON.stringify(config,null,2)+'\n');
const url='https://identitytoolkit.googleapis.com/admin/v2/projects/'+projectId+'/config';
const settings=await googleRequest(url);
const domain='the-choir-library.bonnyloom256.chatgpt.site';
if(!settings.authorizedDomains.includes(domain))await googleRequest(url+'?updateMask=authorizedDomains',{
  method:'PATCH',body:JSON.stringify({authorizedDomains:[...settings.authorizedDomains,domain]})});
console.log(JSON.stringify({owner:ownerEmail,project:projectId,appId:app.appId,domain}));

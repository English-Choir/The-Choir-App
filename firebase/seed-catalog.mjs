import { readFile } from 'node:fs/promises';
import { adminAuth, googleRequest, firestoreUrl, databasePath, firestoreFields, projectId, ownerEmail } from './admin-session.mjs';

const source = JSON.parse(await readFile(new URL('../android/app/src/main/assets/registrations.json',import.meta.url),'utf8'));
if(source.registrations.length !== 458 || source.sections.length !== 20) throw new Error('Unexpected source catalog');
let host;
try { host = await adminAuth.getUserByEmail(ownerEmail); }
catch(error) {
  if(error.code !== 'auth/user-not-found') throw error;
  // The owner chooses the app password through Firebase's password setup/reset flow.
  host = await adminAuth.createUser({email:ownerEmail,displayName:'English Choir Host'});
}
const existing = [];
let nextPageToken;
do {
  const page = await googleRequest(`${firestoreUrl}/documents/registrations?pageSize=1000${nextPageToken ? '&pageToken='+encodeURIComponent(nextPageToken) : ''}`);
  existing.push(...(page.documents || [])); nextPageToken=page.nextPageToken;
} while(nextPageToken);
const ids = new Set(existing.map(d=>d.name.split('/').pop()));
const missing = source.registrations.filter(row=>!ids.has(row.id));
const writes = [];
try { await googleRequest(`${firestoreUrl}/documents/hosts/${host.uid}`); }
catch(error) {
  if(!error.message.startsWith('404:')) throw error;
  writes.push({update:{name:`${databasePath}/documents/hosts/${host.uid}`,fields:firestoreFields({active:true,email:ownerEmail,createdAt:new Date()})},currentDocument:{exists:false}});
}
// Create-only writes preserve every later host edit and tombstone on reruns.
for(const row of missing) writes.push({update:{name:`${databasePath}/documents/registrations/${row.id}`,fields:firestoreFields({
  ...row,deleted:false,revision:1,updatedBy:host.uid,updatedAt:new Date()
})},currentDocument:{exists:false}});
for(let i=0;i<writes.length;i+=400) {
  await googleRequest(`${firestoreUrl}/documents:commit`,{method:'POST',body:JSON.stringify({writes:writes.slice(i,i+400)})});
}
console.log(JSON.stringify({projectId,ownerEmail,hostUid:host.uid,added:missing.length,preserved:existing.length,total:existing.length+missing.length}));

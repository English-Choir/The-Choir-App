import { readFile, writeFile } from 'node:fs/promises';
import { deepStrictEqual } from 'node:assert';
import { googleRequest, firestoreUrl, ownerEmail, adminAuth, projectId } from './admin-session.mjs';
const source = JSON.parse(await readFile(new URL('../android/app/src/main/assets/registrations.json',import.meta.url),'utf8'));
const docs=[]; let pageToken;
do {
  const response = await fetch(`${firestoreUrl}/documents/registrations?pageSize=1000${pageToken?'&pageToken='+encodeURIComponent(pageToken):''}`);
  if(!response.ok) throw Error(`Public library read failed: ${response.status}`);
  const result=await response.json(); docs.push(...(result.documents||[]));pageToken=result.nextPageToken;
} while(pageToken);
function decode(v) {
  if('stringValue' in v) return v.stringValue;
  if('integerValue' in v) return Number(v.integerValue);
  if('booleanValue' in v) return v.booleanValue;
  if('arrayValue' in v) return (v.arrayValue.values||[]).map(decode);
  if('mapValue' in v) return Object.fromEntries(Object.entries(v.mapValue.fields||{}).map(([k,v])=>[k,decode(v)]));
  return null;
}
const rows=new Map(docs.map(doc=>[doc.name.split('/').pop(),Object.fromEntries(Object.entries(doc.fields).map(([k,v])=>[k,decode(v)]))]));
if(rows.size!==source.registrations.length) throw Error('Catalog count mismatch');
for(const expected of source.registrations) {
  const actual=rows.get(expected.id);
  deepStrictEqual(Object.fromEntries(Object.keys(expected).map(k=>[k,actual[k]])),expected);
  if(actual.deleted) throw Error('Unexpected deleted source row');
}
const host=await adminAuth.getUserByEmail(ownerEmail);
const role=await googleRequest(`${firestoreUrl}/documents/hosts/${host.uid}`);
if(role.fields.active.booleanValue!==true) throw Error('Host role is inactive');
const privateRead=await fetch(`${firestoreUrl}/documents/hosts/${host.uid}`);
if(privateRead.status!==403) throw Error('Host role must not be publicly readable');
const result={projectId,account:ownerEmail,registrations:rows.size,sections:source.sections.length,sourceFieldsMatch:true,hostActive:true,hostRolePrivate:true,checkedAt:new Date().toISOString()};
await writeFile(new URL('../outputs/choir-android/firebase-catalog-verification.json',import.meta.url),JSON.stringify(result,null,2));
console.log(JSON.stringify(result));

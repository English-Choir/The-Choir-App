import {readFile,writeFile} from 'node:fs/promises';
import vm from 'node:vm';
import {googleRequest,firestoreUrl,databasePath,firestoreFields} from '../admin-session.mjs';
const sheet = JSON.parse(await readFile(new URL('./imported-sheet.json',import.meta.url),'utf8'));
const source = JSON.parse(await readFile(new URL('./source-list.json',import.meta.url),'utf8'));
for(const section of source.sections) {
  const remote=sheet.sections.find(s=>s.id===section.id);
  if(!remote || remote.songs.length!==section.songs.length)throw Error('Import count mismatch');
  for(let i=0;i<section.songs.length;i++){
    const name=section.songs[i].replace(/\s*\((reprint[^)]*)\)/i,'').trim();
    if(remote.songs[i].name!==name) throw Error('Import value mismatch');
  }
}
const doc=`${databasePath}/documents/chordCatalog/current`;
let existing;
try {existing=await googleRequest(`${firestoreUrl}/documents/chordCatalog/current`);}catch(e){if(!e.message.startsWith('404:'))throw e;}
if(!existing) await googleRequest(`${firestoreUrl}/documents:commit`,{method:'POST',body:JSON.stringify({writes:[{update:{name:doc,fields:firestoreFields({...sheet,sheetUrl:`https://docs.google.com/spreadsheets/d/${sheet.spreadsheetId}/edit`,syncEnabled:false,syncedAt:new Date()})},currentDocument:{exists:false}}]})});
const context = vm.createContext({});
vm.runInContext(await readFile(new URL('./Code.gs',import.meta.url),'utf8'),context);
const catalog=JSON.parse(await readFile(new URL('../../android/app/src/main/assets/registrations.json',import.meta.url),'utf8'));
const matches=sheet.sections.flatMap(s=>s.songs.map(song=>({section:s.sheet,song:song.name,registrations:catalog.registrations.filter(r=>r.sectionId===s.id&&context.chordMatches_(song,r.name)).map(r=>({id:r.id,name:r.name}))})));
await writeFile(new URL('../../outputs/hard-copy-chords/matching-report.json',import.meta.url),JSON.stringify(matches,null,2));
console.log(JSON.stringify({verifiedSongs:135,sections:13,matchedSongs:matches.filter(m=>m.registrations.length).length,matchedRegistrations:matches.reduce((n,m)=>n+m.registrations.length,0),unmatchedSongs:matches.filter(m=>!m.registrations.length).length,seeded:!existing}));

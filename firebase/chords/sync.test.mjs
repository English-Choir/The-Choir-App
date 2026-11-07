import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';

const source = await readFile(new URL('./Code.gs',import.meta.url),'utf8');
const original = JSON.parse(await readFile(new URL('./imported-sheet.json',import.meta.url),'utf8'));
const headers = ['Song name','Also match registration','Notes'];
function fixture() {
  const sheets = new Map(original.sections.map(s=>[s.sheet,[headers.slice(),...s.songs.map(v=>[v.name,v.aliases.join('|'),v.notes||''])]]));
  const written = [], pending = [], props = new Map(), registrations = new Map(), sections = [];
  let acknowledgeFailure = false, changed = false, owner = 'englishchoir00@gmail.com', sheetReads = 0;
  const sheet = name => ({
    getLastRow:()=>sheets.get(name).length,
    setFrozenRows(){},setColumnWidths(){},setColumnWidth(){},
    getRange:(row,col,n=1,m=1)=>({
      getDisplayValues:()=>Array.from({length:n},(_,i)=>Array.from({length:m},(_,j)=>sheets.get(name)[row+i-1]?.[col+j-1]||'')),
      setNumberFormat(){return this;},
      setBackground(){return this;},setFontColor(){return this;},setFontWeight(){return this;},
      setValues(values){values.forEach((v,i)=>{sheets.get(name)[row+i-1]=v;});return this;},
      setValue(value){
        const rows=sheets.get(name); while(rows.length<row)rows.push(['','','']);
        rows[row-1][col-1]=String(value).replace(/^'/,''); return this;
      }
    })
  });
  const context = vm.createContext({Date,console:{log(){}},
    Session:{getEffectiveUser:()=>({getEmail:()=>owner})},
    LockService:{getScriptLock:()=>({tryLock:()=>true,releaseLock(){}})},
    SpreadsheetApp:{openById:()=>({getSheetByName:name=>sheets.has(name)?sheet(name):null,
      insertSheet:name=>{sheets.set(name,[]);return sheet(name);}}),flush(){}},
    Sheets:{Spreadsheets:{get:()=>{sheetReads++; return {sheets:[...sheets].map(([title,rows])=>({properties:{title},
      data:[{rowData:rows.map(row=>({values:row.map(formattedValue=>({formattedValue}))}))}]}))};}}},
    PropertiesService:{getScriptProperties:()=>({getProperties:()=>Object.fromEntries(props),getProperty:k=>props.get(k),setProperty:(k,v)=>props.set(k,v),deleteProperty:k=>props.delete(k)})},
    Utilities:{DigestAlgorithm:{SHA_256:'sha256'},newBlob:s=>({getBytes:()=>Buffer.from(s)}),
      computeDigest:(_,s)=>createHash('sha256').update(s).digest(),base64Encode:b=>Buffer.from(b).toString('base64')}
  });
  vm.runInContext(source,context);
  context.choirWrite_=(path,data)=>written.push({path,data});
  context.choirRequest_=(url,method,data)=>{
    if(url.endsWith(':runQuery'))return data.structuredQuery.from[0].collectionId === 'sections'
      ? sections.map(s=>({document:{fields:context.choirFields_(s)}})) : pending.map(document=>({document}));
    if(url.includes('/hosts/'))return {fields:context.choirFields_({active:true})};
    if(url.includes('/registrations/')){
      const row=registrations.get(decodeURIComponent(url.split('/').pop()));
      return row ? {fields:context.choirFields_(row)} : null;
    }
    if(url.includes('/chordOverrides/'))return {...pending.find(d=>d.name===url.slice(url.indexOf('projects/'))),updateTime:changed?'newer':'original'};
    if(url.endsWith(':commit')){
      if(acknowledgeFailure)throw Error('Temporary connection failure');
      const id=data.writes[0].update.name;
      pending.splice(pending.findIndex(d=>d.name===id),1); return {};
    }
    throw Error('Unexpected request: '+url);
  };
  return {context,sheets,written,pending,registrations,sections,
    queue(id,name,sectionId='entrance',mode='enabled'){
      registrations.set(id,{name,sectionId,deleted:false});
      pending.push({name:'projects/the-choir-app-39f0a/databases/(default)/documents/chordOverrides/'+id,
        updateTime:'original',fields:context.choirFields_({mode,updatedBy:'fixture-host',appendPending:true})});
    },
    reads:()=>sheetReads,expireSnapshot:()=>props.set('lastSnapshotAt','0'),
    failAcknowledgement:v=>acknowledgeFailure=v,changeRequest:()=>changed=true,wrongOwner:()=>owner='someone-else@example.test'};
}

test('publishes the whole list and removes deleted sheet rows from the next snapshot',()=>{
  const f=fixture(); f.context.syncChords();
  const catalog=()=>f.written.filter(w=>w.path==='chordCatalog/current').at(-1).data;
  assert.equal(catalog().sections.reduce((n,s)=>n+s.songs.length,0),135);
  assert.equal(catalog().syncEnabled,true);
  f.sheets.get('ENTRANCE').splice(1,1); f.context.syncChords();
  assert.equal(catalog().sections[0].songs.some(s=>s.name==='Beautiful Sunday'),false);
});

test('rejects a missing tab before acknowledging or appending host requests',()=>{
  const f=fixture(); f.queue('one','New hymn'); f.sheets.delete('HOLY');
  assert.throws(()=>f.context.syncChords(),/Missing required tab/);
  assert.equal(f.pending.length,1);
  assert.equal(f.written.some(w=>w.path==='chordCatalog/current'),false);
  assert.equal(f.sheets.get('ENTRANCE').length,14);
});

test('host enable adds once even when the first acknowledgement fails',()=>{
  const f=fixture(); f.queue('one','New hymn'); f.failAcknowledgement(true);
  assert.throws(()=>f.context.syncChords(),/connection failure/);
  f.failAcknowledgement(false); f.context.syncChords();
  assert.equal(f.sheets.get('ENTRANCE').filter(row=>row[0]==='New hymn').length,1);
  assert.equal(f.pending.length,0);
});

test('an alias match does not duplicate a song and matching stays within its section',()=>{
  const f=fixture(); f.sheets.get('ENTRANCE')[1][1]='Alternate title';
  f.queue('one','Alternate title'); f.queue('two','Alternate title','holy'); f.context.syncChords();
  assert.equal(f.sheets.get('ENTRANCE').length,14);
  assert.equal(f.sheets.get('HOLY').at(-1)[0],'Alternate title');
});

test('a newer host setting prevents a stale enable from appending',()=>{
  const f=fixture(); f.queue('one','New hymn'); f.changeRequest(); f.context.syncChords();
  assert.equal(f.sheets.get('ENTRANCE').length,14);
  assert.equal(f.pending.length,1);
});

test('deleted registrations are acknowledged without adding a song',()=>{
  const f=fixture(); f.queue('one','Deleted hymn'); f.registrations.get('one').deleted=true; f.context.syncChords();
  assert.equal(f.pending.length,0); assert.equal(f.sheets.get('ENTRANCE').length,14);
});

test('refuses to run as a different Google account',()=>{
  const f=fixture(); f.wrongOwner(); assert.throws(()=>f.context.syncChords(),/must run as/);
  assert.equal(f.written.length,0);
});

test('idle timers defer sheet reads, while edit events and the periodic refresh catch changes',()=>{
  const f=fixture(); f.context.syncChords();
  f.context.syncChords({triggerUid:'timer'}); assert.equal(f.reads(),1);
  f.context.chordSheetEdited({range:{getColumn:()=>1}}); assert.equal(f.reads(),2);
  f.expireSnapshot(); f.context.syncChords({triggerUid:'timer'}); assert.equal(f.reads(),3);
});

test('a pending host addition bypasses the idle optimization',()=>{
  const f=fixture(); f.context.syncChords(); f.queue('one','New hymn');
  f.context.syncChords({triggerUid:'timer'});
  assert.equal(f.sheets.get('ENTRANCE').at(-1)[0],'New hymn'); assert.equal(f.pending.length,0);
});

test('custom sections create their own safe tab on first host enable and sync subsequent sheet edits',()=>{
  const f=fixture();
  const section={id:'section-'+'a'.repeat(64),title:'Easter / Vigil',other:true};
  f.sections.push(section);
  f.queue('custom-song','New Easter hymn',section.id);
  f.context.syncChords();
  const title=f.context.choirCustomSheet_(section);
  assert.equal(f.sheets.get(title)[1][0],'New Easter hymn');
  assert.equal(f.pending.length,0);
  const catalog=()=>f.written.filter(w=>w.path==='chordCatalog/current').at(-1).data;
  assert.equal(catalog().sections.find(s=>s.id===section.id).songs[0].name,'New Easter hymn');
  f.sheets.get(title).push(['Another hymn','','']);
  f.context.syncChords();
  assert.equal(catalog().sections.find(s=>s.id===section.id).songs.length,2);
});

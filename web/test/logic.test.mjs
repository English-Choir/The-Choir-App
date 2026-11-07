import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {catalogView,chordState,parseHymns,matchHymns,retrieval,filterRows,sectionTitle,validateRegistration,guestChange} from '../src/logic.js';
const base=JSON.parse(readFileSync(new URL('../src/registrations.json',import.meta.url)));
const sheet=JSON.parse(readFileSync(new URL('../src/reviewed_chords.json',import.meta.url)));
const catalog=catalogView(base,[]),chords=chordState(sheet.sections,{});
test('all 458 reviewed registrations remain valid, with 292 linked entries',()=>{
  assert.equal(catalog.registrations.length,458);
  assert.equal(catalog.registrations.filter(r=>r.links.length).length,292);
  for(const row of catalog.registrations){
    const validated=validateRegistration(row,catalog,row.id);
    for(const field of ['id','sectionId','name','style','tempo','transpose'])assert.equal(validated[field],row[field]);
    assert.deepEqual(validated.links,row.links.map(l=>({title:l.title.trim(),url:l.url})));
  }
});
test('renames, chained removals and tombstones preserve identity, links and chord availability',()=>{
  const row=base.registrations.find(r=>r.sectionId==='adoration'),settings={adoration:{title:'Adoration service',removed:true,moveTo:'ash'},ash:{title:'Ash',removed:true,moveTo:'holy'}};
  const moved=catalogView(base,[],[],settings),kept=moved.registrations.find(r=>r.id===row.id);
  assert.equal(moved.registrations.length,458);assert.equal(kept.sectionId,'holy');
  assert.deepEqual(kept.links,row.links);assert.equal(kept.rawSectionId,'adoration');
  assert.ok(!moved.sections.some(s=>['adoration','ash'].includes(s.id)));
  assert.ok(chordState([{id:'adoration',songs:[{name:row.name}]}],{},settings).available(kept));
  assert.ok(!catalogView(base,[{...row,deleted:true}]).registrations.some(r=>r.id===row.id));
});
test('filters respect host overrides and combine search, files and sheet availability',()=>{
  const row=catalog.registrations.find(r=>r.links.length),enabled=chordState([], {[row.id]:{mode:'enabled'}});
  assert.deepEqual(filterRows(catalog,enabled,{chord:'with'}).map(r=>r.id),[row.id]);
  assert.equal(filterRows(catalog,enabled,{chord:'without'}).length,457);
  assert.equal(filterRows(catalog,enabled,{chord:'with',links:'without'}).length,0);
  assert.equal(filterRows(catalog,enabled,{query:row.name,links:'with',chord:'with'}).length,1);
});
test('hymn parsing keeps repeated songs, sections and order; ambiguous and unknown songs require choices',()=>{
  const text='ENTRANCE\n1. Beautiful Sunday\n2. Beautiful Sunday\nHoly: Aicha\nNever-heard hymn';
  const requests=parseHymns(text,catalog.sections);
  assert.equal(requests.length,4);assert.equal(requests[2].sectionId,'holy');
  const result=matchHymns({text,choices:{}},catalog,chords);
  assert.ok(result[0].selected);assert.equal(result[3].status,'Not available');
  const copy={...catalog,registrations:[...catalog.registrations,{...result[0].selected,id:'copy'}]};
  assert.equal(matchHymns({text:'Beautiful Sunday'},copy,chords)[0].status,'Choose a registration');
  assert.equal(matchHymns({text:'Beautiful Sunday',choices:{1:''}},catalog,chords)[0].status,'Not available');
});
test('hard-copy retrieval uses sheet names once, retains hymn positions, and respects disabled choices',()=>{
  const matches=matchHymns({text:'Entrance: Beautiful Sunday\nBeautiful Sunday'},catalog,chords);
  const found=retrieval(matches,chords);
  assert.equal(found.songs.length,1);assert.deepEqual(found.songs[0].positions,[1,2]);
  const disabled=chordState(sheet.sections,{[matches[0].selected.id]:{mode:'disabled'}});
  assert.equal(retrieval(matches,disabled).songs.length,0);
});
test('validation prevents duplicate names, arbitrary links, invalid music settings and guest replacement',()=>{
  assert.throws(()=>sectionTitle(' ENTRANCE ',catalog.sections),/already exists/);
  const row=catalog.registrations[0];
  for(const patch of [{tempo:0},{transpose:99},{links:[{title:'Bad',url:'javascript:alert(1)'}]},{links:[{title:'Bad',url:'https://drive.google.com.evil.test/a'}]}])
    assert.throws(()=>validateRegistration({...row,...patch},catalog,row.id));
  assert.ok(guestChange({title:'List',text:'Original'},{title:'List',text:'Original\nNew'}));
  assert.ok(!guestChange({title:'List',text:'Original'},{title:'List',text:''}));
});

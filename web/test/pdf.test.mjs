import test from 'node:test';
import assert from 'node:assert/strict';
import {PDFDocument} from 'pdf-lib';
import {hymnResources,chordState} from '../src/logic.js';
import {pdfSources,driveFile,combinePdfs,downloadDrivePdf,pdfFilename} from '../src/pdf-core.js';

const row=(id,links=[])=>({id,name:id,sectionId:'entrance',links});
const match=(position,selected)=>({request:{position,name:'Requested '+position},selected,status:'Not available'});
const link=id=>({title:id,url:`https://drive.google.com/file/d/${id}/view`});
test('one hymn list partitions into exactly three groups, respecting host overrides and unresolved hymns',()=>{
  const yes=row('printed',[link('file_printed_123')]),pending=row('pending'),no=row('no'),file=row('file',[link('file_drive_123')]);
  const chords=chordState([{id:'entrance',songs:[{name:'printed'},{name:'file'}]}],{pending:{mode:'enabled'},file:{mode:'disabled'}});
  const groups=hymnResources([match(1,yes),match(2,no),match(3,file),match(4,null),match(5,pending),match(6,yes)],chords);
  assert.deepEqual(groups.offline.map(m=>m.request.position),[1,5,6]);
  assert.deepEqual(groups.missing.map(m=>m.request.position),[2,4]);
  assert.deepEqual(groups.drive.map(m=>m.request.position),[3]);
  assert.deepEqual(groups.songs[0].positions,[1,6]);assert.match(groups.pending[0],/Waiting for chord-list sync/);
});
test('PDF sources preserve first-use file order and collect repeated positions across link formats',()=>{
  const a=link('first_file_123'),b=link('second_file_123');
  const list=pdfSources([match(1,row('one',[a,b])),match(4,row('two',[{title:'Alias',url:'https://drive.google.com/open?id=first_file_123'}]))]);
  assert.deepEqual(list.map(f=>f.id),['first_file_123','second_file_123']);assert.deepEqual(list[0].positions,[1,4]);
  assert.throws(()=>driveFile('https://example.com/file/d/first_file_123'));
  assert.throws(()=>driveFile('https://drive.google.com/drive/folders/first_file_123'));
  assert.match(pdfFilename('../../Sunday:Mass'),/^SundayMass/);
});
async function pdf(width,pages=1){const d=await PDFDocument.create();for(let i=0;i<pages;i++)d.addPage([width,300]);return d.save();}
test('merging preserves page order, reports broken sources, and never silently labels partial output complete',async()=>{
  const one=await pdf(201),two=await pdf(202,2),sources=['one','broken','two'].map(id=>({id,title:id,positions:[1]}));
  const result=await combinePdfs(sources,{load:async s=>s.id==='one'?one:s.id==='two'?two:new Uint8Array([0,1,2])});
  assert.equal(result.failures.length,1);assert.equal(result.failures[0].id,'broken');assert.equal(result.included.length,2);
  const merged=await PDFDocument.load(result.bytes);assert.deepEqual(merged.getPages().map(p=>p.getWidth()),[201,202,202]);
  assert.equal(result.pages,3);
  const allFailed=await combinePdfs([sources[1]],{load:async()=>{throw Error('Private file');}});assert.equal(allFailed.bytes,null);
});
test('cancellation and collection limits prevent an unbounded merge',async()=>{
  await assert.rejects(combinePdfs(Array.from({length:61},()=>({})),{load:async()=>pdf(1)}),/60/);
  const abort=new AbortController();abort.abort();
  await assert.rejects(combinePdfs([{}],{load:async()=>pdf(1),signal:abort.signal}),{name:'AbortError'});
  const tooMany=await combinePdfs([{id:'large'}],{load:async()=>pdf(200,301)});
  assert.equal(tooMany.bytes,null);assert.match(tooMany.failures[0].error,/300 pages/);
});
test('native Docs use PDF export while raw PDFs use media, with public-only requests and size checks',async()=>{
  const bytes=await pdf(210),calls=[];
  for(const mimeType of ['application/pdf','application/vnd.google-apps.document']){
    const fetcher=async(url,options)=>{calls.push({url,options});return url.includes('fields=')?Response.json({mimeType,capabilities:{canDownload:true}}):new Response(bytes)};
    assert.deepEqual(await downloadDrivePdf({id:'sample_file_123'},'public-key',100000,null,fetcher),bytes);
  }
  assert.ok(calls[1].url.includes('alt=media'));assert.ok(calls[3].url.includes('/export?mimeType=application%2Fpdf'));
  assert.ok(calls.every(c=>c.options.credentials==='omit'&&!c.options.headers.Authorization));
  await assert.rejects(downloadDrivePdf({id:'x'},'k',100000,null,async()=>Response.json({mimeType:'image/png'})),/Only PDF/);
  await assert.rejects(downloadDrivePdf({id:'x'},'k',100000,null,async()=>new Response('',{status:403})),/private/);
});

import { readFile } from 'node:fs/promises';
import { after, before, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, setDoc, updateDoc, deleteDoc, serverTimestamp, writeBatch } from 'firebase/firestore';

let env;
const projectId = 'demo-the-choir';
before(async () => {
  env = await initializeTestEnvironment({projectId, firestore: {rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8')}});
});
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    await setDoc(doc(ctx.firestore(), 'hosts/host'), {active:true});
    await setDoc(doc(ctx.firestore(), 'hosts/revoked'), {active:false});
  });
});
after(async () => { await env?.cleanup(); });
const data = (uid = 'host') => ({id:'song', sectionId:'entrance', name:'Test hymn', style:'CountryWaltz', tempo:90, transpose:-2,
  links:[{title:'Score',url:'https://drive.google.com/file/d/test/view'}], deleted:false, revision:1, updatedBy:uid, updatedAt:serverTimestamp()});
const dbFor = uid => uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();

const setting=(change={})=>({title:'Adoration service',removed:false,moveTo:'',revision:1,updatedBy:'host',updatedAt:serverTimestamp(),...change});
test('hosts may rename original and custom other sections; guests and main sections are protected', async()=>{
  const db=dbFor('host'),id='section-'+'b'.repeat(64);
  await setDoc(doc(db,'sections',id),{id,title:'Custom',other:true,createdBy:'host',createdAt:serverTimestamp()});
  for(const section of ['adoration',id]){
    for(const uid of [null,'viewer','revoked'])await assertFails(setDoc(doc(dbFor(uid),'sectionSettings',section),setting({updatedBy:uid})));
    await assertSucceeds(setDoc(doc(db,'sectionSettings',section),setting()));
    await assertFails(updateDoc(doc(db,'sectionSettings',section),setting({title:'Stale',revision:1})));
    await assertSucceeds(updateDoc(doc(db,'sectionSettings',section),setting({title:'Renamed',revision:2})));
  }
  for(const id of ['entrance','holy','log','unknown'])await assertFails(setDoc(doc(db,'sectionSettings',id),setting()));
  await assertSucceeds(getDocs(collection(dbFor(null),'sectionSettings')));
});
test('section removal preserves rows, rejects new writes to removed sections, and cannot resurrect or form cycles',async()=>{
  const db=dbFor('host');
  await setDoc(doc(db,'registrations/song'),{...data(),sectionId:'adoration'});
  for(const change of [{moveTo:''},{moveTo:'adoration'},{moveTo:'missing'},{extra:true},{title:' '},{revision:0}])
    await assertFails(setDoc(doc(db,'sectionSettings/adoration'),setting({removed:true,moveTo:'holy',...change})));
  await assertSucceeds(setDoc(doc(db,'sectionSettings/adoration'),setting({removed:true,moveTo:'ash'})));
  await assertSucceeds(getDoc(doc(dbFor(null),'registrations/song')));
  await assertFails(setDoc(doc(db,'registrations/new'),{...data(),id:'new',sectionId:'adoration'}));
  await assertFails(updateDoc(doc(db,'sectionSettings/adoration'),setting({revision:2})));
  await assertFails(deleteDoc(doc(db,'sectionSettings/adoration')));
  await assertFails(setDoc(doc(db,'sectionSettings/ash'),setting({removed:true,moveTo:'adoration'})));
  await assertSucceeds(setDoc(doc(db,'sectionSettings/ash'),setting({removed:true,moveTo:'holy'})));
  await assertSucceeds(updateDoc(doc(db,'registrations/song'),{sectionId:'holy',revision:2,updatedAt:serverTimestamp()}));
  const batch=writeBatch(db);
  batch.set(doc(db,'sectionSettings/sob'),setting({removed:true,moveTo:'we-believe'}));
  batch.set(doc(db,'sectionSettings/we-believe'),setting({removed:true,moveTo:'sob'}));
  await assertFails(batch.commit());
});

test('only hosts can create other sections; custom registrations require an existing section', async () => {
  const id='section-'+'a'.repeat(64);
  const section={id,title:'Easter Vigil',other:true,createdBy:'host',createdAt:serverTimestamp()};
  for(const uid of [null,'viewer','revoked'])
    await assertFails(setDoc(doc(dbFor(uid),'sections',id),{...section,createdBy:uid}));
  const ref=doc(dbFor('host'),'sections',id);
  for(const invalid of [{other:false},{title:''},{title:'x'.repeat(61)},{createdBy:'viewer'},{extra:true}])
    await assertFails(setDoc(ref,{...section,...invalid}));
  await assertFails(setDoc(doc(dbFor('host'),'sections','entrance'),{...section,id:'entrance'}));
  await assertFails(setDoc(doc(dbFor('host'),'registrations/song'),{...data(),sectionId:id}));
  await assertSucceeds(setDoc(ref,section));
  await assertSucceeds(getDocs(collection(dbFor(null),'sections')));
  await assertSucceeds(setDoc(doc(dbFor('host'),'registrations/song'),{...data(),sectionId:id}));
  await assertFails(setDoc(ref,section));
  await assertFails(deleteDoc(ref));
});

test('guests, viewers and revoked hosts cannot create, edit or remove registrations', async () => {
  await setDoc(doc(dbFor('host'), 'registrations/song'), data());
  for (const uid of [null,'viewer','revoked']) {
    const db = dbFor(uid);
    await assertFails(setDoc(doc(db,'registrations/new'), {...data(uid),id:'new'}));
    await assertFails(updateDoc(doc(db,'registrations/song'),{tempo:120, revision:2, updatedBy:uid,updatedAt:serverTimestamp()}));
    await assertFails(updateDoc(doc(db,'registrations/song'),{deleted:true, revision:2,updatedBy:uid,updatedAt:serverTimestamp()}));
    await assertFails(deleteDoc(doc(db,'registrations/song')));
  }
});
test('host can add, edit, move and remove with a persistent tombstone', async () => {
  const ref = doc(dbFor('host'),'registrations/song');
  await assertSucceeds(setDoc(ref,data()));
  await assertSucceeds(updateDoc(ref,{sectionId:'holy',tempo:110,transpose:3,revision:2,updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(ref,{deleted:true,revision:3,updatedAt:serverTimestamp()}));
  await assertFails(deleteDoc(ref));
});
test('registration reads are available to viewers but host list is private', async () => {
  await setDoc(doc(dbFor('host'),'registrations/song'),data());
  await assertSucceeds(getDocs(collection(dbFor(null),'registrations')));
  await assertSucceeds(getDoc(doc(dbFor('host'),'hosts/host')));
  await assertFails(getDoc(doc(dbFor('viewer'),'hosts/host')));
  await assertFails(getDocs(collection(dbFor('host'),'hosts')));
});
test('no client can grant or restore host privileges', async () => {
  for (const uid of [null,'viewer','host','revoked']) {
    await assertFails(setDoc(doc(dbFor(uid),`hosts/${uid || 'anonymous'}`),{active:true}));
  }
});
test('revocation immediately prevents further writes', async () => {
  const db = dbFor('host');
  await setDoc(doc(db,'registrations/song'),data());
  await env.withSecurityRulesDisabled(ctx => updateDoc(doc(ctx.firestore(),'hosts/host'),{active:false}));
  await assertFails(updateDoc(doc(db,'registrations/song'),{tempo:115,revision:2,updatedAt:serverTimestamp()}));
});
test('invalid fields, ranges, sections, links, revision and identity are rejected', async () => {
  for (const change of [{tempo:4},{tempo:501},{tempo:90.5},{transpose:13},{transpose:-13},{sectionId:'bad'},
    {name:''},{style:' '},{voice:'unexpected'},{id:'different'},{updatedBy:'viewer'},{revision:2},
    {updatedAt:new Date(0)},{links:[{title:'Bad',url:'https://drive.google.com.evil.test/file'}]},
    {links:[{title:'Bad',url:'javascript:alert(1)'}]},{links:Array(11).fill({title:'File',url:'https://docs.google.com/a'})}]) {
    await assertFails(setDoc(doc(dbFor('host'),'registrations/song'),{...data(),...change}));
  }
});
test('all 458 original registrations fit the server validation', async () => {
  const source = JSON.parse(await readFile(new URL('../../android/app/src/main/assets/registrations.json',import.meta.url),'utf8'));
  if(source.registrations.length !== 458) throw new Error('Unexpected source count');
  const db = dbFor('host');
  for (let i=0; i<source.registrations.length; i+=20) {
    await Promise.all(source.registrations.slice(i,i+20).map(row => assertSucceeds(setDoc(doc(db,`registrations/${row.id}`),{
      ...row,deleted:false,revision:1,updatedBy:'host',updatedAt:serverTimestamp()
    }))));
  }
});

test('only hosts can change chord controls; enabling requires a sheet append request', async () => {
  await setDoc(doc(dbFor('host'),'registrations/song'), data());
  const control = {mode:'enabled',revision:1,appendPending:true,name:'Test hymn',sectionId:'entrance',updatedBy:'host',updatedAt:serverTimestamp()};
  for (const uid of [null,'viewer','revoked']) {
    await assertFails(setDoc(doc(dbFor(uid),'chordOverrides/song'),{...control,updatedBy:uid}));
  }
  const ref = doc(dbFor('host'),'chordOverrides/song');
  await assertFails(setDoc(ref,{...control,appendPending:false}));
  await assertFails(setDoc(ref,{...control,name:'Another song'}));
  await assertFails(setDoc(ref,{...control,sectionId:'holy'}));
  await assertSucceeds(setDoc(ref,control));
  await assertSucceeds(getDoc(doc(dbFor(null),'chordOverrides/song')));
  await assertSucceeds(setDoc(ref,{...control,mode:'disabled',appendPending:false,revision:2}));
  await assertFails(setDoc(ref,{...control,revision:2}));
  await assertSucceeds(setDoc(ref,{...control,mode:'automatic',appendPending:false,revision:3}));
  await assertFails(deleteDoc(ref));
  await updateDoc(doc(dbFor('host'),'registrations/song'),{deleted:true,revision:2,updatedAt:serverTimestamp()});
  await assertFails(setDoc(ref,{...control,revision:4}));
});

test('sheet snapshots are public to read but no app user can overwrite sync data', async () => {
  for(const collection of ['chordCatalog','chordSync']) {
    for(const uid of [null,'viewer','host']) {
      await assertFails(setDoc(doc(dbFor(uid),collection,'current'),{sections:[]}));
      await assertSucceeds(getDoc(doc(dbFor(uid),collection,'current')));
    }
  }
});

test('published Android update is readable without login and no app user can publish releases', async () => {
  const release = {enabled:true, versionCode:5, versionName:'1.4', downloadUrl:'https://drive.google.com/file/d/test/view'};
  await env.withSecurityRulesDisabled(ctx => setDoc(doc(ctx.firestore(),'appReleases/android'),release));
  for (const uid of [null,'viewer','host','revoked']) {
    const db = dbFor(uid);
    const ref = doc(db,'appReleases/android');
    await assertSucceeds(getDoc(ref));
    await assertFails(setDoc(ref,release));
    await assertFails(deleteDoc(ref));
    await assertFails(getDocs(collection(db,'appReleases')));
    await assertFails(getDoc(doc(db,'appReleases/unpublished')));
  }
});

test('only hosts can clear three shared hymn lists; everyone can read and stale edits fail', async () => {
  const plan = {title:'Sunday Mass',text:'Entrance: Beautiful Sunday\nUnknown hymn',choices:{'1':'song'},revision:1,updatedBy:'host',updatedAt:serverTimestamp()};
  const db=dbFor('host');
  for(const slot of ['1','2','3']) await assertSucceeds(setDoc(doc(db,'hymnLists',slot),plan));
  await assertFails(setDoc(doc(db,'hymnLists','4'),plan));
  await assertSucceeds(getDocs(collection(dbFor(null),'hymnLists')));
  for(const uid of [null,'viewer','revoked']) {
    const ref=doc(dbFor(uid),'hymnLists','1');
    await assertSucceeds(getDoc(ref));
    await assertFails(setDoc(ref,{...plan,text:'',choices:{},revision:2,updatedBy:uid}));
    await assertFails(deleteDoc(ref));
  }
  const ref=doc(db,'hymnLists','1');
  await assertFails(setDoc(ref,{...plan,text:'Stale edit'}));
  for(const invalid of [{title:''},{text:'x'.repeat(20001)},{choices:[]},{unexpected:true},{updatedBy:'viewer'}])
    await assertFails(setDoc(ref,{...plan,revision:2,...invalid}));
  await assertSucceeds(setDoc(ref,{...plan,title:'Hymn list 1',text:'',choices:{},revision:2}));
  await assertFails(deleteDoc(ref));
  await env.withSecurityRulesDisabled(ctx=>updateDoc(doc(ctx.firestore(),'hosts/host'),{active:false}));
  await assertFails(setDoc(ref,{...plan,revision:3}));
});

test('ordinary and anonymous users append hymns and choose registrations without erasing existing lines', async () => {
  for (const uid of ['viewer','revoked','anonymous-user']) {
    const db = uid === 'anonymous-user' ? env.authenticatedContext(uid,{firebase:{sign_in_provider:'anonymous'}}).firestore() : dbFor(uid);
    const ref = doc(db,'hymnLists','1');
    const original = {title:'Sunday Mass',text:'Entrance: Come (Lord) [1].*\nJesus 🙏',choices:{'1':'song'},revision:1,updatedBy:'host',updatedAt:serverTimestamp()};
    await env.withSecurityRulesDisabled(ctx => setDoc(doc(ctx.firestore(),'hymnLists','1'),original));
    const added = {...original,text:original.text+'\nCommunion: Trust in me',updatedBy:uid,revision:2};
    await assertSucceeds(setDoc(ref,added));
    const chosen = {...added, choices:{'1':'different','2':'song'},revision:3};
    await assertSucceeds(setDoc(ref,chosen));
    for (const text of ['', ' ', '\n\n', 'Replacement', original.text, chosen.text.replace('Come','Go'), chosen.text+'suffix'])
      await assertFails(setDoc(ref,{...chosen,text,revision:4}));
    await assertFails(setDoc(ref,{...chosen,title:'Renamed',revision:4}));
    await assertFails(setDoc(ref,{...chosen,updatedBy:'host',revision:4}));
    await assertFails(setDoc(ref,{...chosen,text:chosen.text+'\nAnother hymn',revision:3}));
    await assertFails(deleteDoc(ref));
    await assertFails(setDoc(doc(db,'hymnLists','4'),{...added,revision:1}));
    await assertFails(setDoc(doc(dbFor(null),'hymnLists','1'),{...chosen,revision:4,updatedBy:null}));
  }
});

test('guests can start or refill empty slots, while only hosts can reset filled lists', async () => {
  const db = dbFor('guest');
  const ref = doc(db,'hymnLists','1');
  const data = {title:'Hymn list 1',text:'',choices:{},revision:1,updatedBy:'guest',updatedAt:serverTimestamp()};
  await assertFails(setDoc(ref,data));
  await assertSucceeds(setDoc(ref,{...data,text:'First hymn'}));
  await assertSucceeds(setDoc(doc(dbFor('host'),'hymnLists','1'),{...data,updatedBy:'host',revision:2}));
  await assertSucceeds(setDoc(ref,{...data,text:'New service hymn',revision:3}));
  await assertFails(setDoc(ref,{...data,revision:4}));
});

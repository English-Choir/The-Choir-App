import { initializeApp } from 'firebase/app';
import { getAuth, onAuthStateChanged, signInWithEmailAndPassword, signInAnonymously, signOut, sendPasswordResetEmail, connectAuthEmulator } from 'firebase/auth';
import { initializeFirestore,memoryLocalCache,onSnapshot,collection,doc,runTransaction,serverTimestamp,connectFirestoreEmulator } from 'firebase/firestore';
import config from './firebase-config.json';
import bundled from './registrations.json';
import reviewed from './reviewed_chords.json';
import {catalogView,chordState,sectionTitle,validateRegistration,guestChange,parseHymns} from './logic.js';

// Emulator mode is compiled out of production. It can never target the live project.
const testing=import.meta.env.DEV&&new URLSearchParams(location.search).get('emulator')==='true';
const app=initializeApp(testing?{apiKey:'demo-key',projectId:'demo-the-choir',appId:'demo-web'}:config);
export const auth=getAuth(app);
// Keep offline public snapshots separately from Firebase's connection state.
// This avoids an IndexedDB/multi-tab initialization stall in WebKit.
export const db=initializeFirestore(app,{experimentalForceLongPolling:true,localCache:memoryLocalCache()});
if(testing){connectAuthEmulator(auth,'http://127.0.0.1:9199',{disableWarnings:true});connectFirestoreEmulator(db,'127.0.0.1',8180);}
const raw={rows:[],sections:[],settings:{},chords:reviewed.sections,overrides:{},plans:[],release:null};
const cacheKey='the-choir-public-v1';
if(!testing)try{
  const saved=JSON.parse(localStorage.getItem(cacheKey)||'null');
  if(saved?.projectId===config.projectId&&saved.version===1){
    for(const name of ['rows','sections','chords','plans'])if(Array.isArray(saved[name]))raw[name]=saved[name];
    for(const name of ['settings','overrides'])if(saved[name]&&typeof saved[name]==='object'&&!Array.isArray(saved[name]))raw[name]=saved[name];
    raw.release=saved.release||null;
  }
}catch{/* Private-storage restrictions must never prevent online browsing. */}
const state={user:null,host:false,authReady:false,catalog:catalogView(bundled,[]),chords:chordState(reviewed.sections,{}),plans:[],release:null,online:navigator.onLine,synced:false,health:null,errors:new Set()};
const listeners=new Set();let hostUnsubscribe=()=>{},hostGeneration=0;
const snapshots=new Map();
function emit(){
  state.catalog=catalogView(bundled,raw.rows,raw.sections,raw.settings);
  state.chords=chordState(raw.chords,raw.overrides,raw.settings);
  state.plans=['1','2','3'].map(slot=>raw.plans.find(p=>p.slot===slot)||{slot,title:'Hymn list '+slot,text:'',choices:{},revision:0});
  state.release=raw.release;state.online=navigator.onLine;
  state.synced=['registrations','sections','sectionSettings','chordCatalog'].every(k=>snapshots.get(k)===false);
  listeners.forEach(fn=>fn(state));
}
export function subscribe(fn){listeners.add(fn);fn(state);return()=>listeners.delete(fn);}
export const getState=()=>state;
function watch(name,ref,receive){
  return onSnapshot(ref,{includeMetadataChanges:true},snap=>{
    // Empty memory-cache startup events must not erase the last server snapshot.
    if(!snap.metadata.fromCache){
      receive(snap);
      if(!testing)try{localStorage.setItem(cacheKey,JSON.stringify({version:1,projectId:config.projectId,...raw}));}catch{/* The live view remains usable if browser storage is full. */}
    }
    snapshots.set(name,snap.metadata.fromCache);state.errors.delete(name);emit();
  },()=>{state.errors.add(name);emit();});
}
watch('registrations',collection(db,'registrations'),s=>{raw.rows=s.docs.map(d=>({...d.data(),id:d.id}));});
watch('sections',collection(db,'sections'),s=>{raw.sections=s.docs.map(d=>({...d.data(),id:d.id})).sort((a,b)=>a.title.localeCompare(b.title));});
watch('sectionSettings',collection(db,'sectionSettings'),s=>{raw.settings=Object.fromEntries(s.docs.map(d=>[d.id,d.data()]));});
watch('chordCatalog',doc(db,'chordCatalog/current'),s=>{if(s.exists())raw.chords=s.data().sections;});
watch('chordOverrides',collection(db,'chordOverrides'),s=>{raw.overrides=Object.fromEntries(s.docs.map(d=>[d.id,d.data()]));});
watch('hymnLists',collection(db,'hymnLists'),s=>{raw.plans=s.docs.map(d=>({...d.data(),slot:d.id}));});
watch('release',doc(db,'appReleases/android'),s=>{raw.release=s.exists()?s.data():null;});
watch('health',doc(db,'chordSync/status'),s=>{state.health=s.exists()?s.data():null;});
onAuthStateChanged(auth,user=>{
  hostUnsubscribe();const generation=++hostGeneration;
  state.user=user;state.host=false;state.authReady=true;emit();
  if(user&&!user.isAnonymous)hostUnsubscribe=onSnapshot(doc(db,'hosts',user.uid),d=>{
    if(generation===hostGeneration){state.host=d.data()?.active===true;emit();}
  },()=>{if(generation===hostGeneration){state.host=false;emit();}});
});
addEventListener('online',emit);addEventListener('offline',emit);
function connected(){if(!navigator.onLine)throw Error('Connect to the internet to save changes.');}
export async function login(email,password){connected();await signInWithEmailAndPassword(auth,email.trim(),password);}
export async function logout(){await signOut(auth);}
export async function resetPassword(email){connected();await sendPasswordResetEmail(auth,email.trim());}
async function host(tx){
  const user=auth.currentUser;
  if(!user||!state.host)throw Error('Host sign-in is required.');
  const membership=await tx.get(doc(db,'hosts',user.uid));
  if(!membership.data()?.active||auth.currentUser?.uid!==user.uid||!state.host)throw Error('Host access has ended. Sign in again.');
  return user.uid;
}
export async function addSection(value){
  connected();const title=sectionTitle(value,state.catalog.sections);
  const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(title.toLowerCase()));
  const id='section-'+[...new Uint8Array(bytes)].map(v=>v.toString(16).padStart(2,'0')).join('');
  await runTransaction(db,async tx=>{
    const uid=await host(tx),ref=doc(db,'sections',id),existing=await tx.get(ref);
    if(existing.exists())throw Error('This section name has already been used. Choose a different name.');
    tx.set(ref,{id,title,other:true,createdBy:uid,createdAt:serverTimestamp()});
  });return id;
}
export async function changeSection(section,value,moveTo=null,expectedRevision=0){
  connected();if(!section.other)throw Error('Only Other registrations sections can be changed.');
  const title=sectionTitle(value,state.catalog.sections,section.id);
  if(moveTo!==null&&!state.catalog.sections.some(s=>s.id===moveTo&&s.id!==section.id))throw Error('Choose a destination section.');
  await runTransaction(db,async tx=>{
    const uid=await host(tx),ref=doc(db,'sectionSettings',section.id),old=await tx.get(ref);
    const target=moveTo?await tx.get(doc(db,'sectionSettings',moveTo)):null;
    if((old.data()?.revision||0)!==expectedRevision||old.data()?.removed)throw Error('This section changed. Reopen its controls.');
    if(target?.data()?.removed)throw Error('The destination was removed. Choose another section.');
    tx.set(ref,{title,removed:moveTo!==null,moveTo:moveTo||'',revision:expectedRevision+1,updatedBy:uid,updatedAt:serverTimestamp()});
  });
}
export async function saveRegistration(input,original=null,deleted=false){
  connected();const id=original?.id||'custom-'+crypto.randomUUID(),row=validateRegistration(input,state.catalog,id);
  await runTransaction(db,async tx=>{
    const uid=await host(tx),ref=doc(db,'registrations',id),old=await tx.get(ref);
    if((old.data()?.revision||0)!==(original?.revision||0)||old.data()?.deleted)throw Error('This registration changed. Close and reopen the editor.');
    tx.set(ref,{...row,deleted,revision:(old.data()?.revision||0)+1,updatedBy:uid,updatedAt:serverTimestamp()});
  });return id;
}
export async function setChord(row,mode,expectedRevision){
  connected();
  await runTransaction(db,async tx=>{
    const uid=await host(tx),ref=doc(db,'chordOverrides',row.id),old=await tx.get(ref),reg=await tx.get(doc(db,'registrations',row.id));
    if(!reg.exists()||reg.data().deleted||reg.data().name!==row.name)throw Error('This registration changed. Reopen its controls.');
    if((old.data()?.revision||0)!==expectedRevision)throw Error('Another host changed this setting. Reopen its controls.');
    tx.set(ref,{mode,revision:expectedRevision+1,appendPending:mode==='enabled',name:reg.data().name,sectionId:reg.data().sectionId,updatedBy:uid,updatedAt:serverTimestamp()});
  });
}
let guestSignIn;
export async function savePlan(plan,expectedRevision){
  connected();
  if(!['1','2','3'].includes(plan.slot)||!plan.title.trim()||plan.title.length>80||plan.text.length>20000||parseHymns(plan.text,state.catalog.sections).length>200)throw Error('Use a title up to 80 characters and up to 200 hymns per list.');
  if(!auth.currentUser){
    guestSignIn??=signInAnonymously(auth).finally(()=>{guestSignIn=null;});
    await guestSignIn;
  }
  const uid=auth.currentUser.uid;
  await runTransaction(db,async tx=>{
    const ref=doc(db,'hymnLists',plan.slot),old=await tx.get(ref),membership=await tx.get(doc(db,'hosts',uid));
    const before=old.data()||{title:'Hymn list '+plan.slot,text:'',revision:0};
    if((before.revision||0)!==expectedRevision)throw Error('Someone changed this list. Close and reopen it to use the latest version.');
    if(!membership.data()?.active&&!guestChange(before,plan))throw Error('Only hosts can remove hymns, rename or clear a list.');
    tx.set(ref,{title:plan.title,text:plan.text,choices:plan.choices||{},revision:expectedRevision+1,updatedBy:uid,updatedAt:serverTimestamp()});
  });
}
export function friendlyError(e){
  const code=e?.code||'';
  if(/invalid-credential|wrong-password|user-not-found/.test(code))return 'The email or app password is incorrect.';
  if(code==='auth/too-many-requests')return 'Too many attempts. Please wait a little and try again.';
  if(/network|unavailable/.test(code))return 'Connection unavailable. Your changes have not been saved.';
  if(/permission-denied/.test(code))return 'Permission was denied. Refresh the page and check your host access.';
  if(code==='auth/invalid-email')return 'Enter a valid email address.';
  return e?.message?.startsWith('Firebase:')?'Could not complete this action. Please try again.':e?.message||'Could not save. Please try again.';
}

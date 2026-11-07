import './styles.css';
import hymnCatalog from './hymn-catalog.json';
import {subscribe,getState,login,logout,resetPassword,addSection,changeSection,saveRegistration,setChord,savePlan,friendlyError} from './data.js';
import {filterRows,matchHymns,parseHymns,hymnResources} from './logic.js';

const $=s=>document.querySelector(s);
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icons={
 wave:'<path d="M3 10v4m4-8v12m5-16v20m5-16v12m4-8v4"/>',
 grid:'<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
 library:'<path d="M4 4v16m5-16v16m5-16v16m4-16 3 16"/>',
 list:'<path d="M9 5h12M9 12h12M9 19h12M3 5h1M3 12h1M3 19h1"/>',
 search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
 lock:'<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2"/>',
 arrow:'<path d="m9 5 7 7-7 7"/>',
 back:'<path d="m14 5-7 7 7 7"/>',
 plus:'<path d="M12 5v14M5 12h14"/>',
 folder:'<path d="M3 6a2 2 0 0 1 2-2h5l2 3h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
 link:'<path d="m10 13 4-4m-6 5-2 2a4 4 0 0 0 6 6l4-4m0-8 2-2a4 4 0 0 0-6-6L8 6"/>',
 edit:'<path d="m15 4 5 5M3 21l5-1L21 7a2 2 0 0 0-5-5L3 15z"/>',
 music:'<path d="M9 18V5l12-3v13M9 8l12-3"/><ellipse cx="6" cy="18" rx="3" ry="2"/><ellipse cx="18" cy="15" rx="3" ry="2"/>',
 copy:'<rect x="8" y="8" width="13" height="13" rx="2"/><path d="M16 8V3H3v13h5"/>',
 hymnal:'<path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1-2.5-2.5Z"/><path d="M6 6h10M6 10h10M6 14h6"/>'
};
const icon=(name,cls='')=>`<svg class="icon ${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name]||icons.music}</svg>`;
let state=getState(),query='',chordFilter='all',linkFilter='all',limit=60,toastTimer,modalHost=false,hadHost=false;
let route=readRoute();
function readRoute(){const [page='sections',section='']=location.hash.slice(1).split('/');return {page,section:decodeURIComponent(section)};}
function navigate(path){location.hash=path;}
addEventListener('hashchange',()=>{route=readRoute();query='';chordFilter='all';linkFilter='all';limit=60;render();window.scrollTo(0,0);});
function notify(text){$('#toast').textContent=text;$('#toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').classList.remove('visible'),5000);}
const titleFor=id=>state.catalog.sections.find(s=>s.id===id)?.title||'Other registrations';
const action=(name,label,attrs='',cls='secondary')=>`<button type="button" class="${cls}" data-action="${name}" ${attrs}>${label}</button>`;
function navigation(where){
  return `<nav class="${where}" aria-label="Main navigation">${[
    ['sections','grid','Sections'],['library','library','All registrations'],['hymns','list','Hymn lists'],['allhymns','hymnal','All Hymns']
  ].map(([page,symbol,label])=>`<a href="#${page}" ${route.page===page?'aria-current="page"':''}>${icon(symbol)}<span>${label}</span></a>`).join('')}</nav>`;
}
function searchBar(){
  return `<div class="search">${icon('search')}<input id="search" type="search" placeholder="Search registrations or styles" aria-label="Search registrations or styles" value="${esc(query)}" autocomplete="off"></div>`;
}
function stats(){
  return `<div class="stats"><div><strong>${state.catalog.registrations.length}</strong><span>Registrations</span></div><div><strong>${state.catalog.registrations.filter(r=>r.links.length).length}</strong><span>Drive links</span></div><div><strong class="amber-text">${state.catalog.registrations.filter(state.chords.available).length}</strong><span>Offline Chords</span></div></div>`;
}
function sectionTile(s,i){
  const rows=state.catalog.registrations.filter(r=>r.sectionId===s.id);
  return `<div class="section-wrap"><a class="section-tile" href="#section/${encodeURIComponent(s.id)}">
    <div class="tile-top"><span class="ordinal">${String(i+1).padStart(2,'0')}</span>${icon('arrow')}</div>
    <h3>${esc(s.title)}</h3><span>${rows.length} registrations</span>
    <div class="tile-foot"><i class="tiny-dot ${rows.some(state.chords.available)?'lit':''}"></i>${rows.filter(state.chords.available).length} with chords</div></a>
    ${s.other&&state.host?action('manage-section',icon('edit')+' Rename / remove',`data-id="${esc(s.id)}"`,'text-button section-manage'):''}</div>`;
}
function home(){
  const main=state.catalog.sections.filter(s=>!s.other);
  return `<div class="eyebrow">THE SERVICE, IN HARMONY</div><div class="hero"><div><h1>Your library<span class="blue">.</span></h1><p>Ready for every service.</p></div><div class="hero-mark">${icon('wave')}</div></div>
    ${searchBar()}<p class="count-line"><i class="tiny-dot lit"></i>${state.catalog.registrations.filter(state.chords.available).length} registrations with Offline Chords</p>
    ${query?registrationResults():`${stats()}<div class="section-heading"><h2>Order of service</h2><span>13 SECTIONS</span></div><div class="section-grid">${main.map(sectionTile).join('')}</div>
    <a class="other-banner" href="#other">${icon('folder')}<div><h3>Other registrations</h3><p>${state.catalog.sections.filter(s=>s.other).length} sections for every occasion</p></div>${icon('arrow')}</a>`}`;
}
function other(){
  return `<a class="back-link" href="#sections">${icon('back')} Your library</a><div class="page-heading"><div><div class="eyebrow">BEYOND THE ORDER OF SERVICE</div><h1>Other registrations</h1><p>Every occasion has its own music.</p></div>${state.host?action('add-section',icon('plus')+' Add section','','primary'):''}</div>
    <div class="section-grid">${state.catalog.sections.filter(s=>s.other).map(sectionTile).join('')}</div>`;
}
function filters(){
  return `<div class="filters"><label>Chords<select id="chord-filter"><option value="all">All hymns</option><option value="with" ${chordFilter==='with'?'selected':''}>With Offline Chords</option><option value="without" ${chordFilter==='without'?'selected':''}>Without Offline Chords</option></select></label>
    <label>Drive files<select id="link-filter"><option value="all">All registrations</option><option value="with" ${linkFilter==='with'?'selected':''}>With links</option><option value="without" ${linkFilter==='without'?'selected':''}>Needs link</option></select></label>
    ${query||chordFilter!=='all'||linkFilter!=='all'?action('clear-filters','Clear filters','','text-button'):''}</div>`;
}
function chordIndicator(r){
  return `<span class="chord-badge ${state.chords.available(r)?'available':''}"><i></i>Offline Chords</span>`;
}
function registrationCard(r,compact=false){
  const files=r.links.map(l=>`<a class="file-link" href="${esc(l.url)}" target="_blank" rel="noopener noreferrer">${icon('link')}${esc(r.links.length===1?'Open Drive file':l.title)}<span aria-hidden="true">↗</span></a>`).join('');
  return `<article class="registration ${compact?'compact':''}" data-registration="${esc(r.id)}">
    <div class="registration-head"><div><div class="eyebrow">${esc(titleFor(r.sectionId))}</div><h3>${esc(r.name)}</h3></div>${state.host&&!compact?action('edit-registration',icon('edit'),`data-id="${esc(r.id)}" aria-label="Edit ${esc(r.name)}"`,'icon-button'):''}</div>
    ${state.host&&compact?action('edit-registration',icon('edit')+' Edit registration',`data-id="${esc(r.id)}" aria-label="Edit ${esc(r.name)}"`,'text-button'):''}
    <div class="music-settings"><div class="style"><span>STYLE</span><strong>${esc(r.style)}</strong></div><div><span>TEMPO</span><strong>${r.tempo}<small> BPM</small></strong></div><div><span>TRANSPOSE</span><strong>${r.transpose>0?'+':''}${r.transpose}</strong></div></div>
    <div class="chord-row">${chordIndicator(r)}${state.host?action('chord-control','Change',`data-id="${esc(r.id)}"`,'text-button'):''}</div>
    <div class="file-row">${files||(state.host?action('edit-registration',icon('plus')+' Add Drive link',`data-id="${esc(r.id)}"`,'text-button'):'<span class="muted small">No Drive file attached</span>')}</div></article>`;
}
function registrationResults(){
  const rows=filterRows(state.catalog,state.chords,{query,section:route.page==='section'?route.section:'',chord:chordFilter,links:linkFilter});
  return `${filters()}<div class="result-heading"><span>${rows.length} REGISTRATIONS</span>${state.host?action('add-registration',icon('plus')+' Add registration','','primary'):''}</div>
    ${rows.length?`<div class="registration-grid">${rows.slice(0,limit).map(r=>registrationCard(r)).join('')}</div>${rows.length>limit?action('more',`Show more (${rows.length-limit} remaining)`,'','secondary load-more'):''}`:empty('No registrations found','Try another name or clear your filters.',action('clear-filters','Clear filters'))}`;
}
function library(){
  const section=route.page==='section'?state.catalog.sections.find(s=>s.id===route.section):null;
  if(route.page==='section'&&!section)return empty('This section has moved','Refresh your sections to find its registrations.','<a class="primary" href="#sections">Open sections</a>');
  return `${section?`<a class="back-link" href="#${section.other?'other':'sections'}">${icon('back')} ${section.other?'Other registrations':'Your library'}</a>`:''}
    <div class="page-heading"><div><div class="eyebrow">${section?'YOUR REGISTRATIONS':'THE COMPLETE COLLECTION'}</div><h1>${esc(section?.title||'All registrations')}</h1></div>
    ${section?.other&&state.host?action('manage-section','Rename / remove',`data-id="${esc(section.id)}"`):''}</div>
    ${searchBar()}<p class="count-line"><i class="tiny-dot lit"></i>${state.catalog.registrations.filter(r=>(!section||r.sectionId===section.id)&&state.chords.available(r)).length} registrations with Offline Chords</p>${registrationResults()}`;
}
function empty(title,body,buttons=''){return `<div class="empty">${icon('music')}<h3>${esc(title)}</h3><p>${esc(body)}</p>${buttons}</div>`;}
function plan(){return state.plans.find(p=>p.slot===(route.section||'1'))||state.plans[0];}
function resourceSummary(matches){
  const groups=hymnResources(matches,state.chords);
  const row=m=>`<li><strong>${m.request.position}. ${esc(m.selected?.name||m.request.name)}</strong><span>${m.selected?esc(titleFor(m.selected.sectionId)):esc(m.status)+' · no registration selected'}</span></li>`;
  return `<section class="resource-summary"><div class="eyebrow">RESOURCES FOR THIS HYMN LIST</div><h2>Ready for the service</h2><div class="resource-groups">
    <section class="retrieval resource-offline" data-resource="offline"><div class="eyebrow amber-text">01 · OFFLINE CHORDS</div><h2>Hard-copy chords</h2><p>Use these exact chord-sheet names. Repeated entries appear once.</p>
      ${groups.songs.length?`<ol>${groups.songs.map(({song,positions})=>`<li><strong>${esc(song.name)}</strong><span>${esc(titleFor(song.sectionId))} · hymns ${positions.join(', ')}</span></li>`).join('')}</ol>`:'<p class="muted">No matched chord-sheet names.</p>'}
      ${groups.pending.map(s=>`<p class="small">${esc(s)}</p>`).join('')}
      <div class="copy-buttons">${action('copy-chords',icon('copy')+' Copy chord names')}</div></section>
    <section class="retrieval" data-resource="missing"><div class="eyebrow">02 · NO CHORDS OR FILES</div><h2>Without offline chords or Drive links</h2><p>${groups.missing.length} hymns need resources or a registration choice.</p>
      ${groups.missing.length?`<ol class="hymn-resource-rows">${groups.missing.map(row).join('')}</ol>`:'<p class="muted">None in this list.</p>'}</section>
    <section class="retrieval" data-resource="drive"><div class="eyebrow blue">03 · DRIVE FILES</div><h2>Without offline chords, with Drive links</h2><p>${groups.drive.length} hymns. Collect their attached files into one PDF in hymn order.</p>
      ${groups.drive.length?`<ol class="hymn-resource-rows">${groups.drive.map(m=>`<li><strong>${m.request.position}. ${esc(m.selected.name)}</strong><span>${esc(titleFor(m.selected.sectionId))}</span>${m.selected.links.map(l=>`<a class="resource-file" href="${esc(l.url)}" target="_blank" rel="noopener">${esc(l.title)} ↗</a>`).join('')}</li>`).join('')}</ol>
      ${action('collect-pdfs','Create combined PDF','','primary')}`:'<p class="muted">None in this list.</p>'}</section>
    </div><div class="copy-buttons">${action('copy-plan','Copy full list','','secondary')}</div></section>`;
}
function hymnPage(){
  const current=plan();if(!current)return '';
  const matches=matchHymns(current,state.catalog,state.chords);
  return `<div class="eyebrow">PREPARE TOGETHER</div><div class="page-heading"><div><h1>Hymn lists<span class="blue">.</span></h1><p>Three shared lists. One choir, always in sync.</p></div></div>
    <div class="list-tabs" role="navigation" aria-label="Shared lists">${state.plans.map(p=>`<a href="#hymns/${p.slot}" class="${p.slot===current.slot?'selected':''}"><span>LIST ${p.slot}</span>${esc(p.title)}</a>`).join('')}</div>
    <div class="plan-toolbar"><div><h2>${esc(current.title)}</h2><p>${matches.length} hymns · ${matches.filter(m=>m.selected).length} registrations found</p></div><div class="button-group">
    ${action('add-hymns',icon('plus')+' Add hymns','','primary')}${state.host?action('edit-hymns','Edit list')+action('clear-plan','Clear list','','danger-outline'):''}</div></div>
    ${matches.length?`<div class="plan-layout"><div class="plan-hymns">${matches.map(m=>`<section class="hymn-item"><div class="hymn-top"><span class="hymn-number">${String(m.request.position).padStart(2,'0')}</span><div><h3>${esc(m.request.name)}</h3><span class="${m.selected?'muted':'unavailable'}">${esc(m.status)}</span></div></div>
      ${m.selected?registrationCard(m.selected,true):''}<div class="hymn-choice">${action('choose-registration',m.selected?'Change registration':'Find registration',`data-position="${m.request.position}"`)}</div></section>`).join('')}</div>
    ${resourceSummary(matches)}</div>`:
      empty('A service starts with a song','Add your hymns in service order. We’ll find the registrations and gather the chord names.',action('add-hymns',icon('plus')+' Add hymns','','primary'))}`;
}
function allHymnsPage(){
  const sec = route.section ? hymnCatalog.find(s=>s.id===route.section) : null;
  const q = query.trim().toLowerCase();

  if(sec){
    const list = q ? sec.hymns.filter(h=>h.toLowerCase().includes(q)) : sec.hymns;
    return `<a class="back-link" href="#allhymns">${icon('back')} All Hymns</a>
      <div class="page-heading"><div><div class="eyebrow">ORDER OF SERVICE HYMNS</div><h1>${esc(sec.title)}<span class="blue">.</span></h1><p>${sec.hymns.length} hymns in this section</p></div></div>
      <div class="search">${icon('search')}<input id="search" type="search" placeholder="Search hymns in ${esc(sec.title)}" aria-label="Search hymns" value="${esc(query)}" autocomplete="off"></div>
      ${list.length ? `<ol class="hymnal-list">${list.map((h,i)=>`<li><span class="hymn-num">${String(i+1).padStart(2,'0')}</span><span class="hymn-title">${esc(h)}</span></li>`).join('')}</ol>` : empty('No hymns found', 'Try another search term.', '')}`;
  }

  if(q){
    const allMatches = [];
    for(const s of hymnCatalog){
      for(const h of s.hymns){
        if(h.toLowerCase().includes(q)){
          allMatches.push({section: s.title, sectionId: s.id, hymn: h});
        }
      }
    }
    return `<div class="eyebrow">ORDER OF SERVICE HYMNS</div><div class="hero"><div><h1>All Hymns<span class="blue">.</span></h1><p>Every hymn across all 13 sections.</p></div><div class="hero-mark">${icon('hymnal')}</div></div>
      <div class="search">${icon('search')}<input id="search" type="search" placeholder="Search across all hymns" aria-label="Search across all hymns" value="${esc(query)}" autocomplete="off"></div>
      <div class="result-heading"><span>${allMatches.length} MATCHING HYMNS</span></div>
      ${allMatches.length ? `<ul class="hymnal-search-results">${allMatches.map(m=>`<li><a href="#allhymns/${encodeURIComponent(m.sectionId)}"><strong>${esc(m.hymn)}</strong><span class="badge">${esc(m.section)}</span></a></li>`).join('')}</ul>` : empty('No hymns found', 'Try another search term.', '')}`;
  }

  const totalHymns = hymnCatalog.reduce((sum, s) => sum + s.hymns.length, 0);
  return `<div class="eyebrow">ORDER OF SERVICE HYMNS</div><div class="hero"><div><h1>All Hymns<span class="blue">.</span></h1><p>Browse hymns by Mass section.</p></div><div class="hero-mark">${icon('hymnal')}</div></div>
    <div class="search">${icon('search')}<input id="search" type="search" placeholder="Search across all hymns" aria-label="Search across all hymns" value="${esc(query)}" autocomplete="off"></div>
    <div class="section-heading"><h2>Sections</h2><span>13 SECTIONS · ${totalHymns} HYMNS</span></div>
    <div class="hymnal-grid">${hymnCatalog.map((s, i)=>`<a class="hymnal-card" href="#allhymns/${encodeURIComponent(s.id)}">
      <div class="hymnal-card-top"><span class="ordinal">${String(i+1).padStart(2,'0')}</span>${icon('arrow')}</div>
      <h3>${esc(s.title)}</h3>
      <span class="hymnal-count">${s.hymns.length} hymns</span>
    </a>`).join('')}</div>`;
}
function render(){
  const active=document.activeElement,focus=active?.id,selection=active?.selectionStart,scroll=window.scrollY;
  const content=route.page==='other'?other():route.page==='hymns'?hymnPage():route.page==='allhymns'?allHymnsPage():['section','library'].includes(route.page)?library():home();
  $('#app').innerHTML=`<aside class="sidebar"><a class="brand" href="#sections"><span class="brand-icon">${icon('wave')}</span><span>THE CHOIR<small>READY FOR EVERY SERVICE</small></span></a>${navigation('desktop-nav')}
    <div class="sidebar-bottom"><div class="keyboard-label">${icon('music')} PSR-SX900 library</div><p>Music brings us together.</p>${action('install-help','Use on your home screen','','text-button')}</div></aside>
    <div class="workspace"><header class="topbar"><a class="mobile-brand" href="#sections">${icon('wave')}THE CHOIR</a><div class="connection"><i class="${state.online&&state.synced?'connected':''}"></i>${!state.online?'Offline · saved library':state.errors.size?'Connection needs attention':state.synced?'Library synced':'Connecting…'}</div><div class="topbar-actions">${state.release?.enabled?`<a class="android-link" href="${esc(state.release.downloadUrl)}" target="_blank" rel="noopener">Android app ↗</a>`:''}
    ${action('host',icon('lock')+`<span>${state.host?'Host controls':'Host login'}</span>`,'',state.host?'host-button active':'host-button')}</div></header>
    <main id="main">${!state.online?'<p class="offline-notice">Offline · showing saved data. Connect to save changes.</p>':''}${content}<footer>THE CHOIR <span>•</span> Shared music. Shared moments.</footer></main></div>${navigation('mobile-nav')}`;
  if(focus&&document.getElementById(focus)){const target=document.getElementById(focus);target.focus({preventScroll:true});if(selection!=null&&target.setSelectionRange)try{target.setSelectionRange(selection,selection);}catch{}}
  window.scrollTo(0,scroll);
}
function closeModal(){const dialog=$('#dialogs dialog');$('#app').inert=false;if(dialog){dialog.close();dialog.remove();}modalHost=false;}
function modal({title,body,save='Save',submit,hostOnly=false,mount}){
  closeModal();modalHost=hostOnly;
  $('#dialogs').innerHTML=`<dialog aria-labelledby="dialog-title"><form><div class="dialog-heading"><h2 id="dialog-title">${esc(title)}</h2><button class="icon-button" type="button" data-close aria-label="Close">×</button></div><div class="dialog-body">${body}</div><p class="form-error" role="alert" hidden></p><div class="dialog-actions"><button type="button" class="secondary" data-close>Cancel</button>${submit?`<button type="submit" class="primary">${esc(save)}</button>`:''}</div></form></dialog>`;
  const dialog=$('#dialogs dialog'),form=dialog.querySelector('form');dialog.showModal();
  $('#app').inert=true;
  dialog.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>closeModal());
  dialog.addEventListener('cancel',e=>{e.preventDefault();if(!form.dataset.saving)closeModal();});
  if(submit)form.onsubmit=async e=>{
    e.preventDefault();const data=new FormData(form),buttons=[...form.querySelectorAll('button,input,select,textarea')];
    form.dataset.saving='true';buttons.forEach(b=>b.disabled=true);const error=form.querySelector('.form-error');error.hidden=true;
    try{await submit(data,form);closeModal();}catch(e){delete form.dataset.saving;error.textContent=friendlyError(e);error.hidden=false;buttons.forEach(b=>b.disabled=false);}
  };
  mount?.(form);
}
const field=(label,name,value='',type='text',attrs='')=>`<label>${esc(label)}<input name="${name}" type="${type}" value="${esc(value)}" ${attrs}></label>`;
const select=(label,name,options,value)=>`<label>${esc(label)}<select name="${name}" required>${options.map(([id,label])=>`<option value="${esc(id)}" ${id===value?'selected':''}>${esc(label)}</option>`).join('')}</select></label>`;
function hostDialog(){
  if(state.host){modal({title:'Host controls',hostOnly:true,body:`<p class="signed-in">Signed in as <strong>${esc(state.user.email)}</strong></p><p>You can manage registrations, sections, chord availability and the three shared hymn lists.</p>${action('sign-out','Sign out','','secondary')}`});return;}
  modal({title:'Host login',save:'Sign in',body:`<p>Use your existing Choir host account and app password.</p>${field('Email','email',state.user?.email||'','email','required autocomplete="username"')}${field('App password','password','','password','required autocomplete="current-password"')}${action('reset-password','Set or reset app password','','text-button')}<p class="small muted">Everyone can browse and add hymns without signing in.</p>`,
    submit:async data=>{await login(data.get('email'),data.get('password'));notify('Signed in. Checking host access…');}});
}
function sectionDialog(section=null){
  const revision=section?state.catalog.settings[section.id]?.revision||0:0;
  if(section)modal({title:'Manage section',hostOnly:true,save:'Save name',body:`${field('Section name','title',section.title,'text','required maxlength="60"')}<p class="small muted">Changes sync to every device. Existing chord-sheet tabs are preserved.</p>${action('remove-section','Remove section',`data-id="${esc(section.id)}"`,'danger-outline')}`,
    submit:async data=>{await changeSection(section,data.get('title'),null,revision);notify('Section renamed for everyone.');}});
  else modal({title:'Add section',hostOnly:true,save:'Add section',body:`<p>Create a section under Other registrations.</p>${field('Section name','title','','text','required maxlength="60"')}`,
    submit:async data=>{const id=await addSection(data.get('title'));navigate('section/'+id);notify('Section added for everyone.');}});
}
function removeSectionDialog(section){
  const revision=state.catalog.settings[section.id]?.revision||0,count=state.catalog.registrations.filter(r=>r.sectionId===section.id).length;
  modal({title:`Remove ${section.title}?`,hostOnly:true,save:'Remove section',body:`<p>Keep this section’s <strong>${count} registrations</strong> in another section. Their settings, links and chord availability are preserved.</p>${select('Move registrations to','destination',[['','Choose a destination'],...state.catalog.sections.filter(s=>s.id!==section.id).map(s=>[s.id,s.title])],'')}<p class="small muted">The section will be removed for everyone. No Drive files or chord-sheet rows will be deleted.</p>`,
    submit:async data=>{await changeSection(section,section.title,data.get('destination'),revision);navigate('other');notify('Section removed. Registrations kept in the chosen section.');}});
}
function registrationDialog(original=null){
  const initial=original||{name:'',style:'',tempo:120,transpose:0,sectionId:route.page==='section'?route.section:'entrance',links:[]};
  modal({title:original?'Edit registration':'Add registration',hostOnly:true,save:original?'Save changes':'Add registration',body:`
    ${field('Registration name','name',initial.name,'text','required maxlength="150"')}
    ${select('Section','sectionId',state.catalog.sections.map(s=>[s.id,(s.other?'Other · ':'')+s.title]),initial.sectionId)}
    ${field('Style','style',initial.style,'text','required maxlength="150"')}
    <div class="form-columns">${field('Tempo (BPM)','tempo',initial.tempo,'number','required min="5" max="500" step="1"')}${field('Transpose','transpose',initial.transpose,'number','required min="-12" max="12" step="1"')}</div>
    <label>Drive files<textarea name="links" aria-label="Drive files" rows="4" placeholder="File name | https://drive.google.com/file/d/…/view">${esc(initial.links.map(l=>l.title+' | '+l.url).join('\n'))}</textarea><small>One file per line: name | link. Up to 10 files.</small></label>
    ${original?action('delete-registration','Delete registration',`data-id="${esc(original.id)}"`,'danger-outline'):''}`,
    submit:async data=>{
      const links=data.get('links').trim().split('\n').filter(s=>s.trim()).map(line=>{const split=line.indexOf('|');return split<0?{title:'',url:line.trim()}:{title:line.slice(0,split).trim(),url:line.slice(split+1).trim()};});
      await saveRegistration({...Object.fromEntries(data),links},original);notify(original?'Registration saved.':'Registration added.');
    }});
}
function chordDialog(row){
  const setting=state.chords.setting(row);
  modal({title:'Offline Chords',hostOnly:true,body:`<h3>${esc(row.name)}</h3><p>${state.chords.matching(row).length?'This registration matches the chord sheet.':'No chord-sheet match yet.'}</p>${select('Availability','mode',[['automatic','Automatic — follow the chord sheet'],['enabled','Enabled — available'],['disabled','Disabled — unavailable']],setting.mode)}
    <p class="small muted">Enabled adds the registration name to the chord sheet when needed. Disabled preserves the sheet entry.</p>`,
    submit:async data=>{await setChord(row,data.get('mode'),setting.revision||0);notify('Chord availability saved.');}});
}
function editHymns(append){
  const original=structuredClone(plan());
  modal({title:append?'Add hymns':'Edit hymn list',hostOnly:!append,save:append?'Find & add':'Find & save',body:`
    ${append?`<p>Add songs to <strong>${esc(original.title)}</strong> in the order you will sing them.</p>`:field('List title','title',original.title,'text','required maxlength="80"')}
    <label>Hymns<textarea name="text" aria-label="Hymns" rows="10" maxlength="20000" ${append?'required':''} placeholder="Entrance: Beautiful Sunday&#10;Holy: Aicha&#10;Unknown hymn">${esc(append?'':original.text)}</textarea><small>One hymn per line. Section headings are optional. Up to 200 hymns per list.</small></label>`,
    submit:async data=>{
      const text=append?[original.text,data.get('text').trim()].filter(Boolean).join('\n'):data.get('text').trim();
      let choices={...original.choices};
      if(!append&&text!==original.text){
        const old=parseHymns(original.text,state.catalog.sections),next=parseHymns(text,state.catalog.sections);
        choices=Object.fromEntries(Object.entries(choices).filter(([pos])=>JSON.stringify(old[Number(pos)-1])===JSON.stringify(next[Number(pos)-1])));
      }
      await savePlan({...original,title:append?original.title:data.get('title').trim(),text,choices},original.revision);notify('Hymn list saved for everyone.');
    }});
}
function chooseRegistration(position){
  const original=structuredClone(plan()),match=matchHymns(original,state.catalog,state.chords).find(m=>m.request.position===position);
  modal({title:'Choose a registration',body:`<p>For <strong>${esc(match.request.name)}</strong></p><label>Search all registrations<input type="search" id="choice-search" placeholder="Search by name or style"></label><div id="choice-results"></div>`,mount:form=>{
    const show=(q='')=>{
      const candidates=q?filterRows(state.catalog,state.chords,{query:q}):match.candidates;
      form.querySelector('#choice-results').innerHTML=`<p class="small muted">${q?'Search results':'Possible matches'}</p>${candidates.length?candidates.slice(0,80).map(r=>`<button class="choice" type="button" data-choice="${esc(r.id)}"><strong>${esc(r.name)}</strong><span>${esc(titleFor(r.sectionId))} · ${esc(r.style)} · ${r.tempo} BPM · ${r.transpose>0?'+':''}${r.transpose}</span>${chordIndicator(r)}</button>`).join(''):'<p>No matches. Search another name above.</p>'}<button type="button" class="secondary" data-choice="">Mark as not available</button>`;
    };show();
    form.querySelector('#choice-search').oninput=e=>show(e.target.value);
    form.addEventListener('click',async e=>{
      const b=e.target.closest('[data-choice]');if(!b)return;
      form.querySelectorAll('button').forEach(b=>b.disabled=true);
      try{await savePlan({...original,choices:{...original.choices,[position]:b.dataset.choice}},original.revision);closeModal();notify('Registration choice saved.');}
      catch(e){const error=form.querySelector('.form-error');error.textContent=friendlyError(e);error.hidden=false;form.querySelectorAll('button').forEach(b=>b.disabled=false);}
    });
  }});
}
function confirmAction(title,body,save,submit,hostOnly=true){modal({title,body:`<p>${esc(body)}</p>`,save,submit,hostOnly});}
async function copyList(chordsOnly){
  const current=plan(),matches=matchHymns(current,state.catalog,state.chords);
  const groups=hymnResources(matches,state.chords);
  const chordText=['1. OFFLINE CHORDS / HARD-COPY CHORDS',...groups.songs.map(({song,positions})=>`${titleFor(song.sectionId)} / ${song.name} (hymns ${positions.join(', ')})`),...groups.pending].join('\n');
  const describe=m=>`${m.request.position}. ${m.selected?.name||m.request.name}${m.selected?'':' — '+m.status}`;
  const text=chordsOnly?chordText:[current.title,...matches.map(m=>`${m.request.position}. ${m.request.name}\n   ${m.selected?`${titleFor(m.selected.sectionId)} / ${m.selected.name}\n   ${m.selected.style} · ${m.selected.tempo} BPM · Transpose ${m.selected.transpose}\n${m.selected.links.map(l=>'   '+l.title+': '+l.url).join('\n')}`:m.status}`),'',chordText,'','2. WITHOUT OFFLINE CHORDS OR DRIVE LINKS',...groups.missing.map(describe),'','3. WITHOUT OFFLINE CHORDS, WITH DRIVE LINKS',...groups.drive.map(m=>describe(m)+'\n'+m.selected.links.map(l=>l.title+': '+l.url).join('\n'))].join('\n');
  try{await navigator.clipboard.writeText(text);notify('Copied to clipboard.');}
  catch{modal({title:'Copy list',body:`<label>Select and copy<textarea rows="14" readonly>${esc(text)}</textarea></label>`,mount:f=>f.querySelector('textarea').select()});}
}
document.addEventListener('input',e=>{if(e.target.id==='search'){query=e.target.value;limit=60;render();}});
document.addEventListener('change',e=>{
  if(e.target.id==='chord-filter')chordFilter=e.target.value;
  else if(e.target.id==='link-filter')linkFilter=e.target.value;else return;
  limit=60;render();
});
document.addEventListener('click',async e=>{
  const b=e.target.closest('[data-action]');if(!b)return;
  const a=b.dataset.action,id=b.dataset.id,row=state.catalog.registrations.find(r=>r.id===id),section=state.catalog.sections.find(s=>s.id===id);
  try{
    if(a==='host')hostDialog();
    else if(a==='sign-out'){await logout();closeModal();notify('Signed out.');}
    else if(a==='reset-password'){
      const email=$('#dialogs [name=email]').value;
      if(!email.trim())throw Error('Enter your host email first.');
      b.disabled=true;try{await resetPassword(email);notify('If this host account exists, password setup instructions will arrive by email.');}finally{b.disabled=false;}
    }
    else if(a==='install-help')modal({title:'Keep The Choir close',body:'<p><strong>iPhone:</strong> Open this link in Safari, tap Share, then Add to Home Screen.</p><p><strong>PC:</strong> Bookmark this page, or use your browser’s Install app option when available.</p><p class="small muted">Previously synced registrations and lists remain readable offline. Saving changes needs an internet connection.</p>'});
    else if(a==='add-section'&&state.host)sectionDialog();
    else if(a==='manage-section'&&section&&state.host)sectionDialog(section);
    else if(a==='remove-section'&&section&&state.host)removeSectionDialog(section);
    else if(a==='add-registration'&&state.host)registrationDialog();
    else if(a==='edit-registration'&&row&&state.host)registrationDialog(structuredClone(row));
    else if(a==='delete-registration'&&row&&state.host){
      const original=structuredClone(row);
      confirmAction('Delete registration?',`Remove “${row.name}” from the shared library? Its Drive files will be kept.`,'Delete registration',async()=>{await saveRegistration(original,original,true);notify('Registration removed.');});
    }
    else if(a==='chord-control'&&row&&state.host)chordDialog(row);
    else if(a==='clear-filters'){query='';chordFilter='all';linkFilter='all';limit=60;render();}
    else if(a==='more'){limit+=60;render();}
    else if(a==='add-hymns')editHymns(true);
    else if(a==='edit-hymns'&&state.host)editHymns(false);
    else if(a==='clear-plan'&&state.host){
      const original=structuredClone(plan());
      confirmAction('Clear this hymn list?',`Clear all hymns from “${original.title}” on every device? Registrations and chord-sheet entries will be kept.`,'Clear list',async()=>{await savePlan({...original,text:'',choices:{}},original.revision);notify('Hymn list cleared for everyone.');});
    }
    else if(a==='choose-registration')chooseRegistration(Number(b.dataset.position));
    else if(a==='copy-chords'||a==='copy-plan')await copyList(a==='copy-chords');
    else if(a==='collect-pdfs'){
      const current=structuredClone(plan()),groups=hymnResources(matchHymns(current,state.catalog,state.chords),state.chords);
      const {showPdfCollection}=await import('./pdf-ui.js');showPdfCollection(groups.drive,current.title);
    }
  }catch(e){notify(friendlyError(e));}
});
subscribe(next=>{
  state=next;
  if(hadHost&&!state.host&&modalHost){closeModal();notify('Host access ended. Sign in again to make changes.');}
  if(!hadHost&&state.host&&state.authReady)notify('Host controls enabled.');
  hadHost=state.host;render();
});
if('serviceWorker' in navigator&&!import.meta.env.DEV){
  const wasControlled=!!navigator.serviceWorker.controller;
  navigator.serviceWorker.register('/sw.js').then(reg=>{
    function offer(worker){
      if(!worker||!wasControlled||!navigator.serviceWorker.controller)return;
      if($('#dialogs dialog[open]')){notify('A web update is ready. Finish your edits, then reload.');return;}
      modal({title:'A new web version is ready',body:'<p>Reload to use the latest version of The Choir. Save any open edits first.</p>',save:'Reload',submit:async()=>{worker.postMessage('ACTIVATE');}});
    }
    if(reg.waiting)offer(reg.waiting);
    reg.addEventListener('updatefound',()=>reg.installing?.addEventListener('statechange',()=>{if(reg.waiting)offer(reg.waiting);}));
    let reloading=false;navigator.serviceWorker.addEventListener('controllerchange',()=>{if(wasControlled&&!reloading){reloading=true;location.reload();}});
  }).catch(()=>{});
}

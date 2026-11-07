export const key = name => String(name).normalize('NFD').replace(/\p{M}/gu,'').toLowerCase()
  .replace(/\s*\((?:lhm|holy|log|comm|communion|offertory|recc|recessional|proclamation|thanks|reprint[^)]*)\)/g,'')
  .replace(/&/g,'and').replace(/[^a-z0-9]/g,'');
export function nameKeys(song) {
  return [...new Set([song.name,...(song.aliases||[])].flatMap(name=>{
    const m=name.match(/^(.*?)\s*\(([^)]+)\)\s*$/);
    return m&&m[2].trim().split(/\s+/).length>=2&&!/^reprint/i.test(m[2])?[name,m[1],m[2]]:[name];
  }).map(key).filter(Boolean))];
}
export function resolveSection(id, settings) {
  const seen=new Set();
  while(settings[id]?.removed){
    if(seen.has(id))throw Error('Section moves contain a loop.');
    seen.add(id);id=settings[id].moveTo;
  }
  return id;
}
export function catalogView(base, rows, custom=[], settings={}) {
  const sections=[...base.sections,...custom].filter(s=>!settings[s.id]?.removed)
    .map(s=>({...s,title:settings[s.id]?.title||s.title}));
  const merged=new Map(base.registrations.map(r=>[r.id,r]));
  rows.forEach(r=>r.deleted?merged.delete(r.id):merged.set(r.id,r));
  const registrations=[...merged.values()].map(r=>({...r,rawSectionId:r.sectionId,sectionId:resolveSection(r.sectionId,settings)}));
  for(const r of registrations)if(!sections.some(s=>s.id===r.sectionId))
    sections.push({id:r.sectionId,title:'Other section · syncing…',other:true});
  const order=new Map(sections.map((s,i)=>[s.id,i]));
  registrations.sort((a,b)=>order.get(a.sectionId)-order.get(b.sectionId)||a.name.localeCompare(b.name));
  return {sections,registrations,settings};
}
export function chordState(sections, overrides, settings={}) {
  const songs=sections.flatMap(s=>(s.songs||[]).map(song=>({...song,sectionId:resolveSection(s.id,settings),sheet:s.sheet})));
  const index=new Map();
  for(const song of songs)for(const k of nameKeys(song)){
    const id=song.sectionId+':'+k; index.set(id,[...(index.get(id)||[]),song]);
  }
  const matching=r=>index.get(r.sectionId+':'+key(r.name))||[];
  const setting=r=>overrides[r.id]||{mode:'automatic',revision:0};
  return {songs,matching,setting,available:r=>setting(r).mode==='enabled'||(setting(r).mode!=='disabled'&&matching(r).length>0)};
}
export function parseHymns(text, sections) {
  const aliases={lordhavemercy:'lhm',lambofgod:'log',thanksgiving:'thanks-giving'};
  for(const s of sections){aliases[key(s.id)]=s.id;aliases[key(s.title)]=s.id;}
  let sectionId=null;const result=[];
  for(const raw of text.split(/\r?\n/)){
    const line=raw.trim().replace(/^(?:\d+[.)]\s*|[-*•]\s+|#+\s*)/,'').trim();
    if(!line)continue;
    if(aliases[key(line.replace(/:$/,''))]){sectionId=aliases[key(line.replace(/:$/,''))];continue;}
    const split=line.match(/^(.*?)\s*(?::|–|—|\s-\s)\s*(.*)$/);
    const inline=split&&aliases[key(split[1])];
    const name=inline?split[2].trim():line;
    if(name)result.push({position:result.length+1,name,sectionId:inline||sectionId});
  }
  return result;
}
export function matchHymns(plan,catalog,chords){
  const names=new Map(catalog.registrations.map(r=>[r.id,new Set([key(r.name),...chords.matching(r).flatMap(nameKeys)])]));
  return parseHymns(plan.text,catalog.sections).map(request=>{
    const k=key(request.name),pool=catalog.registrations.filter(r=>!request.sectionId||r.sectionId===request.sectionId);
    const exact=pool.filter(r=>k&&names.get(r.id).has(k));
    const candidates=exact.length?exact:pool.filter(r=>k.length>=4&&[...names.get(r.id)].some(n=>n.length>=4&&(n.includes(k)||k.includes(n)))).slice(0,12);
    const chosen=plan.choices?.[request.position];
    const selected=chosen!==undefined?catalog.registrations.find(r=>r.id===chosen):exact.length===1?exact[0]:null;
    return {request,selected,candidates,status:selected?'Available':chosen!==undefined?'Not available':exact.length>1?'Choose a registration':'Not available'};
  });
}
export function retrieval(matches,chords) {
  const songs=new Map(),missing=[];
  for(const m of matches){
    const r=m.selected;if(!r)continue;
    const matched=chords.matching(r),mode=chords.setting(r).mode;
    const reason=mode==='disabled'?'Disabled by host':!matched.length?(mode==='enabled'?'Waiting for chord-list sync':'No chord-list match'):null;
    if(reason){missing.push(`${m.request.position}. ${r.name} — ${reason}`);continue;}
    for(const song of matched){
      const k=song.sectionId+':'+song.name;
      if(!songs.has(k))songs.set(k,{song,positions:[]});
      songs.get(k).positions.push(m.request.position);
    }
  }
  return {songs:[...songs.values()],missing};
}
// These groups partition one hymn list. Unresolved hymns stay visible in group 2.
export function hymnResources(matches,chords) {
  const offline=[],missing=[],drive=[];
  for(const m of matches){
    if(m.selected&&chords.available(m.selected))offline.push(m);
    else if(m.selected?.links.length)drive.push(m);
    else missing.push(m);
  }
  const printed=retrieval(offline,chords);
  return {offline,missing,drive,songs:printed.songs,pending:printed.missing};
}
export function filterRows(catalog,chords,{query='',section='',chord='all',links='all'}={}) {
  const normalize=s=>s.normalize('NFD').replace(/\p{M}/gu,'').toLowerCase();
  const terms=normalize(query).trim().split(/\s+/).filter(Boolean),titles=Object.fromEntries(catalog.sections.map(s=>[s.id,s.title]));
  return catalog.registrations.filter(r=>(!section||r.sectionId===section)
    &&(chord==='all'||(chord==='with')===!!chords.available(r))
    &&(links==='all'||(links==='with')===(r.links.length>0))
    &&terms.every(t=>normalize(`${r.name} ${r.style} ${titles[r.sectionId]||''}`).includes(t)));
}
export function sectionTitle(title, sections, except=''){
  const clean=title.trim().replace(/\s+/g,' ');
  if(!clean||clean.length>60)throw Error('Enter a section name up to 60 characters.');
  if(sections.some(s=>s.id!==except&&s.title.trim().replace(/\s+/g,' ').toLowerCase()===clean.toLowerCase()))throw Error('A section with this name already exists.');
  return clean;
}
export function validateRegistration(input,catalog,id=''){
  const row={id,sectionId:input.sectionId,name:input.name.trim(),style:input.style.trim(),tempo:Number(input.tempo),transpose:Number(input.transpose),links:input.links||[]};
  if(!row.name||row.name.length>150||!row.style||row.style.length>150)throw Error('Enter a name and style up to 150 characters each.');
  if(!catalog.sections.some(s=>s.id===row.sectionId))throw Error('Choose a section.');
  if(!Number.isInteger(row.tempo)||row.tempo<5||row.tempo>500)throw Error('Tempo must be a whole number from 5 to 500.');
  if(!Number.isInteger(row.transpose)||row.transpose<-12||row.transpose>12)throw Error('Transpose must be a whole number from −12 to +12.');
  if(catalog.registrations.some(r=>r.id!==id&&r.sectionId===row.sectionId&&r.name.toLowerCase()===row.name.toLowerCase()))throw Error('This name already exists in that section.');
  if(row.links.length>10)throw Error('Attach up to 10 Drive files.');
  row.links=row.links.map(l=>{
    const url=new URL(l.url.trim()),title=l.title.trim()||row.name;
    if(url.protocol!=='https:'||!['drive.google.com','docs.google.com'].includes(url.hostname)||url.username||url.password||(url.port&&url.port!=='443')||url.pathname==='/'||l.url.length>2048||title.length>150)throw Error('Use an HTTPS Google Drive or Docs file link.');
    return {title,url:url.href};
  });
  if(new Set(row.links.map(l=>l.url)).size!==row.links.length)throw Error('The same Drive link is attached more than once.');
  return row;
}
export const guestChange=(before,after)=>after.title===before.title&&(after.text===before.text||(!before.text||after.text.startsWith(before.text+'\n'))&&after.text.trim().length>0);

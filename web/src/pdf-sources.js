export const PDF_LIMITS={files:60,fileBytes:20*1024*1024,totalBytes:50*1024*1024,pages:300};
export function driveFile(url){
  const u=new URL(url);
  if(u.protocol!=='https:'||!['drive.google.com','docs.google.com'].includes(u.hostname)||u.username||u.password)throw Error('Use a Google Drive file link.');
  const id=u.pathname.match(/\/(?:file|document|spreadsheets|presentation)\/d\/([a-zA-Z0-9_-]+)/)?.[1]||u.searchParams.get('id');
  if(!id||!/^[a-zA-Z0-9_-]{10,200}$/.test(id))throw Error('This is not a supported Drive file link.');
  const resourceKey=u.searchParams.get('resourcekey')||'';
  if(resourceKey&&!/^[a-zA-Z0-9_-]{1,200}$/.test(resourceKey))throw Error('Invalid Drive resource key.');
  return {id,resourceKey};
}
export function pdfSources(matches){
  const files=new Map();
  for(const m of matches)for(const link of m.selected?.links||[]){
    let file;try{file=driveFile(link.url);}catch{/* Report unsupported links alongside failed downloads. */}
    const id=file?.id||link.url;
    if(!files.has(id))files.set(id,{...link,...file,positions:[],registration:m.selected.name});
    const entry=files.get(id);
    if(!entry.resourceKey&&file?.resourceKey)entry.resourceKey=file.resourceKey;
    if(!entry.positions.includes(m.request.position))entry.positions.push(m.request.position);
  }
  return [...files.values()];
}
export const pdfFilename=title=>(String(title).normalize('NFKC').replace(/[^\p{L}\p{N} _-]/gu,'').trim().slice(0,70)||'Hymn list')+' - Drive chords.pdf';

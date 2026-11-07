import {PDFDocument} from 'pdf-lib';

import {PDF_LIMITS} from './pdf-sources.js';
export {PDF_LIMITS,pdfSources,driveFile,pdfFilename} from './pdf-sources.js';
function aborted(signal){if(signal?.aborted)throw new DOMException('Cancelled','AbortError');}
async function boundedBytes(response,remaining,signal){
  const limit=Math.min(PDF_LIMITS.fileBytes,remaining);
  if(Number(response.headers.get('content-length'))>limit)throw Error('File exceeds the PDF size limit.');
  const reader=response.body.getReader(),parts=[];let length=0;
  try{while(true){aborted(signal);const {done,value}=await reader.read();if(done)break;length+=value.byteLength;if(length>limit)throw Error('File exceeds the PDF size limit.');parts.push(value);}}
  finally{await reader.cancel().catch(()=>{});}
  const bytes=new Uint8Array(length);let offset=0;for(const p of parts){bytes.set(p,offset);offset+=p.length;}
  if(new TextDecoder('ascii').decode(bytes.slice(0,1024)).indexOf('%PDF-')<0)throw Error('Google did not return a PDF. Check file access and type.');
  return bytes;
}
export async function downloadDrivePdf(source,apiKey,remaining,signal,fetcher=fetch){
  if(!source.id)throw Error('Unsupported Drive file link.');
  const headers=source.resourceKey?{'X-Goog-Drive-Resource-Keys':`${source.id}/${source.resourceKey}`}:{};
  const base=`https://www.googleapis.com/drive/v3/files/${source.id}`,key=encodeURIComponent(apiKey);
  const options={credentials:'omit',headers,signal};
  const meta=await fetcher(`${base}?fields=mimeType,name,size,capabilities(canDownload)&key=${key}`,options);
  if(!meta.ok)throw Error(meta.status===404||meta.status===403?'File unavailable, private, or download disabled. Open its Drive link to check.':'Google download service is unavailable. Try again.');
  const info=await meta.json();
  if(info.capabilities?.canDownload===false)throw Error('The file owner has disabled downloads.');
  const native=info.mimeType==='application/vnd.google-apps.document';
  if(!native&&info.mimeType!=='application/pdf')throw Error('Only PDF and Google Docs files can be combined.');
  if(Number(info.size)>Math.min(PDF_LIMITS.fileBytes,remaining))throw Error('File exceeds the PDF size limit.');
  const url=native?`${base}/export?mimeType=application%2Fpdf&key=${key}`:`${base}?alt=media&key=${key}`;
  const response=await fetcher(url,options);
  if(!response.ok)throw Error(response.status===403||response.status===404?'Google could not export or download this file. Check its sharing and download settings.':'Google could not download this file. Try again.');
  return boundedBytes(response,remaining,signal);
}
export async function combinePdfs(sources,{load,progress=()=>{},signal}){
  if(sources.length>PDF_LIMITS.files)throw Error(`Use up to ${PDF_LIMITS.files} distinct files per PDF.`);
  const combined=await PDFDocument.create(),failures=[],included=[];let totalBytes=0,totalPages=0;
  for(let i=0;i<sources.length;i++){
    aborted(signal);const source=sources[i];progress({done:i,total:sources.length,title:source.title});
    try{
      const bytes=await load(source,PDF_LIMITS.totalBytes-totalBytes,signal);totalBytes+=bytes.length;
      if(bytes.length>PDF_LIMITS.fileBytes||totalBytes>PDF_LIMITS.totalBytes)throw Error('File exceeds the PDF size limit.');
      const pdf=await PDFDocument.load(bytes);
      if(pdf.isEncrypted)throw Error('Password-protected PDFs cannot be combined.');
      const count=pdf.getPageCount();
      if(!count)throw Error('This PDF has no pages.');
      if(totalPages+count>PDF_LIMITS.pages)throw Error(`The combined PDF is limited to ${PDF_LIMITS.pages} pages.`);
      // Copy all pages before appending so a broken source never contributes half a file.
      const pages=await combined.copyPages(pdf,pdf.getPageIndices());aborted(signal);
      pages.forEach(page=>combined.addPage(page));totalPages+=count;included.push({...source,pages:count});
    }catch(e){
      if(e.name==='AbortError')throw e;
      const message=/encrypt/i.test(e.message)?'Password-protected PDFs cannot be combined.':e.message;
      failures.push({...source,error:message.length>250?'This PDF could not be read. Open the original Drive file to check it.':message});
    }
  }
  aborted(signal);progress({done:sources.length,total:sources.length,title:'Finishing PDF'});
  combined.setTitle('The Choir — Drive chords');combined.setCreator('The Choir');
  return {bytes:included.length?await combined.save():null,included,failures,pages:totalPages};
}

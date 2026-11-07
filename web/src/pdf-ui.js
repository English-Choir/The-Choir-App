import {pdfSources,pdfFilename,PDF_LIMITS} from './pdf-sources.js';
import config from './drive-config.json';
const escape=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export function showPdfCollection(matches,title){
  if(document.querySelector('dialog'))return;
  const sources=pdfSources(matches),dialog=document.createElement('dialog');
  dialog.className='pdf-dialog';dialog.setAttribute('aria-label','Collect Drive chords');
  dialog.innerHTML=`<div class="dialog-heading"><h2>Collect Drive chords</h2><button class="icon-button" data-close aria-label="Close">×</button></div><div class="dialog-body">
    <p>${sources.length} distinct files, in hymn order. Repeated links are included once. Google Docs become PDFs; the files are combined on this device.</p>
    <p class="small muted">Keep this window open. Up to ${PDF_LIMITS.files} files, 20 MB per file, 50 MB total and 300 pages. Nothing is uploaded to a conversion server.</p>
    <p class="pdf-progress" role="status"></p><progress max="1" value="0" aria-label="PDF progress"></progress>
    <div class="pdf-result"></div></div><div class="dialog-actions"><button class="secondary" data-close>Cancel</button><button class="primary" data-start>Create combined PDF</button></div>`;
  document.body.append(dialog);document.getElementById('app').inert=true;dialog.showModal();
  let worker,objectUrl;
  const close=()=>{worker?.terminate();if(objectUrl)URL.revokeObjectURL(objectUrl);dialog.close();dialog.remove();document.getElementById('app').inert=false;};
  dialog.querySelectorAll('[data-close]').forEach(b=>b.onclick=close);
  dialog.addEventListener('cancel',e=>{e.preventDefault();close();});
  const start=dialog.querySelector('[data-start]'),status=dialog.querySelector('.pdf-progress'),result=dialog.querySelector('.pdf-result');
  start.disabled=!sources.length||sources.length>PDF_LIMITS.files;
  if(sources.length>PDF_LIMITS.files)status.textContent=`This list exceeds ${PDF_LIMITS.files} distinct files. Split it into a smaller collection.`;
  start.onclick=()=>{
    if(!navigator.onLine){status.textContent='Connect to the internet to download the Drive files.';return;}
    if(objectUrl){URL.revokeObjectURL(objectUrl);objectUrl=null;}result.replaceChildren();start.disabled=true;
    status.textContent='Starting downloads…';worker?.terminate();
    worker=new Worker(new URL('./pdf-worker.js',import.meta.url),{type:'module'});
    const failed=message=>{worker?.terminate();start.disabled=false;start.textContent='Retry';status.textContent=message;};
    worker.onerror=()=>failed('The PDF could not be created. Try fewer files or close other browser tabs.');
    worker.onmessage=({data})=>{
      if(data.type==='progress'){status.textContent=`${data.done} of ${data.total} files · ${data.title}`;const p=dialog.querySelector('progress');p.max=Math.max(1,data.total);p.value=data.done;return;}
      if(data.type==='error'){failed(data.message);return;}
      worker.terminate();worker=null;start.disabled=false;start.textContent='Create again';
      status.textContent=`${data.included.length} of ${sources.length} files included · ${data.pages} pages${data.failures.length?' · INCOMPLETE':''}`;
      result.innerHTML=data.failures.length?`<div class="pdf-failures"><h3>${data.failures.length} files could not be included</h3><ul>${data.failures.map(f=>`<li><strong>${escape(f.title)}</strong> (hymns ${f.positions.join(', ')})<p>${escape(f.error)}</p><a href="${escape(f.url)}" target="_blank" rel="noopener">Open original Drive file ↗</a></li>`).join('')}</ul></div>`:'<p>All files are ready.</p>';
      if(data.bytes){
        objectUrl=URL.createObjectURL(new Blob([data.bytes],{type:'application/pdf'}));
        const a=document.createElement('a');a.href=objectUrl;a.download=pdfFilename(title);a.className='primary pdf-download';
        a.textContent=data.failures.length?'Download partial PDF':'Download combined PDF';result.append(a);
      }else result.insertAdjacentHTML('beforeend','<p>No PDF was created. Check the files above and try again.</p>');
      dialog.querySelectorAll('[data-close]').forEach(b=>{if(!b.classList.contains('icon-button'))b.textContent='Close';});
    };
    worker.postMessage({sources,apiKey:config.publicApiKey});
  };
}

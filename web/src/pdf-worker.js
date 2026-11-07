import {combinePdfs,downloadDrivePdf} from './pdf-core.js';
self.onmessage=async({data})=>{
  try{
    const result=await combinePdfs(data.sources,{
      load:(source,remaining)=>downloadDrivePdf(source,data.apiKey,remaining,AbortSignal.timeout(60000)),
      progress:value=>self.postMessage({type:'progress',...value})
    });
    self.postMessage({type:'result',...result},result.bytes?[result.bytes.buffer]:[]);
  }catch(e){self.postMessage({type:'error',message:e.message});}
};

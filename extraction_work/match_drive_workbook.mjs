import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {FileBlob,SpreadsheetFile} from '@oai/artifact-tool';

const work=path.dirname(fileURLToPath(import.meta.url));
const output=path.join(work,'..','outputs','sx900-registration-extraction','SX900 Registrations.xlsx');
if(process.argv.includes('--review-all')){
  const patch=JSON.parse(await fs.readFile(path.join(work,'all_review_patch.json'),'utf8'));
  const edited=await SpreadsheetFile.importXlsx(await FileBlob.load(patch.backup));
  const review=edited.worksheets.getItem('Drive review');
  for(const u of patch.updates){
    const sheet=edited.worksheets.getItem(u.folder);
    sheet.getRange(`L${u.row}:P${u.row}`).values=[u.values];
    sheet.getRange(`L${u.row}`).format.fill=u.missing?'#FCE6E7':'#E4F0E7';
    sheet.getRange(`A${u.row}:P${u.row}`).format.autofitRows();
  }
  for(const u of patch.summaries){
    const sheet=edited.worksheets.getItem(u.folder);
    sheet.getRange('L4').values=[[u.header]];
    sheet.getRange('L5').values=[['Reviewed 19 Oct 2026. Green = user-confirmed. Pink = no file candidate. Drive review contains all file links.']];
    review.getRange(`D${u.summary_row}`).values=[[u.description]];
  }
  review.getRange('A3').values=[['19 Oct 2026. 458 registrations: 292 matched or confirmed, 0 candidates awaiting review, 166 without a matched Drive file.']];
  review.getRange('A5').values=[['User review recorded on 19 Oct 2026. Accepted alternate files are retained. Missing files remain unlinked. Drive files and registration names are unchanged.']];
  for(const u of patch.review_updates){
    review.getRange(`C${u.row}:D${u.row}`).values=[u.values];
    review.getRange(`F${u.row}`).values=[[u.note]];
    if(u.values[0]==='Confirmed match')review.getRange(`C${u.row}`).format.fill='#E4F0E7';
    if(u.values[0]==='No registration match')review.getRange(`C${u.row}`).format.fill='#FCE6E7';
    review.getRange(`A${u.row}:F${u.row}`).format.autofitRows();
  }
  edited.recalculate();
  console.log((await edited.inspect({kind:'match',searchTerm:'#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!',options:{useRegex:true,maxResults:15},maxChars:1000})).ndjson);
  await (await SpreadsheetFile.exportXlsx(edited)).save(patch.output);
  for(const [i,u] of patch.summaries.entries()){
    const preview=await edited.render({sheetName:u.folder,range:'L2:P12',scale:1,format:'png'});
    await fs.writeFile(path.join(work,'drive_previews',`final_${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await preview.arrayBuffer()));
  }
  for(const [name,range] of [['final_summary','A1:F30'],['final_inventory','A55:F67']]){
    const preview=await edited.render({sheetName:'Drive review',range,scale:1,format:'png'});
    await fs.writeFile(path.join(work,'drive_previews',name+'.png'),new Uint8Array(await preview.arrayBuffer()));
  }
  for(const [folder,name] of [['LOG','Peace I give to you'],['OUR FATHER','Stanes'],['RECESSIONAL','Irish']]){
    const u=patch.updates.find(u=>u.folder===folder && edited.worksheets.getItem(folder).getRange(`B${u.row}`).values[0][0].trim()===name);
    const preview=await edited.render({sheetName:folder,range:`L${u.row}:P${u.row}`,scale:1,format:'png'});
    await fs.writeFile(path.join(work,'drive_previews',`exception_${folder.replaceAll(' ','_')}.png`),new Uint8Array(await preview.arrayBuffer()));
  }
  console.log('Applied 148 decisions across 15 sections.');
  process.exit(0);
}
if(process.argv.includes('--review-entrance')){
  const patch=JSON.parse(await fs.readFile(path.join(work,'entrance_review_patch.json'),'utf8'));
  const edited=await SpreadsheetFile.importXlsx(await FileBlob.load(patch.backup));
  const entrance=edited.worksheets.getItem('ENTRANCE');
  const review=edited.worksheets.getItem('Drive review');
  for(const u of patch.updates){
    entrance.getRange(`L${u.row}`).values=[[u.status]];
    entrance.getRange(`P${u.row}`).values=[[u.reason]];
    entrance.getRange(`L${u.row}`).format.fill=u.missing?'#FCE6E7':'#E4F0E7';
    if(u.missing){
      entrance.getRange(`N${u.row}:O${u.row}`).clear({applyTo:'contents'});
    }else{
      entrance.getRange(`O${u.row}`).values=[['Open file']];
    }
    entrance.getRange(`A${u.row}:P${u.row}`).format.autofitRows();
  }
  entrance.getRange('L4').values=[['27 matches in ENTRANCE. 2 confirmed in other folders. 18 without a Drive file.']];
  entrance.getRange('L5').values=[['Reviewed 19 Oct 2026. Green = user-confirmed. Pink = no file candidate. Drive review contains all file links.']];
  review.getRange('A3').values=[['19 Oct 2026. 458 registrations: 146 matched or confirmed, 148 candidates to review, 164 without a candidate.']];
  review.getRange('D8').values=[['47 registrations: 29 matched or confirmed, 0 to review, 18 without a candidate.']];
  for(const u of patch.review_updates){
    review.getRange(`C${u.row}:D${u.row}`).values=[[u.status,u.registrations]];
    review.getRange(`F${u.row}`).values=[[u.note]];
    if(u.status==='Confirmed match')review.getRange(`C${u.row}`).format.fill='#E4F0E7';
    review.getRange(`A${u.row}:F${u.row}`).format.autofitRows();
  }
  edited.recalculate();
  console.log((await edited.inspect({kind:'match',searchTerm:'#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!',options:{useRegex:true,maxResults:15},maxChars:1000})).ndjson);
  const xlsx=await SpreadsheetFile.exportXlsx(edited);
  await xlsx.save(patch.output);
  const views=[['ENTRANCE','L2:P14','entrance_confirmed'],['ENTRANCE','L20:P23','entrance_other_folder'],['Drive review','A1:F10','review_confirmed'],['Drive review','A38:F48','inventory_confirmed']];
  for(const [sheetName,range,name] of views){
    const preview=await edited.render({sheetName,range,scale:1,format:'png'});
    await fs.writeFile(path.join(work,'drive_previews',name+'.png'),new Uint8Array(await preview.arrayBuffer()));
  }
  console.log('Applied 16 ENTRANCE decisions and updated the related review entries.');
  process.exit(0);
}
const baseline=path.join(work,'SX900 Registrations.pre-drive.xlsx.bak');
try { await fs.access(baseline); } catch { await fs.copyFile(output,baseline); }
const wb=await SpreadsheetFile.importXlsx(await FileBlob.load(baseline));
const data=JSON.parse(await fs.readFile(path.join(work,'drive_matches.json'),'utf8'));
const inv=JSON.parse(await fs.readFile(path.join(work,'drive_inventory.json'),'utf8'));
const previewDir=path.join(work,'drive_previews');
await fs.mkdir(previewDir,{recursive:true});
const exact=new Set(['Matched name','Matched formatting']);
const reviews=new Set(['Possible name match','Name in other folder','Possible in other folder','Multiple name matches']);
const headerFormat={fill:'#293B58',font:{name:'Arial',size:10,bold:true,color:'#FFFFFF'},wrapText:true,horizontalAlignment:'center',verticalAlignment:'center',rowHeight:35,borders:{insideVertical:{style:'thin',color:'#FFFFFF'}}};
// HYPERLINK calculation is unavailable in this artifact runtime. Author the
// display text here and attach standard native Excel links after export.
const links=[];
const setLink=(sheet,address,id,label,folder=false)=>{
  const file=data.files.find(f=>f.id===id);
  const url=folder?`https://drive.google.com/drive/folders/${id}`:
    file?.label.includes('Google Docs')?`https://docs.google.com/document/d/${id}/edit`:
    `https://drive.google.com/file/d/${id}/view`;
  sheet.getRange(address).values=[[label]];
  links.push({sheet:sheet.name,address,label,url});
};
const checks=[];
let verified=0;
for(const [index,folder] of Object.keys(data.folder_map).entries()){
  const sheet=wb.worksheets.getItem(folder);
  const sourceRows=data.rows.filter(r=>r.folder===folder);
  const last=7+sourceRows.length;
  const saved=sheet.getRange(`A8:J${last}`).values;
  const rows=saved.map(row=>{
    const match=sourceRows.filter(r=>r.registration===row[1]);
    if(match.length!==1)throw new Error(`Registration lookup failed: ${folder}/${row[1]}`);
    return match[0];
  });
  const folderInfo=data.folder_map[folder];
  const counts={matched:rows.filter(r=>exact.has(r.match_status)).length,review:rows.filter(r=>reviews.has(r.match_status)).length,missing:rows.filter(r=>!r.candidates.length).length};
  if(counts.matched+counts.review+counts.missing!==rows.length)throw new Error('Counts do not reconcile');
  const existingTable=sheet.tables.items[0];
  const tableName=existingTable.name,tableStyle=existingTable.style;
  existingTable.delete();
  sheet.getRange('L7:P7').values=[['Drive match status','Drive folder / candidate','Drive filename / candidate','Drive file link','Match details']];
  const newValues=rows.map(r=>{
    const candidate=r.candidates[0];
    let detail=r.match_reason;
    if(!r.mapped_folder&&candidate)detail+=' No corresponding Drive folder for this sheet.';
    if(r.candidates.length>1)detail+=` Other candidate${r.candidates.length>2?'s':''}: `+r.candidates.slice(1).map(c=>c.name+(c.folder!==candidate.folder?` (${c.folder})`:'')).join('; ')+'. All links are in Drive review.';
    return [r.match_status,candidate?.folder||r.mapped_folder||'',candidate?.name||'',null,detail];
  });
  sheet.getRange(`L8:P${last}`).values=newValues;
  sheet.getRange(`L1:P${last}`).format.font={name:'Arial',size:10,color:'#202938'};
  sheet.getRange(`L1:P${last}`).format.verticalAlignment='center';
  for(const [col,width] of Object.entries({L:27,M:28,N:47,O:17,P:65}))sheet.getRange(`${col}1:${col}${last}`).format.columnWidth=width;
  const table=sheet.tables.add(`A7:P${last}`,true,tableName);
  table.style=tableStyle;
  table.showFilterButton=true;
  sheet.getRange('A7:P7').format=headerFormat;
  sheet.getRange(`L8:P${last}`).format.wrapText=true;
  sheet.getRange(`L8:P${last}`).format.horizontalAlignment='left';
  sheet.mergeCells('L2:P2');
  sheet.getRange('L2').values=[['Drive filename comparison']];
  sheet.getRange('L2').format.font={name:'Arial',size:16,bold:true,color:'#293B58'};
  sheet.getRange('L2:P2').format.borders={bottom:{style:'thin',color:'#AEBACC'}};
  for(const n of [3,4,5])sheet.mergeCells(`L${n}:P${n}`);
  if(folderInfo)setLink(sheet,'L3',folderInfo.id,`Drive folder: ${folderInfo.name.trim()}`,true);
  else sheet.getRange('L3').values=[['No corresponding folder found in the linked Drive.']];
  sheet.getRange('L4').values=[[`${counts.matched} same-folder name matches. ${counts.review} candidates to review. ${counts.missing} without a file candidate.`]];
  sheet.getRange('L5').values=[['Checked 19 Oct 2026. Amber = review. Pink = no candidate. Full file list and alternate links are in Drive review.']];
  sheet.getRange('L3:P5').format.font={name:'Arial',size:10,color:'#536274'};
  if(folderInfo)sheet.getRange('L3').format.font={name:'Arial',size:10,color:'#1263A0',underline:'single'};
  for(const [i,row] of rows.entries()){
    const n=i+8;
    if(row.candidates.length){
      setLink(sheet,`O${n}`,row.candidates[0].id,row.candidates.length>1?'Open candidate 1':exact.has(row.match_status)?'Open file':'Open candidate');
      sheet.getRange(`O${n}`).format.font={name:'Arial',size:10,color:'#1263A0',underline:'single'};
    }
    if(reviews.has(row.match_status))sheet.getRange(`L${n}`).format.fill='#FFF0CC';
    if(!row.candidates.length)sheet.getRange(`L${n}`).format.fill='#FCE6E7';
    if(row.match_status.includes('other folder'))sheet.getRange(`M${n}`).format.fill='#FFF0CC';
    if(row.notes.startsWith('CountryWaltz verified')){
      verified++;
      sheet.getRange(`K${n}`).values=[[row.notes]];
      sheet.getRange(`C${n}`).format.fill='#E4F0E7';
      sheet.getRange(`K${n}`).format.fill='#E4F0E7';
    }
  }
  if(rows.some(r=>r.notes.startsWith('CountryWaltz verified'))){
    sheet.getRange(`A${last+3}`).values=[['CountryWaltz: all 30 previously highlighted registrations were verified directly on the keyboard by the user (19 Oct 2026).']];
    sheet.getRange(`A${last+3}`).format.font={name:'Arial',size:10,color:'#376348'};
  }
  sheet.getRange(`A8:P${last}`).format.autofitRows();
  sheet.freezePanes.freezeRows(7);
  sheet.freezePanes.freezeColumns(2);
  if(JSON.stringify(sheet.getRange(`A8:J${last}`).values)!==JSON.stringify(saved))throw new Error(`Original settings changed: ${folder}`);
  checks.push({folder,last,rows:rows.length,...counts});
}
if(verified!==30)throw new Error(`Expected 30 verified CountryWaltz rows, got ${verified}`);

const sheet=wb.worksheets.add('Drive review');
sheet.showGridLines=false;
sheet.tabColor='#64748B';
const folderSummary=Object.keys(data.folder_map).map(name=>{
  const f=data.folder_map[name];
  const c=checks.find(x=>x.folder===name);
  return {name,folder:f?.name||'No matching folder',status:f?'Folder matched':'No matching folder',description:`${c.rows} registrations: ${c.matched} name matches, ${c.review} to review, ${c.missing} without a candidate.`,count:f?data.files.filter(x=>x.folder_id===f.id).length:0,id:f?.id};
});
const mappedIds=new Set(Object.values(data.folder_map).filter(Boolean).map(f=>f.id));
for(const f of inv.filter(f=>!mappedIds.has(f.id))){
  folderSummary.push({name:'(No workbook sheet)',folder:f.path||'(Drive root)',status:f.path?'No corresponding sheet':'Top-level files',description:'Files are included in the inventory below. Some may be candidates for registrations on other sheets.',count:data.files.filter(x=>x.folder_id===f.id).length,id:f.id});
}
const summaryLast=7+folderSummary.length;
const fileHeader=summaryLast+7;
const fileStart=fileHeader+1;
const files=data.files;
const fileLast=fileHeader+files.length;
sheet.getRange(`A1:F${fileLast}`).format.font={name:'Arial',size:10,color:'#202938'};
sheet.getRange(`A1:F${fileLast}`).format.verticalAlignment='center';
sheet.getRange(`A1:F${fileLast}`).format.rowHeight=24;
for(const [col,width] of Object.entries({A:26,B:48,C:27,D:65,E:18,F:55}))sheet.getRange(`${col}1:${col}${fileLast}`).format.columnWidth=width;
sheet.getRange('A1:F1').format.rowHeight=9;
for(const n of [2,3,4,5])sheet.mergeCells(`A${n}:F${n}`);
sheet.getRange('A2').values=[['Drive folder and filename review']];
sheet.getRange('A2').format.font={name:'Arial',size:16,bold:true,color:'#293B58'};
sheet.getRange('A2:F2').format.rowHeight=30;
sheet.getRange('A2:F2').format.borders={bottom:{style:'thin',color:'#AEBACC'}};
sheet.getRange('A3').values=[[`19 Oct 2026. 458 registrations: ${checks.reduce((s,c)=>s+c.matched,0)} same-folder name matches, ${checks.reduce((s,c)=>s+c.review,0)} candidates to review, ${checks.reduce((s,c)=>s+c.missing,0)} without a candidate.`]];
setLink(sheet,'A4',inv[0].id,'Source: ENGLISH CHOIR DATABASE on Google Drive',true);
sheet.getRange('A4').format.font={name:'Arial',size:10,color:'#1263A0',underline:'single'};
sheet.getRange('A5').values=[['Filename comparison only. Similar names and files in other folders need review. No files or registration names have been renamed or moved.']];
sheet.getRange('A6:F6').format.rowHeight=10;
sheet.getRange('A7:F7').values=[['Workbook sheet','Drive folder','Folder status','Registration comparison','Drive file count','Drive folder link']];
sheet.getRange(`A8:F${summaryLast}`).values=folderSummary.map(x=>[x.name,x.folder,x.status,x.description,x.count,null]);
const mappingTable=sheet.tables.add(`A7:F${summaryLast}`,true,'DriveFolderMapping');
mappingTable.style='TableStyleMedium2';mappingTable.showFilterButton=true;
sheet.getRange('A7:F7').format=headerFormat;
for(const [i,row] of folderSummary.entries()){
  if(row.id)setLink(sheet,`F${i+8}`,row.id,'Open folder',true);
  if(row.status!=='Folder matched')sheet.getRange(`C${i+8}`).format.fill='#FFF0CC';
}
sheet.getRange(`F8:F${summaryLast}`).format.font={name:'Arial',size:10,color:'#1263A0',underline:'single'};
sheet.getRange(`E8:E${summaryLast}`).setNumberFormat('0');
sheet.getRange(`A8:F${summaryLast}`).format.wrapText=true;
sheet.getRange(`A8:F${summaryLast}`).format.autofitRows();
sheet.getRange(`D${summaryLast+2}`).values=[['Total files inventoried']];
sheet.getRange(`E${summaryLast+2}`).formulas=[[`=SUM(E8:E${summaryLast})`]];
sheet.getRange(`D${summaryLast+2}:E${summaryLast+2}`).format.font={name:'Arial',size:10,bold:true,color:'#293B58'};
sheet.mergeCells(`A${fileHeader-2}:F${fileHeader-2}`);
sheet.getRange(`A${fileHeader-2}`).values=[['All 322 Drive files. Filter Registration comparison to find unmatched files or unresolved candidates.']];
sheet.getRange(`A${fileHeader-2}`).format.font={name:'Arial',size:12,bold:true,color:'#293B58'};
sheet.getRange(`A${fileHeader}:F${fileHeader}`).values=[['Drive folder','Drive filename','Registration comparison','Related workbook registrations','Drive file link','Review notes']];
sheet.getRange(`A${fileStart}:F${fileLast}`).values=files.map(f=>[
  f.folder,f.name,f.status,f.registrations.join('\n'),null,
  f.status==='No registration match'?'No name-based match or candidate identified in the workbook.':
  f.status==='Needs review'?'Listed as a possible, duplicate or other-folder match. Review before confirming.':
  'Same-folder filename match. Musical content was not compared.'
]);
const fileTable=sheet.tables.add(`A${fileHeader}:F${fileLast}`,true,'DriveFileInventory');
fileTable.style='TableStyleMedium2';fileTable.showFilterButton=true;
sheet.getRange(`A${fileHeader}:F${fileHeader}`).format=headerFormat;
sheet.getRange(`A${fileStart}:F${fileLast}`).format.wrapText=true;
sheet.getRange(`A${fileStart}:F${fileLast}`).format.autofitRows();
for(const [i,file] of files.entries()){
  const n=fileStart+i;
  setLink(sheet,`E${n}`,file.id,'Open file');
  if(file.status==='Needs review')sheet.getRange(`C${n}`).format.fill='#FFF0CC';
  if(file.status==='No registration match')sheet.getRange(`C${n}`).format.fill='#FCE6E7';
}
sheet.getRange(`E${fileStart}:E${fileLast}`).format.font={name:'Arial',size:10,color:'#1263A0',underline:'single'};
sheet.freezePanes.freezeRows(7);

wb.recalculate();
if(sheet.getRange(`E${summaryLast+2}`).values[0][0]!==322)throw new Error('Drive file total is not 322');
const errorScan=await wb.inspect({kind:'match',searchTerm:'#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!',options:{useRegex:true,maxResults:30},summary:'Final error scan',maxChars:2000});
console.log(errorScan.ndjson);
console.log((await wb.inspect({kind:'region',sheetId:'ENTRANCE',range:'L7:P10',maxChars:2500,tableMaxRows:4,tableMaxCols:5})).ndjson);
const xlsx=await SpreadsheetFile.exportXlsx(wb);
await xlsx.save(output);
await fs.writeFile(path.join(work,'drive_hyperlinks.json'),JSON.stringify(links,null,2));
for(const [i,c] of checks.entries()){
  const view=await wb.render({sheetName:c.folder,range:`L2:P${Math.min(c.last,12)}`,scale:1,format:'png'});
  await fs.writeFile(path.join(previewDir,`${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await view.arrayBuffer()));
}
for(const [name,range] of [['review_folders',`A1:F${summaryLast+2}`],['review_files',`A${fileHeader-2}:F${fileHeader+10}`]]){
  const view=await wb.render({sheetName:'Drive review',range,scale:1,format:'png'});
  await fs.writeFile(path.join(previewDir,name+'.png'),new Uint8Array(await view.arrayBuffer()));
}
const confirmation=checks.find(c=>data.rows.some(r=>r.folder===c.folder&&r.notes.startsWith('CountryWaltz verified')));
const confirmedRow=wb.worksheets.getItem(confirmation.folder).getRange(`K8:K${confirmation.last}`).values.findIndex(v=>v[0]?.startsWith('CountryWaltz verified'))+8;
const confirmPreview=await wb.render({sheetName:confirmation.folder,range:`B${confirmedRow}:K${confirmedRow}`,scale:1,format:'png'});
await fs.writeFile(path.join(previewDir,'countrywaltz_verified.png'),new Uint8Array(await confirmPreview.arrayBuffer()));
await fs.writeFile(path.join(work,'drive_workbook_validation.json'),JSON.stringify({checks,verified,sourceFiles:files.length,summaryLast,fileHeader,fileStart,fileLast,output},null,2));
console.log(JSON.stringify({output,registrations:458,verified,matched:checks.reduce((s,c)=>s+c.matched,0),review:checks.reduce((s,c)=>s+c.review,0),missing:checks.reduce((s,c)=>s+c.missing,0),driveFiles:files.length}));

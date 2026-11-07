import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Workbook, SpreadsheetFile} from '@oai/artifact-tool';

const work=path.dirname(fileURLToPath(import.meta.url));
const data=JSON.parse(await fs.readFile(path.join(work,'registrations.json'),'utf8'));
const out=path.join(work,'..','outputs','sx900-registration-extraction');
await fs.mkdir(out,{recursive:true});
await fs.mkdir(path.join(work,'previews'),{recursive:true});
const wb=Workbook.create();
const headers=['Folder','Registration name','Button 1 style','Tempo (BPM)','Transpose (semitones)','Right 1 voice','Right 2 voice','Right 3 voice','Left voice','Active voice parts','Notes'];
const fields=['folder','registration','style','tempo','transpose','right1','right2','right3','left','active_parts','notes'];
const cols=['A','B','C','D','E','F','G','H','I','J','K'];
const widths=[23,43,29,13,17,27,27,27,27,20,53];
const sheetChecks=[];
for (const [index,folder] of Object.keys(data.folder_counts).entries()) {
  const rows=data.rows.filter(r=>r.folder===folder).sort((a,b)=>a.registration.localeCompare(b.registration,undefined,{sensitivity:'base'}));
  const sheet=wb.worksheets.add(folder);
  sheet.showGridLines=false;
  sheet.tabColor='#293B58';
  const last=7+rows.length;
  sheet.getRange(`A1:K${last}`).format.font={name:'Arial',size:10,color:'#202938'};
  sheet.getRange(`A1:K${last}`).format.verticalAlignment='center';
  sheet.getRange(`A1:K${last}`).format.rowHeight=23;
  for (let i=0;i<cols.length;i++) sheet.getRange(`${cols[i]}1:${cols[i]}${last}`).format.columnWidth=widths[i];
  sheet.getRange('A1:K1').format.rowHeight=9;
  sheet.getRange('A2').values=[[`${folder} registrations`]];
  sheet.getRange('A2').format.font={name:'Arial',size:16,bold:true,color:'#293B58'};
  sheet.getRange('A2:K2').format.rowHeight=30;
  sheet.getRange('A2:K2').format.borders={bottom:{style:'thin',color:'#AEBACC'}};
  sheet.getRange('A3').values=[[`${rows.length} registration banks. Settings shown are from button 1.`]];
  sheet.getRange('A4').values=[['Voice columns show saved selections. Active voice parts identifies which selections are enabled.']];
  sheet.getRange('A5').values=[[`Backup: ${data.source_file}`]];
  sheet.getRange('A3:K5').format.font={name:'Arial',size:10,color:'#536274'};
  sheet.getRange('A6:K6').format.rowHeight=10;
  sheet.getRange('A7:K7').values=[headers];
  const values=rows.map(r=>fields.map(f=>r[f]));
  sheet.getRange(`A8:K${last}`).values=values;
  const table=sheet.tables.add(`A7:K${last}`,true,`Registrations${index+1}`);
  table.style='TableStyleMedium2';
  table.showFilterButton=true;
  sheet.getRange('A7:K7').format={fill:'#293B58',font:{name:'Arial',size:10,bold:true,color:'#FFFFFF'},wrapText:true,horizontalAlignment:'center',verticalAlignment:'center',rowHeight:35,borders:{insideVertical:{style:'thin',color:'#FFFFFF'}}};
  sheet.getRange(`A8:K${last}`).format.wrapText=true;
  sheet.getRange(`A8:K${last}`).format.horizontalAlignment='left';
  sheet.getRange(`D8:E${last}`).setNumberFormat('0');
  sheet.getRange(`D8:E${last}`).format.horizontalAlignment='right';
  sheet.freezePanes.freezeRows(7);
  for (let i=0;i<rows.length;i++) {
    if (rows[i].notes) {
      sheet.getRange(`K${i+8}`).format.fill='#FFF0CC';
      sheet.getRange(`C${i+8}`).format.fill='#FFF0CC';
      sheet.getRange(`A${i+8}:K${i+8}`).format.rowHeight=48;
    }
  }
  // Size data rows for long saved names without reducing the font.
  sheet.getRange(`A8:K${last}`).format.autofitRows();
  const required=rows.filter(r=>r.notes).length;
  if (required) {
    const noteRow=last+3;
    sheet.getRange(`A${noteRow}`).values=[['Amber cells: CountryWaltz uses a style-ID mapping from the SX700/Genos lists. Verify those 30 banks on the keyboard.']];
    sheet.getRange(`A${noteRow}`).format.font={name:'Arial',size:10,color:'#805600'};
  }
  // Preserve extraction references in a cell note, away from the data table.
  wb.notes.add({id:`source-${index}`,target:{cell:{sheetName:folder,sheetId:sheet.sheetId,address:'A5'}},authorId:'',createdAt:'',body:{plainText:`Source: ${data.source_file}\nSHA-256: ${data.source_sha256}\nStyle lookup: VoEtSoft MixMaster instrument definitions, https://voetsoft.be/downloads/DefaultInstrumentFiles.zip\nRegistration block reference: https://www.heikoplate.de/pdf/tyros_registrationfiles_deutsch.pdf\nTranspose: saved value minus 64. All three saved transpose fields agree in every bank. Tempo: saved current tempo. Voice names come from saved voice paths; file-type and Yamaha icon suffixes are omitted.\nRows with notes use style ID 2817 = CountryWaltz from SX700 and Genos lists. The SX900 list has 2816 for that name.`}});
  if (JSON.stringify(sheet.getRange(`A8:K${last}`).values)!==JSON.stringify(values)) throw new Error(`Read-back mismatch: ${folder}`);
  sheetChecks.push({folder,rows:rows.length,notes:required,lastRow:last});
}
wb.recalculate();
const summary=await wb.inspect({kind:'sheet',include:'id,name',maxChars:5000});
await fs.writeFile(path.join(work,'workbook_inspection.ndjson'),summary.ndjson);
const errors=await wb.inspect({kind:'match',searchTerm:'#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!',options:{useRegex:true,maxResults:30},summary:'Final error scan',maxChars:2000});
console.log(errors.ndjson);
const xlsx=await SpreadsheetFile.exportXlsx(wb);
const target=path.join(out,'SX900 Registrations.xlsx');
await xlsx.save(target);
console.log(JSON.stringify({target,sheets:sheetChecks,total:sheetChecks.reduce((s,r)=>s+r.rows,0)}));
for (const [i,s] of sheetChecks.entries()) {
  const preview=await wb.render({sheetName:s.folder,range:`A1:K${Math.min(s.lastRow,13)}`,scale:1,format:'png'});
  await fs.writeFile(path.join(work,'previews',`${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await preview.arrayBuffer()));
  console.log(`Rendered ${s.folder}`);
}
await fs.writeFile(path.join(work,'workbook_validation.json'),JSON.stringify(sheetChecks,null,2));

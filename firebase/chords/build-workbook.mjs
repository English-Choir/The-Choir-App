import fs from 'node:fs/promises';
import path from 'node:path';
import { Workbook, SpreadsheetFile } from '@oai/artifact-tool';

const root = process.cwd();
const source = JSON.parse(await fs.readFile(path.join(root,'firebase/chords/source-list.json'),'utf8'));
const output = path.join(root,'outputs/hard-copy-chords');
await fs.mkdir(path.join(output,'previews'),{recursive:true});
const wb = Workbook.create();
let count = 0;
for (const section of source.sections) {
  const sheet = wb.worksheets.add(section.sheet);
  sheet.showGridLines = false;
  sheet.tabColor = '#285EA6';
  sheet.getRange('A1:C1').values = [['Song name','Also match registration','Notes']];
  const values = section.songs.map(name => {
    const reprint = name.match(/\s*\((reprint[^)]*)\)/i);
    return [reprint ? name.replace(reprint[0],'').trim() : name, '', reprint ? reprint[1] : ''];
  });
  const end = values.length + 1;
  sheet.getRange(`A2:C${end}`).values = values;
  sheet.getRange(`A1:C${end}`).format.font.name = 'Aptos';
  sheet.getRange(`A1:C${end}`).format.font.size = 12;
  sheet.getRange(`A1:C${end}`).format.rowHeight = 27;
  sheet.getRange('A1:C1').format.fill = '#163964';
  sheet.getRange('A1:C1').format.font.color = '#FFFFFF';
  sheet.getRange('A1:C1').format.font.bold = true;
  sheet.getRange('A1:C1').format.horizontalAlignment = 'center';
  sheet.getRange('A1:C1').format.rowHeight = 32;
  sheet.getRange('A1:A30').format.columnWidth = 53;
  sheet.getRange('B1:B30').format.columnWidth = 43;
  sheet.getRange('C1:C30').format.columnWidth = 26;
  sheet.getRange(`B2:B${end}`).format.fill = '#FFF5DB';
  sheet.getRange(`A2:C${end}`).format.verticalAlignment = 'center';
  sheet.getRange('E1').values = [[source.source]];
  sheet.getRange('E2').values = [['Add songs below column A. Optional aliases in B: separate names with |.']];
  sheet.getRange('E3').values = [['Matches stay within this section. Host overrides are managed in The Choir app.']];
  sheet.getRange('E1:E3').format.font.color = '#64748B';
  sheet.getRange('E1:E3').format.columnWidth = 100;
  sheet.freezePanes.freezeRows(1);
  if (JSON.stringify(sheet.getRange(`A2:C${end}`).values) !== JSON.stringify(values)) throw Error('Workbook data mismatch');
  count += values.length;
}
if (count !== 135 || source.sections.length !== 13) throw Error('Source count changed');
wb.recalculate();
for(const section of source.sections) {
  const png = await wb.render({sheetName:section.sheet, range:`A1:C${section.songs.length+1}`,scale:1.3,format:'png'});
  await fs.writeFile(path.join(output,'previews',`${section.id}.png`),new Uint8Array(await png.arrayBuffer()));
}
const errors = await wb.inspect({kind:'match',searchTerm:'#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!',options:{useRegex:true,maxResults:20}});
await fs.writeFile(path.join(output,'workbook-check.json'),JSON.stringify({songs:count,sections:source.sections.map(s=>({name:s.sheet,count:s.songs.length})),errors:errors.ndjson},null,2));
await (await SpreadsheetFile.exportXlsx(wb)).save(path.join(output,'Hard copy chord list.xlsx'));
console.log(JSON.stringify({songs:count,sheets:13,file:path.join(output,'Hard copy chord list.xlsx')}));

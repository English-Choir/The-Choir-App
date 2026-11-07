import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {FileBlob,SpreadsheetFile} from '@oai/artifact-tool';
const file=new URL('../outputs/sx900-registration-extraction/SX900 Registrations.xlsx',import.meta.url);
const wb=await SpreadsheetFile.importXlsx(await FileBlob.load(fileURLToPath(file)));
console.log((await wb.inspect({kind:'sheet',include:'id,name',maxChars:3000})).ndjson);
console.log(wb.help('*',{search:'hyperlink|cell.links|range.links',include:'index,examples,notes',maxChars:6500}).ndjson);
const preview=await wb.render({sheetName:'ENTRANCE',range:'A1:E13',scale:1,format:'png'});
await fs.writeFile(new URL('before_matching.png',import.meta.url),new Uint8Array(await preview.arrayBuffer()));

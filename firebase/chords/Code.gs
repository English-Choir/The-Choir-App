/** Syncs Hard copy chord list. Runs only as English Choir; no credentials in this source. */
const CHOIR_SHEET = '1IBbT9Btrdr6Pivlwfp5HI-CSmUJitiPJWnDTx_o-b7M';
const CHOIR_OWNER = 'englishchoir00@gmail.com';
const CHOIR_PROJECT = 'the-choir-app-39f0a';
const CHOIR_ROOT = 'projects/' + CHOIR_PROJECT + '/databases/(default)/documents';
const CHOIR_API = 'https://firestore.googleapis.com/v1/' + CHOIR_ROOT;
const CHOIR_SECTIONS = [
  ['entrance','ENTRANCE'],['lhm','LORD HAVE MERCY'],['glory','GLORY'],['response','RESPONSE'],
  ['alleluia','ALLELUIA'],['offertory','OFFERTORY'],['holy','HOLY'],['proclamation','PROCLAMATION'],
  ['our-father','OUR FATHER'],['log','LAMB OF GOD'],['communion','COMMUNION'],
  ['thanks-giving','THANKSGIVING'],['recessional','RECESSIONAL'],['adoration','ADORATION'],
  ['ash','ASH'],['mission-wedding','MISSION WEDDING'],['sob','SOB'],['way-of-the-cross','WAY OF THE CROSS'],
  ['we-believe','WE BELIEVE'],['z-grade','Z-GRADE']
];

function installSync() {
  choirOwner_();
  syncChords();
  const handlers = ['syncChords','chordSheetEdited'];
  ScriptApp.getProjectTriggers().filter(t => handlers.indexOf(t.getHandlerFunction()) >= 0)
    .forEach(t => ScriptApp.deleteTrigger(t));
  ScriptApp.newTrigger('syncChords').timeBased().everyMinutes(1).create();
  ScriptApp.newTrigger('chordSheetEdited').forSpreadsheet(CHOIR_SHEET).onEdit().create();
  console.log('Automatic chord sync enabled: sheet edits and a one-minute reconciliation.');
}

function chordSheetEdited(event) {
  if (!event || !event.range || event.range.getColumn() <= 3) syncChords();
}

function choirOwner_() {
  if (Session.getEffectiveUser().getEmail().toLowerCase() !== CHOIR_OWNER) {
    throw Error('This sync must run as ' + CHOIR_OWNER + '.');
  }
}

function chordKey_(name) {
  return String(name).normalize('NFD').replace(/[\u0300-\u036f]+/g,'').toLowerCase()
    .replace(/\s*\((?:lhm|holy|log|comm|communion|offertory|recc|recessional|proclamation|thanks|reprint[^)]*)\)/g,'')
    .replace(/&/g,'and').replace(/[^a-z0-9]/g,'');
}

function chordNames_(name) {
  const alternate = String(name).match(/^(.*?)\s*\(([^)]+)\)\s*$/);
  return alternate && alternate[2].trim().split(/\s+/).length >= 2 && !/^reprint/i.test(alternate[2])
    ? [name,alternate[1].trim(),alternate[2].trim()] : [name];
}

function chordMatches_(song, name) {
  const key = chordKey_(name);
  return [song.name].concat(song.aliases).some(n => chordNames_(n).some(v => chordKey_(v) === key));
}

function choirReadSheets_() {
  const custom = choirRequest_(CHOIR_API + ':runQuery','post',{
    structuredQuery:{from:[{collectionId:'sections'}]}
  }).filter(item=>item.document).map(item=>choirDecode_(item.document.fields));
  const sections = CHOIR_SECTIONS.concat(custom.filter(s=>s.other === true && /^section-[a-f0-9]{64}$/.test(s.id))
    .sort((a,b)=>a.id.localeCompare(b.id)).map(s=>[s.id,choirCustomSheet_(s)]));
  // One batched read avoids dozens of SpreadsheetApp round trips on every trigger.
  const snapshot = Sheets.Spreadsheets.get(CHOIR_SHEET, {
    fields:'sheets(properties(title),data(rowData(values(formattedValue))))'
  });
  return sections.map((section,index) => {
    const sheet = snapshot.sheets.find(s=>s.properties.title === section[1]);
    if (!sheet && index < 13) throw Error('Missing required tab ' + section[1] + '. Restore its name to resume sync.');
    if (!sheet) return {id:section[0],sheet:section[1],songs:[]};
    const values = (sheet.data && sheet.data[0] && sheet.data[0].rowData || []).map(row=>
      [0,1,2].map(i=>row.values && row.values[i] && row.values[i].formattedValue || ''));
    const headers = values[0] || [];
    if (headers[0] !== 'Song name' || headers[1] !== 'Also match registration' || headers[2] !== 'Notes')
      throw Error('Restore the three column headings in ' + section[1] + ' to resume sync.');
    if (values.length > 5000) throw Error('Too many rows in ' + section[1]);
    const rows = values.slice(1);
    const songs = rows.filter(row => row[0].trim()).map(row => {
      if (row[0].length > 250 || row[1].length > 2000) throw Error('Song or aliases too long in ' + section[1]);
      return {name:row[0].trim(),aliases:row[1].split('|').map(n=>n.trim()).filter(Boolean),notes:row[2].slice(0,500)};
    });
    return {id:section[0],sheet:section[1],songs:songs};
  });
}

function choirCustomSheet_(section) {
  // Stable suffix keeps equally named/sanitized tabs separate from the original sections.
  return String(section.title).replace(/[\\/\[\]*?:]/g,' ').trim().slice(0,60) + ' - ' + section.id.slice(-16);
}

function syncChords(event) {
  choirOwner_();
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(3000)) return;
  try {
    const props = PropertiesService.getScriptProperties();
    const settings = props.getProperties();
    const now = new Date();
    const pending = choirRequest_(CHOIR_API + ':runQuery','post',{
      structuredQuery:{from:[{collectionId:'chordOverrides'}],
        where:{fieldFilter:{field:{fieldPath:'appendPending'},op:'EQUAL',value:{booleanValue:true}}},limit:100}
    }).filter(item=>item.document).map(item=>item.document);
    // Edits refresh immediately. Idle timers only check host requests; a full five-minute
    // refresh also catches edits made through APIs, which do not fire sheet edit triggers.
    if (event && event.triggerUid && pending.length === 0 && !settings.lastError &&
        now.getTime() - Number(settings.lastSnapshotAt || 0) < 5*60*1000) return;
    let book = null;
    let sections = choirReadSheets_(); // Validate the entire list before changing anything.
    let added = 0;
    pending.forEach(request => {
      const data = choirDecode_(request.fields);
      if (data.mode !== 'enabled') return;
      const host = choirRequest_(CHOIR_API + '/hosts/' + encodeURIComponent(data.updatedBy),'get',null,true);
      if (!host || choirDecode_(host.fields).active !== true) return;
      const id = request.name.split('/').pop();
      const regDoc = choirRequest_(CHOIR_API + '/registrations/' + encodeURIComponent(id),'get',null,true);
      const row = regDoc ? choirDecode_(regDoc.fields) : null;
      if (row && !row.deleted) {
        const section = sections.find(s=>s.id === row.sectionId);
        if (!section) throw Error('Unknown registration section');
        if (!section.songs.some(song=>chordMatches_(song,row.name))) {
          // Re-read before append: a host may have disabled the control during this run.
          const latest = choirRequest_(CHOIR_API + '/chordOverrides/' + encodeURIComponent(id),'get');
          if (latest.updateTime !== request.updateTime) return;
          if (!book) book = SpreadsheetApp.openById(CHOIR_SHEET);
          let sheet = book.getSheetByName(section.sheet);
          if (!sheet) {
            sheet = book.insertSheet(section.sheet);
            sheet.getRange(1,1,1,3).setValues([['Song name','Also match registration','Notes']]);
            sheet.setFrozenRows(1); sheet.setColumnWidths(1,2,340); sheet.setColumnWidth(3,220);
            sheet.getRange(1,1,1,3).setBackground('#163964').setFontColor('#ffffff').setFontWeight('bold');
          }
          // Treat host names as literal text, including names beginning with formula characters.
          const values = sheet.getRange(1,1,Math.max(sheet.getLastRow(),1),1).getDisplayValues();
          let lastSongRow = values.length;
          while (lastSongRow > 1 && !values[lastSongRow-1][0].trim()) lastSongRow--;
          sheet.getRange(lastSongRow+1,1).setNumberFormat('@').setValue("'" + row.name);
          sheet.getRange(lastSongRow+1,3).setValue('Added by host in The Choir');
          SpreadsheetApp.flush();
          section.songs.push({name:row.name,aliases:[],notes:'Added by host in The Choir'});
          added++;
        }
      }
      // A retry after a partial failure rechecks the sheet, so it never appends the same name twice.
      choirRequest_(CHOIR_API + ':commit','post',{writes:[{
        update:{name:request.name,fields:{appendPending:{booleanValue:false}}},
        updateMask:{fieldPaths:['appendPending']}, currentDocument:{updateTime:request.updateTime}
      }]},false,true);
    });
    if (added) sections = choirReadSheets_();
    const serialized = JSON.stringify(sections);
    if (Utilities.newBlob(serialized).getBytes().length > 700000) throw Error('Chord list is too large to sync safely.');
    const digest = Utilities.base64Encode(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256,serialized));
    if (settings.lastPublishedHash !== digest) {
      choirWrite_('chordCatalog/current',{sections:sections,spreadsheetId:CHOIR_SHEET,
        sheetUrl:'https://docs.google.com/spreadsheets/d/'+CHOIR_SHEET+'/edit',syncEnabled:true,syncedAt:now});
      props.setProperty('lastPublishedHash',digest);
    }
    props.setProperty('lastSnapshotAt',String(now.getTime()));
    // Heartbeat lets the app distinguish an old unchanged list from a failed background sync.
    const lastHeartbeat = Number(settings.lastHeartbeat || 0);
    if (now.getTime()-lastHeartbeat > 15*60*1000 || settings.lastError) {
      choirWrite_('chordSync/status',{lastSuccessAt:now,status:'ok',message:''});
      props.setProperty('lastHeartbeat',String(now.getTime())); props.deleteProperty('lastError');
    }
    console.log('Synced '+sections.reduce((n,s)=>n+s.songs.length,0)+' songs; '+added+' host additions.');
  } catch (error) {
    PropertiesService.getScriptProperties().setProperty('lastError',String(error.message).slice(0,500));
    try { choirWrite_('chordSync/status',{status:'error',message:'Google Sheets sync needs attention. Open the sync project and run syncChords.',failedAt:new Date()}); } catch (_) {}
    throw error;
  } finally { lock.releaseLock(); }
}

function choirRequest_(url,method,data,allowMissing,allowConflict) {
  const options = {method:method,headers:{Authorization:'Bearer '+ScriptApp.getOAuthToken()},muteHttpExceptions:true};
  if (data != null) {options.contentType='application/json';options.payload=JSON.stringify(data);}
  const response = UrlFetchApp.fetch(url,options);
  const status = response.getResponseCode();
  const result = JSON.parse(response.getContentText() || '{}');
  if (allowMissing && status === 404) return null;
  if (allowConflict && (status === 409 || status === 412)) return null;
  if (status < 200 || status >= 300) throw Error('Firestore '+status+': '+(result.error && result.error.message || 'Request failed'));
  return result;
}

function choirWrite_(path,data) {
  return choirRequest_(CHOIR_API+':commit','post',{writes:[{update:{name:CHOIR_ROOT+'/'+path,fields:choirFields_(data)}}]});
}
function choirFields_(data) {
  const result = {};
  Object.keys(data).forEach(key=>result[key]=choirValue_(data[key]));
  return result;
}
function choirValue_(value) {
  if (value instanceof Date) return {timestampValue:value.toISOString()};
  if (typeof value === 'string') return {stringValue:value};
  if (typeof value === 'boolean') return {booleanValue:value};
  if (typeof value === 'number') return {integerValue:String(value)};
  if (Array.isArray(value)) return {arrayValue:{values:value.map(choirValue_)}};
  return {mapValue:{fields:choirFields_(value)}};
}
function choirDecode_(fields) {
  const result = {};
  Object.keys(fields || {}).forEach(key => {
    const value = fields[key];
    result[key] = value.stringValue !== undefined ? value.stringValue : value.booleanValue !== undefined ? value.booleanValue
      : value.integerValue !== undefined ? Number(value.integerValue) : value.timestampValue;
  });
  return result;
}

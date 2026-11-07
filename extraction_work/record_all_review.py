import json,shutil
from pathlib import Path
from collections import Counter
from openpyxl import load_workbook

p=Path(__file__).resolve().parent
data=json.loads((p/'drive_matches.json').read_text(encoding='utf-8'))
validation=json.loads((p/'drive_workbook_validation.json').read_text(encoding='utf-8'))
links=json.loads((p/'drive_hyperlinks.json').read_text(encoding='utf-8'))
decisions=json.loads((p/'drive_decisions.json').read_text(encoding='utf-8'))
target=Path(validation['output'])
backup=p/'SX900 Registrations.before-all-sections-review.xlsx.bak'
if not backup.exists():shutil.copy2(target,backup)
w=load_workbook(target,data_only=False)
pending={'Possible name match','Name in other folder','Possible in other folder','Multiple name matches'}
accepted={'Matched name','Matched formatting','Confirmed match','Confirmed other folder','Confirmed multiple files'}
missing_keys={('LOG','Peace I give to you'),('OUR FATHER','Stanes')}
updates=[]; affected=set(); new_decisions=[]; changed_folders=[]
for row in data['rows']:
    if row['match_status'] not in pending:continue
    folder=row['folder']; name=row['registration']; key=(folder,name.strip())
    if folder not in changed_folders:changed_folders.append(folder)
    n=next(i for i in range(8,w[folder].max_row+1) if w[folder].cell(i,2).value==name)
    assert w[folder].cell(n,12).value==row['match_status']
    affected.update(c['id'] for c in row['candidates'])
    if key in missing_keys:
        row['candidates']=[]
        row['match_status']='Missing file (confirmed)'
        reason='User confirmed no Drive link for this registration (19 Oct 2026).'
    else:
        if key==('RECESSIONAL','Irish'):
            row['candidates']=[c for c in row['candidates'] if c['folder'].strip()=='7 HOLY']
            assert len(row['candidates'])==1 and row['candidates'][0]['id']=='1bbjzfNMGLuGf-5IoGMdmv5FvFga3U0hN'
        if len(row['candidates'])>1:
            row['match_status']='Confirmed multiple files'
            reason='User accepted the listed file candidates (19 Oct 2026). Both retained.'
        elif row['candidates'][0]['folder_id']!=row['mapped_folder_id']:
            row['match_status']='Confirmed other folder'
            reason='Pairing confirmed by user (19 Oct 2026). File remains in '+row['candidates'][0]['folder'].strip()+'.'
        else:
            row['match_status']='Confirmed match'
            reason='Pairing confirmed by user (19 Oct 2026).'
    row['match_reason']=reason
    if len(row['candidates'])>1:
        reason+=' Additional file: '+ '; '.join(c['name'].strip() for c in row['candidates'][1:])+'. All links are in Drive review.'
    candidate=row['candidates'][0] if row['candidates'] else None
    label=('Open file 1' if len(row['candidates'])>1 else 'Open file') if candidate else None
    values=[row['match_status'],candidate['folder'] if candidate else row['mapped_folder'],candidate['name'] if candidate else None,label,reason]
    updates.append({'folder':folder,'row':n,'missing':not bool(candidate),'values':values})
    links=[l for l in links if not(l['sheet']==folder and l['address']==f'O{n}')]
    if candidate:
        url=f"https://docs.google.com/document/d/{candidate['id']}/edit" if 'Google Docs' in candidate['label'] else f"https://drive.google.com/file/d/{candidate['id']}/view"
        links.append({'sheet':folder,'address':f'O{n}','label':label,'url':url})
    new_decisions.append({'folder':folder,'registration':name,'decision':'missing' if not candidate else 'confirmed','file_ids':[c['id'] for c in row['candidates']],'date':'2026-10-19','source':'User: LOG 7 does not have a drive link; Recessional 11 Use HOLY; ourfather 3 no link; Others are fine.'})
assert len(updates)==148 and sum(u['missing'] for u in updates)==2
assert len([u for u in updates if u['values'][0]=='Confirmed multiple files'])==6
review_updates=[]
for file in data['files']:
    if file['id'] not in affected:continue
    related=[r for r in data['rows'] if any(c['id']==file['id'] for c in r['candidates'])]
    file['registrations']=[f"{r['folder']}: {r['registration']}" for r in related]
    confirmed=[r for r in related if r['match_status'].startswith('Confirmed')]
    if confirmed:
        file['status']='Confirmed match'
        note='User confirmed: '+ '; '.join(f"{r['folder']}: {r['registration'].strip()}" for r in confirmed)+'.'
        if len(related)>len(confirmed):note+=' Other pairings retain their individual sheet statuses.'
    elif any(r['match_status'] in {'Matched name','Matched formatting'} for r in related):
        file['status']='Matched name';note='Same-folder filename match. Musical content was not compared.'
    else:
        file['status']='Needs review' if related else 'No registration match'
        note='Review the related registrations before confirming.' if related else 'No matching registration confirmed. Previously suggested association was rejected by user.'
    file['review_note']=note
    review_updates.append({'row':data['files'].index(file)+validation['fileStart'],'values':[file['status'],'\n'.join(file['registrations'])],'note':note})
data['counts']=dict(Counter(r['match_status'] for r in data['rows']))
matched=sum(r['match_status'] in accepted for r in data['rows'])
missing=sum(not r['candidates'] for r in data['rows'])
review=len(data['rows'])-matched-missing
assert (matched,review,missing)==(292,0,166)
summaries=[]
for check in validation['checks']:
    folder=check['folder']
    if folder not in changed_folders:continue
    rows=[r for r in data['rows'] if r['folder']==folder]
    matched=sum(r['match_status'] in accepted for r in rows)
    missing=sum(not r['candidates'] for r in rows)
    multiple=sum(len(r['candidates'])>1 for r in rows)
    other=sum(bool(r['candidates']) and all(c['folder_id']!=r['mapped_folder_id'] for c in r['candidates']) for r in rows)
    check.update(matched=matched,review=0,missing=missing)
    summary_row=next(i for i in range(8,validation['summaryLast']+1) if w['Drive review'].cell(i,1).value==folder)
    summaries.append({'folder':folder,'summary_row':summary_row,'header':f'{matched} matched or confirmed. {other} in other folders. {missing} without a Drive file match.','description':f'{len(rows)} registrations: {matched} matched or confirmed, 0 to review, {missing} without a candidate.','multiple':multiple})
changed={(d['folder'],d['registration']) for d in new_decisions}
decisions['decisions']=[d for d in decisions['decisions'] if (d['folder'],d['registration']) not in changed]+new_decisions
patch={'updates':updates,'review_updates':review_updates,'summaries':summaries,'overall':{'matched':292,'review':0,'missing':166},'backup':str(backup),'output':str(target)}
for name,value in [('drive_matches.json',data),('drive_hyperlinks.json',links),('drive_workbook_validation.json',validation),('drive_decisions.json',decisions),('all_review_patch.json',patch)]:
    (p/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'updated':len(updates),'confirmed':146,'missing':2,'accepted_multiple_files':6,'links':len(links),'changed_folders':len(summaries),'overall':patch['overall']},indent=2))

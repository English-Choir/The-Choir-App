import json,shutil
from pathlib import Path
from collections import Counter
from openpyxl import load_workbook

p=Path(__file__).resolve().parent
target=p.parent/'outputs/sx900-registration-extraction/SX900 Registrations.xlsx'
backup=p/'SX900 Registrations.before-entrance-review.xlsx.bak'
if not backup.exists():shutil.copy2(target,backup)
data=json.loads((p/'drive_matches.json').read_text(encoding='utf-8'))
links=json.loads((p/'drive_hyperlinks.json').read_text(encoding='utf-8'))
validation=json.loads((p/'drive_workbook_validation.json').read_text(encoding='utf-8'))
confirmed=['Call to walk','Come on celebrate','Come people of the risen','Hail the day','How lovely','It came upon','Jesus stand among','Jesus your the sweetest','Lord I lift','Oh what a wonderful','Standing on the promise','We are here because of grace','We have come into his','Come ring out your joy','Holy spirit we welcome']
selected=set(confirmed+['As we gather may'])
w=load_workbook(target,data_only=False)
updates=[]; decisions=[]; affected=set()
for row in data['rows']:
    if row['folder']!='ENTRANCE' or row['registration'].strip() not in selected:continue
    affected.update(c['id'] for c in row['candidates'])
    missing=row['registration'].strip()=='As we gather may'
    n=next(i for i in range(8,55) if w['ENTRANCE'].cell(i,2).value==row['registration'])
    if missing:
        row['candidates']=[]
        row['match_status']='Missing file (confirmed)'
        row['match_reason']='User confirmed that this registration has no Drive file (19 Oct 2026).'
    else:
        assert len(row['candidates'])==1
        other=row['candidates'][0]['folder_id']!=row['mapped_folder_id']
        row['match_status']='Confirmed other folder' if other else 'Confirmed match'
        row['match_reason']='Pairing confirmed by user (19 Oct 2026).'
        if other:row['match_reason']+=' File remains in '+row['candidates'][0]['folder'].strip()+'.'
    updates.append({'row':n,'missing':missing,'status':row['match_status'],'reason':row['match_reason']})
    decisions.append({'folder':'ENTRANCE','registration':row['registration'],'decision':'missing' if missing else 'confirmed','file_ids':[c['id'] for c in row['candidates']],'date':'2026-10-19','source':'User: P1 is not right it doesnt have a drive file; rest is fine.'})
    for link in links:
        if link['sheet']=='ENTRANCE' and link['address']==f'O{n}':link['label']='Open file'
assert len(updates)==16
removed={f"O{x['row']}" for x in updates if x['missing']}
links=[x for x in links if not(x['sheet']=='ENTRANCE' and x['address'] in removed)]
reviewUpdates=[]
for file in data['files']:
    if file['id'] not in affected:continue
    related=[r for r in data['rows'] if any(c['id']==file['id'] for c in r['candidates'])]
    file['registrations']=[f"{r['folder']}: {r['registration']}" for r in related]
    confirmed_rows=[r for r in related if r['match_status'].startswith('Confirmed')]
    if confirmed_rows:
        file['status']='Confirmed match'
        note='User confirmed: '+ '; '.join(f"{r['folder']}: {r['registration'].strip()}" for r in confirmed_rows)+'.'
        if len(related)>len(confirmed_rows):note+=' Other pairings retain their individual sheet statuses.'
    elif any(r['match_status'] in ['Matched name','Matched formatting'] for r in related):
        file['status']='Matched name';note='Same-folder filename match. Musical content was not compared.'
    else:
        file['status']='Needs review' if related else 'No registration match'
        note='Review the related registrations before confirming.' if related else 'No name-based match or candidate identified in the workbook.'
    file['review_note']=note
    i=data['files'].index(file)+validation['fileStart']
    reviewUpdates.append({'row':i,'status':file['status'],'registrations':'\n'.join(file['registrations']),'note':note})
data['counts']=dict(Counter(r['match_status'] for r in data['rows']))
accepted={'Matched name','Matched formatting','Confirmed match','Confirmed other folder'}
matched=sum(r['match_status'] in accepted for r in data['rows'])
missing=sum(not r['candidates'] for r in data['rows'])
review=len(data['rows'])-matched-missing
assert (matched,review,missing)==(146,148,164)
for check in validation['checks']:
    if check['folder']=='ENTRANCE':check.update(matched=29,review=0,missing=18)
existing=json.loads((p/'drive_decisions.json').read_text(encoding='utf-8')) if (p/'drive_decisions.json').exists() else {'decisions':[]}
existing['decisions']=[d for d in existing['decisions'] if not(d['folder']=='ENTRANCE' and d['registration'].strip() in selected)]+decisions
patch={'updates':updates,'review_updates':reviewUpdates,'overall':{'matched':matched,'review':review,'missing':missing},'backup':str(backup),'output':str(target)}
for name,value in [('drive_matches.json',data),('drive_hyperlinks.json',links),('drive_workbook_validation.json',validation),('drive_decisions.json',existing),('entrance_review_patch.json',patch)]:
    (p/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'confirmed':15,'missing_file_confirmed':1,'entrance_remaining_missing':18,'affected_drive_files':len(reviewUpdates),'links':len(links)}))

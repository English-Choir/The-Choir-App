import json, re, unicodedata
from pathlib import Path
from difflib import SequenceMatcher
from collections import Counter

work=Path(__file__).resolve().parent
data=json.loads((work/'registrations.json').read_text(encoding='utf-8'))
inventory=json.loads((work/'drive_inventory.json').read_text(encoding='utf-8'))
for folder in inventory:
    assert len(folder['rows'])==int(folder['rowcount']),folder['name']
files=[dict(r,folder=f['path'] or '(Drive root)',folder_id=f['id']) for f in inventory for r in f['rows'] if not r['label'].endswith('folder')]
assert len({f['id'] for f in files})==len(files)
def stem(s):
    return re.sub(r'\.(pdf|docx?|rtf|txt|rgt|xlsx?|pptx?|jpe?g|png)$','',s.strip(),flags=re.I).strip()
def simple(s):
    return re.sub(r'\s+',' ',stem(s)).casefold()
def norm(s):
    s=unicodedata.normalize('NFKD',simple(s)).replace('&',' and ')
    return ''.join(c for c in s if c.isalnum())
def tokens(s):
    return set(re.findall(r'[a-z0-9]+',simple(s)))
def similarity(a,b):
    a,b=norm(a),norm(b)
    return SequenceMatcher(None,a,b).ratio()
def reduced(s):
    s=stem(s)
    s=re.sub(r'\((LHM|HOLY|LOG|COMMUNION|COMM|PROCLAMATION|OFFERTORY|THANKS|RECC|Acclamation|Alleluia)\)', '',s,flags=re.I)
    s=re.sub(r'\b(LHM|HOLY|LOG|OFFERTORY)\s*$','',s,flags=re.I)
    return norm(s)
def candidate_score(a,b,global_search=False):
    aa,bb=reduced(a),reduced(b)
    if aa and aa==bb:
        return .995
    score=similarity(a,b)
    minimum=12 if global_search else 8
    short,long=sorted((aa,bb),key=len)
    if len(short)>=minimum and short in long:
        score=max(score,.90+.05*len(short)/len(long))
    threshold=.89 if global_search else (.82 if len(norm(a))>=12 else .9)
    return score if score>=threshold else 0
aliases={'LHM':'LORD HAVE MERCY','LOG':'LAMB OF GOD'}
# These candidates were reviewed against the observed filenames. They remain
# suggestions, not confirmed song identities or authorization to rename files.
manual={
 ('GLORY','Pruning'):[('3 GLORY','Prunning Glory')],
 ('GLORY','Glory to the trinity'):[('3 GLORY','Trinity.pdf')],
 ('GLORY','St.Fransisco'):[('3 GLORY','Francisco')],
 ('RESPONSE','How good'):[('4 RESPONSE','How good is the God we adore')],
 ('RESPONSE','Knowing You'):[('11 COMMUNION','Knowing You,   Jesus')],
 ('ALLELUIA','Seek ye (Alleluia)'):[('5 ALLELUIA','Seek ye first.pdf')],
 ('ALLELUIA','Shrek'):[('5 ALLELUIA','Shrek (Hallelujah)')],
 ('ALLELUIA','This day God made'):[('5 ALLELUIA','This day.pdf')],
 ('OFFERTORY','This my desire (OFFERTORY)'):[('4 RESPONSE','This is my desire')],
 ('OFFERTORY','I offer you oh lord'):[('6 OFFERTORY','I offer you Lord this day.pdf')],
 ('OFFERTORY','I Bring an offering of praise'):[('6 OFFERTORY','I bring an offering of worship (The Sun)')],
 ('HOLY','Aicha (Holy)'):[('7 HOLY','Aicha Aicha HOLY')],
 ('HOLY','MGC'):[('7 HOLY','MGC Holy..pdf')],
 ('LOG','MGC'):[('10 LAMB OF GOD',"Mass of God's children.pdf")],
 ('COMMUNION','Candle'):[('11 COMMUNION','Carry your candle')],
 ('COMMUNION','Take eat'):[('11 COMMUNION','Take and eat'),('11 COMMUNION','Take eat and hunger.pdf')],
 ('OFFERTORY','Take eat'):[('11 COMMUNION','Take and eat'),('11 COMMUNION','Take eat and hunger.pdf')],
 ('COMMUNION','Make me channel of your peace'):[('11 COMMUNION','Make me a channel of Your peace (Chorus added)')],
 ('COMMUNION','Don Moen Our father'):[('11 COMMUNION','Our Father by Don Moen')],
 ('THANKS GIVING','There is a roof'):[('12 THANKS GIVING',"There's a roof up above me")],
 ('THANKS GIVING','Give thanks - Don Moen'):[('12 THANKS GIVING','Give thanks.pdf')],
 ('THANKS GIVING','I will give thanks to you (Be Exalted)'):[('12 THANKS GIVING','I will give thanks (Be Exalted).pdf')],
 ('OUR FATHER','Echo'):[('9 OUR FATHER','Our Father Echo.pdf')],
 ('OUR FATHER',"Tony's"):[('9 OUR FATHER','Tony.pdf')],
 ('Ash','We rise from ashes'):[('4 RESPONSE','We rise again from the ashes.pdf')],
 ('Way of the cross','God of mercy'):[('11 COMMUNION','God of mercy and compassion')],
 ('Way of the cross','Have thine'):[('11 COMMUNION','Have thine own way')],
 ('Mission Wedding','Shrek'):[('5 ALLELUIA','Shrek (Hallelujah)')],
 ('Mission Wedding','Echo'):[('9 OUR FATHER','Our Father Echo.pdf')],
 ('Mission Wedding','The voice (Eden)'):[('1 ENTRANCE','The voice that breathed o’er Eden')],
}
folder_map={}
for folder in data['folder_counts']:
    matches=[f for f in inventory[1:] if norm(re.sub(r'^\d+\s+','',f['name']))==norm(aliases.get(folder,folder))]
    assert len(matches)<=1
    folder_map[folder]=matches[0] if matches else None
rows=[]
for original in data['rows']:
    row=dict(original)
    folder=folder_map[row['folder']]
    local=[f for f in files if folder and f['folder_id']==folder['id']]
    exact=[f for f in local if simple(f['name'])==simple(row['registration'])]
    normalized=[f for f in local if norm(f['name'])==norm(row['registration'])]
    elsewhere=[f for f in files if f not in local and norm(f['name'])==norm(row['registration'])]
    candidates=[]
    if exact:
        candidates=exact; status='Matched name' if len(exact)==1 else 'Multiple name matches'
        reason='Same name, ignoring case, spaces and file extension.'
    elif normalized:
        candidates=normalized; status='Matched formatting' if len(normalized)==1 else 'Multiple name matches'
        reason='Same name after ignoring punctuation and spacing.'
    else:
        scores=sorted([(candidate_score(row['registration'],f['name']),f) for f in local],key=lambda x:x[0],reverse=True)
        if scores and scores[0][0]>0:
            candidates=[f for score,f in scores if score>0 and score>=scores[0][0]-.035][:3]
            status='Possible name match'
            reason='Similar, shortened or annotated name in the mapped folder. Review before confirming.'
        elif elsewhere:
            candidates=elsewhere; status='Name in other folder'
            reason='Matching name outside the mapped Drive folder.' if folder else 'Matching name found, but this sheet has no corresponding Drive folder.'
        else:
            scores=sorted([(candidate_score(row['registration'],f['name'],True),f) for f in files if f not in local],key=lambda x:x[0],reverse=True)
            if scores and scores[0][0]>0:
                candidates=[f for score,f in scores if score>0 and score>=scores[0][0]-.025][:3]
                status='Possible in other folder'
                reason='Similar or shortened name outside the mapped folder. Review song identity and folder.'
            else:
                status='No filename match' if folder else 'No matching folder'
                reason='No sufficiently close filename found.' if folder else 'No corresponding folder or sufficiently close filename found in the linked Drive.'
    override=manual.get((row['folder'],row['registration'].strip()))
    if override:
        candidates=[]
        for target_folder,target_name in override:
            found=[f for f in files if f['folder'].strip()==target_folder and f['name'].strip()==target_name]
            assert len(found)==1,(target_folder,target_name)
            candidates.extend(found)
        status='Possible name match' if all(c in local for c in candidates) else 'Possible in other folder'
        reason='Filename suggests a shortened title, spelling difference or variant. Review before confirming.'
        if status=='Possible in other folder': reason+=' Candidate is in a different folder.'
    row.update(mapped_folder=folder['name'] if folder else '',mapped_folder_id=folder['id'] if folder else '',match_status=status,match_reason=reason,candidates=candidates)
    if row['style']=='CountryWaltz' and row['notes']:
        row['notes']='CountryWaltz verified directly on keyboard by user (19 Oct 2026).'
    rows.append(row)
used={c['id'] for r in rows if r['match_status'] in ('Matched name','Matched formatting') for c in r['candidates']}
suggested={c['id'] for r in rows if r['match_status'] not in ('Matched name','Matched formatting') for c in r['candidates']}
review=[]
for f in files:
    linked=[r for r in rows if any(c['id']==f['id'] for c in r['candidates'])]
    review.append(dict(f,status='Matched name' if f['id'] in used else ('Needs review' if f['id'] in suggested else 'No registration match'),registrations=[f"{r['folder']}: {r['registration']}" for r in linked]))
result={'checked_date':'2026-10-19','source_url':'https://drive.google.com/drive/folders/1iJFcDoMNKBA4CgSr96da5-kTWNubKsnW','folder_map':{k:({a:v[a] for a in ('id','name')} if v else None) for k,v in folder_map.items()},'files':review,'rows':rows,'counts':dict(Counter(r['match_status'] for r in rows))}
(work/'drive_matches.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'counts':result['counts'],'files':len(files),'file_counts':dict(Counter(f['status'] for f in review))},indent=2))
for folder in folder_map:
    print('\n'+folder)
    for row in rows:
        if row['folder']==folder and row['match_status'] not in ('Matched name','Matched formatting'):
            print(row['match_status']+' | '+row['registration']+' | '+'; '.join(f"{c['folder']}/{c['name']}" for c in row['candidates']))

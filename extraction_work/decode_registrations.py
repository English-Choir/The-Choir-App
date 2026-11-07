from pathlib import Path
import json, re, collections, zipfile, hashlib

ROOT=Path(__file__).resolve().parent.parent
WORK=ROOT/'extraction_work'
def mappings(text, section):
    section=text.split('['+section+']')[1].split('[')[0]
    return dict(re.findall(r'^(\d+);([^\r\n]+)',section,re.M))

text=(WORK/'PSR-SX900.dat').read_text('latin1')
styles={int(k):v for k,v in mappings(text,'STYLES').items()}
voices=mappings(text,'VOICES')
with zipfile.ZipFile(WORK/'instrument_files.zip') as z:
    otherstyles=[mappings(z.read(n).decode('latin1'),'STYLES') for n in ['PSR-SX700.dat','GENOS 1.dat']]
assert all(m['2817']=='CountryWaltz' for m in otherstyles)
assert styles[2816]=='CountryWaltz'

banks=json.loads((WORK/'banks.json').read_text('utf8'))
source=next(ROOT.glob('*.bup'))
backup=source.read_bytes()
rows=[]
for bank in banks:
    d={int(k):bytes.fromhex(v) for k,v in bank['buttons'][0].items()}
    path=bank['path']
    assert len(d[39])==3 and len(set(d[39]))==1
    trans=d[39][0]-64
    tempo=int.from_bytes(d[41][4:6],'big')
    assert -12<=trans<=12 and 5<=tempo<=500
    sid=int.from_bytes(d[7][:2],'big')
    note=''
    stylepath=''
    if sid==65535:
        stylepath=d[9][2:].decode('utf8')
        style=re.sub(r'\.[ST]\d+$','',stylepath.rsplit('/',1)[-1].rsplit('.',1)[0])
        method='Saved style path'
    elif sid in styles:
        style=styles[sid]
        method='SX900 instrument definition'
    elif sid==2817:
        style='CountryWaltz'
        method='SX700 and Genos instrument definitions'
        note='Style ID 2817 maps to CountryWaltz in SX700/Genos lists. SX900 list uses ID 2816. Verify on keyboard.'
    else:
        raise ValueError(f'Unmapped style {sid}: {path}')
    voice_names=[]; active=[]; voice_paths=[]; voice_codes=[]
    for part,group,pathgroup in [('R1',11,66),('R2',15,67),('R3',23,69),('Left',19,68)]:
        vpath=d[pathgroup][2:].decode('utf8')
        name=re.sub(r'\.[ST]\d+$','',vpath.rsplit('/',1)[-1].rsplit('.',1)[0])
        assert name and d[group][0] in (0,127)
        code=''.join(f'{v:03}' for v in d[group][1:4])
        # The saved path preserves user names and older-model voice selections.
        voice_names.append(name); voice_paths.append(vpath); voice_codes.append(code)
        if d[group][0]==127:
            active.append(part)
    row={'folder':path.rsplit('/',1)[0].removeprefix('C:/REGIST/'),
         'registration':path.rsplit('/',1)[1][:-4],
         'style':style,'tempo':tempo,'transpose':trans,
         'right1':voice_names[0],'right2':voice_names[1],'right3':voice_names[2],'left':voice_names[3],
         'active_parts':', '.join(active) if active else 'None',
         'notes':note,'button1_name':d[1].decode('utf8'),
         'source_path':path,'style_id':sid,'style_path':stylepath,'style_name_source':method,
         'voice_paths':voice_paths,'voice_codes':voice_codes,
         'offset':bank['offset'],'size':bank['size']}
    rows.append(row)
    output=WORK/'raw_registrations'/path.removeprefix('C:/REGIST/')
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_bytes(backup[bank['offset']:bank['offset']+bank['size']])

assert len(rows)==458 and len(set(r['source_path'] for r in rows))==458
groups=collections.Counter(r['folder'] for r in rows)
report={'source_file':source.name,'source_sha256':hashlib.sha256(backup).hexdigest(),
        'registration_count':len(rows),'folder_counts':dict(groups),
        'style_mapping_notes':sum(bool(r['notes']) for r in rows),
        'source_urls':['https://voetsoft.be/downloads/DefaultInstrumentFiles.zip',
                       'https://www.heikoplate.de/pdf/tyros_registrationfiles_deutsch.pdf'],
        'rows':rows}
(WORK/'registrations.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({k:v for k,v in report.items() if k!='rows'},indent=2))
print('First row:',json.dumps(rows[0],ensure_ascii=True))

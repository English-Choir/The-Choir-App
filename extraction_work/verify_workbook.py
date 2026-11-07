from pathlib import Path
import json, hashlib
from collections import Counter
from openpyxl import load_workbook
from PIL import Image, ImageDraw

root=Path(__file__).resolve().parent.parent
work=root/'extraction_work'
d=json.loads((work/'registrations.json').read_text('utf8'))
target=root/'outputs'/'sx900-registration-extraction'/'SX900 Registrations.xlsx'
wb=load_workbook(target,data_only=False)
fields=['folder','registration','style','tempo','transpose','right1','right2','right3','left','active_parts','notes']
assert wb.sheetnames==list(d['folder_counts'])
count=0
for sheet in wb:
    n=d['folder_counts'][sheet.title]
    found=list(sheet.iter_rows(min_row=8,max_row=7+n,max_col=11,values_only=True))
    expected=[tuple(r[f] if r[f]!='' else None for f in fields) for r in d['rows'] if r['folder']==sheet.title]
    normalized=[tuple(v if v!='' else None for v in r) for r in found]
    assert Counter(normalized)==Counter(expected),sheet.title
    assert sheet.freeze_panes=='A8', (sheet.title,sheet.freeze_panes)
    assert len(sheet.tables)==1
    assert list(sheet.tables.values())[0].autoFilter is not None
    for row in found:
        assert type(row[3]) is int and type(row[4]) is int
    assert sheet['A5'].comment and d['source_sha256'] in sheet['A5'].comment.text
    count+=len(found)
assert count==458
assert hashlib.sha256((root/d['source_file']).read_bytes()).hexdigest()==d['source_sha256']
assert len(list((work/'raw_registrations').rglob('*.rgt')))==458
print(f'Validated {count} rows, {len(wb.sheetnames)} folder sheets, numeric values, filters, frozen headers and source notes. Backup unchanged.')

previews=sorted((work/'previews').glob('[0-9][0-9].png'))
assert len(previews)==20
for batch in range(4):
    selected=previews[batch*5:(batch+1)*5]
    images=[]
    for p in selected:
        im=Image.open(p).convert('RGB')
        # Keep the title and table; omit the identical explanatory rows in this QA montage.
        top=im.crop((0,0,im.width,72))
        body=im.crop((0,175,im.width,im.height))
        crop=Image.new('RGB',(im.width,top.height+body.height),'white')
        crop.paste(top,(0,0)); crop.paste(body,(0,top.height))
        images.append(crop)
    canvas=Image.new('RGB',(max(im.width for im in images),sum(im.height+24 for im in images)),'#DDE3EB')
    y=0
    for im in images:
        canvas.paste(im,(0,y)); y+=im.height+24
    canvas.save(work/'previews'/f'contact_{batch+1}.png')

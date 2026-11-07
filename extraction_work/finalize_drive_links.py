"""Add native OOXML hyperlinks, unsupported by the artifact runtime, and verify."""
import json, posixpath, zipfile, io
from pathlib import Path
from collections import defaultdict, Counter
from xml.dom import minidom
from openpyxl import load_workbook

work=Path(__file__).resolve().parent
validation=json.loads((work/'drive_workbook_validation.json').read_text(encoding='utf-8'))
links=json.loads((work/'drive_hyperlinks.json').read_text(encoding='utf-8'))
target=Path(validation['output'])
ns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'
rns='http://schemas.openxmlformats.org/officeDocument/2006/relationships'
pns='http://schemas.openxmlformats.org/package/2006/relationships'
by_sheet=defaultdict(list)
for link in links: by_sheet[link['sheet']].append(link)
with zipfile.ZipFile(target) as zin:
    contents={i.filename:zin.read(i.filename) for i in zin.infolist()}
book=minidom.parseString(contents['xl/workbook.xml'])
rels=minidom.parseString(contents['xl/_rels/workbook.xml.rels'])
targets={e.getAttribute('Id'):e.getAttribute('Target') for e in rels.getElementsByTagNameNS(pns,'Relationship')}
later={'printOptions','pageMargins','pageSetup','headerFooter','rowBreaks','colBreaks','customProperties','cellWatches','ignoredErrors','smartTags','drawing','legacyDrawing','legacyDrawingHF','picture','oleObjects','controls','webPublishItems','tableParts','extLst'}
processed=0
for node in book.getElementsByTagNameNS(ns,'sheet'):
    name=node.getAttribute('name')
    if name not in by_sheet: continue
    part=targets[node.getAttribute('r:id')]
    part=part.lstrip('/') if part.startswith('/') else posixpath.normpath(posixpath.join('xl',part))
    doc=minidom.parseString(contents[part])
    root=doc.documentElement
    if not root.hasAttribute('xmlns:r'):root.setAttribute('xmlns:r',rns)
    old=root.getElementsByTagNameNS(ns,'hyperlinks')
    for child in list(old):root.removeChild(child)
    prefix=(root.prefix+':') if root.prefix else ''
    collection=doc.createElementNS(ns,prefix+'hyperlinks')
    relpart=posixpath.join(posixpath.dirname(part),'_rels',posixpath.basename(part)+'.rels')
    rel=minidom.parseString(contents[relpart]) if relpart in contents else minidom.parseString(f'<Relationships xmlns="{pns}"/>')
    for child in list(rel.getElementsByTagNameNS(pns,'Relationship')):
        if child.getAttribute('Id').startswith('rIdDriveLink'):rel.documentElement.removeChild(child)
    existing={e.getAttribute('Id') for e in rel.getElementsByTagNameNS(pns,'Relationship')}
    for i,link in enumerate(by_sheet[name],1):
        rid=f'rIdDriveLink{i}'
        assert rid not in existing
        element=doc.createElementNS(ns,prefix+'hyperlink')
        element.setAttribute('ref',link['address'])
        element.setAttributeNS(rns,'r:id',rid)
        element.setAttribute('display',link['label'])
        collection.appendChild(element)
        relationship=rel.createElementNS(pns,'Relationship')
        for key,value in {'Id':rid,'Type':rns+'/hyperlink','Target':link['url'],'TargetMode':'External'}.items():relationship.setAttribute(key,value)
        rel.documentElement.appendChild(relationship)
        processed+=1
    after=next((c for c in root.childNodes if c.nodeType==c.ELEMENT_NODE and c.localName in later),None)
    root.insertBefore(collection,after) if after else root.appendChild(collection)
    contents[part]=doc.toxml(encoding='UTF-8')
    contents[relpart]=rel.toxml(encoding='UTF-8')
assert processed==len(links),(processed,len(links))
buffer=io.BytesIO()
with zipfile.ZipFile(buffer,'w',compression=zipfile.ZIP_DEFLATED) as zout:
    for name,blob in contents.items():zout.writestr(name,blob)
target.write_bytes(buffer.getvalue())

# Read-only verification of original data, native links, table filters and counts.
with (work/'SX900 Registrations.pre-drive.xlsx.bak').open('rb') as baseline:
    original=load_workbook(baseline,data_only=False)
final=load_workbook(target,data_only=False)
cached=load_workbook(target,data_only=True)
assert final.sheetnames==original.sheetnames+['Drive review']
count=0; verified=0; statuses=Counter()
matches=json.loads((work/'drive_matches.json').read_text(encoding='utf-8'))
for check in validation['checks']:
    name=check['folder']; a,b=original[name],final[name]; last=check['last']
    for row in range(8,last+1):
        assert [a.cell(row,c).value for c in range(1,11)]==[b.cell(row,c).value for c in range(1,11)],(name,row)
        count+=1
        record=next(r for r in matches['rows'] if r['folder']==name and r['registration']==b.cell(row,2).value)
        assert b.cell(row,12).value==record['match_status']
        statuses[record['match_status']]+=1
        if a.cell(row,11).value:
            assert b.cell(row,3).value=='CountryWaltz'
            assert b.cell(row,11).value.startswith('CountryWaltz verified')
            assert b.cell(row,3).fill.fgColor.rgb=='FFE4F0E7'
            verified+=1
        else: assert b.cell(row,11).value==a.cell(row,11).value
    assert list(b.tables.values())[0].ref==f'A7:P{last}'
    assert b.freeze_panes=='C8',(name,b.freeze_panes)
    assert b['A5'].comment and b['A5'].comment.text==a['A5'].comment.text
    assert not any(isinstance(c.value,str) and 'Verify those 30' in c.value for row in b for c in row)
for link in links:
    cell=final[link['sheet']][link['address']]
    assert cell.value==link['label']
    assert cell.hyperlink and cell.hyperlink.target==link['url']
assert len(links)==len({(l['sheet'],l['address']) for l in links})
assert count==458 and verified==30
assert dict(statuses)==matches['counts']
assert cached['Drive review'][f"E{validation['summaryLast']+2}"].value==322
for row,file in enumerate(matches['files'],validation['fileStart']):
    assert final['Drive review'].cell(row,2).value==file['name']
    assert final['Drive review'].cell(row,3).value==file['status']
    assert final['Drive review'].cell(row,5).hyperlink
errors=[]
for sh in cached:
    for row in sh:
        for c in row:
            if c.data_type=='e' or (isinstance(c.value,str) and 'HYPERLINK is not implemented' in c.value):errors.append((sh.title,c.coordinate,c.value))
assert not errors,errors
print(json.dumps({'registrations_preserved':count,'CountryWaltz_verified':verified,'native_hyperlinks':len(links),'drive_files':322,'sheets':len(final.sheetnames),'statuses':dict(statuses),'validation':'passed'},indent=2))

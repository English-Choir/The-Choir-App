from pathlib import Path
import re, json, struct, collections, hashlib

ROOT = Path(__file__).resolve().parent.parent
SRC = next(ROOT.glob('*.bup'))
B = SRC.read_bytes()
u16 = lambda b, p: int.from_bytes(b[p:p+2], 'big')
u32 = lambda b, p: int.from_bytes(b[p:p+4], 'big')
banks=[]
for m in re.finditer(rb'SpfF..[\s\S]{2}RGST', B):
    p=m.start()
    size=u32(B,p+16)
    raw=B[p:p+size]
    assert raw[-6:]==b'FEnd\0\0', (p,size,raw[-6:])
    assert int.from_bytes(B[p-816:p-812], 'little') == size
    path=B[p-771:p].split(b'\0',1)[0].decode('utf8')
    assert path.startswith('C:/REGIST/')
    assert path.lower().endswith('.rgt'), (p,path)
    cursor=6+u16(raw,4)
    blocks=[]
    buttons=[]
    while cursor<len(raw)-6:
        tag=raw[cursor:cursor+4]
        n=u16(raw,cursor+4)
        assert tag[:3]==b'BHd', (path,cursor,tag)
        part=raw[cursor+6:cursor+6+n]
        blocks.append([tag.hex(),n])
        if tag==b'BHd\0':
            d={}; q=0
            while q<len(part):
                assert part[q:q+3]==b'GPm', (path,q,part[q:q+6])
                typ=part[q+3]; ln=u16(part,q+4)
                assert typ not in d and q+6+ln<=len(part)
                d[typ]=part[q+6:q+6+ln]
                q+=6+ln
            assert q==len(part)
            buttons.append(d)
        cursor+=6+n
    assert cursor==len(raw)-6
    assert len(buttons)==8, (path,len(buttons))
    banks.append({'path':path,'offset':p,'size':size,'blocks':blocks,'buttons':[{str(k):v.hex() for k,v in d.items()} for d in buttons]})

(ROOT/'extraction_work'/'banks.json').write_text(json.dumps(banks,ensure_ascii=False),encoding='utf8')
paths=[m.group()[:-1].decode('utf8') for m in re.finditer(rb'C:/REGIST/[^\0]*\.rgt\x00',B)]
assert sorted(paths)==sorted(b['path'] for b in banks), 'Not every registration path has a parsed bank'
print('Banks:',len(banks),'Unique paths:',len(set(b['path'] for b in banks)))
print('Folders:',collections.Counter(b['path'].rsplit('/',1)[0] for b in banks))
print('Bank1:',banks[0]['path'],banks[0]['blocks'])
for k,v in banks[0]['buttons'][0].items():
    b=bytes.fromhex(v)
    if int(k)<0x2b or 0x42<=int(k)<=0x47 or len(b)<10:
        print(hex(int(k)),len(b),b.hex() if len(b)<60 else '',repr(b) if len(b)<150 else '')
print('Groups:',collections.Counter(int(k) for bank in banks for k in bank['buttons'][0]))
print('SHA256:',hashlib.sha256(B).hexdigest())

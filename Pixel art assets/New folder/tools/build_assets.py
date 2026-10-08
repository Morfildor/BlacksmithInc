#!/usr/bin/env python3
"""Tiny Blacksmith V2 handcrafted procedural pixel-art starter pack.
All pixels are placed on the original integer sprite grid. No resampling of previews.
"""
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import json, math, random, zipfile, shutil, collections

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'drawable-nodpi'; OUT.mkdir(parents=True, exist_ok=True)
PREVIEW=ROOT/'previews'; PREVIEW.mkdir(parents=True, exist_ok=True)
# Coherent indexed RGB palette (no more than 48 RGB values in final exports).
C={
 'ink':'1A1210','deep':'2B2320','wood0':'4A3B33','wood1':'6B4B32','wood2':'8C6239',
 'bone':'E8E4DA','cream':'FFF0A0', 'stone0':'55595D','stone1':'7D8186','stone2':'B0B4B8',
 'ash':'5C6166', 'fire0':'B3301A','fire1':'E2601F','fire2':'FFC14D',
 'skin':'E0B08A','leather':'7A4A2A','blue':'3A5A8A','red':'A03030','gold':'D8A030',
 'iron0':'5C6166','iron1':'8A8F94','bronze0':'8A5A22','bronze1':'C7883A',
 'silver0':'9AA3AD','silver1':'D9DDE3','obs0':'423254','obs1':'78608C',
 'star0':'5C86B8','star1':'AAD2EC','moon0':'867EBE','moon1':'CEC8F0',
 'frost0':'3AA0D8','frost1':'7FD8FF','storm0':'6A4BD6','storm1':'B48CFF',
 'grave0':'506854','grave1':'B2CE96','verd0':'5AA05A','verd1':'9CDE78',
 'skin_dark':'946647','skin_olive':'A7B078','skin_pale':'EAD0BA',
 'skin_orc':'709156','skin_lizard':'4B9087','teal':'43BDA6',
 'hair':'47342F','hairlight':'CBA66F', 'purple':'8058A5', 'pink':'E4A1BF',
}
assert len(set(C.values()))<=48,len(set(C.values()))
P={k:tuple(bytes.fromhex(v)) for k,v in C.items()}
METALS={'iron':('iron0','iron1'),'bronze':('bronze0','bronze1'),'silver':('silver0','silver1'),
        'obsidian':('obs0','obs1'),'starsteel':('star0','star1'),'moonsteel':('moon0','moon1')}
manifest=[]
def image(size):return Image.new('RGBA',size,(0,0,0,0))
def draw(im):return ImageDraw.Draw(im)
def rgba(k,alpha=255):return (*P[k],alpha)
def enforce_margin(im,anchor):
    # Enforce required 1px transparent safety margin on sprites (never on tiles).
    if anchor=='tile' or not im.getbbox(): return im
    l,t,r,b=im.getbbox(); region=im.crop((l,t,r,b))
    width,height=region.size; iw,ih=im.size[0]-2,im.size[1]-2
    if width>iw or height>ih:
        region=region.resize((min(width,iw),min(height,ih)),Image.Resampling.NEAREST)
    w,h=region.size
    # Preserve declared anchor as much as possible within margin.
    x=1 if 'left' in anchor else max(1,(im.width-w)//2)
    y=max(1,im.height-h-1) if anchor.startswith('bottom') else 1 if anchor.startswith('top') else max(1,(im.height-h)//2)
    dest=image(im.size);dest.alpha_composite(region,(x,y))
    return dest

def save(id,im,kind,anchor='top-left',notes='',duration=None,extra=False):
    assert id.replace('_','').isalnum(),id
    im=enforce_margin(im,anchor)
    assert im.mode=='RGBA'
    im.save(OUT/(id+'.png'),optimize=True)
    m={'id':id,'width':im.width,'height':im.height,'kind':kind,'anchor':anchor,'notes':notes,'extra':extra}
    if duration is not None:m['durationMs']=duration
    manifest.append(m)
    return im

def rect(d,box,k): d.rectangle(box,fill=rgba(k))
def point(d,xy,k): d.point(xy,fill=rgba(k))
def line(d,pts,k,w=1):d.line(pts,fill=rgba(k),width=w)
def poly(d,pts,k):d.polygon(pts,fill=rgba(k))
def oval(d,box,k):d.ellipse(box,fill=rgba(k))
def outline(im):
    # Dark outline around silhouette, 1 logical pixel, using only the shared palette.
    a=im.getchannel('A'); b=Image.new('RGBA',im.size,(0,0,0,0)); bp=b.load(); px=a.load()
    for y in range(im.height):
      for x in range(im.width):
       if px[x,y]>0:
        for dy,dx in ((-1,0),(1,0),(0,-1),(0,1)):
         xx=x+dx;yy=y+dy
         if 0<=xx<im.width and 0<=yy<im.height and px[xx,yy]==0:bp[xx,yy]=rgba('ink')
    b.alpha_composite(im);return b

def race_skin(race):return {'human':'skin','dwarf':'skin_dark','elf':'skin_pale','orc':'skin_orc',
     'dragonkin':'skin_lizard','goblin':'skin_orc','satyr':'skin_olive','lizardfolk':'skin_lizard',
     'halfling':'skin','tiefling':'pink','feline':'skin_olive','undead':'stone2',
     'dryad':'verd1','beastkin':'bronze1','fae':'skin_pale'}.get(race,'skin')

# ---- Forge stage: consistent masonry, timber, upper-left-lit metal. -----------------
def forge():
 im=image((16,16));d=draw(im)
 rect(d,(0,0,15,15),'stone0')
 for y in (0,8):
  rect(d,(0,y,15,y+6),'stone1')
  for x in ([0,8] if y==0 else [0,5,13]):
   if x:line(d,[(x,y),(x,y+6)],'stone0')
  line(d,[(0,y),(15,y)],'stone2')
  for x in (2,10):point(d,(x,y+3),'ash')
 # 2px beam, clean and quiet
 rect(d,(0,15,15,15),'wood0')
 save('tile_wall',im,'forge','tile','Low-contrast masonry; seamless horizontally and vertically')
 im=image((16,8));d=draw(im)
 rect(d,(0,0,15,7),'wood1');rect(d,(0,0,15,1),'wood2')
 for x in (3,11):line(d,[(x,3),(x,6)],'wood0')
 line(d,[(0,7),(15,7)],'deep')
 save('tile_floor',im,'forge','tile','Seamless timber floor strip')
 for state in ['cold','warm','hot']:
  im=image((24,24));d=draw(im)
  poly(d,[(2,22),(2,9),(4,5),(7,2),(16,2),(20,6),(21,21)],'ink')
  poly(d,[(3,21),(3,9),(5,5),(8,3),(15,3),(19,7),(20,21)],'stone1')
  rect(d,(3,18,20,22),'stone0');rect(d,(4,18,19,19),'stone2')
  rect(d,(7,15,18,20),'ink')
  poly(d,[(6,17),(6,11),(8,8),(15,8),(18,11),(18,17)],'deep')
  for x,y in ((5,8),(8,4),(15,4),(18,8),(4,18),(19,18)):
   point(d,(x,y),'stone2')
  rect(d,(10,20,14,22),'wood0');rect(d,(11,21,13,21),'bronze1')
  for x in (4,8,14,19):point(d,(x,14),'stone0')
  if state!='cold':
   rect(d,(9,16,16,17),'fire0');rect(d,(10,16,15,16),'fire1')
   for a,b in [(10,15),(14,14),(16,15)]:point(d,(a,b),'fire2')
  else:
   point(d,(11,16),'stone0');point(d,(15,16),'stone0')
  if state=='hot':
   poly(d,[(9,17),(8,14),(10,12),(11,9),(13,13),(15,9),(17,13),(16,17)],'fire0')
   poly(d,[(10,17),(10,14),(12,11),(13,15),(15,12),(15,17)],'fire1')
   line(d,[(12,17),(12,14)],'fire2');point(d,(15,9),'cream')
  save('furnace_'+state,im,'forge','bottom-left','Identical housing, '+state+' fire state')
 im=image((24,12));d=draw(im)
 poly(d,[(1,2),(18,2),(21,1),(22,4),(16,5),(17,7),(13,8),(13,10),(10,10),(10,8),(6,7),(5,5),(2,5)],'ink')
 poly(d,[(2,3),(18,3),(20,2),(20,4),(15,4),(15,6),(12,7),(11,7),(6,6),(6,4),(2,4)],'stone1')
 line(d,[(2,2),(17,2)],'stone2')
 rect(d,(9,8,15,11),'wood1');line(d,[(9,8),(15,8)],'wood2')
 save('anvil',im,'forge','bottom-left','Forge anvil with pronounced beak')
 im=image((12,24));d=draw(im)
 rect(d,(0,0,11,2),'wood1');rect(d,(0,21,11,23),'wood0');rect(d,(0,0,1,23),'wood1');rect(d,(10,0,11,23),'wood0')
 rect(d,(2,3,9,4),'wood2')
 # hammer
 rect(d,(3,6,4,17),'leather');rect(d,(2,6,6,8),'stone2');rect(d,(3,6,5,6),'silver1')
 # tongs
 line(d,[(7,7),(7,14),(6,18)],'stone2');line(d,[(8,7),(8,14),(9,18)],'stone1')
 # file
 rect(d,(4,19,8,20),'stone1')
 save('tool_rack',im,'forge','top-left','Rack for hammer, tongs, file')
 im=image((32,12));d=draw(im)
 rect(d,(0,5,31,7),'ink');rect(d,(1,5,30,6),'wood2');rect(d,(2,8,4,11),'wood0');rect(d,(27,8,29,11),'wood0')
 for x in (7,23):point(d,(x,6),'gold')
 save('shelf',im,'forge','bottom-left','Empty wood display shelf; weapon slots remain free')
 frames=[[(3,6),(4,5)],[(3,4),(5,5),(4,6)],[(4,2),(5,4),(3,5)],[(5,1),(4,3),(6,4)]]
 for i,p in enumerate(frames):
  im=image((8,8));d=draw(im)
  for j,xy in enumerate(p):point(d,xy,['fire1','fire2','cream'][j%3])
  save('ember_'+str(i),im,'animation','top-left','Ember loop frame '+str(i),180)

# ---- Pixel weapons, six silhouettes x six cores; distinctive core motifs. ----------
def weapon(family,core='iron',accent=None,signature=None):
 im=image((16,16));d=draw(im)
 shade,lit=METALS[core]
 magic=accent or ('frost1' if core=='starsteel' else 'moon1' if core=='moonsteel' else 'gold')
 if family=='sword':
  # arming sword with long readable straight blade
  poly(d,[(4,10),(9,3),(12,1),(12,5),(7,12)],'ink')
  poly(d,[(5,10),(10,3),(11,2),(11,5),(7,11)],shade)
  line(d,[(6,9),(10,3)],lit,1)
  line(d,[(4,10),(8,12)],'gold',1)
  line(d,[(5,11),(3,14)],'wood1',2)
  point(d,(3,14),'gold')
  if core in ('obsidian','starsteel','moonsteel'):
   point(d,(9,6),magic)
 elif family=='axe':
  line(d,[(4,13),(9,3)],'ink',3);line(d,[(4,13),(9,3)],'wood1',1)
  poly(d,[(8,2),(13,2),(14,4),(12,5),(11,9),(8,10),(8,7),(6,5)],'ink')
  poly(d,[(8,3),(12,3),(13,4),(11,5),(11,8),(9,9),(9,6),(7,5)],shade)
  line(d,[(10,3),(12,3),(12,5)],lit)
  point(d,(4,13),'gold')
  if core in ('starsteel','moonsteel'):point(d,(10,6),magic)
 elif family=='spear':
  line(d,[(4,14),(10,4)],'ink',3);line(d,[(4,14),(10,4)],'wood1',1)
  poly(d,[(9,6),(9,3),(12,1),(13,2),(12,6),(10,8)],'ink')
  poly(d,[(10,6),(10,3),(12,2),(12,5)],shade)
  line(d,[(11,3),(11,5)],lit)
  point(d,(6,12),'gold')
 elif family=='bow':
  # no solid sword-like stem: open interior and fine string
  line(d,[(6,2),(4,5),(4,10),(7,13)],'ink',3)
  line(d,[(6,2),(4,5),(4,10),(7,13)],'wood2',1)
  point(d,(6,2),lit);point(d,(7,13),lit)
  line(d,[(7,2),(8,13)],'silver1')
  point(d,(4,8),'gold');point(d,(3,7),'wood1')
  if core in ('starsteel','moonsteel','obsidian'):
   point(d,(6,5),magic);point(d,(6,10),magic)
 elif family=='dagger':
  poly(d,[(5,10),(9,6),(12,4),(11,8),(7,12)],'ink')
  poly(d,[(6,10),(9,7),(11,5),(10,8),(7,11)],shade)
  line(d,[(7,9),(10,6)],lit)
  line(d,[(4,10),(7,13)],'gold')
  line(d,[(5,12),(3,14)],'wood1',2)
  point(d,(3,14),'gold')
 elif family=='staff':
  line(d,[(3,14),(9,5)],'ink',3);line(d,[(3,14),(9,5)],'wood2',1)
  # metal setting around single round gemstone, avoids fork/tuning-fork
  oval(d,(7,1,13,7),'ink');oval(d,(8,2,12,6),shade);oval(d,(9,2,11,4),magic)
  point(d,(9,2),lit);point(d,(6,8),'gold');point(d,(4,12),'gold')
 if signature:
  # signature descriptors apply a unique visible glint, blade flame, rune or bow-curve ornament
  index=signature['index']; motif=signature['motif']; chroma=signature['color']
  if family in ('sword','dagger','spear'):
   if motif=='flame':point(d,(12,2),'fire1');point(d,(13,1),'fire2')
   if motif=='ice':point(d,(12,2),'frost1');point(d,(13,3),'cream')
   if motif=='bolt':point(d,(12,1),'storm1');point(d,(13,3),'storm0')
   if motif=='thorn':point(d,(11,1),'verd1');point(d,(13,3),'verd0')
   if motif=='grave':point(d,(12,1),'grave1');point(d,(13,2),'grave0')
   if motif=='sun':point(d,(13,2),'cream');point(d,(12,1),'gold')
   point(d,(9,7),chroma)
  if family=='axe':
   for x,y in [(13,3),(12,6)]:point(d,(x,y),chroma)
   if motif=='ice':point(d,(11,1),'frost1')
   if motif=='flame':point(d,(13,1),'fire2')
   if motif=='bolt':point(d,(14,5),'storm1')
   if motif=='grave':point(d,(12,8),'grave1')
  if family=='bow':
   point(d,(3,4),chroma);point(d,(5,10),chroma)
   if motif=='ice':point(d,(7,2),'frost1')
   if motif=='flame':point(d,(8,8),'fire2')
   if motif=='bolt':point(d,(8,3),'storm1')
   if motif=='thorn':point(d,(2,6),'verd1')
  if family=='staff':
   point(d,(9,2),chroma)
   if motif=='sun':point(d,(13,2),'cream')
   if motif=='grave':point(d,(13,4),'grave1')
   if motif=='bolt':point(d,(10,1),'storm1')
  # Signature-specific silhouettes. These read by shape, not solely by recoloring.
  nm=signature['name']
  if nm=='dawnbrand':
   line(d,[(8,11),(6,13)],'gold');point(d,(12,4),'cream');point(d,(13,2),'fire2')
  elif nm=='winterwake':
   point(d,(13,1),'frost1');point(d,(13,4),'cream');line(d,[(3,10),(2,9)],'frost0')
  elif nm=='tempest_edge':
   line(d,[(13,1),(12,2),(13,3)],'storm1');point(d,(10,8),'cream')
  elif nm=='ashen_vow':
   line(d,[(12,2),(13,3)],'grave1');point(d,(7,10),'fire0');point(d,(8,7),'grave1')
  elif nm=='hearthcleaver':
   line(d,[(14,3),(13,2)],'fire2');point(d,(12,7),'fire1');point(d,(10,5),'cream')
  elif nm=='glacier_maul':
   line(d,[(13,2),(14,1)],'frost1');point(d,(12,8),'frost0');point(d,(10,4),'cream')
  elif nm=='thunderhead':
   line(d,[(13,2),(14,4),(13,6)],'storm1');point(d,(10,7),'cream')
  elif nm=='emberfall':
   line(d,[(13,2),(14,1)],'fire1');point(d,(12,6),'fire2');point(d,(11,8),'fire1')
  elif nm=='stormsong':
   point(d,(8,4),'storm1');point(d,(8,8),'frost1');point(d,(7,12),'storm1')
  elif nm=='frostwhisper':
   point(d,(8,3),'frost1');point(d,(8,8),'cream');point(d,(8,11),'frost0')
  elif nm=='cinder_arc':
   point(d,(9,5),'fire2');point(d,(9,7),'fire1');point(d,(8,10),'fire2')
  elif nm=='galewood':
   point(d,(2,5),'verd1');point(d,(3,3),'verd0');point(d,(7,9),'verd1')
 return im

def weapons():
 for family in ['sword','axe','spear','bow','dagger','staff']:
  for core in METALS:
   im=weapon(family,core)
   save(f'weapon_{family}_{core}',im,'weapon','bottom-center',f'{family} silhouette in {core}')
 defs={
  'dawnbrand':('sword','starsteel','sun','cream'),
  'winterwake':('sword','moonsteel','ice','frost1'),
  'tempest_edge':('sword','obsidian','bolt','storm1'),
  'ashen_vow':('sword','iron','grave','grave1'),
  'hearthcleaver':('axe','bronze','flame','fire2'),
  'glacier_maul':('axe','silver','ice','frost1'),
  'thunderhead':('axe','starsteel','bolt','storm1'),
  'emberfall':('axe','obsidian','flame','fire2'),
  'stormsong':('bow','moonsteel','bolt','storm1'),
  'frostwhisper':('bow','silver','ice','frost1'),
  'cinder_arc':('bow','bronze','flame','fire2'),
  'galewood':('bow','iron','thorn','verd1')}
 for index,(name,(family,core,motif,chroma)) in enumerate(defs.items()):
  im=weapon(family,core,signature={'index':index,'motif':motif,'color':chroma,'name':name})
  save('weapon_sig_'+name,im,'signature','bottom-center',f'{name}, distinctive {motif} ornament')

# ---- Overlays and rarity badges --------------------------------------------------
def overlays_badges():
 base={
 'fire':[(3,10),(4,8),(4,6),(5,7),(6,5),(7,3),(8,2),(9,4),(9,6),(11,4),(11,7),(10,10)],
 'frost':[(4,11),(5,9),(6,8),(7,6),(8,4),(9,2),(10,6),(11,8),(12,10)],
 'storm':[(3,5),(7,5),(5,8),(10,8),(7,11),(11,11)],
 'grave':[(5,6),(6,4),(9,4),(10,6),(10,9),(8,11),(6,10)],
 'verdant':[(4,10),(5,8),(6,6),(7,4),(9,2),(8,7),(10,8),(11,5)],
 'sun':[(7,3),(8,3),(3,8),(5,5),(12,8),(10,5),(8,12),(11,11)]}
 for element,coords in base.items():
  im=image((16,16));d=draw(im)
  palette={'fire':('fire1','fire2'),'frost':('frost0','frost1'),
    'storm':('storm0','storm1'),'grave':('grave0','grave1'),
    'verdant':('verd0','verd1'),'sun':('gold','cream')}[element]
  for j,(x,y) in enumerate(coords):
   d.point((x,y),fill=rgba(palette[j%2],153))
   if j%3==0 and x+1<15:d.point((x+1,y),fill=rgba(palette[1],153))
  if element=='grave':
   rect(d,(7,6,9,8),'grave1');point(d,(7,7),'ink');point(d,(9,7),'ink')
   im.putalpha(im.getchannel('A').point(lambda p:min(p,153)))
  if element=='storm':
   for x,y in [(8,3),(9,4),(4,9)]:point(d,(x,y),'storm1')
   im.putalpha(im.getchannel('A').point(lambda p:min(p,153)))
  save('overlay_'+element,im,'overlay','top-left','~60% alpha; drawn over base weapon')
 def badge(name):
  im=image((8,8));d=draw(im)
  kinds={
  'common':('iron1',[(3,1),(5,3),(5,5),(3,7),(1,5),(1,3)]),
  'uncommon':('verd1',[(3,1),(6,6),(1,6)]),
  'rare':('frost0',[(1,1),(6,1),(6,6),(1,6)]),
  'epic':('storm1',[(3,0),(6,2),(5,6),(3,7),(1,5),(1,2)]),
  'legendary':('gold',[(3,0),(4,2),(7,3),(5,5),(5,7),(3,6),(1,7),(1,5),(0,3),(2,2)]),
  'flaw':('red',[(3,0),(5,1),(6,3),(4,7),(2,6),(1,4),(2,2)])}
  color,shape=kinds[name];poly(d,shape,'ink')
  interior=[(x,y) for x,y in shape if x in range(1,7) and y in range(1,7)]
  # hand-positioned fill/symbol per shape
  if name=='common':poly(d,[(3,2),(5,4),(3,6),(2,4)],color);point(d,(3,3),'silver1')
  elif name=='uncommon':poly(d,[(3,2),(5,5),(2,5)],color);point(d,(3,4),'cream')
  elif name=='rare':rect(d,(2,2,5,5),color);point(d,(2,2),'frost1')
  elif name=='epic':poly(d,[(3,1),(5,3),(4,6),(2,5),(2,3)],color);point(d,(3,2),'moon1')
  elif name=='legendary':point(d,(3,2),'cream');line(d,[(3,2),(3,5)],color);line(d,[(1,4),(5,4)],color)
  else:rect(d,(2,2,5,5),color);line(d,[(4,1),(3,3),(5,4),(2,6)],'cream')
  return im
 for name in ['common','uncommon','rare','epic','legendary','flaw']:
  save('badge_'+name,badge(name),'badge','top-left','Unique geometry for color-blind readability')

# ---- 5 classes x 5 distinct ancestry variants; 16x16 top-left anchors -----------
CLASSES={
 'guardian':[('human','standard'),('dwarf','beard'),('elf','wing'),('orc','scar'),('dragonkin','horn')],
 'ranger':[('human','braid'),('elf','leaf'),('goblin','hood'),('satyr','horn'),('lizardfolk','mask')],
 'duelist':[('human','feather'),('halfling','bow'),('elf','curl'),('tiefling','horn'),('feline','ear')],
 'battlemage':[('human','star'),('dwarf','beard'),('elf','moon'),('tiefling','horn'),('undead','mask')],
 'warden':[('elf','antler'),('dwarf','horn'),('orc','root'),('dryad','flower'),('beastkin','mask')]
}
HATS={'guardian':'silver0','ranger':'verd0','duelist':'red','battlemage':'purple','warden':'bronze0'}
TORSO={'guardian':'blue','ranger':'grave0','duelist':'red','battlemage':'obs0','warden':'verd0'}
def portrait(cls,index,race,motif):
 im=image((16,16));d=draw(im)
 skin=race_skin(race);hat=HATS[cls];cloth=TORSO[cls]
 # Shoulder silhouette, padded body and collar.
 poly(d,[(1,15),(1,12),(4,10),(11,10),(14,12),(14,15)],'ink')
 poly(d,[(2,15),(2,13),(5,11),(10,11),(13,13),(13,15)],cloth)
 rect(d,(6,11,9,12),'leather')
 if cls=='guardian':
  rect(d,(1,12,3,14),'iron1');rect(d,(12,12,14,14),'iron1')
 if cls=='warden':
  point(d,(1,13),'verd1');point(d,(13,12),'verd1')
 # Neck and head basic masses.
 rect(d,(6,9,9,11),skin)
 poly(d,[(4,5),(6,3),(10,3),(12,6),(11,10),(9,12),(6,11),(4,9)],'ink')
 poly(d,[(5,5),(7,4),(10,4),(11,6),(10,10),(8,11),(6,10),(5,9)],skin)
 # species differentiators
 if race in ('elf','goblin','dryad','lizardfolk'):
  poly(d,[(5,7),(1,5),(4,9)],'ink');poly(d,[(5,7),(2,6),(4,8)],skin)
  point(d,(12,6),skin)
 if race in ('orc','goblin'):
  point(d,(6,9),'bone');point(d,(11,9),'bone')
 if race=='dwarf':
  poly(d,[(5,9),(10,9),(10,13),(8,14),(5,12)],'hair');point(d,(8,11),'hairlight')
 if race=='dragonkin':
  poly(d,[(4,5),(2,2),(6,4)],'bronze1');poly(d,[(10,4),(13,1),(12,6)],'bronze1')
  point(d,(6,8),'grave1');point(d,(10,7),'grave1')
 if race=='tiefling':
  poly(d,[(4,5),(3,2),(6,5)],'ink');poly(d,[(11,5),(13,2),(11,7)],'ink')
  point(d,(3,3),'obs1');point(d,(12,3),'obs1')
 if race=='satyr':
  poly(d,[(4,5),(2,3),(5,2)],'bronze1');poly(d,[(10,4),(13,3),(11,1)],'bronze1')
 if race=='feline':
  poly(d,[(5,5),(3,2),(7,4)],'ink');poly(d,[(10,5),(13,2),(12,7)],'ink')
  point(d,(3,3),'pink');point(d,(12,3),'pink')
 if race=='undead':
  point(d,(7,8),'ink');point(d,(10,8),'ink');rect(d,(7,10,9,10),'bone')
 # hair/eye and class headgear
 if cls=='guardian':
  poly(d,[(4,8),(4,4),(6,2),(11,2),(13,5),(12,9),(10,8),(10,5),(6,5),(6,9)],'ink')
  poly(d,[(5,7),(5,4),(7,3),(10,3),(12,5),(11,8),(10,8),(10,5),(6,5),(6,8)],hat)
  line(d,[(6,5),(11,5)],'stone2');line(d,[(7,7),(10,7)],'ink')
  point(d,(8,7),'cream')
  # plumes/or crests by index
  crests=['blue','red','gold','hair','verd1']
  line(d,[(8,2),(8,0 if index%2 else 1)],crests[index])
  if motif=='wing':point(d,(12,3),'gold')
  if motif=='horn':point(d,(3,3),'bronze1')
 elif cls=='ranger':
  poly(d,[(2,8),(3,4),(5,2),(10,1),(13,5),(14,10),(11,8),(11,5),(6,4),(5,8)],'ink')
  poly(d,[(3,7),(4,4),(6,3),(10,2),(12,5),(12,7),(11,6),(9,4),(6,5),(5,9)],hat)
  line(d,[(4,6),(8,4)],'verd1')
  if motif in ('leaf','braid'):point(d,(11,3),'verd1')
  if motif=='mask':rect(d,(6,8,10,9),'grave0')
  if motif=='horn':point(d,(12,2),'bronze1')
 elif cls=='duelist':
  # distinctive brim with feather curling up
  rect(d,(1,5,14,6),'ink');rect(d,(2,5,13,5),hat)
  poly(d,[(3,5),(5,2),(10,2),(12,5)],'ink');poly(d,[(4,5),(6,3),(10,3),(11,5)],hat)
  point(d,(11,4),'gold')
  if motif!='horn':
   line(d,[(10,2),(12,0)],'bone' if index%2 else 'pink')
   point(d,(12,0),'bone')
  else:point(d,(12,3),'obs1')
  if motif=='curl':point(d,(4,7),'hairlight')
  if motif=='ear':point(d,(13,8),'bone')
 elif cls=='battlemage':
  poly(d,[(2,5),(7,0),(9,0),(12,5)],'ink');poly(d,[(3,5),(7,1),(8,1),(11,5)],hat)
  rect(d,(1,5,14,6),'ink');rect(d,(2,5,13,5),hat)
  line(d,[(4,5),(12,5)],'gold')
  point(d,(8,3),'frost1' if index in (0,2) else 'storm1')
  if motif=='moon':point(d,(5,2),'cream')
  if motif=='beard':point(d,(8,12),'bone')
 elif cls=='warden':
  poly(d,[(4,7),(4,3),(6,1),(10,1),(12,3),(12,9),(10,6),(6,6),(5,10)],'ink')
  poly(d,[(5,6),(5,3),(7,2),(9,2),(11,4),(11,7),(10,6),(6,6)],hat)
  # broad split antlers, not just repeated pointy horns
  line(d,[(5,3),(3,1)],'wood2');line(d,[(3,1),(2,0)],'wood2')
  line(d,[(11,3),(13,1)],'wood2');line(d,[(13,1),(14,0)],'wood2')
  if motif=='flower':point(d,(12,2),'pink');point(d,(13,3),'cream')
  elif motif=='root':point(d,(12,4),'verd1')
  elif motif=='mask':rect(d,(6,7,11,10),'bronze0')
  else:point(d,(8,3),'verd1')
 # Visible face features (skip if covered fully)
 if cls not in ('guardian','warden') and race!='undead':
  point(d,(7,8),'ink');point(d,(10,8),'ink')
  point(d,(8,10),'leather' if race not in ('orc','goblin') else 'bone')
 # Racial and personal markings are added *after* hats so each portrait stays legible.
 # Face, hair, ears and horns need to vary in silhouette, not be palette swaps.
 if cls=='guardian':
  if race=='dwarf':
   poly(d,[(6,9),(10,9),(10,13),(8,14),(6,12)],'hair')
   point(d,(8,11),'hairlight');point(d,(7,12),'hairlight')
  elif race=='elf':
   poly(d,[(12,6),(15,5),(13,8)],'skin_pale');point(d,(13,6),'gold')
  elif race=='orc':
   rect(d,(6,9,10,10),'skin_orc');point(d,(7,10),'bone');point(d,(10,10),'bone')
  elif race=='dragonkin':
   rect(d,(6,9,10,11),'skin_lizard');point(d,(5,10),'teal');point(d,(11,10),'teal')
 if cls=='ranger':
  if race=='human':line(d,[(10,10),(11,12)],'hairlight')
  elif race=='elf':poly(d,[(11,6),(14,5),(12,8)],'skin_pale')
  elif race=='goblin':
   poly(d,[(4,8),(1,6),(3,10)],'skin_orc');point(d,(10,8),'fire2')
  elif race=='satyr':
   line(d,[(4,5),(3,1)],'bronze1');point(d,(11,3),'bronze1')
   point(d,(6,11),'hair')
  elif race=='lizardfolk':
   poly(d,[(5,9),(4,10),(9,11),(11,9)],'skin_lizard');point(d,(6,8),'cream')
 if cls=='duelist':
  if race=='halfling':
   point(d,(4,9),'hairlight');point(d,(5,10),'hairlight')
  elif race=='elf':
   poly(d,[(12,7),(15,6),(13,9)],'skin_pale');point(d,(4,9),'hair')
  elif race=='tiefling':
   line(d,[(10,3),(13,1)],'obs1');point(d,(6,9),'pink')
  elif race=='feline':
   poly(d,[(4,4),(3,1),(6,4)],'skin_olive');point(d,(3,2),'pink')
   poly(d,[(10,4),(12,1),(12,5)],'skin_olive');point(d,(12,2),'pink')
 if cls=='battlemage':
  if race=='dwarf':
   poly(d,[(5,10),(11,10),(10,14),(7,14)],'hair');point(d,(7,12),'hairlight')
  elif race=='elf':
   poly(d,[(12,7),(15,6),(13,9)],'skin_pale')
  elif race=='tiefling':
   line(d,[(3,5),(2,2)],'obs1');line(d,[(12,5),(13,2)],'obs1')
  elif race=='undead':
   rect(d,(6,7,10,10),'bone');point(d,(7,8),'ink');point(d,(10,8),'ink')
 if cls=='warden':
  # Visibly framed skin in the lower helm opening, with eyes and nose.
  rect(d,(6,7,10,10),skin)
  point(d,(7,8),'ink');point(d,(10,8),'ink')
  point(d,(8,10),'leather')
  if race=='dwarf':
   rect(d,(6,11,10,13),'hairlight');point(d,(8,14),'hair')
  elif race=='orc':
   point(d,(6,10),'bone');point(d,(11,10),'bone')
  elif race=='dryad':
   point(d,(5,5),'verd1');point(d,(12,4),'pink');point(d,(11,3),'verd1')
  elif race=='beastkin':
   poly(d,[(5,10),(8,12),(11,10)],'skin_olive');point(d,(8,10),'ink')
 # side shoulder emblem differentiating each set
 embell=['gold','frost0','storm1','fire1','verd1'][index]
 point(d,(3,13),embell)
 return im

def portraits():
 for cls,variants in CLASSES.items():
  for idx,(race,motif) in enumerate(variants):
   im=portrait(cls,idx,race,motif)
   name='portrait_'+cls+(('_'+str(idx)) if idx else '')
   save(name,im,'portrait','top-left',f'{race} {cls} variant {idx}; motif: {motif}',extra=idx>0)

# ---- faction monsters; intentionally distinct elites ----------------------------
def monster(faction,elite=False):
 im=image((16,16));d=draw(im)
 if faction=='ashclaw':
  color='wood2' if not elite else 'hair'
  poly(d,[(2,14),(3,9),(6,7),(11,7),(13,10),(14,15)],'ink')
  poly(d,[(3,14),(4,9),(7,8),(11,8),(12,10),(13,14)],color)
  poly(d,[(4,8),(4,4),(7,2),(11,3),(12,6),(10,9)],'ink')
  poly(d,[(5,7),(5,5),(7,3),(10,4),(11,6),(9,8)],color)
  poly(d,[(4,5),(2,2),(6,4)],'stone2');point(d,(5,6),'fire1')
  line(d,[(6,10),(4,14)],'red',2)
  if elite:
   poly(d,[(4,3),(2,0),(6,3)],'bone');poly(d,[(11,3),(14,0),(12,5)],'bone')
   rect(d,(10,10,15,12),'stone0');point(d,(14,11),'bronze1')
  else:
   line(d,[(11,10),(14,7)],'iron1',2)
 elif faction=='hollowbound':
  if elite:
   # wraith robe and smoky silhouette floating
   poly(d,[(3,15),(3,10),(1,7),(4,2),(7,1),(11,3),(13,8),(15,13),(11,12),(10,15),(7,13)],'ink')
   poly(d,[(4,14),(4,9),(2,7),(5,3),(8,2),(11,4),(12,8),(14,12),(10,11),(9,14),(7,12)],'obs0')
   oval(d,(6,5,10,8),'grave1');point(d,(7,6),'frost1');point(d,(10,6),'frost1')
   line(d,[(3,10),(1,11)],'grave1');line(d,[(11,11),(14,13)],'teal')
  else:
   poly(d,[(4,13),(3,6),(5,3),(11,3),(13,7),(11,14)],'ink')
   poly(d,[(5,13),(4,6),(6,4),(10,4),(12,7),(10,13)],'wood0')
   oval(d,(6,4,10,9),'bone');point(d,(7,7),'ink');point(d,(10,7),'ink')
   line(d,[(7,9),(9,9)],'ink')
   rect(d,(5,11,9,13),'grave0')
 elif faction=='embermaw':
  color='fire0' if not elite else 'red'
  poly(d,[(2,12),(3,9),(4,7),(7,6),(10,8),(12,10),(13,14),(8,14),(5,13)],'ink')
  poly(d,[(3,12),(4,9),(6,7),(9,8),(12,11),(12,13),(8,13)],color)
  poly(d,[(3,8),(5,4),(8,3),(11,4),(12,7),(9,9),(5,10)],'ink')
  poly(d,[(4,8),(6,5),(9,4),(11,5),(11,7),(8,8),(5,9)],color)
  point(d,(5,7),'cream');point(d,(12,5),'fire2')
  poly(d,[(9,8),(11,3),(15,5),(13,11)],'ink')
  poly(d,[(10,8),(12,4),(14,6),(12,10)],'fire1')
  if elite:
   line(d,[(6,3),(7,0)],'bone');line(d,[(9,3),(11,1)],'bone')
   point(d,(14,11),'fire2');point(d,(15,12),'fire1')
  else:
   point(d,(12,4),'gold')
 return im

def monsters():
 m=[('faction_ashclaw_raider','ashclaw',False),('faction_ashclaw_brute','ashclaw',True),
    ('faction_hollowbound_shade','hollowbound',False),('faction_hollowbound_wraith','hollowbound',True),
    ('faction_embermaw_whelp','embermaw',False),('faction_embermaw_drake','embermaw',True)]
 for id,faction,elite in m:save(id,monster(faction,elite),'monster','bottom-center','Elite has distinctive silhouette' if elite else 'Faction grunt')

# ---- 12x12 material and blessing icon drawings ----------------------------------
def material_icon(name):
 im=image((12,12));d=draw(im)
 if name in METALS:
  shade,light=METALS[name]
  poly(d,[(1,5),(3,3),(9,3),(10,5),(9,9),(2,9)],'ink')
  poly(d,[(2,5),(4,4),(9,4),(9,5),(8,8),(3,8)],shade)
  line(d,[(3,5),(8,5)],light)
  if name in ('starsteel','moonsteel'):point(d,(7,5),'cream')
 elif name=='ember_resin':
  poly(d,[(2,9),(3,5),(5,3),(9,4),(10,9)],'ink');poly(d,[(3,8),(4,5),(6,4),(8,5),(9,8)],'fire1');point(d,(6,5),'fire2')
 elif name=='frost_bloom':
  for p in [[(6,2),(6,9)],[(3,4),(9,7)],[(3,8),(9,4)]]:line(d,p,'frost1')
  rect(d,(5,5,7,7),'frost0');point(d,(6,5),'cream')
 elif name=='stormglass':
  poly(d,[(5,1),(9,4),(7,10),(3,7)],'ink');poly(d,[(5,2),(8,4),(7,9),(4,7)],'storm1');point(d,(6,5),'cream')
 elif name=='grave_dust':
  poly(d,[(3,4),(8,4),(10,10),(2,10)],'grave0');line(d,[(4,4),(7,4)],'grave1')
  rect(d,(5,7,7,8),'bone');point(d,(5,7),'ink');point(d,(7,7),'ink')
 elif name=='verdant_sap':
  poly(d,[(6,1),(3,6),(3,8),(5,10),(8,9),(9,7)],'ink');poly(d,[(6,2),(4,6),(4,8),(6,9),(8,7)],'verd0');point(d,(5,6),'verd1')
 elif name=='sun_ash':
  poly(d,[(2,9),(4,6),(7,5),(10,9)],'ink');poly(d,[(3,8),(5,6),(7,6),(9,8)],'gold');point(d,(5,6),'cream')
 elif name=='binding_salt':
  for x,y in [(2,7),(5,5),(8,7)]:poly(d,[(x,y),(x+1,y-2),(x+3,y),(x+2,y+2)],'bone')
 elif name=='runestone_shard':
  poly(d,[(3,2),(9,2),(10,9),(4,10),(2,6)],'stone0')
  line(d,[(5,4),(8,4),(6,7),(8,8)],'frost1')
 elif name=='dragon_oil':
  rect(d,(4,2,7,3),'wood2');rect(d,(3,4,8,10),'ink');rect(d,(4,5,7,9),'fire0')
  point(d,(6,6),'fire2');point(d,(5,8),'fire1')
 elif name=='void_ink':
  rect(d,(3,4,9,10),'ink');rect(d,(4,5,8,9),'obs0');rect(d,(4,3,8,4),'gold');point(d,(6,6),'storm1')
 return im

def blessing_icon(name):
 im=image((12,12));d=draw(im)
 if name=='forgefire':
  poly(d,[(6,1),(3,6),(4,10),(8,10),(9,6)],'fire0');poly(d,[(6,3),(5,7),(6,9),(8,7)],'fire2')
 elif name=='tireless_hands':
  rect(d,(3,2,9,4),'iron1');rect(d,(5,4,6,10),'wood1');point(d,(3,2),'silver1')
 elif name=='merchants_favor':
  for x,y in [(2,6),(5,4),(6,7)]:oval(d,(x,y,x+3,y+3),'gold')
 elif name=='runic_insight':
  oval(d,(1,3,10,8),'storm0');oval(d,(3,4,8,7),'bone');oval(d,(5,5,6,6),'frost0')
 elif name=='stalwart_town':
  poly(d,[(3,2),(9,2),(9,7),(6,10),(3,7)],'iron1');rect(d,(5,3,7,6),'gold')
 elif name=='hunters_edge':
  line(d,[(2,9),(9,2)],'wood2');poly(d,[(7,2),(10,1),(9,4)],'iron1')
 elif name=='lucky_alloy':
  for x,y in [(4,3),(6,3),(4,5),(6,5)]:oval(d,(x,y,x+2,y+2),'verd1')
  line(d,[(5,6),(6,10)],'verd0')
 elif name=='guild_patronage':
  rect(d,(3,2,9,8),'blue');poly(d,[(3,7),(6,10),(9,7)],'blue');line(d,[(6,3),(6,7)],'gold')
 return im

def material_blessings():
 for name in list(METALS)+['ember_resin','frost_bloom','stormglass','grave_dust','verdant_sap','sun_ash','binding_salt','runestone_shard','dragon_oil','void_ink']:
  save('material_'+name,material_icon(name),'material','top-left','Crafting ingredient')
 for name in ['forgefire','tireless_hands','merchants_favor','runic_insight','stalwart_town','hunters_edge','lucky_alloy','guild_patronage']:
  save('blessing_'+name,blessing_icon(name),'blessing','top-left','Survived-siege reward')

# ---- UI icons, nine-slice panels -------------------------------------------------
def nav_icon(name):
 im=image((12,12));d=draw(im);c='bone'
 if name=='forge':
  poly(d,[(1,5),(10,5),(9,7),(7,7),(7,10),(4,10),(4,7),(2,7)],c)
 elif name=='market':
  rect(d,(2,5,9,10),c);line(d,[(1,4),(10,4)],'bone');rect(d,(3,7,4,9),'ink')
 elif name=='town':
  rect(d,(3,5,9,10),c);poly(d,[(2,5),(6,1),(10,5)],c);rect(d,(5,7,6,10),'ink')
 elif name=='journal':
  rect(d,(2,2,9,10),c);rect(d,(3,2,3,10),'ink');line(d,[(5,5),(8,5)],'ink')
 elif name=='gazette':
  rect(d,(2,2,9,10),c);rect(d,(4,3,8,4),'ink');line(d,[(4,6),(8,6)],'ink');line(d,[(4,8),(8,8)],'ink')
 elif name=='legacy':
  oval(d,(3,1,8,6),c);rect(d,(4,6,7,8),c);poly(d,[(4,7),(3,10),(6,9),(9,10),(8,7)],c)
 return im

def small_icon(name):
 im=image((8,8));d=draw(im)
 if name=='energy':line(d,[(5,0),(2,4),(4,4),(3,7),(6,3),(4,3)],'fire2')
 elif name=='gold':oval(d,(1,1,6,6),'gold');point(d,(3,2),'cream')
 elif name=='integrity':poly(d,[(1,1),(6,1),(6,4),(4,7),(1,4)],'iron1')
 elif name=='militia':line(d,[(2,1),(6,6)],'silver1');line(d,[(6,1),(2,6)],'silver1')
 elif name=='reputation':point(d,(3,0),'gold');line(d,[(3,1),(3,6)],'gold');line(d,[(1,3),(6,3)],'gold')
 elif name=='day':oval(d,(2,2,5,5),'fire2');point(d,(3,0),'cream');point(d,(7,3),'cream')
 return im

def panel(kind):
 im=image((24,24));d=draw(im)
 border='wood1' if kind=='journal' else 'stone1'
 light='gold' if kind=='journal' else 'bone'
 fill='deep' if kind=='journal' else 'skin_pale'
 rect(d,(0,0,23,23),'ink');rect(d,(1,1,22,22),border);rect(d,(4,4,19,19),fill)
 for x in (2,20):
  for y in (2,20):rect(d,(x,y,x+1,y+1),light)
 # 8 px fixed borders; plain stretchable center.
 return im

def chrome():
 for n in ['forge','market','town','journal','gazette','legacy']:
  save('icon_nav_'+n,nav_icon(n),'ui_nav','top-left','Monochrome tintable nav icon')
 for n in ['energy','gold','integrity','militia','reputation','day']:
  save('icon_'+n,small_icon(n),'ui_status','top-left','Top status icon')
 for n in ['gazette','journal']:
  save('panel_'+n,panel(n),'ui_panel','tile','9-patch style; 8px frame on all sides')
 im=image((16,16));d=draw(im);rect(d,(0,0,15,15),'skin_pale')
 # subtle repeating paper flecks; seam-free texture
 for x,y in [(3,3),(8,7),(12,12),(2,11)]:point(d,(x,y),'skin')
 save('tile_paper',im,'ui_tile','tile','Subtle paper texture; seamless')

# ---- Combat animation side sprites and siege stage -------------------------------
def combat_base(cls):
 im=image((16,16));d=draw(im)
 cloth=TORSO[cls]
 poly(d,[(2,15),(2,10),(5,8),(9,8),(12,11),(11,15)],'ink')
 poly(d,[(3,14),(3,11),(6,9),(9,9),(11,11),(10,14)],cloth)
 oval(d,(5,3,11,9),'skin')
 point(d,(6,6),'ink')
 if cls=='guardian':rect(d,(5,2,11,5),'stone1');line(d,[(5,6),(10,6)],'ink')
 elif cls=='ranger':poly(d,[(3,6),(6,1),(10,2),(12,6)],'verd0')
 elif cls=='duelist':line(d,[(3,4),(12,4)],'red',2);point(d,(11,1),'cream')
 elif cls=='battlemage':poly(d,[(4,4),(7,0),(9,1),(11,4)],'purple');line(d,[(3,5),(12,5)],'purple')
 else:line(d,[(5,4),(3,0)],'wood2');line(d,[(10,3),(12,0)],'wood2')
 return im

def shift_layer(im,dx=0,dy=0):
 dest=image(im.size);dest.alpha_composite(im,(dx,dy));return dest

def stage(damaged=False):
 im=image((96,32));d=draw(im)
 rect(d,(0,0,95,31),'stone0')
 # distant slate wall with small block detail and stylized towers
 for y in range(0,32,6):
  line(d,[(0,y),(95,y)],'stone1')
  for x in range((y//6)%2*7,96,14):line(d,[(x,y),(x,y+5 if y+5<32 else 31)],'ash')
 for x in (9,81):
  rect(d,(x-6,9,x+6,30),'ink');rect(d,(x-5,8,x+5,30),'stone1')
  for p in (x-5,x-1,x+4):rect(d,(p,6,p+1,9),'stone2')
 # large gate on right background to keep playable foreground open
 poly(d,[(40,31),(40,19),(44,16),(52,16),(56,19),(56,31)],'ink')
 rect(d,(43,20,53,31),'wood0');line(d,[(48,20),(48,31)],'wood2')
 if damaged:
  poly(d,[(10,6),(17,10),(15,16),(23,20),(15,19),(12,14)],'deep')
  line(d,[(9,11),(18,17)],'fire0');point(d,(17,18),'fire2')
  rect(d,(46,24,53,31),'ink')
 else:
  rect(d,(47,15,49,18),'gold')
 return im

def animation():
 for cls in CLASSES:
  base=combat_base(cls)
  for j in range(4):
   im=shift_layer(base,0,(-1 if j==1 else 0))
   if j==1:
    # Blink and chest motion; distinct on the fixed game grid.
    d=draw(im);point(d,(6,5),'skin');point(d,(6,6),'skin')
    point(d,(9,12),'silver1' if cls=='guardian' else 'gold')
   if j>=2:
    d=draw(im)
    # attacking empty hand reaches forward; weapon is composited separately
    line(d,[(10,10),(13 if j==2 else 14,8 if j==2 else 9)],'skin',2)
    if j==3:point(d,(8,12),'gold')
   save(f'hero_{cls}_{"idle" if j<2 else "attack"}_{j%2}',im,'animation','bottom-center',
     'Side view facing right; empty attack hand',120)
 for id,faction,elite in [
  ('ashclaw_raider','ashclaw',False),('ashclaw_brute','ashclaw',True),
  ('hollowbound_shade','hollowbound',False),('hollowbound_wraith','hollowbound',True),
  ('embermaw_whelp','embermaw',False),('embermaw_drake','embermaw',True)]:
  base=monster(faction,elite)
  frames=[('idle',0,0),('idle',1,0),('attack',0,-1),('attack',1,-2),('hit',0,1)]
  for k,idx,dx in frames:
   im=shift_layer(base,dx,0)
   d=draw(im)
   if k=='idle' and idx==1:
    point(d,(5,6),'cream' if faction=='embermaw' else 'frost1')
    point(d,(9,10),'stone2' if faction=='hollowbound' else 'gold')
   elif k=='attack' and idx==0:
    point(d,(3,10),'fire2' if faction=='embermaw' else 'bone')
   elif k=='attack' and idx==1:
    line(d,[(2,8),(3,9)],'fire2' if faction=='embermaw' else 'bone')
    point(d,(4,5),'red' if faction=='ashclaw' else 'frost1')
   elif k=='hit':
    point(d,(2,2),'bone');point(d,(3,1),'fire2')
   save(f'monster_{id}_{k}_{idx}' if k!='hit' else f'monster_{id}_hit',im,'animation','bottom-center',
     'Combat '+k+' frame',120)
 for damaged in (False,True):
  save('siege_wall'+('_damaged' if damaged else ''),stage(damaged),'siege','tile','Siege backdrop 96x32')
 for k in range(4):
  im=image((16,16));d=draw(im)
  for dx,dy in [(-3,0),(3,0),(0,-3),(0,3)]:
   if k<3:point(d,(8+dx,8+dy),'gold' if k%2 else 'cream')
  if k in (1,2):
   line(d,[(8,5),(8,11)],'cream');line(d,[(5,8),(11,8)],'cream')
   if k==2:
    point(d,(5,5),'gold');point(d,(11,11),'gold')
  if k==3:point(d,(8,8),'fire2')
  save('fx_milestone_'+str(k),im,'animation','top-left','Milestone burst',100)
 im=image((8,8));d=draw(im);rect(d,(2,4,5,7),'stone1');line(d,[(2,4),(5,4)],'bone')
 save('marker_dead',im,'marker','top-left','Gravestone')
 im=image((8,8));d=draw(im)
 for xy in [(1,4),(2,3),(2,5),(3,2),(4,1),(5,2),(6,3),(6,4),(5,5)]:point(d,xy,'verd1')
 save('marker_retired',im,'marker','top-left','Laurel wreath')

# ---- Contact sheets and packaging -----------------------------------------------
def contact(group_ids,filename,cols=8,zoom=6,label_h=14,background=(27,24,26)):
 ids=list(group_ids);tw=max(16,max((a['width'] for a in manifest if a['id'] in ids),default=16))
 th=max(16,max((a['height'] for a in manifest if a['id'] in ids),default=16))
 cellw=max(110,tw*zoom+16);cellh=th*zoom+label_h+17
 rows=math.ceil(len(ids)/cols)
 im=Image.new('RGB',(cellw*cols,cellh*rows+38),background);d=ImageDraw.Draw(im)
 d.text((12,11),filename.replace('_',' ').replace('.png','').upper(),fill=(238,213,172))
 for idx,id in enumerate(ids):
  a=next(a for a in manifest if a['id']==id)
  x=(idx%cols)*cellw;y=(idx//cols)*cellh+36
  # checkerboard helps check transparent margins
  sw=a['width']*zoom;sh=a['height']*zoom
  for yy in range(0,sh,zoom*2):
   for xx in range(0,sw,zoom*2):
    d.rectangle((x+6+xx,y+4+yy,x+6+xx+zoom*2-1,y+4+yy+zoom*2-1),fill=(47,43,47) if (xx//(zoom*2)+yy//(zoom*2))%2 else (56,53,57))
  asset=Image.open(OUT/(id+'.png')).resize((sw,sh),Image.Resampling.NEAREST)
  im.paste(asset,(x+6,y+4),asset)
  # break long ids in two chunks for contact sheet label
  d.text((x+5,y+sh+8),id[:int((cellw-8)/6)],fill=(224,218,207))
 im.save(PREVIEW/filename)

def main():
 forge();weapons();overlays_badges();portraits();monsters();material_blessings();chrome();animation()
 # deterministic manifest by generation order, explicit metadata
 with open(ROOT/'manifest.json','w',encoding='utf-8') as f:json.dump({'name':'Tiny Blacksmith V2 pixel-grid working pack','palette':C,'assets':manifest},f,indent=2)
 with open(ROOT/'palette.gpl','w') as f:
  f.write('GIMP Palette\nName: Tiny Blacksmith V2\nColumns: 8\n#\n')
  for n,k in C.items(): f.write(f'{P[n][0]:3} {P[n][1]:3} {P[n][2]:3} {n}\n')
 categories=[('forge',8),('weapon',8),('signature',6),('portrait',5),('monster',6),('material',8),('blessing',8),('overlay',6),('badge',6),('ui_nav',6),('ui_status',6)]
 for kind,cols in categories:
  ids=[a['id'] for a in manifest if a['kind']==kind]
  contact(ids,f'{kind}_contact.png',cols=cols,zoom=(5 if kind=='forge' else 6))
 contact([a['id'] for a in manifest if a['kind']=='animation'][:54],'animation_contact.png',cols=9,zoom=5)
 # single visual index of all non-animated sprites at 4x; animation gets own contact sheet
 ids=[a['id'] for a in manifest if a['kind'] not in ('animation','siege','ui_panel','ui_tile')]
 contact(ids,'master_contact.png',cols=10,zoom=4)
 readme='''# Tiny Blacksmith — V2 production starter pack\n\n## What this is\nGenuine 1x integer-grid RGBA pixel sprites, drawn programmatically from deliberately authored shapes.\nThis is an integration-ready **working sprite draft**, not a pixel-perfect extraction of the earlier AI concept boards. The concept boards are visual references; those enlarged images cannot safely be sliced into 16x16 game sprites without repainting.\n\n## Files\n- `drawable-nodpi/`: exactly named individual PNG assets.\n- `manifest.json`: IDs, sizes, classes, anchors, duration metadata, notes.\n- `previews/`: nearest-neighbor contact sheets with labeled IDs.\n- `palette.gpl`: coherent palette editable in most pixel tools.\n- `tools/build_assets.py`: full editable pixel source; rerun with Pillow installed.\n\n## Integration\nCopy `drawable-nodpi/*.png` to `app/src/main/res/drawable-nodpi/`. Disable the placeholder generator only for IDs you replace, and preserve the renderer's fixed anchor and nearest-neighbor settings.\nThe five `portrait_<class>` IDs match the art brief. Four additional variants per class use `portrait_<class>_1..4`: **the game must select one of these IDs explicitly**; it will not use variants without that code change.\n\n## Deliberate boundaries\nOnly the initial 12 named signatures are included. The brief intentionally leaves 12 additional signatures unnamed, so none were fabricated.\nAnimations and portrait variants are experimental; verify poses and layering in the Compose renderer. `panel_gazette` and `panel_journal` use 8px borders but may need Android nine-patch conversion depending on implementation.\nThe existing GDD has no established species/race rules: races here are artistic variation, not invented game mechanics.\n\n## Art direction\nStyle F: clean 16-bit readability + hand-made forge personality. Upper-left highlights. All text remains UI-rendered. Strong silhouettes, restrained world colors, saturated metals and spell sparks.\n\n## Verification\nRun `python tools/build_assets.py` (Pillow required) and inspect `previews/master_contact.png` at 1x and enlarged. All PNGs use RGBA and exact logical pixel dimensions.\n'''
 (ROOT/'README.md').write_text(readme)
 # build manifest consistency check
 bad=[]
 for a in manifest:
  im=Image.open(OUT/(a['id']+'.png'))
  if im.size!=(a['width'],a['height']) or im.mode!='RGBA': bad.append(a['id'])
  badcols=set(p[:3] for p in im.getdata() if p[3]>0)-set(P.values())
  if badcols:bad.append((a['id'],badcols))
 assert not bad,bad
 print('Assets:',len(manifest),'Distinct palette RGB colors:',len(set(P.values())),'Failures:',len(bad))
 print('Categories:',dict(collections.Counter(a['kind'] for a in manifest)))
 print('Extra portraits:',sum(1 for a in manifest if a['kind']=='portrait' and a['extra']))
 print('All exact PNG sizes, palette membership, RGBA: PASS')
if __name__=='__main__':main()

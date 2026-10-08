from PIL import Image,ImageDraw,ImageFont
from pathlib import Path
import json, random, math, zipfile, csv
R=Path('/mnt/data/Tiny_Blacksmith_UI_Backgrounds_v3')
D=R/'drawable-nodpi'; P=R/'previews'; D.mkdir(exist_ok=True); P.mkdir(exist_ok=True)
# Exact selected RGB values from the existing 48-colour V2 asset pack.
C={
 'ink':(26,18,16),'deep':(43,35,32),'wood0':(74,59,51),'wood1':(107,75,50),'wood2':(140,98,57),'bone':(232,228,218),
 'cream':(255,240,160),'stone0':(85,89,93),'stone1':(125,129,134),'stone2':(176,180,184),'ash':(92,97,102),
 'fire0':(179,48,26),'fire1':(226,96,31),'fire2':(255,193,77),'skin':(224,176,138),'leather':(122,74,42),
 'blue':(58,90,138),'red':(160,48,48),'gold':(216,160,48),'iron0':(92,97,102),'iron1':(138,143,148),
 'bronze0':(138,90,34),'bronze1':(199,136,58),'silver0':(154,163,173),'silver1':(217,221,227),
 'obs0':(66,50,84),'obs1':(120,96,140),'star0':(92,134,184),'star1':(170,210,236),
 'moon0':(134,126,190),'moon1':(206,200,240),'frost0':(58,160,216),'frost1':(127,216,255),
 'storm0':(106,75,214),'storm1':(180,140,255),'grave0':(80,104,84),'grave1':(178,206,150),
 'verd0':(90,160,90),'verd1':(156,222,120),'skin_dark':(148,102,71),'skin_olive':(167,176,120),
 'skin_pale':(234,208,186),'skin_orc':(112,145,86),'skin_lizard':(75,144,135),'teal':(67,189,166),
 'hair':(71,52,47),'hairlight':(203,166,111),'purple':(128,88,165),'pink':(228,161,191),
}
INK=C['ink']; TRANSPARENT=(0,0,0,0)
def rgba(name, a=255):
 rgb=C[name] if isinstance(name,str) else name
 return (*rgb,a)
def layer(w,h):return Image.new('RGBA',(w,h),TRANSPARENT)
def rect(d, xy, color):d.rectangle(xy, fill=rgba(color))
def line(d, points, color, width=1):d.line(points,fill=rgba(color),width=width)
def pix(d,x,y,color):d.point((int(x),int(y)), fill=rgba(color))
def ellipse(d,xy,color):d.ellipse(xy, fill=rgba(color))
def save(name,im,category,mode='new',notes=''):
 path=D/(name+'.png'); im.save(path)
 entries.append(dict(id=name,path='drawable-nodpi/'+path.name,width=im.width,height=im.height,category=category,integration=mode,notes=notes))
 return im
entries=[]
seed=random.Random(270319)

def brickwall(im, y0=0, y1=None, base='wood0', stone='stone0', joint='deep', stagger=0):
 d=ImageDraw.Draw(im); w,h=im.size; y1=y1 if y1 is not None else h
 rect(d,(0,y0,w-1,y1-1),base)
 for row,y in enumerate(range(y0,y1,6)):
  off= 0 if (row+stagger)%2 else 7
  line(d,[(0,y),(w-1,y)],joint)
  for x in range(-off,w+8,14):
   line(d,[(x,y),(x,y+5)],joint)
   if 0<=x+2<w and y+1<y1: pix(d,x+2,y+1,stone)

def planks(im, y0, y1, base='wood1'):
 d=ImageDraw.Draw(im);w,h=im.size
 rect(d,(0,y0,w-1,y1-1),base)
 for y in range(y0,y1,5):
  line(d,[(0,y),(w-1,y)],'deep')
  for x in range(3,w,17):
   pix(d,x+((y//5)*5)%11,y+2,'wood2')
   if (x+((y//5)*5)%11)<w: pix(d,x+((y//5)*5)%11,y+3,'wood0')

def timber(d,x,y,w,h):
 rect(d,(x,y,x+w,y+h),'deep')
 rect(d,(x+1,y+1,x+w-1,y+h-1),'wood1')
 line(d,[(x+1,y+1),(x+w-1,y+1)],'wood2')
 for yy in range(y+4,y+h,8):pix(d,x+2,yy,'wood0')

def window(d,x,y,w=9,h=12):
 rect(d,(x,y,x+w,y+h),'deep')
 rect(d,(x+2,y+2,x+w-2,y+h-2),'blue')
 rect(d,(x+2,y+3,x+w-2,y+4),'star0')
 line(d,[(x+w//2,y+2),(x+w//2,y+h-2)],'wood2')
 line(d,[(x+2,y+h//2),(x+w-2,y+h//2)],'wood2')
 pix(d,x+3,y+3,'star1')

def lantern(d,x,y):
 line(d,[(x+2,y-5),(x+2,y)],'ink')
 rect(d,(x,y,x+5,y+7),'ink');rect(d,(x+1,y+1,x+4,y+5),'fire1');rect(d,(x+2,y+2,x+3,y+4),'cream')
 pix(d,x,y,'gold');pix(d,x+5,y,'gold');line(d,[(x,y+6),(x+5,y+6)],'wood2')

def banner(d,x,y,w=7,h=15,color='red'):
 rect(d,(x,y,x+w-1,y+h-5),'ink');rect(d,(x+1,y+1,x+w-2,y+h-5),color)
 line(d,[(x+1,y+h-5),(x+w//2,y+h-1),(x+w-2,y+h-5)],'gold')
 pix(d,x+w//2,y+5,'fire2')

def plants(d,x,y,height=10):
 line(d,[(x,y),(x,y-height)],'grave0')
 for k in range(3):
  yy=y-height+k*3
  line(d,[(x,yy),(x+3,yy-2)],'verd0');line(d,[(x,yy+1),(x-3,yy-1)],'verd1')
  pix(d,x+3,yy-2,'verd1');pix(d,x-3,yy-1,'verd0')

def flame(d,x,y,scale=1):
 # Ember with stepped asymmetric silhouette and carefully restrained bright pixels.
 points=[(0,0),(1,-2),(0,-4),(3,-6),(2,-10),(5,-8),(6,-13),(9,-9),(8,-4),(10,-3),(8,1)]
 pts=[(x+px*scale,y+py*scale) for px,py in points]
 d.polygon(pts,fill=rgba('fire0'))
 d.polygon([(x+2*scale,y),(x+3*scale,y-6*scale),(x+6*scale,y-8*scale),(x+7*scale,y)],fill=rgba('fire1'))
 d.polygon([(x+4*scale,y),(x+5*scale,y-5*scale),(x+6*scale,y-2*scale),(x+6*scale,y)],fill=rgba('cream'))

def anvil(d,x,y):
 rect(d,(x+4,y+7,x+14,y+10),'wood0');rect(d,(x+6,y+8,x+12,y+12),'wood1')
 rect(d,(x+2,y+3,x+17,y+5),'ink');rect(d,(x+3,y+2,x+16,y+4),'stone2')
 d.polygon([(x+3,y+3),(x-2,y+4),(x+5,y+6)],fill=rgba('iron1'))
 rect(d,(x+7,y+5,x+13,y+7),'stone0');pix(d,x+5,y+2,'silver1')

def forge_scene(heat='warm',night=False):
 im=layer(96,48);d=ImageDraw.Draw(im)
 brickwall(im,0,39,base='wood0' if night else 'wood1',stone='wood1' if night else 'wood2')
 planks(im,39,48,'wood0')
 timber(d,0,0,5,38);timber(d,88,0,7,38)
 line(d,[(0,5),(95,5)],'deep',3);line(d,[(1,7),(94,7)],'wood2')
 # The furnace has a recognizable, stepped masonry silhouette.
 rect(d,(8,15,35,41),'ink');rect(d,(9,15,34,40),'stone0')
 rect(d,(11,14,32,38),'stone1');rect(d,(14,17,29,34),'ink')
 rect(d,(15,19,28,34),'deep')
 for by in [16,22,28]:
  line(d,[(10,by),(13,by)],'stone2');line(d,[(31,by),(34,by)],'stone2')
 if heat=='hot':
  flame(d,15,34,1);flame(d,22,34,1);flame(d,18,30,1)
 elif heat=='warm':
  flame(d,17,34,1);pix(d,27,32,'fire2')
 else:
  for x in [17,20,22,25]:pix(d,x,33,'stone0')
 rect(d,(8,38,35,40),'stone2')
 # Tools, plant life and an open shelf.
 for x,shape in [(41,'h'),(45,'t'),(49,'f')]:
  line(d,[(x,12),(x,28)],'silver0');pix(d,x,13,'cream')
  if shape=='h':rect(d,(x-2,12,x+2,15),'iron0')
  if shape=='t':line(d,[(x-2,14),(x,20),(x+2,14)],'silver1')
  if shape=='f':line(d,[(x,14),(x+1,19)],'bone')
 anvil(d,51,28)
 rect(d,(73,25,88,27),'deep');rect(d,(73,26,87,26),'wood2')
 rect(d,(73,28,76,38),'wood0');rect(d,(86,28,88,38),'wood0')
 for x in [74,79,84]:rect(d,(x,22,x+3,25),'bronze0')
 window(d,65,9,11,12)
 plants(d,83,22,10)
 banner(d,56,6,6,13)
 lantern(d,34,16)
 if night:
  for x,y in [(70,12),(72,11),(70,16)]: pix(d,x,y,'moon1')
 return im

def town_scene():
 im=layer(96,48);d=ImageDraw.Draw(im)
 rect(d,(0,0,95,31),'blue')
 for xx,yy in [(6,9),(27,4),(52,8),(75,3)]:pix(d,xx,yy,'star1')
 d.polygon([(0,30),(21,12),(37,27),(53,13),(79,25),(95,14),(95,34)],fill=rgba('stone0'))
 rect(d,(0,30,95,47),'stone1')
 # town wall and watch towers
 for x in (3,79):
  rect(d,(x,14,x+13,41),'stone0');rect(d,(x-1,12,x+14,17),'stone1')
  for t in range(x,x+13,4):rect(d,(t,10,t+2,13),'stone1')
  window(d,x+4,20,5,10)
 rect(d,(17,22,78,44),'stone1')
 for x in range(19,77,7):rect(d,(x,20,x+4,23),'stone2')
 rect(d,(40,30,57,47),'ink');rect(d,(42,30,55,46),'wood0')
 for y in range(32,46,3):line(d,[(42,y),(55,y)],'wood1')
 rect(d,(39,29,58,31),'stone0')
 banner(d,24,22,6,15);banner(d,68,22,6,15)
 return im

def market_scene():
 im=layer(96,48);d=ImageDraw.Draw(im)
 brickwall(im,0,39,'wood1','wood2')
 planks(im,40,48,'stone0')
 for start,color in [(6,'red'),(62,'blue')]:
  rect(d,(start+3,25,start+28,34),'wood0');rect(d,(start+4,24,start+27,26),'wood2')
  rect(d,(start+4,34,start+6,41),'deep');rect(d,(start+25,34,start+27,41),'deep')
  rect(d,(start,14,start+32,17),'gold')
  for i in range(8):rect(d,(start+i*4,18,start+i*4+3,22),color if i%2==0 else 'cream')
  for x in range(start+5,start+28,8):
   rect(d,(x,28,x+4,31),'bronze0');pix(d,x+1,28,'fire2')
 for x in [44,52]:
  rect(d,(x,32,x+6,40),'leather');rect(d,(x+1,31,x+5,33),'gold')
 lantern(d,43,19);plants(d,86,35,8)
 return im

def paper(d,x,y,w,h):
 rect(d,(x,y,x+w,y+h),'wood0')
 rect(d,(x+1,y+1,x+w-1,y+h-1),'skin_pale')
 for yy in range(y+4,y+h-2,4):
  line(d,[(x+3,yy),(x+w-4,yy)],'hairlight')

def journal_scene():
 im=layer(96,48);d=ImageDraw.Draw(im)
 brickwall(im,0,28,'wood0','wood1')
 rect(d,(0,29,95,47),'wood0')
 for y in (33,38,44):line(d,[(0,y),(95,y)],'deep')
 rect(d,(7,7,89,15),'wood2');rect(d,(10,5,23,12),'leather');rect(d,(28,5,43,12),'red');rect(d,(50,5,62,12),'obs0')
 # journal open with quill and colored ink
 rect(d,(21,19,75,38),'ink')
 d.polygon([(22,20),(46,19),(47,35),(22,36)],fill=rgba('skin_pale'))
 d.polygon([(48,19),(73,20),(73,36),(48,35)],fill=rgba('skin_pale'))
 for x in [28,54]:
  for yy in range(23,34,3):line(d,[(x,yy),(x+14,yy)],'hairlight')
 line(d,[(75,12),(66,29)],'bone');line(d,[(75,11),(78,16),(72,19)],'star1')
 rect(d,(79,31,87,38),'deep');rect(d,(81,32,85,36),'storm0')
 lantern(d,3,13);return im

def gazette_scene():
 im=layer(96,48);d=ImageDraw.Draw(im)
 brickwall(im,0,33,'wood0','wood1');planks(im,34,48,'wood1')
 # printing press is wooden, toothed, with roller and stack of papers.
 rect(d,(7,17,42,36),'deep');rect(d,(10,18,38,22),'wood2');rect(d,(12,23,36,30),'wood0')
 rect(d,(11,31,39,33),'wood2');rect(d,(16,13,31,17),'stone0')
 for x in range(15,34,5):pix(d,x,29,'ink')
 rect(d,(51,18,86,39),'skin_pale');rect(d,(53,20,84,37),'hairlight')
 rect(d,(54,21,82,35),'skin_pale')
 for y in [24,27,30,33]:line(d,[(57,y),(80,y)],'wood1')
 rect(d,(58,21,77,22),'ink')
 for x,y in [(42,13),(46,19),(39,27)]:paper(d,x,y,9,6)
 lantern(d,5,13);return im

def legacy_scene():
 im=layer(96,48);d=ImageDraw.Draw(im)
 brickwall(im,0,42,'deep','wood0')
 rect(d,(0,41,95,47),'stone0')
 for x in range(3,95,16):
  rect(d,(x,41,x+12,43),'stone1')
  line(d,[(x+6,8),(x+6,36)],'wood2')
 # stone alcove, glowing hammer, engraved family tree silhouettes
 rect(d,(32,9,64,41),'stone0');rect(d,(34,11,62,39),'obs0')
 for x in range(40,59,5):pix(d,x,15,'gold')
 rect(d,(42,26,55,29),'gold');rect(d,(48,18,51,37),'bronze1')
 pix(d,45,27,'cream');pix(d,52,27,'cream')
 for x in [10,20,77,86]:
  plants(d,x,37,13)
 banner(d,23,11,6,19,'blue');banner(d,67,11,6,19,'red')
 return im

def title_scene(damaged=False):
 im=layer(270,150); d=ImageDraw.Draw(im)
 rect(d,(0,0,269,149),'deep' if damaged else 'obs0')
 # stars and stepped cloud wisps
 for x,y in [(25,16),(74,21),(110,8),(171,27),(206,10),(253,34),(229,7),(141,19)]:pix(d,x,y,'moon1')
 if not damaged:ellipse(d,(210,12,230,32),'cream');ellipse(d,(215,10,236,26),'obs0')
 d.polygon([(0,89),(24,76),(48,88),(78,67),(113,89),(150,65),(177,88),(207,65),(270,91),(270,120),(0,120)], fill=rgba('wood0'))
 # shop building at night, craft architecture
 rect(d,(39,58,233,135),'wood0')
 brickwall(im,61,133,'wood0','wood1',stagger=1)
 d.polygon([(26,65),(139,23),(245,66),(236,71),(138,38),(36,71)],fill=rgba('ink'))
 d.polygon([(32,62),(138,29),(238,63),(233,66),(138,38),(35,69)],fill=rgba('red' if damaged else 'wood2'))
 for x in range(44,234,16):rect(d,(x,65,x+2,131),'deep')
 rect(d,(117,78,169,135),'ink');rect(d,(121,83,164,133),'wood1')
 line(d,[(143,83),(143,131)],'wood2',2)
 rect(d,(62,81,94,112),'ink');rect(d,(65,85,90,108),'fire0')
 flame(d,68,108,2)
 window(d,189,83,25,26)
 banner(d,107,55,13,25)
 for x in (70,90,184,213):lantern(d,x,75)
 planks(im,136,150,'wood0')
 # subdued dark lower third to leave room for native Compose actions
 rect(d,(0,138,269,149),'deep')
 if damaged:
  for i in range(30):pix(d,seed.randrange(38,233),seed.randrange(53,113),'fire0')
  d.polygon([(140,25),(167,32),(187,44),(165,41),(144,34)],fill=rgba('ink'))
 return im

def siege_scene(damaged=False):
 im=layer(96,32); d=ImageDraw.Draw(im)
 rect(d,(0,0,95,31),'blue')
 d.polygon([(0,24),(14,12),(30,22),(50,8),(78,23),(96,15),(96,32),(0,32)],fill=rgba('stone0'))
 rect(d,(0,16,95,31),'stone1');
 for x in range(0,96,12):rect(d,(x,13,x+8,18),'stone1')
 for x in (0,80):
  rect(d,(x,4,x+15,31),'stone0');
  for i in range(x,x+16,6):rect(d,(i,2,i+3,5),'stone2')
 rect(d,(34,20,62,31),'wood0');rect(d,(46,20,48,31),'ink')
 for y in range(23,31,4):line(d,[(35,y),(61,y)],'wood2')
 if damaged:
  d.polygon([(57,13),(65,19),(72,17),(68,23),(62,20),(58,24)],fill=rgba('ink'))
  for x,y in [(10,7),(17,19),(66,20),(76,27)]:pix(d,x,y,'fire1')
 return im

# Background sprites: modular visual panels in the chosen game resolution, all new names except intentional siege replacements.
for name,image,note in [
 ('bg_forge_warm',forge_scene('warm'),'96x48 decorative forge; optional custom background, do not replace existing animated scene without code'),
 ('bg_forge_hot',forge_scene('hot'),'96x48 furnace hot; background only'),
 ('bg_forge_cold',forge_scene('cold'),'96x48 furnace cold; background only'),
 ('bg_forge_night',forge_scene('warm',True),'96x48 night forge; optional scene variant'),
 ('bg_market',market_scene(),'96x48 market panel art; header image requires code'),
 ('bg_town',town_scene(),'96x48 champion/town panel art; header image requires code'),
 ('bg_journal',journal_scene(),'96x48 journal panel art; header image requires code'),
 ('bg_gazette',gazette_scene(),'96x48 Gazette panel art; header image requires code'),
 ('bg_legacy',legacy_scene(),'96x48 Legacy panel art; header image requires code'),
 ('bg_title_workshop_night',title_scene(),'270x150 title backdrop; TitleScreen code required'),
 ('bg_run_end_fallen_forge',title_scene(True),'270x150 run-end art; RunEndScreen code required'),
]:save(name,image,'background',notes=note)
# Exact current sprite IDs for drop-in siege backgrounds.
save('siege_wall',siege_scene(),'siege','drop-in','Replaces existing 96x32 siege stage sprite')
save('siege_wall_damaged',siege_scene(True),'siege','drop-in','Replaces existing 96x32 damaged siege stage sprite')
# Strictly tileable, small contrast textures. Do not replace tile_wall/tile_floor: existing ForgeScene uses a shared pixels-per-cell unit from tile_wall.
for kind in ('soot','parchment','wood','iron'):
 im=layer(16,16); d=ImageDraw.Draw(im)
 base={'soot':'deep','parchment':'skin_pale','wood':'wood0','iron':'stone0'}[kind]
 rect(d,(0,0,15,15),base)
 if kind=='wood':
  for y in (0,5,10,15):line(d,[(0,y),(15,y)],'wood1')
  for x,y in ((3,2),(9,7),(13,13)):pix(d,x,y,'wood2')
 elif kind=='parchment':
  for x,y in ((2,3),(9,5),(5,12),(13,13)):pix(d,x,y,'hairlight')
 elif kind=='soot':
  for x,y in ((4,7),(11,13),(13,3)):pix(d,x,y,'wood0')
 else:
  for x,y in ((2,4),(10,2),(12,13)):pix(d,x,y,'stone1')
 save('ui_tile_'+kind,im,'tile',notes='Seamless 16x16 texture; new UI tile, needs explicit integration')
# Current paperBackground() recognizes tile_paper widths <=16 as repeatable.
im=layer(16,16); d=ImageDraw.Draw(im);rect(d,(0,0,15,15),'skin_pale')
for x,y in ((2,3),(9,9),(13,4),(7,14)):pix(d,x,y,'hairlight')
save('tile_paper',im,'tile','drop-in','16x16 seamless Gazette paper, matching paperBackground() tiling branch')
# Nine-slice corner-compatible UI frames. Center 8x8 is unfilled/transparent so Compose can own background and text.
FRAMES={
 'ui_frame_forge':('wood0','bronze1','fire2'),
 'ui_frame_market':('wood0','bronze1','gold'),
 'ui_frame_town':('stone0','stone2','blue'),
 'ui_frame_journal':('leather','bronze1','gold'),
 'ui_frame_gazette':('wood1','hairlight','skin_pale'),
 'ui_frame_legacy':('obs0','moon0','gold'),
 'ui_frame_weapon_result':('obs0','star0','fire2'),
 'ui_frame_day_report':('wood1','hairlight','skin_pale'),
 'ui_frame_run_end':('deep','red','fire1'),
 'ui_frame_blessing':('wood1','gold','cream'),
 'ui_frame_card':('wood0','stone1','bronze1'),
 'ui_frame_warning':('deep','fire0','gold')}
for name,(border,trim,ornament) in FRAMES.items():
 im=layer(24,24); d=ImageDraw.Draw(im)
 # 8px cap with an 8px stretchable interior; corners carry the detail, edges intentionally plain.
 for y in range(24):
  for x in range(24):
   edge=min(x,y,23-x,23-y)
   if edge<2:pix(d,x,y,'ink')
   elif edge<5:pix(d,x,y,border)
   elif edge<7:pix(d,x,y,trim if edge==5 else border)
   elif edge<8:pix(d,x,y,'deep')
 for cx,cy in ((3,3),(20,3),(3,20),(20,20)):
  pix(d,cx,cy,ornament);pix(d,cx-1 if cx>5 else cx+1,cy,trim)
 # fully transparent 8x8 interior to avoid art behind text.
 for y in range(8,16):
  for x in range(8,16):d.point((x,y),fill=TRANSPARENT)
 save(name,im,'nine_slice',notes='24x24 frame; split at x/y = 8,16 into 8px caps and 8px stretch center')
# Buttons and slots as standalone textures. Compose still owns all text and hit targets.
for name,colors in {
 'ui_button_primary':('wood1','fire0','gold'),
 'ui_button_secondary':('wood0','stone0','stone2'),
 'ui_button_disabled':('deep','wood0','stone0'),
 'ui_button_warning':('deep','red','fire2'),
 'ui_slot_empty':('deep','wood0','stone0'),
 'ui_slot_selected':('wood0','bronze1','gold')}.items():
 width,height=(64,24) if 'button' in name else (24,24)
 im=layer(width,height);d=ImageDraw.Draw(im)
 a,b,light=colors
 rect(d,(0,0,width-1,height-1),'ink');rect(d,(1,1,width-2,height-2),a)
 rect(d,(2,2,width-3,3),light)
 rect(d,(2,height-4,width-3,height-3),b)
 for x,y in ((3,3),(width-4,3),(3,height-4),(width-4,height-4)):pix(d,x,y,light)
 save(name,im,'chrome',notes='No text; decorative Compose button/slot layer; requires code')

def nav_icon(name):
 im=layer(20,20); d=ImageDraw.Draw(im)
 co='bone'; gold='gold'; shade='stone0';ink='ink'
 if name=='forge':
  # Horned anvil + warm spark
  rect(d,(4,9,15,11),co);rect(d,(7,12,13,14),shade);rect(d,(9,14,12,16),co)
  d.polygon([(4,9),(1,10),(5,12)],fill=rgba(co));pix(d,10,5,'fire2');pix(d,9,6,'gold')
 elif name=='market':
  rect(d,(3,8,17,9),co)
  for x in range(3,18,4): rect(d,(x,5,x+3,8),gold if x%8==3 else co)
  rect(d,(5,10,6,15),co);rect(d,(14,10,15,15),co);rect(d,(3,15,17,16),co)
 elif name=='town':
  rect(d,(4,9,16,16),co); rect(d,(7,4,12,16),shade)
  for x in [3,8,13]:rect(d,(x,6,x+3,9),co)
  rect(d,(9,12,11,16),'ink')
 elif name=='journal':
  rect(d,(3,4,16,16),co);rect(d,(5,4,7,16),gold)
  rect(d,(9,7,14,8),shade);rect(d,(9,10,14,11),shade);rect(d,(9,13,13,14),shade)
 elif name=='gazette':
  rect(d,(3,4,16,16),co);rect(d,(5,6,14,8),shade)
  for y in (10,12,14): rect(d,(6,y,14,y),shade)
 elif name=='legacy':
  d.polygon([(9,2),(12,7),(17,8),(13,12),(13,17),(9,14),(5,17),(5,12),(2,8),(7,7)], fill=rgba(co))
  pix(d,9,10,'gold');pix(d,9,11,'gold')
 return im.resize((40,40),Image.Resampling.NEAREST)
for name in ('forge','market','town','journal','gazette','legacy'):
 save('icon_nav_'+name,nav_icon(name),'navigation','drop-in','40x40; references in WorkshopScreen.kt; monochrome silhouette plus minimal accent')

def status_icon(name):
 im=layer(12,12);d=ImageDraw.Draw(im)
 if name=='energy':
  d.polygon([(6,1),(3,7),(5,7),(4,11),(9,5),(6,5)],fill=rgba('fire2'))
 elif name=='gold':
  ellipse(d,(1,1,10,10),'bronze1');ellipse(d,(3,3,8,8),'gold');pix(d,5,4,'cream')
 elif name=='integrity':
  d.polygon([(6,1),(10,3),(9,8),(6,11),(3,8),(2,3)],fill=rgba('stone2'))
  rect(d,(5,4,6,8),'bone')
 elif name=='militia':
  line(d,[(2,2),(9,9)],'stone2',2);line(d,[(9,2),(2,9)],'stone2',2)
  rect(d,(1,1,3,3),'gold');rect(d,(8,1,10,3),'gold')
 elif name=='reputation':
  d.polygon([(6,1),(8,4),(11,5),(8,7),(9,10),(6,8),(3,10),(4,7),(1,5),(4,4)],fill=rgba('gold'))
  pix(d,6,5,'cream')
 elif name=='day':
  ellipse(d,(3,3,8,8),'fire2')
  for xx,yy in ((6,0),(6,11),(0,6),(11,6),(2,2),(10,2),(2,10),(10,10)):pix(d,xx,yy,'gold')
 return im.resize((24,24),Image.Resampling.NEAREST)
for n in ('energy','gold','integrity','militia','reputation','day'):
 save('icon_'+n,status_icon(n),'status','drop-in','24x24; used by TopStrip, keeps essential numbers/text in Compose')
# Tiny ornamental motifs for between sections, dividers and three small states.
for name in ('ui_divider_fire','ui_divider_town','ui_divider_legacy'):
 im=layer(96,8);d=ImageDraw.Draw(im)
 line(d,[(2,3),(41,3)],'wood1');line(d,[(55,3),(93,3)],'wood1')
 if 'fire' in name:flame(d,44,5,1)
 elif 'town' in name:rect(d,(45,1,50,5),'stone2');rect(d,(47,0,49,1),'gold')
 else: d.polygon([(48,0),(51,4),(48,7),(45,4)],fill=rgba('gold'))
 save(name,im,'chrome',notes='96x8 decorative divider; optional UI')

# Build shareable contact sheets, with labels OUTSIDE the sprites.
try:FONT=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf',14)
except:FONT=ImageFont.load_default()

def sheet(names, cols, scale=4, cellw=400, cellh=250, out='sheet.png'):
 rows=(len(names)+cols-1)//cols
 im=Image.new('RGB',(cols*cellw,rows*cellh),(22,19,19)); d=ImageDraw.Draw(im)
 for i,n in enumerate(names):
  x=(i%cols)*cellw;y=(i//cols)*cellh
  d.rectangle((x+4,y+4,x+cellw-4,y+cellh-4),outline=(117,91,62),width=2)
  sprite=Image.open(D/(n+'.png')).convert('RGBA'); maxw=cellw-18;maxh=cellh-45
  sc=min(scale,maxw//sprite.width,maxh//sprite.height);sc=max(1,sc)
  preview=sprite.resize((sprite.width*sc,sprite.height*sc),Image.Resampling.NEAREST)
  im.paste(preview,(x+(cellw-preview.width)//2,y+15+(maxh-preview.height)//2),preview)
  d.text((x+15,y+cellh-28),n,font=FONT,fill=(230,205,166))
 im.save(P/out)

bgs=[e['id'] for e in entries if e['category']=='background']+['siege_wall','siege_wall_damaged']
frames=[e['id'] for e in entries if e['category']=='nine_slice']
other=[e['id'] for e in entries if e['category'] in ('navigation','status','tile','chrome')]
sheet(bgs,3,5,430,290,'background_contact.png')
sheet(frames,4,6,240,190,'frames_contact.png')
sheet(other,5,4,240,205,'ui_contact.png')
# Manifest and README.
(R/'manifest.json').write_text(json.dumps({'version':'3.0','sourceRepo':'Morfildor/BlacksmithInc','concept':'Clean Crafted Fantasy A+E','palette':'V2 exact shared 48 RGB colors','sprites':entries},indent=2))
with (R/'asset_inventory.csv').open('w',newline='') as f:
 writer=csv.DictWriter(f,fieldnames=['id','path','width','height','category','integration','notes']);writer.writeheader();writer.writerows(entries)
(R/'README.md').write_text('''# Tiny Blacksmith — Background & UI V3\n\nDesigned after reviewing `Morfildor/BlacksmithInc` on 2026-10-08. Original PNG assets; no project code was modified.\n\n## Contents\n\n- `drawable-nodpi/`: pixel-grid RGBA assets, 1× logical sizes.\n- `previews/`: NN-upscaled annotated contact sheets; annotations are **not** inside sprites.\n- `manifest.json`, `asset_inventory.csv`: dimensions, integration flags, IDs.\n- `build_ui.py`: editable deterministic PIL generation source.\n\n## Drop-in replacements (already referenced in current UI)\n\nThe six `icon_nav_*` graphics (40×40), six `icon_*` status graphics (24×24), two 96×32 `siege_wall` assets, and the 16×16 `tile_paper` can replace files of the same ID in `app/src/main/res/drawable-nodpi/`. Keep a backup or commit these on a separate branch first.\n\n## New background assets — wiring required\n\nThe files starting with `bg_` are backgrounds, not existing `R.drawable` references. Wire them into `TitleScreen`/`RunEndScreen` or panel compositions if desired. The current `WorkshopScreen` deliberately shows one persistent `ForgeScene` across all tabs; switching to per-tab banners changes that behavior. Preserve text as native Compose components.\n\n**Important**: Do **not** replace `tile_wall` or `tile_floor` alone with a different pixel unit. The existing `ForgeScene` derives its shared unit from `tile_wall.width / 16`; replacing only wall tiles would change object scaling. This pack consequently does not include drop-in replacements for those IDs.\n\n## UI frames and buttons — wiring required\n\nFrame sprites are 24×24 nine-slice assets with 8 px cap sizes. Split at x=8,16 and y=8,16; draw corners fixed, stretch edge middles and center. Buttons, slots and divider assets are purely decorative; labels, accessibility semantics, touch targets, resizing, and dynamic state stay in Compose. The `ui_frame_*` nine-slice images are not Android `.9.png` binaries: they need a nine-slice renderer.\n\n## Design intent\n\n- A+E hybrid: legible 1× silhouettes and handcrafted artisan motifs.\n- Same 48-RGB-color palette as the prior V2 production pack; upper-left lighting.\n- High-detail reserved for margins/corners, central content areas kept calm.\n- All labels remain native Compose text, never baked into shipped sprites.\n- Low-contrast ambient backgrounds, brighter accent icons for actionable controls.\n\n## Caveat\n\nThese are original pixel-grid graphic assets and valid importable PNGs, **not already integrated into the app**. Preview on a phone and refine contrast/legibility after integration.\n''')
with zipfile.ZipFile('/mnt/data/Tiny_Blacksmith_UI_Backgrounds_v3.zip','w',zipfile.ZIP_DEFLATED,compresslevel=8) as z:
 for p in R.rglob('*'):
  if p.is_file():z.write(p,p.relative_to(R.parent))
# Validation
all_rgb=set(); bad=[]
for e in entries:
 p=D/(e['id']+'.png'); a=Image.open(p).convert('RGBA')
 if a.size!=(e['width'],e['height']):bad.append('size:'+e['id'])
 all_rgb.update({v[:3] for v in a.getdata() if v[3]>0})
 if not set(v[:3] for v in a.getdata() if v[3]>0).issubset(set(C.values())):bad.append('palette:'+e['id'])
print('ASSET_COUNT',len(entries))
print('PALETTE_COLORS_USED',len(all_rgb))
print('VALIDATION_ERRORS',bad)
print('CONTACT_SHEETS',[p.name for p in P.iterdir()])
print('ZIP_SIZE_BYTES',Path('/mnt/data/Tiny_Blacksmith_UI_Backgrounds_v3.zip').stat().st_size)

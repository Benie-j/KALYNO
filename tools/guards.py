"""Portage Python de LogicGuard.kt et ContentGuard.kt (mêmes règles) pour calibrer sans téléphone."""
import re, unicodedata
NE={"ne","n"}
PART={"pas","plus","jamais","rien","aucun","aucune","nul","nulle","personne","guere","point","nullement","aucunement","ni"}
STAND={"pas","non","sans","aucun","aucune","jamais","rien","nullement"}
ANT=[("positif","negatif"),("croissan","decroissan"),("augment","diminu"),("hausse","baisse"),("acide","basique"),("vrai","faux"),("pair","impair"),("exothermique","endothermique"),("conducteur","isolant"),("oxydation","reduction"),("minimum","maximum"),("fini","infini"),("convexe","concave"),("monte","descend"),("gagne","perd"),("interieur","exterieur"),("solide","liquide"),("legal","illegal"),("democratie","dictature"),("somme","difference"),("addition","soustraction"),("multiplication","division"),("avant","apres"),("nord","sud")]
def toks(s):
    f=unicodedata.normalize('NFD',s.lower().replace('’',"'")); f=''.join(c for c in f if unicodedata.category(c)!='Mn')
    return re.findall(r"[a-z0-9]+",f)
def negcount(t):
    cons=[False]*len(t); c=0
    for i in range(len(t)):
        if t[i] in NE:
            for j in range(i+1,min(len(t),i+5)):
                if not cons[j] and t[j] in PART: cons[j]=True; c+=1; break
    for i in range(len(t)):
        if not cons[i] and t[i] in STAND: c+=1
    return c
def has_stem(t,stem):
    if len(stem)>=6: return any(x.startswith(stem) for x in t)
    return any(x in (stem,stem+'s',stem+'e',stem+'es') for x in t)
def antconf(r,a):
    for x,y in ANT:
        if (has_stem(r,x) and not has_stem(r,y) and has_stem(a,y) and not has_stem(a,x)) or (has_stem(r,y) and not has_stem(r,x) and has_stem(a,x) and not has_stem(a,y)): return True
    return False
NUM=re.compile(r"\d+(?:[.,]\d+)?")
def nn(x):
    x=x.replace(',','.'); return x.rstrip('0').rstrip('.') if '.' in x else x
def nums(s): return {nn(m) for m in NUM.findall(s)}
def inspect(ref,ans):
    r,a=toks(ref),toks(ans)
    if negcount(r)%2!=negcount(a)%2: return 'NEG'
    if antconf(r,a): return 'ANT'
    R,A=nums(ref),nums(ans)
    if R and A and not A>=R: return 'NUM'
    return None


# ---- ContentGuard ----
import re, unicodedata
STOP=set("""le la les un une des du de d l au aux et ou mais donc or ni car que qu qui quoi dont ou où ce cet cette ces se s sa son ses leur leurs mon ma mes ton ta tes notre nos votre vos il elle ils elles on nous vous je tu y en ne n pas plus a ont est sont etre avoir fait font vaut egal egale dans sur sous par pour avec sans entre vers chez comme si tout tous toute toutes tres aussi alors ainsi cela ca c cest ici la dune celui celle ceux celles quand lorsque pendant depuis jusqu jusque apres avant afin ensuite puis grace dou environ peut permet permettent appelle appelleton c'est""".split())
def norm(s):
    s=unicodedata.normalize('NFD',s.lower().replace('’',"'"))
    return ''.join(ch for ch in s if unicodedata.category(ch)!='Mn')
def terms(s):
    out=[]
    for t in re.findall(r"[a-z0-9]+",norm(s)):
        if t in STOP: continue
        if t.isdigit() or len(t)>=4: out.append(t)
    return out
def same(a,b):
    if a==b: return True
    if a.isdigit() or b.isdigit(): return False
    p=0
    for x,y in zip(a,b):
        if x!=y: break
        p+=1
    if p>=5 and p>=min(len(a),len(b))-2: return True
    return (len(a)>=5 and len(b)>=5) and (a in b or b in a)
def has(t,pool): return any(same(t,p) for p in pool)
def coverage(ref,ans,known=''):
    R=terms(ref); A=terms(ans); K=terms(known)
    if not R: return 1.0
    own=[t for t in R if not has(t,K)] or R   # termes propres à la réponse (pas déjà dans la question)
    return sum(has(t,A) for t in own)/len(own)

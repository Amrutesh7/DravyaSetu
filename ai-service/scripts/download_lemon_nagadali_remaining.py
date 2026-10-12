import os, requests, pandas as pd, time
from urllib.parse import urlparse
shortlist=r'D:\DravyaSetu\datasets\v2_expansion\metadata\lemon_nagadali_remaining_shortlist.csv'
outdir=r'D:\DravyaSetu\datasets\v2_expansion\raw\iNaturalist_Lemon_Nagadali_Remaining'
manifest=r'D:\DravyaSetu\datasets\v2_expansion\metadata\lemon_nagadali_remaining_download_manifest.csv'
os.makedirs(outdir,exist_ok=True)
df=pd.read_csv(shortlist)
rows=[]
for i,row in df.iterrows():
    cls=row.class_name; url=row.photo_url
    ext=os.path.splitext(urlparse(url).path)[1].lower() or '.jpg'
    path=os.path.join(outdir,cls,f'{cls}_{int(row.observation_id)}_{int(row.photo_id)}{ext}')
    os.makedirs(os.path.dirname(path),exist_ok=True)
    status='DOWNLOADED'
    try:
        r=requests.get(url,timeout=30); r.raise_for_status()
        open(path,'wb').write(r.content)
    except Exception as e:
        status='FAILED'; print('FAILED:',e)
    rows.append({**row.to_dict(),'local_path':path,'status':status})
    print(f'{i+1}/{len(df)} {cls}: {status}')
    time.sleep(.05)
pd.DataFrame(rows).to_csv(manifest,index=False)
print('DONE'); print('Total:',len(rows)); print('Downloaded:',sum(x['status']=='DOWNLOADED' for x in rows)); print('Failed:',sum(x['status']=='FAILED' for x in rows)); print('Manifest:',manifest)

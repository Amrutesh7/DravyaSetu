#!/usr/bin/env python3
"""Boot the packaged application against Neon and verify health/Flyway without sending mail."""
import datetime
import json
import os
import runpy
import socket
import subprocess
import sys
import time
from pathlib import Path
from urllib.parse import urlsplit, quote
from urllib.request import Request, urlopen
from urllib.error import URLError

ROOT = Path(__file__).resolve().parent.parent
helper = runpy.run_path(str(ROOT/'scripts/deployment-inspect.py'))
settings = helper['read_env']('.env') | helper['read_env']('.env.neon')
target = helper['read_env']('.env.neon')
java = ['java','-Xmx256m','-XX:+UseSerialGC']
hostname = urlsplit(target['DB_URL'].removeprefix('jdbc:')).hostname
try:
    socket.getaddrinfo(hostname, 5432)
except socket.gaierror:
    request = Request('https://dns.google/resolve?name='+quote(hostname)+'&type=A',headers={'accept':'application/dns-json'})
    with urlopen(request,timeout=20) as response: answer=json.load(response)
    addresses=[item['data'] for item in answer.get('Answer',[]) if item.get('type')==1]
    if not addresses: raise SystemExit('No DNS addresses found for Neon')
    hosts=Path('/tmp/dravyasetu-neon-verification.hosts')
    contents='127.0.0.1 localhost\n'+'\n'.join(ip+' '+hostname for ip in addresses)+'\n'
    if hosts.exists():
        previous=hosts.read_text()
        patch='*** Update File: '+str(hosts)+'\n@@\n'+''.join('-'+line+'\n' for line in previous.splitlines())+''.join('+'+line+'\n' for line in contents.splitlines())
    else:
        patch='*** Add File: '+str(hosts)+'\n'+''.join('+'+line+'\n' for line in contents.splitlines())
    applied=subprocess.run(['apply_patch'],input='*** Begin Patch\n'+patch+'*** End Patch\n',text=True,capture_output=True)
    if applied.returncode: raise SystemExit('Temporary DNS workaround could not be prepared')
    java += ['-Djdk.net.hosts.file='+str(hosts)]

jar=ROOT/'backend/target/dravya-setu-backend-0.0.1-SNAPSHOT.jar'
if not jar.is_file(): raise SystemExit('Build the backend package before verification')
backups=ROOT/'backups';backups.mkdir(mode=0o700,exist_ok=True)
log_path=backups/('neon-startup-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'.log')
descriptor=os.open(log_path,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
with os.fdopen(descriptor,'wb') as log:
    process=subprocess.Popen([*java,'-jar',str(jar),'--server.port=18088',
        '--server.address=127.0.0.1','--spring.config.import=',
        '--spring.profiles.active=real-ai,real-knowledge,production'],
        cwd=ROOT,env=dict(os.environ,**settings),stdout=log,stderr=subprocess.STDOUT)
    healthy=False
    try:
        for attempt in range(120):
            if process.poll() is not None: break
            try:
                with urlopen('http://127.0.0.1:18088/api/v1/actuator/health',timeout=3) as response:
                    healthy=json.load(response).get('status')=='UP'
                if healthy: break
            except (URLError,TimeoutError): pass
            time.sleep(1)
        if not healthy: raise RuntimeError('Neon-backed application did not become healthy; startup log saved privately')
        version=helper['sql'](target,'SELECT max(version::int) FROM flyway_schema_history WHERE success')
        if version!='26': raise RuntimeError('Expected Flyway V26 after startup')
        with urlopen('http://127.0.0.1:18088/api/v1/languages',timeout=5) as response:
            assert len(json.load(response)['languages'])==4
        print(json.dumps({'application_health':'UP','flyway_version':version,
            'profiles':'real-ai,real-knowledge,production','languages_endpoint':'passed',
            'startup_log':str(log_path.relative_to(ROOT))}))
        if '--serve' in sys.argv:
            print('Serving Neon-backed backend on localhost:18088. Press Ctrl+C to stop.', flush=True)
            try: process.wait()
            except KeyboardInterrupt: pass
    finally:
        process.terminate()
        try: process.wait(timeout=20)
        except subprocess.TimeoutExpired: process.kill();process.wait()

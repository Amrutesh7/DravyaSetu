#!/usr/bin/env python3
"""Read-only provider checks. Read credentials from ignored files; never log them."""
import json
import os
import subprocess
import sys
import socket
from pathlib import Path
from urllib.parse import urlsplit, parse_qs, quote
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

ROOT = Path(__file__).resolve().parent.parent

def read_env(name):
    values = {}
    for line in (ROOT / name).read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#') or '=' not in line:
            continue
        key, value = line.split('=', 1)
        values[key.strip()] = value.strip().strip('\"\'')
    return values

def pg_args(settings):
    url = urlsplit(settings['DB_URL'].removeprefix('jdbc:'))
    if url.username or url.password:
        raise ValueError('Use separate DB_USERNAME/DB_PASSWORD instead of URL credentials')
    args = ['-h', url.hostname, '-p', str(url.port or 5432), '-U', settings['DB_USERNAME'],
            '-d', url.path.lstrip('/')]
    environment = dict(os.environ, PGPASSWORD=settings['DB_PASSWORD'], PGCONNECT_TIMEOUT='20',
                       PGAPPNAME='dravyasetu-deployment-inspect')
    query = parse_qs(url.query)
    environment['PGSSLMODE'] = query.get('sslmode', ['prefer'])[0]
    environment['PGCHANNELBINDING'] = query.get('channelBinding', ['prefer'])[0]
    if url.hostname.endswith('.neon.tech'):
        try:
            socket.getaddrinfo(url.hostname, url.port or 5432)
        except socket.gaierror:
            # Per-process workaround only. Keep PGHOST for TLS SNI and never pin IPs in DB_URL.
            request = Request('https://dns.google/resolve?name=' + quote(url.hostname) + '&type=A',
                              headers={'accept':'application/dns-json'})
            with urlopen(request, timeout=20) as response: answer = json.load(response)
            addresses = [item['data'] for item in answer.get('Answer',[]) if item.get('type') == 1]
            if not addresses: raise RuntimeError('Database DNS resolution failed')
            environment['PGHOSTADDR'] = addresses[0]
    return args, environment

def sql(settings, statement):
    args, environment = pg_args(settings)
    result = subprocess.run(['psql', '-X', '-w', '-A', '-t', '-v', 'ON_ERROR_STOP=1', *args,
                             '-c', statement], env=environment, capture_output=True, text=True,
                            timeout=45)
    if result.returncode:
        # Don't print raw provider errors, which can include connection details.
        if 'could not translate host name' in result.stderr:
            raise RuntimeError('Database DNS resolution failed')
        if 'password authentication failed' in result.stderr:
            raise RuntimeError('Database authentication rejected')
        if 'Connection refused' in result.stderr:
            raise RuntimeError('Database connection refused')
        raise RuntimeError('Database connection/query failed; raw output withheld')
    return result.stdout.strip()

def inspect_db(name):
    settings = read_env(name)
    metadata = sql(settings, """
        SELECT json_build_object('version',current_setting('server_version'),
          'version_num',current_setting('server_version_num'),
          'server_backend_ssl',(SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()),
          'public_objects',(SELECT coalesce(json_agg(o), '[]'::json) FROM
            (SELECT c.relname AS name,c.relkind AS kind FROM pg_class c
             JOIN pg_namespace n ON n.oid=c.relnamespace
             WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S') ORDER BY c.relname) o))
    """)
    value = json.loads(metadata)
    value['client_tls'] = 'SSL connection' in sql(settings, '\\conninfo')
    tables = [item['name'] for item in value['public_objects'] if item['kind'] in ('r','p')]
    if 'flyway_schema_history' in tables:
        value['flyway'] = json.loads(sql(settings, """
          SELECT coalesce(json_agg(h), '[]'::json) FROM
          (SELECT version,success FROM flyway_schema_history ORDER BY installed_rank) h
        """))
    value['row_counts'] = {}
    for table in tables:
        identifier = '"' + table.replace('"','""') + '"'
        value['row_counts'][table] = int(sql(settings, 'SELECT count(*) FROM public.' + identifier))
    print(json.dumps({'file':name,'metadata':value}))

def inspect_brevo():
    settings = read_env('.env.neon')
    request = Request('https://api.brevo.com/v3/senders', headers={
        'api-key':settings['BREVO_API_KEY'], 'accept':'application/json'})
    try:
        with urlopen(request, timeout=25) as response:
            payload = json.load(response)
        matching = [dict(active=s.get('active'), domain_authenticated=s.get('dkim', False))
                    for s in payload.get('senders',[]) if s.get('email','').lower()==settings['MAIL_FROM'].lower()]
        print(json.dumps({'brevo_api_authenticated':True,'matching_senders':matching}))
    except HTTPError as error:
        print(json.dumps({'brevo_api_authenticated':False,'http_status':error.code}))
        return 2
    except URLError:
        print('Brevo network check failed; raw details withheld')
        return 2
    return 0

def inspect_dns():
    hostname = urlsplit(read_env('.env.neon')['DB_URL'].removeprefix('jdbc:')).hostname
    for provider in ('https://dns.google/resolve?name=', 'https://cloudflare-dns.com/dns-query?name='):
        request = Request(provider + quote(hostname) + '&type=A', headers={'accept':'application/dns-json'})
        try:
            with urlopen(request, timeout=20) as response: result = json.load(response)
            print(json.dumps({'dns_provider':urlsplit(provider).hostname,
                'status':result.get('Status'),'answers':result.get('Answer',[])}))
        except (URLError, TimeoutError):
            print(json.dumps({'dns_provider':urlsplit(provider).hostname,'check_failed':True}))

def send_test_email():
    # Explicit user authorization required. No retry: this sends exactly one message.
    settings = read_env('.env.neon')
    payload = {'sender':{'email':settings['MAIL_FROM'],'name':'DravyaSetu'},
               'to':[{'email':settings['MAIL_FROM']}],
               'subject':'DravyaSetu deployment email check',
               'htmlContent':'<p>This is the one deployment test email you requested.</p><p>Your DravyaSetu backend is configured to send verification and password-reset messages through Brevo HTTPS.</p>'}
    request = Request('https://api.brevo.com/v3/smtp/email', method='POST',
                      data=json.dumps(payload).encode(), headers={'api-key':settings['BREVO_API_KEY'],
                      'accept':'application/json','content-type':'application/json'})
    try:
        with urlopen(request, timeout=25) as response:
            print(json.dumps({'test_email_accepted':response.status==201,'http_status':response.status}))
        return 0
    except HTTPError as error:
        print(json.dumps({'test_email_accepted':False,'http_status':error.code}))
        return 2
    except URLError:
        print('Email request could not be confirmed; no automatic retry was attempted')
        return 2

if __name__ == '__main__':
    try:
        if len(sys.argv) == 2 and sys.argv[1] == 'send-test-email':
            sys.exit(send_test_email())
        if len(sys.argv) == 2 and sys.argv[1] == 'dns':
            inspect_dns()
            sys.exit(0)
        if len(sys.argv) == 2 and sys.argv[1] == 'brevo':
            sys.exit(inspect_brevo())
        for file in sys.argv[1:] or ['.env', '.env.neon']:
            inspect_db(file)
    except (RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
        print(str(error) if not isinstance(error, subprocess.TimeoutExpired) else 'Database check timed out')
        sys.exit(2)

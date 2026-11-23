#!/usr/bin/env python3
"""Backup local data and, only into an empty destination, restore it to Neon."""
import datetime
import json
import os
import runpy
import subprocess
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
helper = runpy.run_path(str(ROOT / 'scripts/deployment-inspect.py'))
read_env, pg_args, sql = (helper[name] for name in ('read_env','pg_args','sql'))

def tables(settings):
    return json.loads(sql(settings, "SELECT coalesce(json_agg(table_name ORDER BY table_name),'[]'::json) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'"))

def counts(settings):
    return {table:int(sql(settings, 'SELECT count(*) FROM public."' + table.replace('"','""') + '"')) for table in tables(settings)}

def sequences(settings):
    names = json.loads(sql(settings, "SELECT coalesce(json_agg(c.relname ORDER BY c.relname),'[]'::json) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='S'"))
    return {name:sql(settings, 'SELECT last_value,is_called FROM public."' + name.replace('"','""') + '"') for name in names}

def main():
    restore = len(sys.argv)>1 and sys.argv[1]=='restore'
    source, target = read_env('.env'), read_env('.env.neon')
    if source['DB_URL'] == target['DB_URL']: raise RuntimeError('Source and target must differ')
    version = sql(source, 'SELECT max(version::int) FROM flyway_schema_history WHERE success')
    if version == '25':
        preflight = sql(source, (ROOT/'docs/database/preflight-v26.sql').read_text())
        if any(int(row.rsplit('|',1)[1]) for row in preflight.splitlines()):
            print(preflight)
            raise RuntimeError('Preflight found inconsistent source data; export/restore stopped')
    if int(sql(source, "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND backend_type='client backend'")):
        raise RuntimeError('Other clients are connected to the source; freeze application writes first')
    before, sequence_state = counts(source), sequences(source)
    backup_dir = ROOT/'backups'; backup_dir.mkdir(mode=0o700, exist_ok=True)
    timestamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    archive = backup_dir/('local-before-neon-' + timestamp + '-' + uuid.uuid4().hex[:8] + '.dump')
    descriptor = os.open(archive, os.O_CREAT|os.O_EXCL|os.O_WRONLY, 0o600); os.close(descriptor)
    args, environment = pg_args(source)
    dumped = subprocess.run(['pg_dump','--format=custom','--no-owner','--no-acl','--file',str(archive),*args],env=environment,capture_output=True,text=True,timeout=180)
    if dumped.returncode: raise RuntimeError('Source backup failed; raw output withheld')
    listed = subprocess.run(['pg_restore','--list',str(archive)],capture_output=True,text=True,timeout=20)
    if listed.returncode or 'flyway_schema_history' not in listed.stdout: raise RuntimeError('Backup archive validation failed')
    print(json.dumps({'backup':str(archive.relative_to(ROOT)),'archive_valid':True,'source_flyway':version,'row_counts':before}))
    if not restore: return
    if int(sql(source,'SELECT current_setting(\'server_version_num\')')) // 10000 > int(sql(target,'SELECT current_setting(\'server_version_num\')')) // 10000:
        raise RuntimeError('Target PostgreSQL must not be older than source')
    objects = int(sql(target,"SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S')"))
    if objects: raise RuntimeError('Neon public schema is not empty; refusing to overwrite it')
    args, environment = pg_args(target)
    restored = subprocess.run(['pg_restore','--no-owner','--no-acl','--exit-on-error','--single-transaction',*args,str(archive)],env=environment,capture_output=True,text=True,timeout=240)
    if restored.returncode: raise RuntimeError('Neon restore failed atomically; raw output withheld')
    if before != counts(target) or sequence_state != sequences(target):
        raise RuntimeError('Post-restore counts or sequences differ; preserve both databases for inspection')
    if before != counts(source): raise RuntimeError('Source changed during migration; cutover stopped')
    print('Restore verified: all table counts and sequence states match; source data preserved.')

if __name__ == '__main__':
    try: main()
    except (RuntimeError,ValueError,subprocess.TimeoutExpired) as error:
        print(str(error) if not isinstance(error,subprocess.TimeoutExpired) else 'Migration operation timed out')
        sys.exit(2)

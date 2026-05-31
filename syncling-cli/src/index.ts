#!/usr/bin/env node
import { Command } from 'commander';
import * as readline from 'readline';
import * as fs from 'fs';
import * as path from 'path';
import { loadConfig, saveConfig, clearConfig, requireConfig, DEFAULT_API_BASE } from './config';
import { SynclingApi, ApiError } from './api';

const program = new Command();

program
  .name('syncling')
  .description('Syncling CLI — manage translations from your terminal')
  .version('0.1.0');

// ── login ─────────────────────────────────────────────────────────────────────

program
  .command('login')
  .description('Authenticate with a Syncling API token')
  .option('--api-base <url>', 'API base URL (default: https://data.androidplay.in)')
  .action(async (opts) => {
    const existing = loadConfig();
    if (existing) {
      const answer = await prompt('Already logged in. Re-authenticate? [y/N] ');
      if (answer.toLowerCase() !== 'y') { console.log('Aborted.'); return; }
    }

    console.log('\nCreate an API token at: https://data.androidplay.in/syncling/tokens');
    const token = await prompt('Paste your token (sli_…): ');

    if (!token.startsWith('sli_')) {
      console.error('Invalid token — must start with sli_');
      process.exit(1);
    }

    const apiBase = (opts.apiBase as string | undefined) ?? DEFAULT_API_BASE;
    const api = new SynclingApi({ token, apiBase });
    try {
      const data = await api.getBootstrap();
      saveConfig({ token, apiBase });
      console.log(`\nLogged in. Plan: ${data.onboarding.plan} · ${data.stats.totalProjects} project(s)\n`);
    } catch (e) {
      console.error('Login failed:', (e as Error).message);
      process.exit(1);
    }
  });

// ── logout ────────────────────────────────────────────────────────────────────

program
  .command('logout')
  .description('Remove stored credentials')
  .action(() => {
    clearConfig();
    console.log('Logged out.');
  });

// ── whoami ────────────────────────────────────────────────────────────────────

program
  .command('whoami')
  .description('Show current account info')
  .action(async () => {
    const cfg = requireConfig();
    const api = new SynclingApi(cfg);
    try {
      const data = await api.getBootstrap();
      console.log(`Plan:     ${data.onboarding.plan}${data.onboarding.inTrial ? ' (trial)' : ''}`);
      console.log(`Projects: ${data.stats.totalProjects}`);
      console.log(`Strings:  ${data.stats.totalStringsTranslated} translated`);
      console.log(`API base: ${cfg.apiBase}`);
    } catch (e) { die(e); }
  });

// ── projects ──────────────────────────────────────────────────────────────────

program
  .command('projects')
  .description('List your projects')
  .action(async () => {
    const cfg = requireConfig();
    const api = new SynclingApi(cfg);
    try {
      const projects = await api.listProjects();
      if (!projects.length) { console.log('No projects yet. Create one at /syncling/projects'); return; }
      console.log('');
      for (const p of projects) {
        console.log(`  ${p.id}`);
        console.log(`  Name:      ${p.name}`);
        console.log(`  Repo:      ${p.githubRepo}  (branch: ${p.watchBranch})`);
        console.log(`  Languages: ${p.targetCount}`);
        console.log('');
      }
    } catch (e) { die(e); }
  });

// ── pull ──────────────────────────────────────────────────────────────────────

program
  .command('pull <project-id>')
  .description('Download translated files for a project')
  .option('-l, --lang <code>', 'language code to pull (repeatable)', collect, [] as string[])
  .option('-o, --out <dir>', 'output directory', '.')
  .option('-f, --format <fmt>', 'output format: xml | json | strings (default: auto)')
  .action(async (projectId: string, opts) => {
    const cfg = requireConfig();
    const api = new SynclingApi(cfg);

    let langs: string[] = opts.lang as string[];
    if (!langs.length) {
      // Pull all configured languages
      try {
        const project = await api.getProject(projectId);
        langs = project.targets?.map(t => t.code) ?? [];
        if (!langs.length) {
          console.error('No target languages configured on this project.');
          process.exit(1);
        }
        console.log(`Pulling ${langs.length} language(s): ${langs.join(', ')}`);
      } catch (e) { die(e); }
    }

    const outDir = opts.out as string;
    fs.mkdirSync(outDir, { recursive: true });

    let ok = 0;
    let skipped = 0;
    for (const lang of langs) {
      try {
        const { content, filename } = await api.exportTranslation(projectId, lang, opts.format);
        const dest = path.join(outDir, filename);
        fs.writeFileSync(dest, content, 'utf8');
        console.log(`  ✓  ${lang}  →  ${dest}`);
        ok++;
      } catch (e) {
        if ((e as ApiError).status === 204) {
          console.log(`  –  ${lang}  (no translations yet)`);
          skipped++;
        } else {
          console.error(`  ✗  ${lang}  ${(e as Error).message}`);
        }
      }
    }
    console.log(`\nPulled ${ok} file(s)${skipped ? `, ${skipped} skipped` : ''}.`);
  });

// ── push ──────────────────────────────────────────────────────────────────────

program
  .command('push <project-id>')
  .description('Trigger a manual translation sync for a project')
  .action(async (projectId: string) => {
    const cfg = requireConfig();
    const api = new SynclingApi(cfg);
    try {
      const result = await api.triggerSync(projectId);
      if (result.queued) {
        console.log(`Sync queued for ${result.repo} @ ${result.branch} (${result.commitShort})`);
      } else {
        console.log('Sync already in progress or no new changes.');
      }
    } catch (e) { die(e); }
  });

// ── status ────────────────────────────────────────────────────────────────────

program
  .command('status')
  .description('Show recent pipeline runs')
  .option('-p, --project <id>', 'filter by project id')
  .action(async (opts) => {
    const cfg = requireConfig();
    const api = new SynclingApi(cfg);
    try {
      let runs = await api.listPipelineRuns();
      if (opts.project) runs = runs.filter(r => r.projectId === opts.project);
      if (!runs.length) { console.log('No recent pipeline runs.'); return; }

      console.log('');
      for (const r of runs.slice(0, 15)) {
        const dur = r.completedAt ? ` (${Math.round((r.completedAt - r.startedAt) / 1000)}s)` : '';
        const strings = r.stringsProcessed != null ? ` · ${r.stringsProcessed} strings` : '';
        console.log(`  ${statusIcon(r.status)} ${r.projectName.padEnd(20)} ${r.repo}@${r.branch}  ${r.status}${dur}${strings}`);
        console.log(`       ${new Date(r.startedAt).toLocaleString()}`);
        console.log('');
      }
    } catch (e) { die(e); }
  });

// ── tokens ────────────────────────────────────────────────────────────────────

const tokens = program.command('tokens').description('Manage API tokens');

tokens
  .command('list')
  .description('List all API tokens')
  .action(async () => {
    const cfg = requireConfig();
    const api = new SynclingApi(cfg);
    try {
      const list = await api.listTokens();
      if (!list.length) { console.log('No tokens. Run: syncling tokens create'); return; }
      console.log('');
      for (const t of list) {
        const lastUsed = t.lastUsedAt ? `Last used: ${new Date(t.lastUsedAt).toLocaleDateString()}` : 'Never used';
        console.log(`  ${t.id}`);
        console.log(`  Name: ${t.name}  · Created: ${new Date(t.createdAt).toLocaleDateString()}  · ${lastUsed}`);
        console.log('');
      }
    } catch (e) { die(e); }
  });

tokens
  .command('create <name>')
  .description('Create a new API token')
  .action(async (name: string) => {
    const cfg = requireConfig();
    const api = new SynclingApi(cfg);
    try {
      const t = await api.createToken(name);
      console.log('\nToken created — copy it now, it will not be shown again:\n');
      console.log(`  ${t.token}`);
      console.log(`\nID: ${t.id}  Name: ${t.name}\n`);
    } catch (e) { die(e); }
  });

tokens
  .command('revoke <id>')
  .description('Revoke an API token by ID')
  .action(async (id: string) => {
    const cfg = requireConfig();
    const api = new SynclingApi(cfg);
    try {
      await api.revokeToken(id);
      console.log('Token revoked.');
    } catch (e) { die(e); }
  });

// ── helpers ───────────────────────────────────────────────────────────────────

function die(e: unknown): never {
  console.error('Error:', (e as Error).message);
  process.exit(1);
}

function statusIcon(status: string): string {
  if (status === 'completed') return '✓';
  if (status === 'failed') return '✗';
  if (status === 'running') return '⟳';
  return '·';
}

function collect(val: string, prev: string[]): string[] {
  return [...prev, val];
}

function prompt(question: string): Promise<string> {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise(resolve => {
    rl.question(question, answer => { rl.close(); resolve(answer.trim()); });
  });
}

program.parse();

import { writeFileSync, mkdirSync } from 'fs';
import { join, resolve } from 'path';
import chalk from 'chalk';
import ora from 'ora';
import { requireToken } from '../config.js';
import { API_BASE } from '../config.js';

export async function pullCommand(projectId, opts) {
  const token = requireToken();
  const langs = opts.lang ? opts.lang.split(',').map(l => l.trim()) : null;
  const outDir = resolve(opts.out ?? '.');

  // First fetch the project to discover configured languages if --lang not given
  let targetLangs = langs;
  if (!targetLangs) {
    const spin = ora('Fetching project info…').start();
    try {
      const res = await fetch(`${API_BASE}/api/projects/${projectId}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      targetLangs = (data.project?.targets ?? data.targets ?? []).map(t =>
        typeof t === 'string' ? t : t.code
      );
      spin.stop();
    } catch (err) {
      spin.fail(`Could not load project: ${err.message}`);
      process.exit(1);
    }
    if (!targetLangs.length) {
      console.error('No target languages configured on this project.');
      process.exit(1);
    }
  }

  mkdirSync(outDir, { recursive: true });

  let pulled = 0;
  for (const lang of targetLangs) {
    const spin = ora(`Pulling ${lang}…`).start();
    try {
      const url = `${API_BASE}/api/projects/${projectId}/export?lang=${encodeURIComponent(lang)}${opts.format ? `&format=${opts.format}` : ''}`;
      const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });

      if (res.status === 204) {
        spin.warn(`${lang}: no translations yet`);
        continue;
      }
      if (!res.ok) {
        let msg = `HTTP ${res.status}`;
        try { const b = await res.json(); if (b.message) msg = b.message; } catch {}
        spin.fail(`${lang}: ${msg}`);
        continue;
      }

      // Derive filename from Content-Disposition or fall back to a safe default
      const cd = res.headers.get('content-disposition') ?? '';
      const fnMatch = cd.match(/filename="([^"]+)"/);
      const filename = fnMatch?.[1] ?? `strings.${lang}.xml`;
      const dest = join(outDir, filename);

      writeFileSync(dest, Buffer.from(await res.arrayBuffer()));
      spin.succeed(`${lang} → ${dest}`);
      pulled++;
    } catch (err) {
      spin.fail(`${lang}: ${err.message}`);
    }
  }

  console.log(`\n${chalk.green(`✓`)} Pulled ${pulled}/${targetLangs.length} language(s).`);
}

import chalk from 'chalk';
import ora from 'ora';
import { requireToken } from '../config.js';
import { apiFetch } from '../api.js';

export async function projectsCommand() {
  const token = requireToken();
  const spin = ora('Fetching projects…').start();

  let data;
  try {
    data = await apiFetch('/api/projects', token);
    spin.stop();
  } catch (err) {
    spin.fail(`Failed: ${err.message}`);
    process.exit(1);
  }

  const projects = data?.projects ?? data ?? [];
  if (!projects.length) {
    console.log('No projects found. Create one at syncling.space/syncling/dashboard');
    return;
  }

  console.log('');
  for (const p of projects) {
    const langs = (p.targets ?? []).join(', ') || '—';
    console.log(`${chalk.bold(p.name)}  ${chalk.dim(p.id)}`);
    console.log(`  ${chalk.dim('languages:')} ${langs}`);
    console.log('');
  }
}

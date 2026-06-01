import chalk from 'chalk';
import ora from 'ora';
import { requireToken } from '../config.js';
import { apiFetch } from '../api.js';

export async function whoamiCommand() {
  const token = requireToken();
  const spin = ora('Checking identity…').start();
  try {
    const data = await apiFetch('/api/me/bootstrap', token);
    spin.stop();
    const email = data?.user?.email ?? data?.email ?? '(unknown)';
    const plan = data?.subscription?.plan ?? data?.plan ?? '';
    console.log(`Logged in as ${chalk.bold(email)}${plan ? `  ${chalk.dim(plan)}` : ''}`);
  } catch (err) {
    spin.fail(`Failed: ${err.message}`);
    process.exit(1);
  }
}

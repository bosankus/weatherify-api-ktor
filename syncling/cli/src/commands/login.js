import { createInterface } from 'readline';
import chalk from 'chalk';
import { writeConfig, readConfig, API_BASE } from '../config.js';
import { apiFetch } from '../api.js';

export async function loginCommand() {
  console.log(chalk.bold('\nSyncling login'));
  console.log(`Open ${chalk.cyan(`${API_BASE}/tokens`)} to create an API token.\n`);

  const rl = createInterface({ input: process.stdin, output: process.stdout });
  const token = await new Promise(resolve => {
    rl.question('Paste your API token: ', ans => { rl.close(); resolve(ans.trim()); });
  });

  if (!token) {
    console.error(chalk.red('No token provided.'));
    process.exit(1);
  }

  // Validate token against the API
  try {
    const data = await apiFetch('/api/me/bootstrap', token);
    const cfg = readConfig();
    writeConfig({ ...cfg, token });
    const email = data?.user?.email ?? data?.email ?? '';
    console.log(chalk.green(`\nLogged in${email ? ` as ${email}` : ''}.`));
  } catch (err) {
    console.error(chalk.red(`\nInvalid token: ${err.message}`));
    process.exit(1);
  }
}

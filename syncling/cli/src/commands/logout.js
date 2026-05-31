import chalk from 'chalk';
import { readConfig, writeConfig, getToken } from '../config.js';

export function logoutCommand() {
  if (!getToken()) {
    console.log('Not logged in.');
    return;
  }
  const cfg = readConfig();
  delete cfg.token;
  writeConfig(cfg);
  console.log(chalk.green('Logged out.'));
}

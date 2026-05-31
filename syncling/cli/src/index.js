#!/usr/bin/env node
import { Command } from 'commander';
import { loginCommand } from './commands/login.js';
import { logoutCommand } from './commands/logout.js';
import { projectsCommand } from './commands/projects.js';
import { pullCommand } from './commands/pull.js';
import { whoamiCommand } from './commands/whoami.js';

const program = new Command();

program
  .name('syncling')
  .description('Syncling CLI — manage your localization pipeline')
  .version('1.0.0');

program
  .command('login')
  .description('Authenticate with an API token')
  .action(loginCommand);

program
  .command('logout')
  .description('Remove stored credentials')
  .action(logoutCommand);

program
  .command('whoami')
  .description('Show the currently authenticated account')
  .action(whoamiCommand);

program
  .command('projects')
  .description('List your projects')
  .action(projectsCommand);

program
  .command('pull <project-id>')
  .description('Download translated files for a project')
  .option('--lang <codes>', 'Comma-separated language codes (default: all configured languages)')
  .option('--format <fmt>', 'Override file format: xml | strings | json | arb')
  .option('--out <dir>', 'Output directory (default: current directory)', '.')
  .action(pullCommand);

program.parseAsync(process.argv);

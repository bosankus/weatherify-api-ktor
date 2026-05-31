import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

const CONFIG_DIR = path.join(os.homedir(), '.syncling');
const CONFIG_FILE = path.join(CONFIG_DIR, 'config.json');

export interface SynclingConfig {
  token: string;
  apiBase: string;
}

export function loadConfig(): SynclingConfig | null {
  try {
    const raw = fs.readFileSync(CONFIG_FILE, 'utf8');
    return JSON.parse(raw) as SynclingConfig;
  } catch {
    return null;
  }
}

export function saveConfig(config: SynclingConfig): void {
  fs.mkdirSync(CONFIG_DIR, { recursive: true });
  fs.writeFileSync(CONFIG_FILE, JSON.stringify(config, null, 2), { mode: 0o600 });
}

export function clearConfig(): void {
  try { fs.unlinkSync(CONFIG_FILE); } catch { /* already gone */ }
}

export function requireConfig(): SynclingConfig {
  const cfg = loadConfig();
  if (!cfg) {
    console.error('Not logged in. Run: syncling login');
    process.exit(1);
  }
  return cfg;
}

export const DEFAULT_API_BASE = 'https://data.androidplay.in';

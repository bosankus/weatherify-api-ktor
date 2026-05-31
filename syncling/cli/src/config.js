#!/usr/bin/env node
import { homedir } from 'os';
import { join } from 'path';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'fs';

const CONFIG_DIR = join(homedir(), '.syncling');
const CONFIG_FILE = join(CONFIG_DIR, 'config.json');

export const API_BASE = 'https://syncling.space';

export function readConfig() {
  if (!existsSync(CONFIG_FILE)) return {};
  try {
    return JSON.parse(readFileSync(CONFIG_FILE, 'utf8'));
  } catch {
    return {};
  }
}

export function writeConfig(data) {
  if (!existsSync(CONFIG_DIR)) mkdirSync(CONFIG_DIR, { recursive: true });
  writeFileSync(CONFIG_FILE, JSON.stringify(data, null, 2), { mode: 0o600 });
}

export function getToken() {
  return readConfig().token ?? null;
}

export function requireToken() {
  const token = getToken();
  if (!token) {
    console.error('Not logged in. Run: syncling login');
    process.exit(1);
  }
  return token;
}

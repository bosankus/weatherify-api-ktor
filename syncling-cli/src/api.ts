import fetch, { Response } from 'node-fetch';
import { SynclingConfig } from './config';

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
  }
}

export class SynclingApi {
  private readonly base: string;
  private readonly token: string;

  constructor(config: SynclingConfig) {
    this.base = config.apiBase.replace(/\/$/, '');
    this.token = config.token;
  }

  private headers(): Record<string, string> {
    return { Authorization: `Bearer ${this.token}`, 'Content-Type': 'application/json' };
  }

  private async check(res: Response): Promise<Response> {
    if (res.ok) return res;
    let msg = `HTTP ${res.status}`;
    try { const body = await res.json() as { error?: string }; msg = body.error ?? msg; } catch { /* ignore */ }
    throw new ApiError(msg, res.status);
  }

  async getBootstrap(): Promise<BootstrapResponse> {
    const res = await fetch(`${this.base}/syncling/api/me/bootstrap`, { headers: this.headers() });
    return (await (await this.check(res)).json()) as BootstrapResponse;
  }

  async listProjects(): Promise<ProjectResponse[]> {
    const res = await fetch(`${this.base}/syncling/api/projects`, { headers: this.headers() });
    const data = (await (await this.check(res)).json()) as { projects: ProjectResponse[] };
    return data.projects;
  }

  async getProject(id: string): Promise<ProjectDetailResponse> {
    const res = await fetch(`${this.base}/syncling/api/projects/${id}`, { headers: this.headers() });
    return (await (await this.check(res)).json()) as ProjectDetailResponse;
  }

  async exportTranslation(projectId: string, lang: string, format?: string): Promise<{ content: string; filename: string }> {
    const params = new URLSearchParams({ lang });
    if (format) params.set('format', format);
    const res = await fetch(`${this.base}/syncling/api/projects/${projectId}/export?${params}`, { headers: this.headers() });
    await this.check(res);
    const content = await res.text();
    const cd = res.headers.get('content-disposition') ?? '';
    const filename = cd.match(/filename="([^"]+)"/)?.[1] ?? `strings.${lang}`;
    return { content, filename };
  }

  async triggerSync(projectId: string): Promise<SyncResponse> {
    const res = await fetch(`${this.base}/syncling/api/projects/${projectId}/sync`, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify({ branch: 'main' })
    });
    return (await (await this.check(res)).json()) as SyncResponse;
  }

  async listPipelineRuns(): Promise<PipelineRun[]> {
    const res = await fetch(`${this.base}/syncling/api/pipeline/runs`, { headers: this.headers() });
    return (await (await this.check(res)).json()) as PipelineRun[];
  }

  async listTokens(): Promise<TokenListItem[]> {
    const res = await fetch(`${this.base}/syncling/api/me/tokens`, { headers: this.headers() });
    const data = (await (await this.check(res)).json()) as { tokens: TokenListItem[] };
    return data.tokens;
  }

  async createToken(name: string): Promise<CreateTokenResponse> {
    const res = await fetch(`${this.base}/syncling/api/me/tokens`, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify({ name })
    });
    return (await (await this.check(res)).json()) as CreateTokenResponse;
  }

  async revokeToken(id: string): Promise<void> {
    const res = await fetch(`${this.base}/syncling/api/me/tokens/${id}`, {
      method: 'DELETE',
      headers: this.headers()
    });
    await this.check(res);
  }
}

// ── Response types ─────────────────────────────────────────────────────────────

export interface BootstrapResponse {
  onboarding: { step: string; plan: string; inTrial: boolean };
  stats: { totalStringsTranslated: number; pendingReview: number; activeLanguages: number; totalProjects: number };
}

export interface ProjectResponse {
  id: string;
  name: string;
  githubRepo: string;
  watchBranch: string;
  sourceFilePaths: string[];
  targetCount: number;
}

export interface ProjectDetailResponse extends ProjectResponse {
  targets?: Array<{ code: string; name: string; file: string }>;
}

export interface SyncResponse {
  queued: boolean;
  repo: string;
  branch: string;
  commitShort: string;
}

export interface PipelineRun {
  runId: string;
  projectId: string;
  projectName: string;
  repo: string;
  branch: string;
  status: string;
  startedAt: number;
  completedAt?: number;
  stringsProcessed?: number;
}

export interface TokenListItem {
  id: string;
  name: string;
  createdAt: number;
  lastUsedAt?: number;
}

export interface CreateTokenResponse {
  id: string;
  name: string;
  token: string;
  createdAt: number;
}

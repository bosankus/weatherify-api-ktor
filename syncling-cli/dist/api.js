"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.SynclingApi = exports.ApiError = void 0;
const node_fetch_1 = __importDefault(require("node-fetch"));
class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
    }
}
exports.ApiError = ApiError;
class SynclingApi {
    constructor(config) {
        this.base = config.apiBase.replace(/\/$/, '');
        this.token = config.token;
    }
    headers() {
        return { Authorization: `Bearer ${this.token}`, 'Content-Type': 'application/json' };
    }
    async check(res) {
        if (res.ok)
            return res;
        let msg = `HTTP ${res.status}`;
        try {
            const body = await res.json();
            msg = body.error ?? msg;
        }
        catch { /* ignore */ }
        throw new ApiError(msg, res.status);
    }
    async getBootstrap() {
        const res = await (0, node_fetch_1.default)(`${this.base}/api/me/bootstrap`, { headers: this.headers() });
        return (await (await this.check(res)).json());
    }
    async listProjects() {
        const res = await (0, node_fetch_1.default)(`${this.base}/api/projects`, { headers: this.headers() });
        const data = (await (await this.check(res)).json());
        return data.projects;
    }
    async getProject(id) {
        const res = await (0, node_fetch_1.default)(`${this.base}/api/projects/${id}`, { headers: this.headers() });
        return (await (await this.check(res)).json());
    }
    async exportTranslation(projectId, lang, format) {
        const params = new URLSearchParams({ lang });
        if (format)
            params.set('format', format);
        const res = await (0, node_fetch_1.default)(`${this.base}/api/projects/${projectId}/export?${params}`, { headers: this.headers() });
        await this.check(res);
        const content = await res.text();
        const cd = res.headers.get('content-disposition') ?? '';
        const filename = cd.match(/filename="([^"]+)"/)?.[1] ?? `strings.${lang}`;
        return { content, filename };
    }
    async triggerSync(projectId) {
        const res = await (0, node_fetch_1.default)(`${this.base}/api/projects/${projectId}/sync`, {
            method: 'POST',
            headers: this.headers(),
            body: JSON.stringify({ branch: 'main' })
        });
        return (await (await this.check(res)).json());
    }
    async listPipelineRuns() {
        const res = await (0, node_fetch_1.default)(`${this.base}/api/pipeline/runs`, { headers: this.headers() });
        return (await (await this.check(res)).json());
    }
    async listTokens() {
        const res = await (0, node_fetch_1.default)(`${this.base}/api/me/tokens`, { headers: this.headers() });
        const data = (await (await this.check(res)).json());
        return data.tokens;
    }
    async createToken(name) {
        const res = await (0, node_fetch_1.default)(`${this.base}/api/me/tokens`, {
            method: 'POST',
            headers: this.headers(),
            body: JSON.stringify({ name })
        });
        return (await (await this.check(res)).json());
    }
    async revokeToken(id) {
        const res = await (0, node_fetch_1.default)(`${this.base}/api/me/tokens/${id}`, {
            method: 'DELETE',
            headers: this.headers()
        });
        await this.check(res);
    }
}
exports.SynclingApi = SynclingApi;

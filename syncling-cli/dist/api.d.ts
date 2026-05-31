import { SynclingConfig } from './config';
export declare class ApiError extends Error {
    readonly status: number;
    constructor(message: string, status: number);
}
export declare class SynclingApi {
    private readonly base;
    private readonly token;
    constructor(config: SynclingConfig);
    private headers;
    private check;
    getBootstrap(): Promise<BootstrapResponse>;
    listProjects(): Promise<ProjectResponse[]>;
    getProject(id: string): Promise<ProjectDetailResponse>;
    exportTranslation(projectId: string, lang: string, format?: string): Promise<{
        content: string;
        filename: string;
    }>;
    triggerSync(projectId: string): Promise<SyncResponse>;
    listPipelineRuns(): Promise<PipelineRun[]>;
    listTokens(): Promise<TokenListItem[]>;
    createToken(name: string): Promise<CreateTokenResponse>;
    revokeToken(id: string): Promise<void>;
}
export interface BootstrapResponse {
    onboarding: {
        step: string;
        plan: string;
        inTrial: boolean;
    };
    stats: {
        totalStringsTranslated: number;
        pendingReview: number;
        activeLanguages: number;
        totalProjects: number;
    };
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
    targets?: Array<{
        code: string;
        name: string;
        file: string;
    }>;
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

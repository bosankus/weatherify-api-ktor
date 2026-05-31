export interface SynclingConfig {
    token: string;
    apiBase: string;
}
export declare function loadConfig(): SynclingConfig | null;
export declare function saveConfig(config: SynclingConfig): void;
export declare function clearConfig(): void;
export declare function requireConfig(): SynclingConfig;
export declare const DEFAULT_API_BASE = "https://data.androidplay.in";

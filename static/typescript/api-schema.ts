export interface Checklist {
  Body: string;
  /**
   * Filled for "base" pull reqs
   */
  Commits: Commit[];
  Config: ChecklistConfig;
  ConfigBlobID: string;
  IsPrivate: boolean;
  Items: ChecklistItem[];
  Number: number;
  Owner: string;
  Repo: string;
  Stage: string;
  Title: string;
  URL: string;
  User: GitHubUserSimple;
}
export interface Commit {
  Message: string;
}
export interface ChecklistConfig {
  Notification: {
    Channels: {
      [k: string]: {
        URL: string;
      };
    };
    Events: {
      OnCheck: string[];
      OnComplete: string[];
    };
  };
  Stages: string[];
}
export interface ChecklistItem {
  Body: string;
  CheckedBy: GitHubUser[];
  /**
   * Filled for "base" pull reqs
   */
  Commits: Commit[];
  ConfigBlobID: string;
  IsPrivate: boolean;
  Number: number;
  Owner: string;
  Repo: string;
  Title: string;
  URL: string;
  User: GitHubUserSimple;
}
export interface GitHubUser {
  AvatarURL: string;
  ID: number;
  Login: string;
}
export interface GitHubUserSimple {
  Login: string;
}
export interface ChecklistRef {
  Number: number;
  Owner: string;
  Repo: string;
  Stage: string;
}
export interface ChecklistResponse {
  Checklist: Checklist;
  Me: GitHubUser;
}
export interface Checks {
  [k: string]: number[];
}
export interface MeResponse {
  Me: GitHubUser;
  PullRequests: {
    [k: string]: PullRequest[];
  };
}
export interface PullRequest {
  Body: string;
  /**
   * Filled for "base" pull reqs
   */
  Commits: Commit[];
  ConfigBlobID: string;
  IsPrivate: boolean;
  Number: number;
  Owner: string;
  Repo: string;
  Title: string;
  URL: string;
  User: GitHubUserSimple;
}

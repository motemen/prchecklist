export type ErrorType = "not_authed";

/**
 * Checklist is the main entity of prchecklist.
 * It is identified by a "release" pull request PullRequest
 * (which is identified by its Owner, Repo and Number) and a Stage, if any.
 * The checklist Items correspond to "feature" pull requests
 * that have been merged into the head of "release" pull request
 * and the "release" pull request is about to merge into master.
 */
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
/**
 * Commit is a commit data on GitHub.
 */
export interface Commit {
  Message: string;
  Oid: string;
}
/**
 * ChecklistConfig is a configuration object for the repository,
 * which is specified by prchecklist.yml on the top of the repository.
 */
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
      OnRemove: string[];
      OnUserComplete: string[];
    };
  };
  Stages: string[];
}
/**
 * ChecklistItem is a checklist item, which belongs to a Checklist
 * and can be checked by multiple GitHubUsers.
 */
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
/**
 * GitHubUser is represents a GitHub user.
 * Its Token field is populated only for the representation of
 * a visiting client.
 */
export interface GitHubUser {
  AvatarURL: string;
  ID: number;
  Login: string;
}
/**
 * GitHubUserSimple is a minimalistic GitHub user data.
 */
export interface GitHubUserSimple {
  Login: string;
}
/**
 * ChecklistRef represents a pointer to Checklist.
 */
export interface ChecklistRef {
  Number: number;
  Owner: string;
  Repo: string;
  Stage: string;
}
/**
 * ChecklistResponse represents the JSON for a single Checklist.
 */
export interface ChecklistResponse {
  Checklist: Checklist;
  Me: GitHubUser;
}
export interface Checks {
  [k: string]: number[];
}
/**
 * ErrorResponse corresponds to JSON containing error results in APIs.
 */
export interface ErrorResponse {
  Type: ErrorType;
}
/**
 * MeResponse represents the JSON for the top page.
 */
export interface MeResponse {
  Me: GitHubUser;
  PullRequests: {
    [k: string]: PullRequest[];
  };
}
/**
 * PullRequest represens a pull request on GitHub.
 */
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

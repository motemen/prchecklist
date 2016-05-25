// prchecklist.views.Checklist
export interface Checklist {
  repo:        Repo;
  pullRequest: PullRequest;
  stage:       string;
  stages:      string[];
  checks:      Check[];
  allChecked:  boolean; // XXX not needed?
}

export interface Repo {
  fullName: string;
}

export interface PullRequest {
  url:    string;
  number: number;
  title:  string;
  body:   string;
}

// GitHubTypes.PullRequestRef
// TODO merge with PullRequest
export interface PullRequestRef {
  number: number;
  title:  string;
  state:  string;
  head: {
    repo: Repo;
  };
}

export interface Check {
  url:      string;
  number:   number;
  title:    string;
  users:    User[];
  checked:  boolean;
  assignee: User;
}

export interface User {
  name:      string;
  avatarUrl: string;
}

export interface News {
  [n: number]: PullRequestRef[];
}

export module API {
  function updateChecklist(mode: string, checklist: Checklist, featureNumber: number): Promise<Checklist> {
    const [repoOwner, repoName] = checklist.repo.fullName.split('/');
    let body = [
      `repoOwner=${repoOwner}`,
      `repoName=${repoName}`,
      `pullRequestNumber=${checklist.pullRequest.number}`,
      `stage=${checklist.stage}`,
      `featureNumber=${featureNumber}`
    ].join('&');
    return fetch(`/-/checklist/${mode}`, { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: body }).then(
      res => res.json<Checklist>()
    );
  }

  export function checkChecklist(checklist: Checklist, featureNumber: number): Promise<Checklist> {
    return updateChecklist('check', checklist, featureNumber);
  }

  export function uncheckChecklist(checklist: Checklist, featureNumber: number): Promise<Checklist> {
    return updateChecklist('uncheck', checklist, featureNumber);
  }

  export function fetchChecklist(repoOwner: string, repoName: string, pullRequestNumber: number, stage: string): Promise<Checklist> {
    return fetch(`/-/checklist?repoOwner=${repoOwner}&repoName=${repoName}&pullRequestNumber=${pullRequestNumber}&stage=${stage}`, { credentials: 'same-origin' }).then(
      res => res.json<Checklist>()
    );
  }

  export function getMe(): Promise<User> {
    return fetch('/-/me', { credentials: 'same-origin' }).then(
      res => res.json<User>()
    );
  }

  export function registerRepo(repoOwner: string, repoName: string): Promise<any> {
    return fetch('/-/repos', { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: `repoOwner=${repoOwner}&repoName=${repoName}` }).then(
      res => res.json()
    );
  }

  export function getNews(): Promise<News> {
    return fetch('/-/news', { credentials: 'same-origin' }).then(
      res => res.json<News>()
    );
  }
}

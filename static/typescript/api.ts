export interface ChecklistResponse {
  Checklist: Checklist;
  Me: GitHubUser;
}

export interface Checklist {
  Body: string;
  Commits: Commit[];
  Config: ChecklistConfig;
  Items: ChecklistItem[];
  Number: number;
  Owner: string;
  Repo: string;
  Title: string;
  URL: string;
}

export interface ChecklistConfig {
  Stages: string[];
}

export interface ChecklistItem {
  Body: string;
  CheckedBy: GitHubUser[];
  Commits: Commit[];
  Number: number;
  Owner: string;
  Repo: string;
  Title: string;
  URL: string;
}

export interface Commit {
  Message: string;
}

export interface GitHubUser {
  AvatarURL: string;
  ID: number;
  Login: string;
}

export interface ChecklistRef {
  Owner: string;
  Repo: string;
  Number: number;
  Stage: string;
}

export function getChecklist(ref: ChecklistRef): Promise<ChecklistResponse> {
  return fetch(`/api/checklist?owner=${ref.Owner}&repo=${ref.Repo}&number=${ref.Number}&stage=${ref.Stage || ''}`, {
      credentials: 'same-origin'
    })
    .then((res) => {
      if (!res.ok) {
        return res.text().then((text) => {
          throw `${res.status} ${res.statusText}\n${text}`;
        });
      }
      return res.json();
    });
}

export function setCheck(ref: ChecklistRef, featNum: number, checked: Boolean): Promise<ChecklistResponse> {
  return fetch(`/api/check?owner=${ref.Owner}&repo=${ref.Repo}&number=${ref.Number}&stage=${ref.Stage || ''}&featureNumber=${featNum}`, {
      credentials: 'same-origin',
      method: checked ? 'PUT' : 'DELETE'
    })
    .then((res) => {
      if (!res.ok) {
        return res.text().then((text) => {
          throw `${res.status} ${res.statusText}\n${text}`;
        });
      }
      return res.json();
    });
}

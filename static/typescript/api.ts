export interface ChecklistResponse {
  Checklist: Checklist;
  Me: GitHubUser;
}

export interface Checklist {
    Body: string;
    Commits: Commit[];
    Items: ChecklistItem[];
    Number: number;
    Owner: string;
    Repo: string;
    Title: string;
}

export interface ChecklistItem {
    Body: string;
    CheckedBy: GitHubUser[];
    Commits: Commit[];
    Number: number;
    Owner: string;
    Repo: string;
    Title: string;
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
}

export function getChecklist(ref: ChecklistRef): Promise<ChecklistResponse> {
  return fetch(`/api/checklist?owner=${ref.Owner}&repo=${ref.Repo}&number=${ref.Number}`, {
      credentials: 'same-origin'
    })
    .then((res) => res.json());
}

export function setCheck(ref: ChecklistRef, featNum: number, checked: Boolean): Promise<ChecklistResponse> {
  return fetch(`/api/check?owner=${ref.Owner}&repo=${ref.Repo}&number=${ref.Number}&featureNumber=${featNum}`, {
      credentials: 'same-origin',
      method: checked ? 'PUT' : 'DELETE'
    })
    .then((res) => res.json());
}

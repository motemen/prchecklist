import {ChecklistRef, ChecklistResponse, MeResponse, ErrorResponse} from './api-schema';

export {Checklist, ChecklistRef, ChecklistResponse, ChecklistItem, GitHubUser} from './api-schema';

function asQueryParam(ref: ChecklistRef) {
  return `owner=${ref.Owner}&repo=${ref.Repo}&number=${ref.Number}&stage=${ref.Stage || ''}`;
}

export function getChecklist(ref: ChecklistRef): Promise<ChecklistResponse> {
  return fetch(`/api/checklist?${asQueryParam(ref)}`, {
      credentials: 'same-origin',
    })
    .then((res) => {
      if (!res.ok) {
        return res.text().then((text): never => {
          try {
            const err: ErrorResponse = JSON.parse(text);
            if (err.Type == 'not_authed') {
              location.href = `/auth?return_to=${encodeURIComponent(location.pathname)}`;
            }
          } catch (e) {
          }

          throw new Error(`${res.status} ${res.statusText}\n${text}`);
        });
      }

      return res.json();
    });
}

export function setCheck(ref: ChecklistRef, featNum: number, checked: boolean): Promise<ChecklistResponse> {
  return fetch(`/api/check?${asQueryParam(ref)}&featureNumber=${featNum}`, {
      credentials: 'same-origin',
      method: checked ? 'PUT' : 'DELETE',
    })
    .then((res) => {
      if (!res.ok) {
        return res.text().then((text) => {
          throw new Error(`${res.status} ${res.statusText}\n${text}`);
        });
      }
      return res.json();
    });
}

export function getMe(): Promise<MeResponse> {
  return fetch('/api/me', {
      credentials: 'same-origin',
    })
  .then((res) => res.json());
}

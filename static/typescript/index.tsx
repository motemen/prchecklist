import * as React from "react";
import * as ReactDOM from "react-dom";

import * as API from "./api";
import { ChecklistComponent } from "./ChecklistComponent";
import { NavComponent } from "./NavComponent";
import { EnvContext } from "./EnvContext";

import "../scss/app.scss";

const appVersion = document
  .querySelector('meta[name="prchecklist-version"]')
  ?.getAttribute("content");

if (/^\/([^/]+)\/([^/]+)\/pull\/(\d+)$/.test(location.pathname)) {
  ReactDOM.render(
    <EnvContext.Provider value={{ appVersion }}>
      <ChecklistComponent
        checklistRef={{
          Owner: RegExp.$1,
          Repo: RegExp.$2,
          Number: parseInt(RegExp.$3, 10),
          Stage: "",
        }}
      />
    </EnvContext.Provider>,
    document.querySelector("#main")
  );
} else if (
  /^\/([^/]+)\/([^/]+)\/pull\/(\d+)\/([^/]+)$/.test(location.pathname)
) {
  ReactDOM.render(
    <EnvContext.Provider value={{ appVersion }}>
      <ChecklistComponent
        checklistRef={{
          Owner: RegExp.$1,
          Repo: RegExp.$2,
          Number: parseInt(RegExp.$3, 10),
          Stage: RegExp.$4,
        }}
      />
    </EnvContext.Provider>,
    document.querySelector("#main")
  );
} else {
  API.getMe().then((data) => {
    ReactDOM.render(
      <EnvContext.Provider value={{ appVersion }}>
        <section>
          <NavComponent me={data.Me} />
          {data.PullRequests ? (
            <section id="index-pullRequets">
              {Object.keys(data.PullRequests).map((repoPath: string) => (
                <div key={`repo-${repoPath}`}>
                  <h2>{repoPath}</h2>
                  <ul>
                    {data.PullRequests[repoPath].map((pr) => (
                      <li key={`repo-${repoPath}-pr-${pr.Number}`}>
                        <a href={`${repoPath}/pull/${pr.Number}`}>
                          #{pr.Number} {pr.Title}
                        </a>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </section>
          ) : (
            []
          )}
        </section>
      </EnvContext.Provider>,
      document.querySelector("#main")
    );
  });
}

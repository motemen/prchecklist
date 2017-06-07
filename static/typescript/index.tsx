import * as React from 'react';
import * as ReactDOM from 'react-dom';

import * as API from './api';
import {ChecklistComponent} from './ChecklistComponent';

import '../scss/app.scss';

if (/^\/([^\/]+)\/([^\/]+)\/pull\/(\d+)$/.test(location.pathname)) {
  ReactDOM.render(
    <ChecklistComponent
      checklistRef={{Owner: RegExp.$1, Repo: RegExp.$2, Number: parseInt(RegExp.$3, 10), Stage: ''}} />,
    document.querySelector('#main'),
  );
} else if (/^\/([^\/]+)\/([^\/]+)\/pull\/(\d+)\/([^\/]+)$/.test(location.pathname)) {
  ReactDOM.render(
    <ChecklistComponent
      checklistRef={{Owner: RegExp.$1, Repo: RegExp.$2, Number: parseInt(RegExp.$3, 10), Stage: RegExp.$4}} />,
    document.querySelector('#main'),
  );
} else {
  API.getMe().then((me) => {
    ReactDOM.render(
      <nav>
        <div className="logo"><strong>prchecklist</strong></div>
        <div className="stages"></div>
        {
          me
            ? <div className="user-signedin">{me.Login}</div>
            : <a className="user-signedin" href="/auth">Login</a>
        }
      </nav>,
      document.querySelector('#main'),
    );
  });
}

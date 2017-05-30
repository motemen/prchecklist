import * as React from 'react';
import * as ReactDOM from 'react-dom';

import {ChecklistComponent} from './ChecklistComponent';

import '../scss/app.scss';

if (/^\/([^\/]+)\/([^\/]+)\/pull\/(\d+)$/.test(location.pathname)) {
  ReactDOM.render(
    <ChecklistComponent checklistRef={{Owner: RegExp.$1, Repo: RegExp.$2, Number: parseInt(RegExp.$3), Stage: ''}} />,
    document.querySelector('#main')
  );
} else if (/^\/([^\/]+)\/([^\/]+)\/pull\/(\d+)\/([^\/]+)$/.test(location.pathname)) {
  ReactDOM.render(
    <ChecklistComponent checklistRef={{Owner: RegExp.$1, Repo: RegExp.$2, Number: parseInt(RegExp.$3), Stage: RegExp.$4}} />,
    document.querySelector('#main')
  );
}

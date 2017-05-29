import * as React from 'react';
import * as ReactDOM from 'react-dom';

import {ChecklistComponent} from './ChecklistComponent';

if (/^\/([^\/]+)\/([^\/]+)\/pull\/(\d+)$/.test(location.pathname)) {
  ReactDOM.render(
    <ChecklistComponent checklistRef={{Owner: RegExp.$1, Repo: RegExp.$2, Number: parseInt(RegExp.$3)}} />,
    document.querySelector('#main')
  )
}

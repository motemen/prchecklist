import * as React       from 'react';
import * as ReactDOM    from 'react-dom';
import { Router, Route, RouteComponentProps, Link, browserHistory }  from 'react-router'

import {Styles} from 'material-ui';

import {ChecklistComponent} from './ChecklistComponent';

const IndexPage = React.createClass({
  render() {
    return (
      <div>
        <h2>For development</h2>
        <ul>
          <li><Link to={"/-/motemen/test/pull/3"}>motemen/test#3</Link></li>
        </ul>
      </div>
    );
  }
});

interface ChecklistPageParams {
  repoOwner:         string;
  repoName:          string;
  pullRequestNumber: number;
}

const ChecklistPage = React.createClass<RouteComponentProps<{}, ChecklistPageParams>, {}>({
  render() {
    const params: ChecklistPageParams = this.props.params;
    return (
      <div style={{ margin: `${Styles.Spacing.desktopGutter * 3}px ${Styles.Spacing.desktopGutter * 4}px`, maxWidth: 768 }}>
        <ChecklistComponent repoOwner={params.repoOwner} repoName={params.repoName} pullRequestNumber={params.pullRequestNumber} />
      </div>
    );
  }
});

ReactDOM.render(
  <Router history={browserHistory}>
    <Route path="/-/" component={IndexPage} />
    <Route path="/-/:repoOwner/:repoName/pull/:pullRequestNumber" component={ChecklistPage} />
  </Router>,
  document.querySelector('#app')
);

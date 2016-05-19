import * as React       from 'react';
import * as ReactDOM    from 'react-dom';
import { Router, Route, IndexRoute, RouteComponentProps, Link, browserHistory }  from 'react-router'
import * as injectTapEventPlugin from 'react-tap-event-plugin';

injectTapEventPlugin();

import {Styles,ThemeWrapper} from 'material-ui';

const theme = Styles.ThemeManager.modifyRawThemePalette(
  Styles.getMuiTheme({}), {
    primary1Color: Styles.Colors.lightBlueA700,
    accent1Color:  Styles.Colors.blue100
  }
)

import {ChecklistComponent, MeAvatarComponent} from './ChecklistComponent';
import {NewsComponent} from './NewsComponent';

const Layout = React.createClass({
  render() {
    return (
      <div style={{ margin: `${Styles.Spacing.desktopGutter * 3}px ${Styles.Spacing.desktopGutter * 4}px`, maxWidth: 768, position: 'relative' }}>
        <MeAvatarComponent />
        {this.props.children}
      </div>
    );
  }
});

const IndexPage = React.createClass({
  render() {
    return (
      <NewsComponent></NewsComponent>
    );
  }
});

interface ChecklistPageParams {
  repoOwner:         string;
  repoName:          string;
  pullRequestNumber: number;
  stage:             string;
}

const ChecklistPage = React.createClass<RouteComponentProps<{}, ChecklistPageParams>, {}>({
  render() {
    const params: ChecklistPageParams = this.props.params;
    return (
      <ChecklistComponent repoOwner={params.repoOwner} repoName={params.repoName} pullRequestNumber={params.pullRequestNumber} stage={params.stage || ''} />
    );
  }
});

ReactDOM.render(
  <ThemeWrapper theme={theme}>
    <Router history={browserHistory}>
      <Route path="/-/" component={Layout}>
        <IndexRoute component={IndexPage} />
        <Route path=":repoOwner/:repoName/pull/:pullRequestNumber" component={ChecklistPage} />
        <Route path=":repoOwner/:repoName/pull/:pullRequestNumber/:stage" component={ChecklistPage} />
      </Route>
    </Router>
  </ThemeWrapper>,
  document.querySelector('#app')
);

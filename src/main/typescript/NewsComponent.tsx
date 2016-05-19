import * as React from 'react';
import {Card,CardTitle,List,ListItem,CircularProgress,Styles} from 'material-ui';
import {Link} from 'react-router';

import {API,News} from './api'

interface NewsComponentState {
  news: News
}

export const NewsComponent = React.createClass<{}, NewsComponentState>({
  componentWillMount() {
    API.getNews()
      .then(news => {
        this.setState({ news: news });
      });
  },

  contextTypes: {
    muiTheme: React.PropTypes.object,
    router: React.PropTypes.object
  },

  getInitialState() {
    return { news: null };
  },

  _handlePullRequestItemTouchTap(newsItem, pr) {
    return () => {
      this.context.router.push(`/${newsItem.head.repo.fullName}/pull/${pr.number}`)
    };
  },

  render() {
    if (!this.state.news) {
      return (
        <div>
          <h1>News</h1>
          <div style={{ textAlign: 'center', marginTop: 64 }}><CircularProgress /></div>
        </div>
      );
    }

    let theme: Styles.MuiTheme = this.context.muiTheme;

    return (
      <div>
        <h1>News</h1>
        {
          this.state.news.map(
            news => (
              <Card style={{marginBottom: theme.baseTheme.spacing.desktopGutter}}>
                <CardTitle title={news[0].head.repo.fullName} />,
                <List>
                {
                  news.slice(0, 5).map(
                    pr => (
                      <ListItem onTouchTap={this._handlePullRequestItemTouchTap(news[0], pr)}>#{pr.number} {pr.title}</ListItem>
                    )
                  )
                }
                </List>
              </Card>
            )
          )
        }
      </div>
    );
  }
});

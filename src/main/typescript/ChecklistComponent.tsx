import * as React from 'react';
import {Checkbox,Paper,List,ListItem,CircularProgress,Avatar,RaisedButton,FlatButton,Styles,LinearProgress} from 'material-ui';
import {ActionThumbUp} from 'material-ui/lib/svg-icons';

const theme = Styles.getMuiTheme({})

// prchecklist.views.Checklist
interface Checklist {
  repo:        Repo;
  pullRequest: PullRequest;
  stage:       string;
  checks:      Check[];
  allChecked:  boolean;
}

interface Repo {
  fullName: string;
}

interface PullRequest {
  url:    string;
  number: number;
  title:  string;
  body:   string;
}

interface Check {
  url:     string;
  number:  number;
  title:   string;
  users:   User[];
  checked: boolean;
}

interface User {
  name:      string;
  avatarUrl: string;
}

interface ChecklistComponentProps {
  repoOwner:         string;
  repoName:          string;
  pullRequestNumber: number;
}

interface ChecklistComponentState {
  checklist: Checklist;
  loadFailed: boolean;
}

module API {
  function updateChecklist(mode: string, checklist: Checklist, featureNumber: number): Promise<Checklist> {
    const [repoOwner, repoName] = checklist.repo.fullName.split('/');
    let body = [
      `repoOwner=${repoOwner}`,
      `repoName=${repoName}`,
      `pullRequestNumber=${checklist.pullRequest.number}`,
      `stage=${checklist.stage}`,
      `featureNumber=${featureNumber}`
    ].join('&');
    return fetch(`/-/checklist/${mode}`, { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: body }).then(
      res => res.json<Checklist>()
    );
  }

  export function checkChecklist(checklist: Checklist, featureNumber: number): Promise<Checklist> {
    return updateChecklist('check', checklist, featureNumber);
  }

  export function uncheckChecklist(checklist: Checklist, featureNumber: number): Promise<Checklist> {
    return updateChecklist('uncheck', checklist, featureNumber);
  }

  export function fetchChecklist(repoOwner: string, repoName: string, pullRequestNumber: number, stage: string = ""): Promise<Checklist> {
    return fetch(`/-/checklist?repoOwner=${repoOwner}&repoName=${repoName}&pullRequestNumber=${pullRequestNumber}&stage=${stage}`, { credentials: 'same-origin' }).then(
      res => res.json<Checklist>()
    );
  }

  export function getMe(): Promise<User> {
    return fetch('/-/me', { credentials: 'same-origin' }).then(
      res => res.json<User>()
    );
  }
}

export const ChecklistComponent = React.createClass<ChecklistComponentProps, ChecklistComponentState>({
  _handleCheck(check: Check, i: number) {
    return (e, isChecked) => {
      let updateChecklist = isChecked ? API.checkChecklist : API.uncheckChecklist;
      updateChecklist(this.state.checklist, check.number).then(checklist => {
        console.log(checklist);
        this.setState({ checklist: checklist })
      });
    };
  },

  componentWillMount() {
    const props: ChecklistComponentProps = this.props;
    API.fetchChecklist(props.repoOwner, props.repoName, props.pullRequestNumber)
      .then((checklist) => {
        this.setState({ checklist: checklist });
      })
      .catch(() => {
        this.setState({ loadFailed: true });
      });
  },

  getInitialState(): ChecklistComponentState {
    return {
      checklist: null,
      loadFailed: false
    };
  },

  render() {
    if (this.state.loadFailed) {
      return (
        <section>
          <h2>
            <small>{this.props.repoOwner}/{this.props.repoName} #{this.props.pullRequestNumber}</small>
          </h2>
          <p>Repository {this.props.repoOwner}/{this.props.repoName} has not been registered yet.</p>
          <RaisedButton label={`Register ${this.props.repoOwner}/${this.props.repoName}`} secondary={true} /> and start using
        </section>
      );
    }

    if (!this.state.checklist) {
      return (
        <section>
          <h2>
            <small>{this.props.repoOwner}/{this.props.repoName} #{this.props.pullRequestNumber}</small>
          </h2>
          <div style={{ textAlign: 'center', marginTop: 128 }}><CircularProgress /></div>
        </section>
      );
    }

    return (
      <section>
        <h2>
          <small style={{color: theme.baseTheme.palette.disabledColor}}>{this.props.repoOwner}/{this.props.repoName} #{this.props.pullRequestNumber}</small>
        </h2>
        <h1>
          <ActionThumbUp style={{height: 48, width: 48, verticalAlign: 'middle', marginRight: 16}} color={this.state.checklist.allChecked ? theme.baseTheme.palette.primary1Color : theme.baseTheme.palette.disabledColor} />
          {this.state.checklist.pullRequest.title}
        </h1>
        <LinearProgress mode="determinate" color={theme.baseTheme.palette.accent1Color} value={this.state.checklist.checks.filter(c => c.checked).length} max={this.state.checklist.checks.length}></LinearProgress>
        <Paper>
          <List>
          {
            this.state.checklist.checks.map((check: Check, i: number) => (
              <ListItem leftCheckbox={<Checkbox defaultChecked={check.checked} onCheck={this._handleCheck(check, i)} />} >
                #{check.number} {check.title}
                <div style={{ position: 'absolute', right: 32, top: 8 }}>
                  {check.users.map(user => <Avatar src={user.avatarUrl} size={32} />)}
                </div>
              </ListItem>
            ))
          }
          </List>
        </Paper>
        <pre style={{ padding: 16, backgroundColor: '#F3F3F3' }}>{this.state.checklist.pullRequest.body}</pre>
      </section>
    );
  }
});

export const MeAvatarComponent = React.createClass({
  componentWillMount() {
    API.getMe().then(me => this.setState({ me: me }));
  },

  getInitialState() {
    return {
      me: null
    };
  },

  render() {
    if (!this.state.me) {
      return (
        <Avatar style={{position: 'absolute', right: 16}} />
      );
    }

    return (
      <Avatar src={this.state.me.avatarUrl} style={{position: 'absolute', right: 16}} />
    );
  }
});

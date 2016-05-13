import * as React from 'react';
import {Checkbox,Paper,List,ListItem,CircularProgress,Avatar,RaisedButton,FlatButton,Styles,LinearProgress,DropDownMenu,MenuItem} from 'material-ui';
import {ActionThumbUp} from 'material-ui/lib/svg-icons';

// prchecklist.views.Checklist
interface Checklist {
  repo:        Repo;
  pullRequest: PullRequest;
  stage:       string;
  stages:      string[];
  checks:      Check[];
  allChecked:  boolean; // XXX not needed?
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
  stage:             string;
}

interface ChecklistComponentState {
  checklist:  Checklist;
  loadFailed: boolean;
  muiTheme:   Styles.MuiTheme;
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

  export function fetchChecklist(repoOwner: string, repoName: string, pullRequestNumber: number, stage: string): Promise<Checklist> {
    return fetch(`/-/checklist?repoOwner=${repoOwner}&repoName=${repoName}&pullRequestNumber=${pullRequestNumber}&stage=${stage}`, { credentials: 'same-origin' }).then(
      res => res.json<Checklist>()
    );
  }

  export function getMe(): Promise<User> {
    return fetch('/-/me', { credentials: 'same-origin' }).then(
      res => res.json<User>()
    );
  }

  export function registerRepo(repoOwner: string, repoName: string): Promise<any> {
    return fetch('/-/repos', { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: `repoOwner=${repoOwner}&repoName=${repoName}` }).then(
      res => res.json()
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

  _handleRegisterTap() {
    API.registerRepo(this.props.repoOwner, this.props.repoName).then(() => location.reload());
  },

  contextTypes: {
    muiTheme: React.PropTypes.object,
  },

  componentWillMount() {
    // const checklist: Checklist = {
    //   repo: { fullName: "motemen/test" },
    //   pullRequest: {
    //     url: '#',
    //     number: 3,
    //     title: 'test pr',
    //     body: 'bobobdy'
    //   },
    //   stage: 'staging',
    //   stages: [ 'de', 'staging', 'production' ],
    //   checks: [
    //     {
    //       url: '#',
    //       number: 1,
    //       title: 'feature-a',
    //       users: [
    //         { name: 'foo', avatarUrl: '' }
    //       ],
    //       checked: false
    //     },
    //     {
    //       url: '#',
    //       number: 2,
    //       title: 'feature-b',
    //       users: [
    //         { name: 'motemen', avatarUrl: '' }
    //       ],
    //       checked: true
    //     },
    //     {
    //       url: '#',
    //       number: 3,
    //       title: 'feature-c',
    //       users: [
    //       ],
    //       checked: false
    //     }
    //   ],
    //   allChecked: false
    // }
    // this.setState({ checklist: checklist });
    const props: ChecklistComponentProps = this.props;
    API.fetchChecklist(props.repoOwner, props.repoName, props.pullRequestNumber, props.stage)
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
      loadFailed: false,
      muiTheme: this.context.muiTheme
    };
  },

  render() {
    let theme = this.state.muiTheme;

    const header = (
      <h2>
        <small style={{color: theme.baseTheme.palette.disabledColor}}>{this.props.repoOwner}/{this.props.repoName} #{this.props.pullRequestNumber} { this.props.stage ? `:: ${this.props.stage}` : '' }</small>
      </h2>
    )

    if (this.state.loadFailed) {
      return (
        <section>
          {header}
          <div style={{ marginTop: 128 }}>
            <p>Repository {this.props.repoOwner}/{this.props.repoName} has not been registered yet.</p>
            <RaisedButton onTouchTap={this._handleRegisterTap} label={`Register ${this.props.repoOwner}/${this.props.repoName}`} secondary={true} /> and start using
          </div>
        </section>
      );
    }

    if (!this.state.checklist) {
      return (
        <section>
          {header}
          <div style={{ textAlign: 'center', marginTop: 128 }}><CircularProgress /></div>
        </section>
      );
    }

    return (
      <section>
        {header}
        <h1>
          <ActionThumbUp style={{height: 48, width: 48, verticalAlign: 'middle', marginRight: 16}} color={this.state.checklist.allChecked ? theme.baseTheme.palette.primary1Color : theme.baseTheme.palette.accent2Color} />
          {this.state.checklist.pullRequest.title}
        </h1>
        <LinearProgress mode="determinate" color={theme.baseTheme.palette.accent1Color} value={this.state.checklist.checks.filter(c => c.users.length > 0).length} max={this.state.checklist.checks.length}></LinearProgress>
        <Paper>
          <List>
          {
            this.state.checklist.checks.map((check: Check) => (
              <ListItem leftCheckbox={<Checkbox defaultChecked={check.checked} onCheck={this._handleCheck(check)} checkedIcon={<ActionThumbUp />} unCheckedIcon={<ActionThumbUp color={theme.baseTheme.palette.accent2Color}/>} />} >
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

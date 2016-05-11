import * as React from 'react';
import {Checkbox,Paper,List,ListItem,CircularProgress,Avatar} from 'material-ui';

// prchecklist.views.Checklist
interface Checklist {
  repo:        Repo;
  pullRequest: PullRequest;
  stage:       string;
  checks:      Check[];
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
  checkLoading: { [i: number]: boolean; };
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
      });
  },

  getInitialState(): ChecklistComponentState {
    return {
      checklist: null,
      checkLoading: {}
    };
  },

  render() {
    if (!this.state.checklist) {
      return (
        <section>
          <h2>
            <small>{this.props.repoOwner}/{this.props.repoName} #{this.props.pullRequestNumber}</small>
          </h2>
          <div style={{ textAlign: 'center', marginTop: 192 }}><CircularProgress /></div>
        </section>
      );
    }

    return (
      <section>
        <h2>
          <small>{this.props.repoOwner}/{this.props.repoName} #{this.props.pullRequestNumber}</small>
        </h2>
        <h1>
          {this.state.checklist.pullRequest.title}
        </h1>
        <pre style={{ padding: 16, backgroundColor: '#F3F3F3' }}>{this.state.checklist.pullRequest.body}</pre>
        <Paper>
        <List>
        {
          this.state.checklist.checks.map((check: Check, i: number) => (
            <ListItem leftCheckbox={<Checkbox disabled={this.state.checkLoading[i]} defaultChecked={check.checked} onCheck={this._handleCheck(check, i)} />} >
              #{check.number} {check.title}
              <div style={{ position: 'absolute', right: 32, top: 8 }}>
                {check.users.map(user => <Avatar src={user.avatarUrl} size={32} />)}
              </div>
            </ListItem>
          ))
        }
        </List>
        </Paper>
      </section>
    );
  }
});

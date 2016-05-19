import * as React from 'react';
import {Checkbox,Paper,List,ListItem,CircularProgress,Avatar,RaisedButton,FlatButton,Styles,LinearProgress,DropDownMenu,MenuItem} from 'material-ui';
import {ActionThumbUp} from 'material-ui/lib/svg-icons';

import {API,Checklist,Check} from './api'

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
    muiTheme: React.PropTypes.object
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
          <div style={{ marginTop: 64 }}>
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
              <ListItem>
                <Checkbox style={{position: 'absolute', left: 16, top: 12, width: 24}} defaultChecked={check.checked} onCheck={this._handleCheck(check)} checkedIcon={<ActionThumbUp />} unCheckedIcon={<ActionThumbUp color={theme.baseTheme.palette.disabledColor}/>} />
                <div style={{paddingLeft: 36}}>
                  <a href={check.url} target="_blank" style={{display: 'block'}}>#{check.number} {check.title}</a>
                  <div style={{ position: 'absolute', right: 32, top: 8 }}>
                    {check.users.map(user => <Avatar src={user.avatarUrl} size={32} />)}
                  </div>
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

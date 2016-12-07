import * as React from 'react';
import {Checkbox,Paper,List,ListItem,CircularProgress,Avatar,RaisedButton,FlatButton,Styles,LinearProgress,DropDownMenu,MenuItem} from 'material-ui';
import {ActionThumbUp} from 'material-ui/lib/svg-icons';

import {API,Checklist,Check,ERROR_REPO_NOT_REGISTERED} from './api'

interface ChecklistComponentProps {
  repoOwner:         string;
  repoName:          string;
  pullRequestNumber: number;
  stage:             string;
}

interface ChecklistComponentState {
  checklist:  Checklist;
  loadFailed: boolean;
  error:      String;
  loading:    boolean;
  muiTheme:   Styles.MuiTheme;
}

export const ChecklistComponent = React.createClass<ChecklistComponentProps, ChecklistComponentState>({
  _handleCheck(check: Check, i: number) {
    return (e, isChecked) => {
      let updateChecklist = isChecked ? API.checkChecklist : API.uncheckChecklist;
      updateChecklist(this.state.checklist, check.number).then(checklist => {
        this.setState({ checklist: checklist })
      });
    };
  },

  _handleRegisterTap() {
    API.registerRepo(this.props.repoOwner, this.props.repoName).then(() => location.reload());
  },

  navigateToStage(stage: string) {
    // TODO build URL smartly
    this.context.router.push(location.pathname.replace(/\/(\d+)(\/[^\/]+)?$/, '/$1/' + stage));
  },

  contextTypes: {
    muiTheme: React.PropTypes.object,
    router: React.PropTypes.object
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
    this.loadChecklist(this.props);
  },

  componentWillReceiveProps(nextProps: ChecklistComponentProps) {
    this.loadChecklist(nextProps);
  },

  loadChecklist(props: ChecklistComponentProps) {
    this.setState({ loading: true });
    API.fetchChecklist(props.repoOwner, props.repoName, props.pullRequestNumber, props.stage)
      .then((checklist) => {
        if (checklist.stage === '' && checklist.stages.length > 0) {
          // XXX: move this logic to server-side?
          this.navigateToStage(checklist.stages[0]);
        }
        this.setState({ loading: false, checklist: checklist });
      })
      .catch((e) => {
        console.error(e);
        this.setState({
          loading: false,
          loadFailed: true,
          error: e
        });
      });
  },

  getInitialState(): ChecklistComponentState {
    return {
      checklist: null,
      loadFailed: false,
      loading: false,
      error: null,
      muiTheme: this.context.muiTheme
    };
  },

  _handleStageChange(event, key: number, payload: any) {
    this.navigateToStage(payload);
  },

  render() {
    let theme = this.state.muiTheme;

    let state: ChecklistComponentState = this.state;
    let stages = state.checklist && state.checklist.stages || [];

    const header = (
      <h2 style={{color: theme.baseTheme.palette.disabledColor, lineHeight: '56px'}}>
        {this.props.repoOwner}/{this.props.repoName}
        { ` #${this.props.pullRequestNumber}` }
        { (this.props.stage || stages.length) && ' :: ' || '' }
        {
          stages.length ? (
            <DropDownMenu value={this.props.stage} style={{fontSize: 'inherit', marginLeft: -20}} onChange={this._handleStageChange}>
              { stages.map(stage => <MenuItem value={stage} primaryText={stage} />) }
              {
                // Non-declared stage
                stages.some(stage => stage === this.props.stage) ? [] :
                  <MenuItem value={this.props.stage} primaryText={this.props.stage} />
              }
            </DropDownMenu>
          ) : this.props.stage
        }
      </h2>
    )

    if (state.loadFailed) {
      return (
        <section>
          {header}
          <div style={{ marginTop: 64 }}>
          {
            state.error === ERROR_REPO_NOT_REGISTERED ? <div>
              <p>Repository {this.props.repoOwner}/{this.props.repoName} has not been registered yet.</p>
              <p><RaisedButton onTouchTap={this._handleRegisterTap} label={`Register ${this.props.repoOwner}/${this.props.repoName}`} secondary={true} /> and start using</p>
            </div> : (
              <Paper style={{padding: 24}}>
                <h3>Error</h3>
                <pre>{''+state.error}</pre>
              </Paper>
            )
          }
          </div>
        </section>
      );
    }

    if (!state.checklist) {
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
          <ActionThumbUp style={{height: 48, width: 48, verticalAlign: 'middle', marginRight: 16}} color={state.checklist.allChecked ? theme.baseTheme.palette.primary1Color : theme.baseTheme.palette.accent2Color} />
          {state.checklist.pullRequest.title}
        </h1>
        <LinearProgress mode="determinate" color={theme.baseTheme.palette.accent1Color} value={state.checklist.checks.filter(c => c.users.length > 0).length} max={state.checklist.checks.length}></LinearProgress>
        <Paper>
          <List>
          {
            state.checklist.checks.map((check: Check) => (
              <ListItem secondaryText={<div style={{paddingLeft: 48}}>{check.assignees.map(assignee => <span style={{paddingLeft: 6}}>@{assignee.name}</span>)}</div>}>
                <Checkbox
                  style={{position: 'absolute', left: 20, top: 24, width: 24}}
                  defaultChecked={check.checked}
                  onCheck={this._handleCheck(check)}
                  checkedIcon={<ActionThumbUp />}
                  unCheckedIcon={<ActionThumbUp color={theme.baseTheme.palette.disabledColor}/>}
                  disabled={!!state.loading}
                  />
                <div style={{paddingLeft: 48, paddingRight: 48}}>
                  <a href={check.url} target="_blank" style={{display: 'block'}}>#{check.number} {check.title}</a>
                  <div style={{ position: 'absolute', right: 32, top: 20 }}>
                    {check.users.map(user => <Avatar src={user.avatarUrl} size={32} />)}
                  </div>
                </div>
              </ListItem>
            ))
          }
          </List>
        </Paper>
        <pre style={{ padding: 16, backgroundColor: '#F3F3F3', overflowX: 'scroll' }}>{state.checklist.pullRequest.body}</pre>
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

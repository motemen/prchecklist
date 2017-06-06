import * as React from 'react';
import * as API from './api';

interface ChecklistProps {
  checklistRef: API.ChecklistRef;
}

interface ChecklistState {
  checklist?: API.Checklist;
  me?: API.GitHubUser;
  loading: boolean;
  error?: any;
}

export class ChecklistComponent extends React.Component<ChecklistProps, ChecklistState> {
  constructor(props: ChecklistProps) {
    super(props);

    this.state = { loading: false };

    API.getChecklist(props.checklistRef)
      .then((data) => {
        if (data.Checklist) {
          if (this.ensureCorrectStage(data.Checklist)) {
            return;
          }
        }
        this.setState({
          checklist: data.Checklist,
          me: data.Me,
        });
      })
      .catch((err) => {
        this.setState({ error: `${err}` });
        console.error(err);
      });
  }

  ensureCorrectStage(checklist: API.Checklist): boolean {
    const stages = checklist.Config && checklist.Config.Stages || [];
    const checklistRef = this.props.checklistRef;
    if (stages.length) {
      if (stages.findIndex((s) => s === checklistRef.Stage) === -1) {
        this.navigateToStage(stages[0]);
        return true;
      }
    } else {
      if (checklistRef.Stage !== '') {
        this.navigateToStage('');
        return true;
      }
    }

    return false;
  }

  navigateToStage(stage: string) {
    const checklistRef = this.props.checklistRef;
    if (stage === '') {
      location.pathname = `/${checklistRef.Owner}/${checklistRef.Repo}/pull/${checklistRef.Number}`;
    } else {
      location.pathname = `/${checklistRef.Owner}/${checklistRef.Repo}/pull/${checklistRef.Number}/${stage}`;
    }
  }

  handleOnClickChecklistItem = (item: API.ChecklistItem): React.ChangeEventHandler<HTMLInputElement> => {
    return (ev: React.ChangeEvent<HTMLInputElement>) => {
      const checked = ev.target.checked;

      this.setState((prevState: ChecklistState, props) => {
        prevState.checklist.Items.forEach((it) => {
          if (it.Number == item.Number) {
            console.log(it);
            if (checked) {
              it.CheckedBy = it.CheckedBy.concat(this.state.me);
            } else {
              it.CheckedBy = it.CheckedBy.filter((user) => user.ID !== this.state.me.ID);
            }
          }
        });
        return { ...prevState, loading: true };
      });

      API.setCheck(this.props.checklistRef, item.Number, checked)
        .then((data) => {
          this.setState({
            checklist: data.Checklist,
            me: data.Me,
            loading: false
          });
        });
    }
  }

  handleOnSelectStage = (ev: React.ChangeEvent<HTMLSelectElement>) => {
    this.navigateToStage(ev.target.value);
  }

  itemIsCheckedByMe(item: API.ChecklistItem): boolean {
    return item.CheckedBy.findIndex((user) => user.ID == this.state.me.ID) !== -1;
  }

  checklistStages(): string[] {
    if (this.state.checklist && this.state.checklist.Config) {
      return this.state.checklist.Config.Stages || [];
    }

    return [];
  }

  completed(): boolean {
    let checklist = this.state.checklist;
    if (!checklist) return false;

    return checklist.Items.every((item) => item.CheckedBy.length > 0);
  }

  render() {
    if (this.state.error) {
      return <pre className="error">{this.state.error}</pre>;
    }

    const checklist = this.state.checklist;
    if (!checklist) {
      return <section>Loading...</section>;
    }

    const stages = this.checklistStages();

    return <section className={this.completed() ? 'completed' : ''}>
      <nav>
        <div className="logo"><strong>{checklist.Owner}/{checklist.Repo}#{checklist.Number}</strong></div>
        <div className="stages">
        {
          stages.length ?
            <select className="stages" value={this.props.checklistRef.Stage} onChange={this.handleOnSelectStage}>
              {
                stages.map((stage) =>
                  <option key={`stage-${stage}`}>{stage}</option>
                )
              }
            </select>
            : []
        }
        </div>
        <div className="user-signedin">{this.state.me.Login}</div>
      </nav>
      <h1>
        <span className="title"><a href={checklist.URL}>#{checklist.Number}</a> {checklist.Title}</span>
      </h1>
      <pre>{checklist.Body}</pre>
      <div className="items">
        <ul>
          {
            checklist.Items.map((item) => {
              return <li key={`item-${item.Number}`}>
                <input type="checkbox" onChange={this.handleOnClickChecklistItem(item)} checked={this.itemIsCheckedByMe(item)} />
                <span className="number"><a href={item.URL}>#{item.Number}</a></span>
                {' '}
                <span className="title">{item.Title}</span>
                {' '}
                <span className="checkedby">
                {
                  item.CheckedBy.map((user) => {
                    return <span className="user" key={`item-${item.Number}-checkedby-${user.ID}`}>
                      <img src={user.AvatarURL} alt={user.Login} />
                    </span>;
                  })
                }
                </span>
              </li>;
            })
          }
        </ul>
      </div>
    </section>;
  }
}

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

export class ChecklistComponent extends React.PureComponent<ChecklistProps, ChecklistState> {
  constructor(props: ChecklistProps) {
    super(props);

    this.state = { loading: false };

    API.getChecklist(props.checklistRef)
      .then((data) => {
        this.setState({
          checklist: data.Checklist,
          me: data.Me,
        });
      })
      .catch((err) => {
        this.setState({ error: err });
      });
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
              it.CheckedBy = it.CheckedBy.filter((user) => user.ID != this.state.me.ID);
            }
          }
        });
        return { ...prevState, loading: true }
      }, () => {
        API.setCheck(this.props.checklistRef, item.Number, checked)
          .then((data) => {
            this.setState({
              checklist: data.Checklist,
              me: data.Me,
              loading: false
            });
          });
      });
    }
  }

  itemIsCheckedByMe(item: API.ChecklistItem): boolean {
    return item.CheckedBy.findIndex((user) => user.ID == this.state.me.ID) !== -1;
  }

  render() {
    if (this.state.error) {
      return <div className="error">{this.state.error}</div>;
    }

    const checklist = this.state.checklist;
    if (!checklist) {
      return <section>Loading...</section>;
    }

    return <section>
      <h1>
        <span className="number">#{checklist.Number}</span>
        {' '}
        <span className="title">{checklist.Title}</span>
      </h1>
      <pre>{checklist.Body}</pre>
      <div className="items">
        <ul>
          {
            checklist.Items.map((item) => {
              return <li key={`item-${item.Number}`}>
                <input type="checkbox" onChange={this.handleOnClickChecklistItem(item)} checked={this.itemIsCheckedByMe(item)} disabled={this.state.loading} />
                <span className="number">#{item.Number}</span>
                {' '}
                <span className="title">{item.Title}</span>
                {' '}
                <span className="checkedby">
                {
                  item.CheckedBy.map((user) => {
                    return <span className="user" key={`item-${item.Number}-checkedby-${user.ID}`}><img src={user.AvatarURL}/></span>;
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

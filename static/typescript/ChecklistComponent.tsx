import * as React from 'react';
import * as API from './api';

interface ChecklistProps {
  checklistRef: API.ChecklistRef;
}

interface ChecklistState {
  checklist?: API.Checklist;
}

export class ChecklistComponent extends React.PureComponent<ChecklistProps, ChecklistState> {
  constructor(props: ChecklistProps) {
    super(props);

    API.getChecklist(props.checklistRef)
      .then((checklist) => {
        this.setState({ checklist });
      });
  }

  handleOnClickChecklistItem = (item: API.ChecklistItem): React.ChangeEventHandler<HTMLInputElement> => {
    return (ev: React.ChangeEvent<HTMLInputElement>) => {
      console.log(ev);
      alert(ev);
      API.setCheck(this.props.checklistRef, item.Number, ev.target.checked)
        .then((checklist) => {
          this.setState({ checklist });
        });
    }
  }

  render() {
    const checklist = this.state && this.state.checklist;
    if (!checklist) {
      return <section>Loading...</section>;
    }

    return <section>
      <h1>
        <span className="number">#{checklist.Number}</span>
        {' '}
        <span className="title">{checklist.Title}</span>
      </h1>
      <div className="items">
        <ul>
          {
            checklist.Items.map((item) => {
              return <li key={`item-${item.Number}`}>
                <input type="checkbox" onChange={this.handleOnClickChecklistItem(item)}/>
                <span className="number">#{item.Number}</span>
                <span className="title">{item.Title}</span>
                <span className="checkedby">
                {
                  item.CheckedBy.map((user) => {
                    return <span className="user"><img width="32" src={user.AvatarURL}/></span>;
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

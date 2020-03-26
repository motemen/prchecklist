import * as React from 'react';
import * as API from './api';

interface NavProps {
  logo?: JSX.Element;
  stages?: JSX.Element;
  me?: API.GitHubUser;
}

interface NavState {
  version: string;
}

export class NavComponent extends React.Component<NavProps, NavState> {
  public componentDidMount() {
    this.setState({
      version: document.querySelector('meta[name="prchecklist version"]').getAttribute('content'),
    });
  }

  public render() {
    return <nav>
      <div className="logo"><strong>{this.props.logo || `prchecklist ${this.state.version}`}</strong></div>
      <div className="stages">{this.props.stages}</div>
      {
        this.props.me
          ? <div className="user-signedin">{this.props.me.Login}</div>
          : <a className="user-signedin" href={`/auth?return_to=${encodeURIComponent(location.pathname)}`}>Login</a>
      }
    </nav>;
  }
}

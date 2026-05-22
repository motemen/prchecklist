import * as React from "react";
import * as API from "./api";
import { EnvContext } from "./EnvContext";

interface NavProps {
  logo?: JSX.Element;
  stages?: JSX.Element;
  me?: API.GitHubUser;
  showSelfOnly?: boolean;
  handleOnChangeSelfOnly?: (event: React.ChangeEvent<HTMLInputElement>) => void;
}

export class NavComponent extends React.Component<NavProps> {
  public render() {
    return (
      <nav>
        <div className="logo">
          <strong>
            <EnvContext.Consumer>
              {(env) => this.props.logo || `prchecklist ${env.appVersion}`}
            </EnvContext.Consumer>
          </strong>
        </div>
        <div className="stages">{this.props.stages}</div>
        {this.props.me ? (
          <label>
            <input
              type="checkbox"
              checked={this.props.showSelfOnly}
              onChange={this.props.handleOnChangeSelfOnly} />
            <div className="user-signedin">{this.props.me.Login}</div>
          </label>
        ) : (
          <a
            className="user-signedin"
            href={`/auth?return_to=${encodeURIComponent(location.pathname)}`}
          >
            Login
          </a>
        )}
      </nav>
    );
  }
}

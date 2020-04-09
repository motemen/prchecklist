import * as React from "react";
import * as renderer from "react-test-renderer";

import { NavComponent } from "./NavComponent";

test("", async () => {
  const component = renderer.create(<NavComponent />);

  const tree = component.toJSON();
  expect(tree).toMatchSnapshot();
});

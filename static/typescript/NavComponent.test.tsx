import * as React from "react";
import { render } from "@testing-library/react";

import { NavComponent } from "./NavComponent";

test("", () => {
  const { container } = render(<NavComponent />);

  expect(container.firstChild).toMatchSnapshot();
});

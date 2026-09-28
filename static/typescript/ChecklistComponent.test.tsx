import * as React from "react";
import { render, screen } from "@testing-library/react";
import { ChecklistComponent } from "./ChecklistComponent";

jest.mock("./api");

test("", async () => {
  const { container } = render(
    <ChecklistComponent
      checklistRef={{
        Number: 1,
        Owner: "test",
        Repo: "test",
        Stage: "production",
      }}
    />
  );

  expect(container.firstChild).toMatchSnapshot();

  await screen.findByText("Release 2017-10-11 20:18:22 +0900");

  expect(container.firstChild).toMatchSnapshot();
  // The selected stage is a DOM property and is not part of the snapshot.
  expect(screen.getByRole<HTMLSelectElement>("combobox").value).toBe(
    "production"
  );
});

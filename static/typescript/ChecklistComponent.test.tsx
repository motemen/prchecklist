import * as React from 'react';
import {ChecklistComponent} from './ChecklistComponent';
import * as renderer from 'react-test-renderer';

jest.mock("./api");

test('', async () => {
  const component = renderer.create(<ChecklistComponent checklistRef={{Number: 1, Owner: "test", Repo: "test", Stage: "production" }} />)

  let tree = component.toJSON();
  expect(tree).toMatchSnapshot();

  await Promise.resolve();

  tree = component.toJSON();
  expect(tree).toMatchSnapshot();
});

import { ChecklistRef, ChecklistResponse, ErrorType } from "../api-schema";

export class APIError {
  constructor(public errorType: ErrorType) {}
}

export function getChecklist(
  ref: ChecklistRef
): Promise<ChecklistResponse | APIError> {
  return Promise.resolve({
    Checklist: {
      URL: "https://github.com/motemen/test-repository/pull/2",
      Title: "Release 2017-10-11 20:18:22 +0900",
      Body:
        "Blah blah blah\n- [ ] #1 feature-1 @motemen\n- [ ] #3 foo bar baz foo foo foo foo foo foo foof foohof ofhfof @motemen\n- [ ] #33 mk-feature @motemen\n- [ ] #4 1403357307 @motemen\n- [ ] #7 feature-y @motemen",
      Owner: "motemen",
      Repo: "test-repository",
      Number: 2,
      IsPrivate: false,
      User: { Login: "motemen" },
      Commits: [
        {
          Message: "feature-1",
          Oid: "142d5962881d3db66bdd2c257486a72f2cb175d8",
        },
        {
          Message: "Merge pull request #1 from motemen/feature-1\n\nfeature-1",
          Oid: "e966324ceb00fcdba463f9db10a2c95b362d5bbe",
        },
        {
          Message: "a commit in feature-1403357222",
          Oid: "341df5410f1b2be3762b6c23cf9419c08830fb23",
        },
        {
          Message:
            "Merge pull request #3 from motemen/feature-1403357222\n\nmerge pr",
          Oid: "ccaa7eb46e3bfeedeaa584f0b5081e3fb19ccdd9",
        },
        {
          Message: "a commit in feature-1403357307",
          Oid: "79c1bc383667b5687c2b773d35a508db5f1954a4",
        },
        {
          Message:
            "Merge pull request #4 from motemen/feature-1403357307\n\nmerge pr",
          Oid: "25b53e8fa82295e0fc358e43129c556318aa95b9",
        },
        {
          Message: "feature-y",
          Oid: "cdf38b20b6d08a549c21f21b19c4c03a1da29dd4",
        },
        {
          Message: "Merge pull request #7 from motemen/feature-y\n\nfeature-y",
          Oid: "63eb831808a209009fb0b3182e2c530f6d384ca3",
        },
        {
          Message: "mk-feature",
          Oid: "bb37709ad41226eca2f5390b7ea041480077ef7b",
        },
        {
          Message:
            "Merge pull request #33 from motemen/mk-feature\n\nmk-feature",
          Oid: "76a91cb8ff26a902e0da5aff34bdbcb8c4e58d4c",
        },
        {
          Message: "+prchecklist.yml",
          Oid: "64b128586823f958c948e10eb88eae129b56ea68",
        },
      ],
      ConfigBlobID: "b85e23e129e68bcf5677dd17860fa90d654a95d8",
      Stage: "qa",
      Items: [
        {
          URL: "https://github.com/motemen/test-repository/pull/1",
          Title: "feature-1",
          Body: "",
          Owner: "motemen",
          Repo: "test-repository",
          Number: 1,
          IsPrivate: false,
          User: { Login: "motemen" },
          Commits: [],
          ConfigBlobID: "",
          CheckedBy: [
            {
              ID: 8465,
              Login: "motemen",
              AvatarURL: "https://avatars2.githubusercontent.com/u/8465?v=4",
            },
          ],
        },
        {
          URL: "https://github.com/motemen/test-repository/pull/3",
          Title: "foo bar baz foo foo foo foo foo foo foof foohof ofhfof",
          Body: "",
          Owner: "motemen",
          Repo: "test-repository",
          Number: 3,
          IsPrivate: false,
          User: { Login: "motemen" },
          Commits: [],
          ConfigBlobID: "",
          CheckedBy: [],
        },
        {
          URL: "https://github.com/motemen/test-repository/pull/4",
          Title: "1403357307",
          Body: "",
          Owner: "motemen",
          Repo: "test-repository",
          Number: 4,
          IsPrivate: false,
          User: { Login: "motemen" },
          Commits: [],
          ConfigBlobID: "",
          CheckedBy: [
            {
              ID: 8465,
              Login: "motemen",
              AvatarURL: "https://avatars2.githubusercontent.com/u/8465?v=4",
            },
          ],
        },
        {
          URL: "https://github.com/motemen/test-repository/pull/7",
          Title: "feature-y",
          Body: "",
          Owner: "motemen",
          Repo: "test-repository",
          Number: 7,
          IsPrivate: false,
          User: { Login: "werckerbot" },
          Commits: [],
          ConfigBlobID: "",
          CheckedBy: [],
        },
        {
          URL: "https://github.com/motemen/test-repository/pull/33",
          Title: "mk-feature",
          Body: "",
          Owner: "motemen",
          Repo: "test-repository",
          Number: 33,
          IsPrivate: false,
          User: { Login: "motemen" },
          Commits: [],
          ConfigBlobID: "",
          CheckedBy: [],
        },
      ],
      Config: {
        Stages: ["qa", "production"],
        Notification: {
          Events: {
            OnComplete: ["default"],
            OnCompleteChecksOfUser: [],
            OnCheck: ["default"],
            OnRemove: ["default"],
          },
          Channels: null,
        },
      },
    },
    Me: {
      ID: 8465,
      Login: "motemen",
      AvatarURL: "https://avatars2.githubusercontent.com/u/8465?v=4",
    },
  });
}

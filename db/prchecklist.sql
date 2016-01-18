CREATE TABLE github_repos (
    id    SERIAL NOT NULL PRIMARY KEY,
    owner VARCHAR(255) NOT NULL,
    name  VARCHAR(255) NOT NULL,
    UNIQUE (owner, name)
);

CREATE TABLE checklists (
    id                SERIAL NOT NULL PRIMARY KEY,
    github_repo_id    INTEGER NOT NULL REFERENCES github_repos (id),
    release_pr_number INTEGER NOT NULL,
    UNIQUE            (github_repo_id, release_pr_number)
);

CREATE TABLE checks (
    id                SERIAL NOT NULL PRIMARY KEY,
    checklist_id      INTEGER NOT NULL REFERENCES checklists (id),
    feature_pr_number INTEGER NOT NULL,
    user_login        VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    UNIQUE (checklist_id, feature_pr_number, user_login)
);

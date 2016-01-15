CREATE TABLE checklist (
    id                   SERIAL NOT NULL PRIMARY KEY,
    repository_full_name VARCHAR(255) NOT NULL,
    release_pr_number    INTEGER NOT NULL,
    UNIQUE (repository_full_name, release_pr_number)
);

CREATE TABLE checks (
    id                SERIAL NOT NULL PRIMARY KEY,
    checklist_id      INTEGER NOT NULL REFERENCES checklist (id),
    feature_pr_number INTEGER NOT NULL,
    user_login        VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    UNIQUE (checklist_id, feature_pr_number, user_login)
);

package repository

import (
	"context"
	"encoding/json"
	"fmt"
	"strconv"
	"time"

	"github.com/boltdb/bolt"
	"github.com/pkg/errors"

	"github.com/motemen/go-prchecklist"
)

type boltRepository struct {
	db *bolt.DB
}

const (
	boltBucketNameUsers  = "users"
	boltBucketNameChecks = "checks"
)

func NewBoltCore(path string) (*boltRepository, error) {
	db, err := bolt.Open(path, 0600, &bolt.Options{Timeout: 1 * time.Second})
	if err != nil {
		return nil, err
	}

	err = db.Update(func(tx *bolt.Tx) error {
		if _, err := tx.CreateBucketIfNotExists([]byte(boltBucketNameUsers)); err != nil {
			return err
		}
		if _, err := tx.CreateBucketIfNotExists([]byte(boltBucketNameChecks)); err != nil {
			return err
		}
		return nil
	})
	if err != nil {
		return nil, err
	}

	return &boltRepository{db: db}, nil
}

func (r boltRepository) AddUser(ctx context.Context, user prchecklist.GitHubUser) error {
	return r.db.Update(func(tx *bolt.Tx) error {
		usersBucket := tx.Bucket([]byte(boltBucketNameUsers))

		buf, err := json.Marshal(user)
		if err != nil {
			return err
		}

		return usersBucket.Put([]byte(strconv.FormatInt(int64(user.ID), 10)), buf)
	})
}

func (r boltRepository) GetUsers(ctx context.Context, userIDs []int) (map[int]prchecklist.GitHubUser, error) {
	users := make(map[int]prchecklist.GitHubUser, len(userIDs))
	err := r.db.View(func(tx *bolt.Tx) error {
		usersBucket := tx.Bucket([]byte(boltBucketNameUsers))

		for _, id := range userIDs {
			buf := usersBucket.Get([]byte(strconv.FormatInt(int64(id), 10)))
			if buf == nil {
				return fmt.Errorf("not found: user id=%v", id)
			}

			var user prchecklist.GitHubUser
			if err := json.Unmarshal(buf, &user); err != nil {
				return err
			}
			users[id] = user
		}

		return nil
	})

	return users, errors.Wrap(err, "GetUsers")
}

func (r boltRepository) GetChecks(ctx context.Context, clRef prchecklist.ChecklistRef) (prchecklist.Checks, error) {
	if err := clRef.Validate(); err != nil {
		return nil, err
	}

	var checks prchecklist.Checks

	err := r.db.View(func(tx *bolt.Tx) error {
		checksBucket := tx.Bucket([]byte(boltBucketNameChecks))

		key := []byte(clRef.String())
		data := checksBucket.Get(key)
		if data != nil {
			err := json.Unmarshal(data, &checks)
			if err != nil {
				return err
			}
		}

		return nil
	})

	return checks, errors.Wrap(err, "GetChecks")
}

func (r boltRepository) AddCheck(ctx context.Context, clRef prchecklist.ChecklistRef, featNum int, user prchecklist.GitHubUser) error {
	if err := clRef.Validate(); err != nil {
		return err
	}

	return r.db.Update(func(tx *bolt.Tx) error {
		var checks prchecklist.Checks

		checksBucket := tx.Bucket([]byte(boltBucketNameChecks))

		key := []byte(clRef.String())
		data := checksBucket.Get(key)
		if data != nil {
			err := json.Unmarshal(data, &checks)
			if err != nil {
				return err
			}
		}

		if checks == nil {
			checks = prchecklist.Checks{}
		}

		if checks.Add(featNum, user) == false {
			return nil
		}

		data, err := json.Marshal(&checks)
		if err != nil {
			return err
		}

		return checksBucket.Put(key, data)
	})
}

func (r boltRepository) RemoveCheck(ctx context.Context, clRef prchecklist.ChecklistRef, featNum int, user prchecklist.GitHubUser) error {
	if err := clRef.Validate(); err != nil {
		return err
	}

	return r.db.Update(func(tx *bolt.Tx) error {
		var checks prchecklist.Checks

		checksBucket := tx.Bucket([]byte(boltBucketNameChecks))

		key := []byte(clRef.String())
		data := checksBucket.Get(key)
		if data != nil {
			err := json.Unmarshal(data, &checks)
			if err != nil {
				return err
			}
		}

		if checks == nil {
			checks = prchecklist.Checks{}
		}

		if checks.Remove(featNum, user) == false {
			return nil
		}

		data, err := json.Marshal(&checks)
		if err != nil {
			return err
		}

		return checksBucket.Put(key, data)
	})
}

package repository

import (
	"context"
	"encoding/json"
	"net/url"
	"strconv"

	"github.com/garyburd/redigo/redis"
	"github.com/pkg/errors"

	"github.com/motemen/prchecklist/v2"
)

const (
	redisKeyPrefixUser  = "user:"
	redisKeyPrefixCheck = "check:"
)

type redisCoreRepository struct {
	url *url.URL
}

func init() {
	registerCoreRepositoryBuilder("redis", NewRedisCore)
}

// NewRedisCore creates a coreRepository backed by boltdb.
// datasource must be a URL of form "redis://[<user>:<password>@]<hostname>",
// whose user is not used.
func NewRedisCore(datasource string) (coreRepository, error) {
	u, err := url.Parse(datasource)
	if err != nil {
		return nil, err
	}
	return &redisCoreRepository{
		url: u,
	}, nil
}

func (r redisCoreRepository) conn() (redis.Conn, error) {
	opts := []redis.DialOption{}
	if u := r.url.User; u != nil {
		if pw, ok := u.Password(); ok {
			opts = append(opts, redis.DialPassword(pw))
		}
	}
	return redis.Dial("tcp", r.url.Host, opts...)
}

func (r redisCoreRepository) withConn(f func(redis.Conn) error) error {
	conn, err := r.conn()
	if err != nil {
		return err
	}

	defer conn.Close()

	return f(conn)
}

// AddUser implements coreRepository.AddUser.
func (r redisCoreRepository) AddUser(ctx context.Context, user prchecklist.GitHubUser) error {
	err := r.withConn(func(conn redis.Conn) error {
		buf, err := json.Marshal(user)
		if err != nil {
			return err
		}

		_, err = conn.Do("SET", redisKeyPrefixUser+strconv.FormatInt(int64(user.ID), 10), buf)
		return err
	})
	return errors.Wrap(err, "AddUser")
}

// GetUsers implements coreRepository.GetUser.
func (r redisCoreRepository) GetUsers(ctx context.Context, userIDs []int) (map[int]prchecklist.GitHubUser, error) {
	users := make(map[int]prchecklist.GitHubUser, len(userIDs))
	if len(userIDs) == 0 {
		return users, nil
	}

	err := r.withConn(func(conn redis.Conn) error {
		keys := make([]interface{}, len(userIDs))
		for i, id := range userIDs {
			keys[i] = redisKeyPrefixUser + strconv.FormatInt(int64(id), 10)
		}
		bufs, err := redis.ByteSlices(conn.Do("MGET", keys...))
		if err != nil {
			return err
		}
		for i, buf := range bufs {
			var user prchecklist.GitHubUser
			if err := json.Unmarshal(buf, &user); err != nil {
				return err
			}
			users[userIDs[i]] = user
		}

		return nil
	})

	return users, errors.Wrap(err, "GetUsers")
}

// GetChecks implements coreRepository.GetChecks.
func (r redisCoreRepository) GetChecks(ctx context.Context, clRef prchecklist.ChecklistRef) (prchecklist.Checks, error) {
	if err := clRef.Validate(); err != nil {
		return nil, err
	}

	var checks prchecklist.Checks

	err := r.withConn(func(conn redis.Conn) error {
		key := redisKeyPrefixCheck + clRef.String()
		buf, err := redis.Bytes(conn.Do("GET", key))
		if err == redis.ErrNil {
			return nil
		} else if err != nil {
			return err
		}

		return json.Unmarshal(buf, &checks)
	})

	return checks, errors.Wrap(err, "GetChecks")
}

// AddCheck implements coreRepository.AddCheck.
func (r redisCoreRepository) AddCheck(ctx context.Context, clRef prchecklist.ChecklistRef, key string, user prchecklist.GitHubUser) error {
	if err := clRef.Validate(); err != nil {
		return err
	}

	return r.withConn(func(conn redis.Conn) error {
		var checks prchecklist.Checks

		dbKey := redisKeyPrefixCheck + clRef.String()
		buf, err := redis.Bytes(conn.Do("GET", dbKey))
		if err == redis.ErrNil {
			checks = prchecklist.Checks{}
		} else if err != nil {
			return err
		} else {
			err := json.Unmarshal(buf, &checks)
			if err != nil {
				return err
			}
		}

		if checks.Add(key, user) == false {
			return nil
		}

		data, err := json.Marshal(&checks)
		if err != nil {
			return err
		}

		_, err = conn.Do("SET", dbKey, data)
		return err
	})
}

// RemoveCheck implements coreRepository.RemoveCheck.
func (r redisCoreRepository) RemoveCheck(ctx context.Context, clRef prchecklist.ChecklistRef, key string, user prchecklist.GitHubUser) error {
	if err := clRef.Validate(); err != nil {
		return err
	}

	return r.withConn(func(conn redis.Conn) error {
		var checks prchecklist.Checks

		dbKey := redisKeyPrefixCheck + clRef.String()
		buf, err := redis.Bytes(conn.Do("GET", dbKey))
		if err == redis.ErrNil {
			checks = prchecklist.Checks{}
		} else if err != nil {
			return err
		} else {
			err := json.Unmarshal(buf, &checks)
			if err != nil {
				return err
			}
		}

		if checks.Remove(key, user) == false {
			return nil
		}

		data, err := json.Marshal(&checks)
		if err != nil {
			return err
		}

		_, err = conn.Do("SET", dbKey, data)
		return err
	})
}

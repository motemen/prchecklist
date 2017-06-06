// +build ignore

package repository

import (
	"context"
	"log"

	"cloud.google.com/go/datastore"
	"github.com/pkg/errors"

	"github.com/motemen/prchecklist"
)

type datastoreRepository struct {
	client *datastore.Client
}

const (
	datastoreKindUser  = "User"
	datastoreKindCheck = "Check"
)

func init() {
	registerCoreRepositoryBuilder("datastore", NewDatastoreCore)
}

func NewDatastoreCore(projectID string) (coreRepository, error) {
	client, err := datastore.NewClient(context.Background(), projectID)
	return &datastoreRepository{
		client: client,
	}, err
}

func (r datastoreRepository) AddUser(ctx context.Context, user prchecklist.GitHubUser) error {
	key := datastore.IDKey(datastoreKindUser, int64(user.ID), nil)
	_, err := r.client.Put(ctx, key, &user)
	return err
}

func (r datastoreRepository) GetUsers(ctx context.Context, userIDs []int) (map[int]prchecklist.GitHubUser, error) {
	keys := make([]*datastore.Key, len(userIDs))
	for i, id := range userIDs {
		keys[i] = datastore.IDKey(datastoreKindUser, int64(id), nil)
	}

	users := make([]prchecklist.GitHubUser, len(userIDs))
	err := r.client.GetMulti(ctx, keys, users)
	if err != nil {
		return nil, errors.Wrap(err, "datastoreRepository.GetUsers")
	}

	result := map[int]prchecklist.GitHubUser{}
	for _, user := range users {
		result[user.ID] = user
	}

	return result, nil
}

func (r datastoreRepository) GetChecks(ctx context.Context, clRef prchecklist.ChecklistRef) (prchecklist.Checks, error) {
	var bridge datastoreChecksBridge
	key := datastore.NameKey(datastoreKindCheck, clRef.String(), nil)
	err := r.client.Get(ctx, key, &bridge)
	if err == datastore.ErrNoSuchEntity {
		err = nil
	}
	return bridge.checks, errors.WithStack(err)
}

func (r datastoreRepository) AddCheck(ctx context.Context, clRef prchecklist.ChecklistRef, key string, user prchecklist.GitHubUser) error {
	dbKey := datastore.NameKey(datastoreKindCheck, clRef.String(), nil)

	_, err := r.client.RunInTransaction(ctx, func(tx *datastore.Transaction) error {
		var bridge datastoreChecksBridge
		err := r.client.Get(ctx, dbKey, &bridge)
		if err != nil && err != datastore.ErrNoSuchEntity {
			return err
		}

		if bridge.checks == nil {
			bridge.checks = prchecklist.Checks{}
		}
		log.Printf("%#v", bridge)
		if bridge.checks.Add(key, user) == false {
			log.Printf("%#v", bridge)
			return nil
		}
		log.Printf("%#v", bridge)

		_, err = r.client.Put(ctx, dbKey, &bridge)
		return err
	})

	return errors.WithStack(err)
}

func (r datastoreRepository) RemoveCheck(ctx context.Context, clRef prchecklist.ChecklistRef, key string, user prchecklist.GitHubUser) error {
	dbKey := datastore.NameKey(datastoreKindCheck, clRef.String(), nil)

	_, err := r.client.RunInTransaction(ctx, func(tx *datastore.Transaction) error {
		var bridge datastoreChecksBridge
		err := r.client.Get(ctx, dbKey, &bridge.checks)
		if err != nil && err != datastore.ErrNoSuchEntity {
			return err
		}

		if bridge.checks.Remove(key, user) == false {
			return nil
		}

		_, err = r.client.Put(ctx, dbKey, &bridge)
		return err
	})

	return errors.WithStack(err)
}

type datastoreChecksBridge struct {
	checks prchecklist.Checks
}

func (b *datastoreChecksBridge) Load(props []datastore.Property) error {
	if b.checks == nil {
		b.checks = prchecklist.Checks{}
	}
	for _, p := range props {
		var ok bool
		ifaces, ok := p.Value.([]interface{})
		if !ok {
			return errors.Errorf("invalid type: %v", p.Value)
		}
		b.checks[p.Name] = interfaceSliceToIntSlice(ifaces)
	}
	return nil
}

func (b *datastoreChecksBridge) Save() ([]datastore.Property, error) {
	props := make([]datastore.Property, 0, len(b.checks))
	for key, value := range b.checks {
		props = append(props, datastore.Property{
			Name:  key,
			Value: intSliceToInterfaceSlice(value),
		})
	}
	return props, nil
}

func intSliceToInterfaceSlice(ints []int) []interface{} {
	ifaces := make([]interface{}, len(ints))
	for i, in := range ints {
		ifaces[i] = int64(in)
	}
	return ifaces
}

func interfaceSliceToIntSlice(ifaces []interface{}) []int {
	ints := make([]int, len(ifaces))
	for i, iface := range ifaces {
		switch v := iface.(type) {
		case int64:
			ints[i] = int(v)
		default:
			return nil
		}
	}
	return ints
}

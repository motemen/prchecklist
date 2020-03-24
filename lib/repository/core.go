package repository

import (
	"context"
	"strings"

	"github.com/motemen/prchecklist/v2"
	"github.com/pkg/errors"
)

type coreRepository interface {
	GetChecks(ctx context.Context, clRef prchecklist.ChecklistRef) (prchecklist.Checks, error)
	AddCheck(ctx context.Context, clRef prchecklist.ChecklistRef, key string, user prchecklist.GitHubUser) error
	RemoveCheck(ctx context.Context, clRef prchecklist.ChecklistRef, key string, user prchecklist.GitHubUser) error

	AddUser(ctx context.Context, user prchecklist.GitHubUser) error
	GetUsers(ctx context.Context, userIDs []int) (map[int]prchecklist.GitHubUser, error)
}

var registry = map[string]coreRepositoryBuilder{}

type coreRepositoryBuilder func(string) (coreRepository, error)

func registerCoreRepositoryBuilder(proto string, builder coreRepositoryBuilder) {
	registry[proto] = builder
}

// NewCore creates a coreRepository based on the value of datasource.
func NewCore(datasource string) (coreRepository, error) {
	p := strings.IndexByte(datasource, ':')
	if p == -1 {
		return nil, errors.Errorf("invalid datasource: %q", datasource)
	}

	proto := datasource[:p]
	builder, ok := registry[proto]
	if !ok {
		return nil, errors.Errorf("cannot handle datasource: %q", datasource)
	}

	return builder(datasource)
}

package repository

import (
	"context"
	"log"
	"os"
	"testing"

	"github.com/stretchr/testify/require"

	prchecklist "github.com/motemen/prchecklist/v2"
)

// https://cloud.google.com/datastore/docs/tools/datastore-emulator

func TestMain(m *testing.M) {
	// call flag.Parse() here if TestMain uses flags
	if os.Getenv("DATASTORE_EMULATOR_HOST") == "" {
		log.Println("to test lib/repository/datastore.go, follow the instruction at https://cloud.google.com/datastore/docs/tools/datastore-emulator")

		return
	}

	os.Exit(m.Run())
}

func TestDatastoreRepository_AddCheck(t *testing.T) {
	repo, err := NewDatastoreCore("datastore:")
	require.NoError(t, err)

	ctx := context.Background()
	err = repo.AddCheck(ctx, prchecklist.ChecklistRef{}, "1", prchecklist.GitHubUser{})
	require.NoError(t, err)
}

func TestDatastoreRepository_RemoveCheck(t *testing.T) {
	repo, err := NewDatastoreCore("datastore:")
	require.NoError(t, err)

	ctx := context.Background()
	err = repo.RemoveCheck(ctx, prchecklist.ChecklistRef{}, "1", prchecklist.GitHubUser{})
	require.NoError(t, err)
}

package repository

import (
	"log"
	"os"
	"testing"

	"github.com/stretchr/testify/require"
)

// https://cloud.google.com/datastore/docs/tools/datastore-emulator

func TestDatastoreRepository(t *testing.T) {
	if os.Getenv("DATASTORE_EMULATOR_HOST") == "" || os.Getenv("DATASTORE_PROJECT_ID") == "" {
		log.Println("to test lib/repository/datastore.go, set DATASTORE_EMULATOR_HOST and DATASTORE_PROJECT_ID; follow the instruction at https://cloud.google.com/datastore/docs/tools/datastore-emulator")

		t.SkipNow()
		return
	}

	repo, err := NewDatastoreCore("datastore:")
	require.NoError(t, err)

	testUsers(t, repo)
	testChecks(t, repo)
}

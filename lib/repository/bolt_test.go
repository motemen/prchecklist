package repository

import "testing"

import (
	"io/ioutil"
	"path/filepath"

	"github.com/stretchr/testify/require"
)

func TestBoltRepository(t *testing.T) {
	require := require.New(t)

	tempdir, err := ioutil.TempDir("", "")
	require.NoError(err)

	repo, err := NewBoltCore("bolt:" + filepath.Join(tempdir, "test.db"))
	require.NoError(err)

	testUsers(t, repo)
	testChecks(t, repo)
}

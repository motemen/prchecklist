package repository

import "testing"

import (
	"context"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/motemen/prchecklist/v2"
)

func testUsers(t *testing.T, repo coreRepository) {
	t.Helper()

	t.Run("Users", func(t *testing.T) {
		assert := assert.New(t)
		require := require.New(t)

		ctx := context.Background()

		u1 := prchecklist.GitHubUser{
			ID:    1,
			Login: "user1",
		}
		u2 := prchecklist.GitHubUser{
			ID:    2,
			Login: "user2",
		}

		_, err := repo.GetUsers(ctx, []int{1, 2})
		require.Error(err, "should be an error: GetUsers for nonexistent users")

		require.NoError(repo.AddUser(ctx, u1))
		require.NoError(repo.AddUser(ctx, u2))

		users, err := repo.GetUsers(ctx, []int{1, 2})
		require.NoError(err)

		assert.Equal(1, users[1].ID)
		assert.Equal(2, users[2].ID)
		assert.Equal("user1", users[1].Login)
	})
}

func testChecks(t *testing.T, repo coreRepository) {
	t.Helper()

	t.Run("Checks", func(t *testing.T) {
		assert := assert.New(t)
		require := require.New(t)

		ctx := context.Background()

		clRef := prchecklist.ChecklistRef{
			Owner:  "test",
			Repo:   "repo",
			Number: 1,
			Stage:  "default",
		}

		checks, err := repo.GetChecks(ctx, clRef)
		require.NoError(err)
		assert.Equal(0, len(checks))

		u1 := prchecklist.GitHubUser{
			ID:    1,
			Login: "user1",
		}
		u2 := prchecklist.GitHubUser{
			ID:    2,
			Login: "user2",
		}

		require.NoError(repo.AddCheck(ctx, clRef, "100", u1))

		checks, err = repo.GetChecks(ctx, clRef)
		require.NoError(err)

		assert.Equal(1, len(checks))
		assert.Equal([]int{u1.ID}, checks["100"])

		require.NoError(repo.AddCheck(ctx, clRef, "101", u1))
		require.NoError(repo.AddCheck(ctx, clRef, "101", u2))

		checks, err = repo.GetChecks(ctx, clRef)
		require.NoError(err)

		assert.Equal(2, len(checks))
		assert.Equal([]int{u1.ID, u2.ID}, checks["101"])

		require.NoError(repo.RemoveCheck(ctx, clRef, "101", u1))

		checks, err = repo.GetChecks(ctx, clRef)
		require.NoError(err)

		assert.Equal(2, len(checks))
		assert.Equal([]int{u2.ID}, checks["101"])
	})
}

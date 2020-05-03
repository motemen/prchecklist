package repository

import (
	"log"
	"os"
	"strings"
	"testing"

	"github.com/stretchr/testify/require"
)

// https://cloud.google.com/datastore/docs/tools/datastore-emulator

func TestRedisRepository(t *testing.T) {
	redisURL := os.Getenv("TEST_REDIS_URL")
	if !strings.HasPrefix(redisURL, "redis:") {
		log.Println("to test lib/repository/redis.go, set TEST_REDIS_URL")
		t.SkipNow()
		return
	}

	repo, err := NewRedisCore(redisURL)
	require.NoError(t, err)

	testUsers(t, repo)
	testChecks(t, repo)
}

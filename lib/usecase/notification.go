package usecase

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"

	"github.com/pkg/errors"

	"github.com/motemen/go-nuts/httputil"
	"github.com/motemen/prchecklist"
)

type eventType int

const (
	eventTypeInvalid eventType = iota
	eventTypeOnCheck
	eventTypeOnComplete
	eventTypeOnUserComplete
	eventTypeOnRemove
)

type notificationEvent interface {
	slackMessageText(ctx context.Context) string
	eventType() eventType
}

type removeCheckEvent struct {
	checklist *prchecklist.Checklist
	item      *prchecklist.ChecklistItem
	user      prchecklist.GitHubUser
}

func (e removeCheckEvent) slackMessageText(ctx context.Context) string {
	u := prchecklist.BuildURL(ctx, e.checklist.Path()).String()
	return fmt.Sprintf("[<%s|%s>] #%d %q check removed by %s", u, e.checklist, e.item.Number, e.item.Title, e.user.Login)
}

func (e removeCheckEvent) eventType() eventType {
	return eventTypeOnRemove
}

type addCheckEvent struct {
	checklist *prchecklist.Checklist
	item      *prchecklist.ChecklistItem
	user      prchecklist.GitHubUser
}

func (e addCheckEvent) slackMessageText(ctx context.Context) string {
	u := prchecklist.BuildURL(ctx, e.checklist.Path()).String()
	return fmt.Sprintf("[<%s|%s>] #%d %q checked by %s", u, e.checklist, e.item.Number, e.item.Title, e.user.Login)
}

func (e addCheckEvent) eventType() eventType { return eventTypeOnCheck }

type completeEvent struct {
	checklist *prchecklist.Checklist
}

func (e completeEvent) slackMessageText(ctx context.Context) string {
	u := prchecklist.BuildURL(ctx, e.checklist.Path()).String()
	return fmt.Sprintf("[<%s|%s>] Checklist completed! :tada:", u, e.checklist)
}

func (e completeEvent) eventType() eventType { return eventTypeOnComplete }

type userCompleteEvent struct {
	checklist *prchecklist.Checklist
	user      prchecklist.GitHubUser
}

func (e userCompleteEvent) slackMessageText(ctx context.Context) string {
	u := prchecklist.BuildURL(ctx, e.checklist.Path()).String()
	return fmt.Sprintf("[<%s|%s>] %s completed", u, e.checklist, e.user.Login)
}

func (e userCompleteEvent) eventType() eventType { return eventTypeOnComplete }

func (u Usecase) notifyEvent(ctx context.Context, checklist *prchecklist.Checklist, event notificationEvent) error {
	config := checklist.Config
	if config == nil {
		return nil
	}

	var chNames []string
	switch event.eventType() {
	case eventTypeOnRemove:
		chNames = config.Notification.Events.OnRemove
	case eventTypeOnCheck:
		chNames = config.Notification.Events.OnCheck
	case eventTypeOnUserComplete:
		chNames = config.Notification.Events.OnUserComplete
	case eventTypeOnComplete:
		chNames = config.Notification.Events.OnComplete
		lastCommitID := checklist.Commits[len(checklist.Commits)-1].Oid
		if err := u.setRepositoryCompletedStatusAs(ctx, checklist.Owner, checklist.Repo, lastCommitID, "success", checklist.Stage, prchecklist.BuildURL(ctx, checklist.Path()).String()); err != nil {
			log.Printf("Failed to SetRepositoryStatusAs: %s (%+v)", err, err)
		}
	default:
		return errors.Errorf("unknown event type: %v", event.eventType())
	}

	for _, name := range chNames {
		name := name
		ch, ok := config.Notification.Channels[name]
		if !ok {
			continue
		}

		go func() {
			payload, err := json.Marshal(&slackMessagePayload{
				Text: event.slackMessageText(ctx),
			})
			if err != nil {
				log.Printf("json.Marshal: %s", err)
				return
			}

			v := url.Values{"payload": {string(payload)}}

			_, err = httputil.Successful(http.PostForm(ch.URL, v))
			if err != nil {
				log.Printf("posting Slack webhook: %s", err)
				return
			}
		}()
	}

	return nil
}

type slackMessagePayload struct {
	Text string `json:"text"`
}

package prchecklist

import "testing"

func TestChecksKeyFeatureNum(t *testing.T) {
}

func makeStubChecklist() Checklist {
	return Checklist{
		PullRequest: &PullRequest{
			Number: 1,
			Owner:  "motemen",
			Repo:   "test",
		},
		Items: []*ChecklistItem{
			{
				PullRequest: &PullRequest{Number: 2, User: GitHubUserSimple{Login: "foo"}},
				CheckedBy:   []GitHubUser{},
			},
			{
				PullRequest: &PullRequest{Number: 3, User: GitHubUserSimple{Login: "foo"}},
				CheckedBy:   []GitHubUser{},
			},
			{
				PullRequest: &PullRequest{Number: 4, User: GitHubUserSimple{Login: "bar"}},
				CheckedBy:   []GitHubUser{},
			},
		},
	}
}

func TestChecklist_Completed(t *testing.T) {
	checklist := makeStubChecklist()

	if expected, got := false, checklist.Completed(); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}

	checklist.Items[0].CheckedBy = append(checklist.Items[0].CheckedBy, GitHubUser{})
	if expected, got := false, checklist.Completed(); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}

	checklist.Items[0].CheckedBy = append(checklist.Items[0].CheckedBy, GitHubUser{})
	if expected, got := false, checklist.Completed(); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}

	checklist.Items[1].CheckedBy = append(checklist.Items[1].CheckedBy, GitHubUser{})
	checklist.Items[2].CheckedBy = append(checklist.Items[1].CheckedBy, GitHubUser{})
	if expected, got := true, checklist.Completed(); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}
}

func TestChecklist_CompletedChecksOfUser(t *testing.T) {
	checklist := makeStubChecklist()

	if expected, got := false, checklist.CompletedChecksOfUser(GitHubUserSimple{Login: "foo"}); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}

	checklist.Items[0].CheckedBy = append(checklist.Items[0].CheckedBy, GitHubUser{})
	if expected, got := false, checklist.CompletedChecksOfUser(GitHubUserSimple{Login: "foo"}); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}

	checklist.Items[1].CheckedBy = append(checklist.Items[1].CheckedBy, GitHubUser{})
	if expected, got := true, checklist.CompletedChecksOfUser(GitHubUserSimple{Login: "foo"}); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}
	if expected, got := false, checklist.CompletedChecksOfUser(GitHubUserSimple{Login: "bar"}); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}

}

func TestChecklist_Completed_IgnoresSkippedItems(t *testing.T) {
	checklist := makeStubChecklist()
	checklist.Items[0].CheckedBy = append(checklist.Items[0].CheckedBy, GitHubUser{})
	checklist.Items[1].Skipped = true
	checklist.Items[2].Skipped = true

	if expected, got := true, checklist.Completed(); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}
}

func TestChecklist_CompletedChecksOfUser_IgnoresSkippedItems(t *testing.T) {
	checklist := makeStubChecklist()
	checklist.Items[0].CheckedBy = append(checklist.Items[0].CheckedBy, GitHubUser{})
	checklist.Items[1].Skipped = true

	if expected, got := true, checklist.CompletedChecksOfUser(GitHubUserSimple{Login: "foo"}); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}
	if expected, got := false, checklist.CompletedChecksOfUser(GitHubUserSimple{Login: "bar"}); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}
}

func TestChecklistConfig_ShouldSkip(t *testing.T) {
	config := &ChecklistConfig{}
	config.Skip.Labels = []SkipLabel{
		{Name: "no-qa"},
		{Name: "no-production-check", Stages: []string{"production"}},
	}

	tests := []struct {
		name     string
		config   *ChecklistConfig
		labels   []string
		stage    string
		expected bool
	}{
		{"label without stages applies to qa", config, []string{"no-qa"}, "qa", true},
		{"label without stages applies to production", config, []string{"no-qa"}, "production", true},
		{"label with stages applies to listed stage", config, []string{"no-production-check"}, "production", true},
		{"label with stages does not apply to unlisted stage", config, []string{"no-production-check"}, "qa", false},
		{"unconfigured label", config, []string{"bug"}, "qa", false},
		{"no labels", config, nil, "qa", false},
		{"nil config", nil, []string{"no-qa"}, "qa", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.config.ShouldSkip(tt.labels, tt.stage); got != tt.expected {
				t.Errorf("expected %v but got %v", tt.expected, got)
			}
		})
	}
}

func TestChecklist_Item(t *testing.T) {
	checklist := makeStubChecklist()

	if item := checklist.Item(1); item != nil {
		t.Errorf("expected nil but got %v", item)
	}

	if item := checklist.Item(2); item == nil {
		t.Errorf("expected an item but got %v", item)
	}

	if item := checklist.Item(100); item != nil {
		t.Errorf("expected an item but got %v", item)
	}
}

func TestChecklist_Path(t *testing.T) {
	checklist := makeStubChecklist()

	if expected, got := "/motemen/test/pull/1", checklist.Path(); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}
}

func TestChecklist_String(t *testing.T) {
}
func TestChecks_Add(t *testing.T) {
}
func TestChecks_Remove(t *testing.T) {
}
func TestChecklistRef_String(t *testing.T) {
}
func TestChecklistRef_Validate(t *testing.T) {
}
func TestGitHubUser_HTTPClient(t *testing.T) {
}

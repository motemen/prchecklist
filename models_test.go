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
				PullRequest: &PullRequest{Number: 2},
				CheckedBy:   []GitHubUser{},
			},
			{
				PullRequest: &PullRequest{Number: 3},
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
	if expected, got := true, checklist.Completed(); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
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

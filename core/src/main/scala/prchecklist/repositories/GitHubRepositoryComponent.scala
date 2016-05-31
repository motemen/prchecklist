package prchecklist.repositories

import prchecklist.infrastructure.{RedisComponent, GitHubHttpClientComponent}
import prchecklist.models.{GitHubTypes, ModelsComponent}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import scala.language.postfixOps

trait GitHubRepositoryComponent {
  self: GitHubHttpClientComponent with RedisComponent with ModelsComponent =>

  def newGitHubRepository(accessible: GitHubAccessible): GitHubRepository = new GitHubRepository {
    override val client = githubHttpClient(accessible.accessToken)
  }

  trait GitHubRepository {
    def client: GitHubHttpClient

    // https://developer.github.com/v3/repos/#get
    def getRepo(owner: String, name: String): Future[GitHubTypes.Repo] = {
      client.getJson[GitHubTypes.Repo](s"/repos/$owner/$name")
    }

    // https://developer.github.com/v3/issues/comments/#create-a-comment
    def addIssueComment(repoFullName: String, issueNumber: Int, body: String): Future[Unit] = {
      client.postJson(
        s"/repos/$repoFullName/issues/$issueNumber/comments",
        GitHubTypes.IssueComment(body)
      )
    }

    // https://developer.github.com/v3/repos/statuses/#create-a-status
    def setCommitStatus(repoFullName: String, sha: String, status: GitHubTypes.CommitStatus): Future[Unit] = {
      client.postJson(
        s"/repos/$repoFullName/statuses/$sha",
        status
      )
    }

    def getPullRequest(repo: Repo, number: Int): Future[GitHubTypes.PullRequest] = {
      // TODO use cache from the caller side
      redis.getOrUpdate(s"pullSimple:${repo.fullName}:$number", 30 seconds) {
        client.getJson[GitHubTypes.PullRequest](s"/repos/${repo.fullName}/pulls/$number").map { (_, true) }
      }
    }

    def getPullRequestWithCommits(repo: Repo, number: Int): Future[GitHubTypes.PullRequestWithCommits] = {
      // TODO use cache from the caller side
      redis.getOrUpdate(s"pull:${repo.fullName}:$number", 30 seconds) {
        for {
          pr <- client.getJson[GitHubTypes.PullRequest](s"/repos/${repo.fullName}/pulls/$number")
          commits <- getPullRequestCommitsPaged(repo, pr)
        } yield (GitHubTypes.PullRequestWithCommits(pr, commits), true)
      }
    }

    // https://developer.github.com/v3/pulls/#list-commits-on-a-pull-request
    // https://developer.github.com/v3/repos/commits/#list-commits-on-a-repository
    def getPullRequestCommitsPaged(repo: Repo, pullRequest: GitHubTypes.PullRequest, allCommits: List[GitHubTypes.Commit] = List(), page: Int = 1): Future[List[GitHubTypes.Commit]] = {
      // The document says "Note: The response includes a maximum of 250 commits"
      // but apparently it returns only 100 commits at maximum
      val commitsPerPage = 100

      if (page > 10) {
        throw new IllegalArgumentException(s"page too far: $page")
      }

      client.getJson[List[GitHubTypes.Commit]](s"/repos/${repo.fullName}/commits?sha=${pullRequest.head.sha}&per_page=${commitsPerPage}&page=${page}").flatMap {
        pageCommits =>
          val commits = (allCommits ++ pageCommits).take(pullRequest.commits)
          if (commits.length < pullRequest.commits) {
            getPullRequestCommitsPaged(repo, pullRequest, commits, page + 1)
          } else {
            Future { commits }
          }
      }
    }

    def listReleasePullRequests(repo: Repo): Future[List[GitHubTypes.PullRequestRef]] = listReleasePullRequests(repo.owner, repo.name)

    def listReleasePullRequests(repoOwner: String, repoName: String): Future[List[GitHubTypes.PullRequestRef]] = {
      client.getJson[List[GitHubTypes.PullRequestRef]](s"/repos/${repoOwner}/${repoName}/pulls?base=master&state=all&per_page=20")
    }

    // https://developer.github.com/v3/repos/contents/#get-contents
    // TODO: be Future[Option[String]]
    def getFileContent(repo: Repo, path: String, ref: String = "master"): Future[Option[String]] = {
      client.getJson[GitHubTypes.Content](s"/repos/${repo.fullName}/contents/$path?ref=$ref")
        .map {
          content =>
            Some(content.fileContent.get)
        }
        .recover {
          case e: FailedHttpResponseException if e.code == 404 => None
        }
    }

    // https://developer.github.com/v3/activity/starring/#list-repositories-being-starred
    def listStarredRepos(): Future[List[GitHubTypes.Repo]] = {
      client.getJson[List[GitHubTypes.Repo]]("/user/starred?sort=updated")
    }
  }

}

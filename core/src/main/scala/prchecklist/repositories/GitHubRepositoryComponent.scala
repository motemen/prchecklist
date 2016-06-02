package prchecklist.repositories

import prchecklist.infrastructure.{GitHubHttpClientComponent, RedisComponent}
import prchecklist.models.GitHubTypes
import prchecklist.models.ModelsComponent

import org.slf4j.LoggerFactory

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

    def logger = LoggerFactory.getLogger(getClass)

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
          commits <- if (pr.commits <= 250) { getPullRequestCommits(repo, pr) } else { getPullRequestCommitsByLog(repo, pr) }
        } yield (GitHubTypes.PullRequestWithCommits(pr, commits), true)
      }
    }

    /**
      * Retrieves all commits from the pullRequest.
      *
      * @param repo
      * @param pullRequest
      * @return
      * @see https://developer.github.com/v3/pulls/#list-commits-on-a-pull-request
      */
    def getPullRequestCommits(repo: Repo, pullRequest: GitHubTypes.PullRequest): Future[List[GitHubTypes.Commit]] = {
      require(pullRequest.commits <= 250)

      val maxPerPage = 100

      def getPullRequestCommitsPage(page: Int, perPage: Int): Future[List[GitHubTypes.Commit]] = {
        client.getJson[List[GitHubTypes.Commit]](s"/repos/${repo.fullName}/pulls/${pullRequest.number}/commits?per_page=$perPage&page=$page")
      }

      // per_page param for each pages.
      // For a PR of 150 commits, return (100, 50).
      // For a PR of 200 commits, return (100, 100, 0).
      val perPages: List[Int] = {
        val lastPage = pullRequest.commits / maxPerPage
        (1 to lastPage).map { _ => maxPerPage } :+ (pullRequest.commits % maxPerPage)
      }.filter(_ != 0).toList

      Future.sequence {
        perPages.zipWithIndex.map {
          case (perPage, page0) =>
            getPullRequestCommitsPage(page0+1, perPage)
        }
      }.map(_.flatten)
    }

    /**
      * Due to the GitHub API restriction, commits of pull requests with more than 250 commits
      * cannot be obtained directly, so we must elaborate to build the commits list using
      * commit list API.
      * @param repo
      * @param pullRequest
      * @return
      * @see https://developer.github.com/v3/repos/commits/#list-commits-on-a-repository
      */
    def getPullRequestCommitsByLog(repo: Repo, pullRequest: GitHubTypes.PullRequest): Future[List[GitHubTypes.Commit]] = {
      val maxPerPage = 100

      def getCommitsReachableFrom(startPoint: GitHubTypes.CommitRef, page: Int) =
        client.getJson[List[GitHubTypes.Commit]](s"/repos/${repo.fullName}/commits?sha=${startPoint.sha}&per_page=$maxPerPage&page=$page")

      /*
       * The strategy is:
       * - Go down the history of the head of the PR
       * - While filtering out the commits that are reachable from the base of the PR
       */
      def getCommitsUntilCommonAncestor(pages: (Int, Int) = (1, 1), allHeadCommits: List[GitHubTypes.Commit] = List(), allBaseCommits: List[GitHubTypes.Commit] = List()): Future[List[GitHubTypes.Commit]] = {
        val (pageHead, pageBase) = pages

        require(pageHead <= 20)

        val futGetReachableFromBase = if (allBaseCommits.length >= allHeadCommits.length + maxPerPage) {
          // The commit is reverse chronogically ordered.
          // We have retrieved enough commits reachable from base if
          // they are more than maxPerPage (=100) commits than commits reachable from head.
          Future.successful { (List(), 0) }
        } else {
          getCommitsReachableFrom(pullRequest.base, pageBase).map { (_, 1) }
        }

        (getCommitsReachableFrom(pullRequest.head, pageHead) zip futGetReachableFromBase).flatMap {
          case (headCommits, (baseCommits, deltaPageBase)) =>
            val commits = allHeadCommits ++ headCommits.filterNot {
              commit =>
                (allBaseCommits ++ baseCommits).exists { _.sha == commit.sha }
            }

            if (commits.length < pullRequest.commits) {
              getCommitsUntilCommonAncestor((pageHead+1, pageBase+deltaPageBase), commits, allBaseCommits ++ baseCommits)
            } else {
              Future.successful { commits }
            }
        }
      }

      getCommitsUntilCommonAncestor()
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

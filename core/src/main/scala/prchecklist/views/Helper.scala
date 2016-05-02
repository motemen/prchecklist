package prchecklist.views

import org.pegdown.{Extensions, PegDownProcessor}
import prchecklist.models._

object Helper {
  val githubFlavouredMarkdownExtensions = {
    import Extensions._
    HARDWRAPS | AUTOLINKS | FENCED_CODE_BLOCKS | ATXHEADERSPACE /* | TASKLISTITEMS */ // no TASKLISTITEMS as its confusing
  }

  lazy val pegdown = new PegDownProcessor(githubFlavouredMarkdownExtensions)

  def formatMarkdown(source: String): String = {
    pegdown.markdownToHtml(source)
  }

  def checklistPath(checklist: TypesComponent#ReleaseChecklist): String = {
    s"/${checklist.repo.fullName}/pull/${checklist.pullRequest.number}" + (checklist.stage match {
      case "" => ""
      case stage => s"/$stage"
    })
  }

}

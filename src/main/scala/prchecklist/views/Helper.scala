package prchecklist.views

import org.pegdown.plugins.PegDownPlugins
import org.pegdown.{ Extensions, Parser, PegDownProcessor }

object Helper {
  val githubFlavouredMarkdownExtensions = {
    import Extensions._
    HARDWRAPS | AUTOLINKS | FENCED_CODE_BLOCKS | ATXHEADERSPACE /* | TASKLISTITEMS */ // no TASKLISTITEMS as its confusing
  }

  lazy val pegdown = new PegDownProcessor(githubFlavouredMarkdownExtensions)

  def formatMarkdown(source: String): String = {
    pegdown.markdownToHtml(source)
  }
}

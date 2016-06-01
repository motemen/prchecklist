package prchecklist
package services

import java.net.URI

trait ReverseRouterComponent {
  this: models.ModelsComponent =>

  def reverseRouter: ReverseRouter

  trait ReverseRouter {
    def scheme: String
    def authority: String

    def checklistUri(checklist: ReleaseChecklist): URI = {
      val suffix = if (checklist.stage.nonEmpty) s"/${checklist.stage}" else ""
      new URI(scheme, authority, s"/${checklist.repo.fullName}/pull/${checklist.pullRequest.number}$suffix", null, null)
    }
  }
}

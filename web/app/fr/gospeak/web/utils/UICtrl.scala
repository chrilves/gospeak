package fr.gospeak.web.utils

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.user
import play.api.i18n.I18nSupport
import play.api.mvc._

abstract class UICtrl(cc: ControllerComponents, silhouette: Silhouette[CookieEnv]) extends AbstractController(cc) with I18nSupport {
  protected def talkNotFound(talk: Talk.Slug): Result =
    Redirect(user.talks.routes.TalkCtrl.list()).flashing("warning" -> s"Unable to find talk with slug '${talk.value}'")

  protected def cfpNotFound(talk: Talk.Slug, cfp: Cfp.Slug): Result =
    Redirect(user.talks.cfps.routes.CfpCtrl.list(talk)).flashing("warning" -> s"Unable to find CFP with slug '${cfp.value}'")

  protected def cfpNotFound(group: Group.Slug, cfp: Cfp.Slug): Result =
    Redirect(user.groups.cfps.routes.CfpCtrl.list(group)).flashing("warning" -> s"Unable to find CFP with slug '${cfp.value}'")

  protected def proposalNotFound(talk: Talk.Slug, proposal: Proposal.Id): Result =
    Redirect(user.talks.proposals.routes.ProposalCtrl.list(talk)).flashing("warning" -> s"Unable to find proposal with id '${proposal.value}'")

  protected def groupNotFound(group: Group.Slug): Result =
    Redirect(user.groups.routes.GroupCtrl.list()).flashing("warning" -> s"Unable to find group with slug '${group.value}'")

  protected def eventNotFound(group: Group.Slug, event: Event.Slug): Result =
    Redirect(user.groups.events.routes.EventCtrl.list(group)).flashing("warning" -> s"Unable to find event with slug '${event.value}'")

  protected def proposalNotFound(group: Group.Slug, proposal: Proposal.Id): Result =
    Redirect(user.groups.proposals.routes.ProposalCtrl.list(group)).flashing("warning" -> s"Unable to find proposal with id '${proposal.value}'")

  protected def notFound()(implicit req: Request[AnyContent]): Result =
    NotFound("Not found :(")
}

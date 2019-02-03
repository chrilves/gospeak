package fr.gospeak.web.user.groups.events

import java.time.Instant

import fr.gospeak.core.domain.Event
import fr.gospeak.web.utils.Mappings._
import play.api.data.Forms._
import play.api.data._

object EventForms {

  final case class Create(name: Event.Name, slug: Event.Slug, start: Instant)

  val create: Form[Create] = Form(mapping(
    "name" -> eventName,
    "slug" -> eventSlug,
    "start" -> instant
  )(Create.apply)(Create.unapply))
}

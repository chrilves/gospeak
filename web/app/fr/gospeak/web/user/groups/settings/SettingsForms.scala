package fr.gospeak.web.user.groups.settings

import fr.gospeak.core.domain.Cfp
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object SettingsForms {
  val cfpCreate: Form[Cfp.Data] = Form(mapping(
    "slug" -> cfpSlug,
    "name" -> cfpName,
    "start" -> optional(localDateTime),
    "end" -> optional(localDateTime),
    "description" -> markdown
  )(Cfp.Data.apply)(Cfp.Data.unapply))
}

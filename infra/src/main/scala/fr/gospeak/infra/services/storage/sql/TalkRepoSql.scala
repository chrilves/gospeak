package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.core.services.TalkRepo
import fr.gospeak.infra.services.storage.sql.TalkRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video, _}

class TalkRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with TalkRepo {
  override def create(user: User.Id, data: Talk.Data, now: Instant): IO[Talk] =
    find(user, data.slug).flatMap {
      case None => run(insert, Talk(data, Talk.Status.Draft, NonEmptyList.one(user), Info(user, now)))
      case _ => IO.raiseError(CustomException(s"You already have a talk with slug ${data.slug}"))
    }

  override def edit(user: User.Id, slug: Talk.Slug)(data: Talk.Data, now: Instant): IO[Done] = {
    if (data.slug != slug) {
      // FIXME: should also check for other speakers !!!
      find(user, data.slug).flatMap {
        case None => run(update(user, slug)(data, now))
        case _ => IO.raiseError(CustomException(s"You already have a talk with slug ${data.slug}"))
      }
    } else {
      run(update(user, slug)(data, now))
    }
  }

  override def editStatus(user: User.Id, slug: Talk.Slug)(status: Talk.Status): IO[Done] = run(updateStatus(user, slug)(status))

  override def editSlides(user: User.Id, slug: Talk.Slug)(slides: Slides, now: Instant): IO[Done] = run(updateSlides(user, slug)(slides, now))

  override def editVideo(user: User.Id, slug: Talk.Slug)(video: Video, now: Instant): IO[Done] = run(updateVideo(user, slug)(video, now))

  override def find(user: User.Id, slug: Talk.Slug): IO[Option[Talk]] = run(selectOne(user, slug).option)

  override def list(user: User.Id, params: Page.Params): IO[Page[Talk]] = run(Queries.selectPage(selectPage(user, _), params))
}

object TalkRepoSql {
  private val _ = talkIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "talks"
  private val fields = Seq("id", "slug", "title", "duration", "status", "description", "speakers", "slides", "video", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "title", "description")
  private val defaultSort = Page.OrderBy("title")

  private def values(e: Talk): Fragment =
    fr0"${e.id}, ${e.slug}, ${e.title}, ${e.duration}, ${e.status}, ${e.description}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: Talk): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def update(user: User.Id, slug: Talk.Slug)(data: Talk.Data, now: Instant): doobie.Update0 = {
    val fields = fr0"slug=${data.slug}, title=${data.title}, duration=${data.duration}, description=${data.description}, slides=${data.slides}, video=${data.video}, updated=$now, updated_by=$user"
    buildUpdate(tableFr, fields, where(user, slug)).update
  }

  private[sql] def updateStatus(user: User.Id, slug: Talk.Slug)(status: Talk.Status): doobie.Update0 =
    buildUpdate(tableFr, fr0"status=$status", where(user, slug)).update

  private[sql] def updateSlides(user: User.Id, slug: Talk.Slug)(slides: Slides, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"slides=$slides, updated=$now, updated_by=$user", where(user, slug)).update

  private[sql] def updateVideo(user: User.Id, slug: Talk.Slug)(video: Video, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"video=$video, updated=$now, updated_by=$user", where(user, slug)).update

  private[sql] def selectOne(user: User.Id, slug: Talk.Slug): doobie.Query0[Talk] =
    buildSelect(tableFr, fieldsFr, where(user, slug)).query[Talk]

  private[sql] def selectPage(user: User.Id, params: Page.Params): (doobie.Query0[Talk], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE speakers LIKE ${"%" + user.value + "%"}"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Talk], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private def where(user: User.Id, slug: Talk.Slug): Fragment =
    fr0"WHERE speakers LIKE ${"%" + user.value + "%"} AND slug=$slug"
}

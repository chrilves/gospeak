package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain._
import fr.gospeak.core.services.CfpRepo
import fr.gospeak.infra.services.storage.sql.CfpRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments.{buildInsert, buildSelect, buildUpdate, paginate}
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page}

class CfpRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with CfpRepo {
  override def create(group: Group.Id, data: Cfp.Data, by: User.Id, now: Instant): IO[Cfp] =
    run(insert, Cfp(group, data, Info(by, now)))

  override def edit(group: Group.Id, cfp: Cfp.Slug)(data: Cfp.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != cfp) {
      find(group, data.slug).flatMap {
        case None => run(update(group, cfp)(data, by, now))
        case _ => IO.raiseError(CustomException(s"You already have a cfp with slug ${data.slug}"))
      }
    } else {
      run(update(group, cfp)(data, by, now))
    }
  }

  override def find(id: Cfp.Id): IO[Option[Cfp]] = run(selectOne(id).option)

  override def find(slug: Cfp.Slug): IO[Option[Cfp]] = run(selectOne(slug).option)

  override def find(group: Group.Id, slug: Cfp.Slug): IO[Option[Cfp]] = run(selectOne(slug).option)

  override def find(id: Event.Id): IO[Option[Cfp]] = run(selectOne(id).option)

  override def find(id: Group.Id): IO[Option[Cfp]] = run(selectOne(id).option)

  override def list(group: Group.Id, params: Page.Params): IO[Page[Cfp]] = run(Queries.selectPage(selectPage(group, _), params))

  override def listAvailable(talk: Talk.Id, params: Page.Params): IO[Page[Cfp]] = run(Queries.selectPage(selectPage(talk, _), params))
}

object CfpRepoSql {
  private val _ = cfpIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "cfps"
  private[sql] val fields = Seq("id", "group_id", "slug", "name", "start", "end", "description", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "name", "description")
  private val defaultSort = Page.OrderBy("name")

  private def values(e: Cfp): Fragment =
    fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.start}, ${e.end}, ${e.description}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: Cfp): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def update(group: Group.Id, slug: Cfp.Slug)(data: Cfp.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, start=${data.start}, end=${data.end}, description=${data.description}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, slug)).update
  }

  private[sql] def selectOne(id: Cfp.Id): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE id=$id").query[Cfp]

  private[sql] def selectOne(slug: Cfp.Slug): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE slug=$slug").query[Cfp]

  private[sql] def selectOne(group: Group.Id, slug: Cfp.Slug): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, where(group, slug)).query[Cfp]

  private[sql] def selectOne(id: Event.Id): doobie.Query0[Cfp] = {
    val selectedTables = Fragment.const0(s"$table c INNER JOIN ${EventRepoSql.table} e ON e.cfp_id=c.id")
    val selectedFields = Fragment.const0(fields.map("c." + _).mkString(", "))
    buildSelect(selectedTables, selectedFields, fr0"WHERE e.id=$id").query[Cfp]
  }

  private[sql] def selectOne(id: Group.Id): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE group_id=$id").query[Cfp]

  private[sql] def selectPage(group: Group.Id, params: Page.Params): (doobie.Query0[Cfp], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE group_id=$group"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Cfp], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPage(talk: Talk.Id, params: Page.Params): (doobie.Query0[Cfp], doobie.Query0[Long]) = {
    val talkCfps = buildSelect(ProposalRepoSql.tableFr, fr0"cfp_id", fr0"WHERE talk_id = $talk")
    val where = fr0"WHERE id NOT IN (" ++ talkCfps ++ fr0")"
    val page = paginate(params, searchFields, defaultSort, Some(where))
    (buildSelect(tableFr, fieldsFr, page.all).query[Cfp], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private def where(group: Group.Id, slug: Cfp.Slug): Fragment =
    fr0"WHERE group_id=$group AND slug=$slug"
}
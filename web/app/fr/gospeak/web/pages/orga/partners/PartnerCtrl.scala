package fr.gospeak.web.pages.orga.partners

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.domain.{Contact, Group, Partner, Venue}
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.AppConf
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.exceptions.DuplicateEmailException
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.partners.PartnerCtrl._
import fr.gospeak.web.utils.{OrgaReq, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

import scala.util.control.NonFatal

class PartnerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf,
                  userRepo: OrgaUserRepo,
                  val groupRepo: OrgaGroupRepo,
                  eventRepo: OrgaEventRepo,
                  partnerRepo: OrgaPartnerRepo,
                  contactRepo: ContactRepo,
                  venueRepo: OrgaVenueRepo,
                  sponsorPackRepo: OrgaSponsorPackRepo,
                  sponsorRepo: OrgaSponsorRepo,
                  groupSettingsRepo: GroupSettingsRepo) extends UICtrl(cc, silhouette, conf) with UICtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    partnerRepo.listFull(params).map(partners => Ok(html.list(partners)(listBreadcrumb)))
  }

  def create(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    createView(group, PartnerForms.create)
  }

  def doCreate(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    PartnerForms.create.bindFromRequest.fold(
      formWithErrors => createView(group, formWithErrors),
      data => partnerRepo.create(data).map(partnerElt => Redirect(routes.PartnerCtrl.detail(group, partnerElt.slug)))
    )
  }

  private def createView(group: Group.Slug, form: Form[Partner.Data])(implicit req: OrgaReq[AnyContent]): IO[Result] = {
    val b = listBreadcrumb.add("New" -> routes.PartnerCtrl.create(group))
    IO.pure(Ok(html.create(form)(b)))
  }

  def detail(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      contacts <- OptionT.liftF(contactRepo.list(partnerElt.id))
      venues <- OptionT.liftF(venueRepo.listAllFull(partnerElt.id))
      packs <- OptionT.liftF(sponsorPackRepo.listActives)
      sponsors <- OptionT.liftF(sponsorRepo.listAllFull(partnerElt.id))
      events <- OptionT.liftF(eventRepo.list(partnerElt.id))
      users <- OptionT.liftF(userRepo.list((partnerElt.users ++ venues.flatMap(_.users)).distinct))
      b = breadcrumb(partnerElt)
      res = Ok(html.detail(partnerElt, venues, contacts, users, sponsors, packs, events)(b))
    } yield res).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }

  def edit(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    editView(group, partner, PartnerForms.create)
  }

  def doEdit(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    PartnerForms.create.bindFromRequest.fold(
      formWithErrors => editView(group, partner, formWithErrors),
      data => for {
        partnerOpt <- partnerRepo.find(data.slug)
        res <- partnerOpt match {
          case Some(duplicate) if data.slug != partner =>
            editView(group, partner, PartnerForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by partner: ${duplicate.name.value}"))
          case _ =>
            partnerRepo.edit(partner, data).map { _ => Redirect(routes.PartnerCtrl.detail(group, data.slug)) }
        }
      } yield res
    )
  }

  private def editView(group: Group.Slug, partner: Partner.Slug, form: Form[Partner.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      filledForm = if (form.hasErrors) form else form.fill(partnerElt.data)
      b = breadcrumb(partnerElt).add("Edit" -> routes.PartnerCtrl.edit(group, partner))
    } yield Ok(html.edit(partnerElt, filledForm)(b))).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }

  def doRemove(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    partnerRepo.remove(partner).map(_ => Redirect(routes.PartnerCtrl.list(group)))
  }

  /* VENUE */

  def createVenue(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    createVenueView(group, partner, PartnerForms.createVenue)
  }

  def doCreateVenue(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    PartnerForms.createVenue.bindFromRequest.fold(
      formWithErrors => createVenueView(group, partner, formWithErrors),
      data => partnerRepo.find(partner).flatMap {
        case Some(partnerElt) => venueRepo.create(partnerElt.id, data).map(_ => Redirect(routes.PartnerCtrl.detail(group, partner)))
        case None => IO.pure(Redirect(routes.PartnerCtrl.detail(group, partner)).flashing("error" -> s"Partner '${partner.value}' not found"))
      }
    )
  }

  private def createVenueView(group: Group.Slug, partner: Partner.Slug, form: Form[Venue.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
      b = venueListBreadcrumb(partnerElt).add("New" -> routes.PartnerCtrl.createVenue(group, partner))
    } yield Ok(html.createVenue(form, partnerElt, meetupAccount.isDefined)(b))).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }

  def editVenue(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    editVenueView(group, partner, venue, PartnerForms.createVenue)
  }

  def doEditVenue(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    PartnerForms.createVenue.bindFromRequest.fold(
      formWithErrors => editVenueView(group, partner, venue, formWithErrors),
      data => venueRepo.edit(venue, data).map(_ => Redirect(routes.PartnerCtrl.detail(group, partner)))
    )
  }

  private def editVenueView(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id, form: Form[Venue.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      venueElt <- OptionT(venueRepo.findFull(venue))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
      filledForm = if (form.hasErrors) form else form.fill(venueElt.data)
      b = venueBreadcrumb(venueElt).add("Edit" -> routes.PartnerCtrl.editVenue(group, partner, venue))
    } yield Ok(html.editVenue(filledForm, venueElt, meetupAccount.isDefined)(b))).value.map(_.getOrElse(venueNotFound(group, partner, venue)))
  }

  def doRemoveVenue(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    venueRepo.remove(venue).map(_ => Redirect(routes.PartnerCtrl.detail(group, partner)))
  }

  /* CONTACT */

  def createContact(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    createContactView(group, partner, PartnerForms.createContact)
  }

  def doCreateContact(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    PartnerForms.createContact.bindFromRequest.fold(
      formWithErrors => createContactView(group, partner, formWithErrors),
      data => (for {
        partnerElt <- OptionT(partnerRepo.find(partner))
        exists <- OptionT.liftF(contactRepo.exists(partnerElt.id, data.email))
        _ <- OptionT.liftF((!exists).toIO(DuplicateEmailException(data.email)))
        _ <- OptionT.liftF(contactRepo.create(data))
      } yield Redirect(routes.PartnerCtrl.detail(group, partner))).value.map(_.getOrElse(partnerNotFound(group, partner)))
        .recoverWith {
          case _: DuplicateEmailException => createContactView(group, partner, PartnerForms.createContact.bindFromRequest().withError("email", "Email already exists"))
          case NonFatal(e) => createContactView(group, partner, PartnerForms.createContact.bindFromRequest().withGlobalError(e.getMessage))
        }
    )
  }

  private def createContactView(group: Group.Slug, partner: Partner.Slug, form: Form[Contact.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      b = contactListBreadcrumb(partnerElt).add("New" -> routes.PartnerCtrl.createContact(group, partner))
      call = routes.PartnerCtrl.doCreateContact(group, partner)
    } yield Ok(html.createContact(partnerElt, form, call)(b))).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }

  def editContact(group: Group.Slug, partner: Partner.Slug, contact: Contact.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    editContactView(group, partner, contact, PartnerForms.createContact)
  }

  def doEditContact(group: Group.Slug, partner: Partner.Slug, contact: Contact.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    PartnerForms.createContact.bindFromRequest.fold(
      formWithErrors => editContactView(group, partner, contact, formWithErrors),
      data => (for {
        partnerElt <- OptionT(partnerRepo.find(partner))
        contactElt <- OptionT(contactRepo.find(contact))
        exists <- OptionT.liftF(contactRepo.exists(partnerElt.id, data.email))
        _ <- OptionT.liftF((!(exists && contactElt.email != data.email)).toIO(DuplicateEmailException(data.email)))
        _ <- OptionT.liftF(contactRepo.edit(contact, data))
      } yield Redirect(routes.PartnerCtrl.detail(group, partner))).value.map(_.getOrElse(partnerNotFound(group, partner)))
        .recoverWith {
          case _: DuplicateEmailException => editContactView(group, partner, contact, PartnerForms.createContact.bindFromRequest().withError("email", "Email already exists"))
          case NonFatal(e) => editContactView(group, partner, contact, PartnerForms.createContact.bindFromRequest().withGlobalError(e.getMessage))
        }
    )
  }

  private def editContactView(group: Group.Slug, partner: Partner.Slug, contact: Contact.Id, form: Form[Contact.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      contactElt <- OptionT(contactRepo.find(contact))
      b = contactBreadcrumb(partnerElt, contactElt).add("Edit" -> routes.PartnerCtrl.editContact(group, partner, contact))
      filledForm = if (form.hasErrors) form else form.fill(contactElt.data)
    } yield Ok(html.editContact(partnerElt, contactElt, filledForm)(b))).value.map(_.getOrElse(contactNotFound(group, partner, contact)))
  }

  def doRemoveContact(group: Group.Slug, partner: Partner.Slug, contact: Contact.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      _ <- OptionT.liftF(contactRepo.remove(partnerElt.id, contact))
    } yield Redirect(routes.PartnerCtrl.detail(group, partner))).value.map(_.getOrElse(contactNotFound(group, partner, contact)))
  }
}

object PartnerCtrl {
  def listBreadcrumb(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    GroupCtrl.breadcrumb.add("Partners" -> routes.PartnerCtrl.list(req.group.slug))

  def breadcrumb(partner: Partner)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    listBreadcrumb.add(partner.name.value -> routes.PartnerCtrl.detail(req.group.slug, partner.slug))

  def venueListBreadcrumb(partner: Partner)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    breadcrumb(partner).add("Venues" -> routes.PartnerCtrl.detail(req.group.slug, partner.slug))

  def venueBreadcrumb(venue: Venue.Full)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    venueListBreadcrumb(venue.partner).add(venue.address.value -> routes.PartnerCtrl.detail(req.group.slug, venue.partner.slug))

  def contactListBreadcrumb(partner: Partner)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    breadcrumb(partner).add("Contacts" -> routes.PartnerCtrl.detail(req.group.slug, partner.slug))

  def contactBreadcrumb(partner: Partner, contact: Contact)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    contactListBreadcrumb(partner).add(contact.email.value -> routes.PartnerCtrl.detail(req.group.slug, partner.slug))
}

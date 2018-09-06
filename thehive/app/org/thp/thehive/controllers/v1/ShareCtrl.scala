package org.thp.thehive.controllers.v1

import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, Results }

import io.scalaland.chimney.dsl._
import javax.inject.{ Inject, Singleton }
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.{ ApiMethod, FieldsParser, UpdateFieldsParser }
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.{ InputShare, OutputShare }
import org.thp.thehive.models._
import org.thp.thehive.services.{ CaseSrv, OrganisationSrv, ShareSrv }

object ShareXfrm {
  def fromInput(inputShare: InputShare): Share =
    inputShare
      .into[Share]
      .transform

  def toOutput(richShare: RichShare): OutputShare =
    richShare
      .into[OutputShare]
      .transform
}

@Singleton
class ShareCtrl @Inject()(apiMethod: ApiMethod, db: Database, shareSrv: ShareSrv, organisationSrv: OrganisationSrv, caseSrv: CaseSrv) {

  def create: Action[AnyContent] =
    apiMethod("create share")
      .extract('share, FieldsParser[InputShare])
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val inputShare: InputShare = request.body('share)
          val organisation           = organisationSrv.getOrFail(inputShare.organisationName)
          val `case`                 = caseSrv.getOrFail(inputShare.caseId)
          val richShare           = shareSrv.create(`case`, organisation)
          val outputShare            = ShareXfrm.toOutput(richShare)
          Results.Created(Json.toJson(outputShare))
        }
      }

  def get(shareId: String): Action[AnyContent] =
    apiMethod("get share")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val share = shareSrv
            .get(shareId)
            .richShare
            .headOption()
            .getOrElse(throw NotFoundError(s"share $shareId not found"))
          val outputShare = ShareXfrm.toOutput(share)
          Results.Ok(Json.toJson(outputShare))
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list share")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val shares = shareSrv
            .initSteps
              .richShare
            .toList
            .map(ShareXfrm.toOutput)
          Results.Ok(Json.toJson(shares))
        }
      }

  def update(shareId: String): Action[AnyContent] =
    apiMethod("update share")
      .extract('share, UpdateFieldsParser[InputShare])
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          shareSrv.update(shareId, request.body('share))
          Results.NoContent
        }
      }
}

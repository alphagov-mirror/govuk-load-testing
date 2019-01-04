package govuk

import govuk.util.LoremIpsum
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.util.Random

class WhitehallPublishing extends Simulation {
  val factor = sys.props.getOrElse("factor", "1").toFloat
  val scale = factor / workers
  val lipsum = new LoremIpsum()

  val scn =
    scenario("Publishing Whitehall guidance")
      .exec(Signon.authenticate)
      .exec(
        http("Draft a new publication")
          .get("/government/admin/publications/new")
          .check(status.is(200))
          .check(regex("Create publication").exists)
          .check(
            css("input[name=authenticity_token]", "value").saveAs("authToken")
          )
      )
      .exec(session => {
        val randomInt = Random.nextInt(Integer.MAX_VALUE)
        session.setAll(
          "randomInt"        -> randomInt,
          "publicationTitle" -> s"Gatling test publication $randomInt"
        )
      })
      .exec(
        http("Save a draft publication")
          .post("/government/admin/publications")
          .formParam("authenticity_token", """${authToken}""")
          .formParam("edition[publication_type_id]", "3")
          .formParam("edition[title]", """${publicationTitle}""")
          .formParam("edition[summary]", """${publicationTitle} summary text""")
          .formParam("edition[body]", s"""## Gatling test content\n\n${lipsum.text}""")
          .formParam("edition[previously_published]", "false")
          .formParam("edition[lead_organisation_ids][]", "1056")
          .check(status.is(200))
          .check(css(".form-actions span.or_cancel a", "href").saveAs("publicationLink"))
      )
      .exec(
        http("Draft overview")
          .get("""${publicationLink}""")
          .check(status.is(200))
          .check(
            css(".taxonomy-topics a.btn-default", "href").saveAs("addTagsLink"),
            css(".edition-view-edit-buttons a.btn-default", "href").saveAs("editDraftLink"),
            css(".force-publish-form", "action").saveAs("forcePublishAction"),
            css(".force-publish-form input[name=authenticity_token]", "value").saveAs("forcePublishAuthToken")
          )
      )
      .exec(
        http("Edit draft")
          .get("""${editDraftLink}""")
          .check(status.is(200))
          .check(regex("Edit publication").exists)
          .check(
            css(".nav-tabs li:nth-of-type(2) a", "href").saveAs("attachmentsLink")
          )
      )
      .exec(
        http("Visit HTML attachment form")
          .get("""${attachmentsLink}/new?type=html""")
          .check(status.is(200))
          .check(
            css("#new_attachment", "action").saveAs("attachmentFormAction"),
            css("#new_attachment input[name=authenticity_token]", "value").saveAs("attachmentAuthToken")
          )
      )
      .exec(
        http("Add HTML attachment")
          .post("""${attachmentFormAction}""")
          .formParam("authenticity_token", """${attachmentAuthToken}""")
          .formParam("type", "html")
          .formParam("attachment[title]", """${publicationTitle} attachment""")
          .formParam(
            "attachment[govspeak_content_attributes][body]",
            s"""## Gatling test attachment\n\n${lipsum.text}"""
          )
          .check(status.is(200))
      )
      .exec(Taxonomy.tag)
      .exec(
        http("Force publish publication")
          .post("""${forcePublishAction}""")
          .formParam("authenticity_token", """${forcePublishAuthToken}""")
          .formParam("reason", "Gatling load test run")
          .formParam("commit", "Force publish")
          .check(status.is(200))
      )
      .exec(
        http("Visit publication overview")
          .get("""${publicationLink}""")
          .check(status.is(200))
          .check(regex("Force published: Gatling load test run").exists)
      )

  run(scn)
}

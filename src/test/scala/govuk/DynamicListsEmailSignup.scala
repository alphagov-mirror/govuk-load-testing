package govuk

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class DynamicListsEmailSignup extends Simulation {
  val factor = sys.props.getOrElse("factor", "1").toFloat

  val duration = sys.props.getOrElse("duration", "0").toInt

  val paths = csv(dataDir + java.io.File.separatorChar + "get-ready-brexit-check-email-signup_paths.csv").readRecords

  val scale = factor / workers

  val scn =
    scenario("DynamicListsEmailSignup")
      .feed(cachebuster)
      .foreach(paths, "path") {
        exec(flattenMapIntoAttributes("${path}"))
          .repeat(session => math.ceil(session("hits").as[Int] * scale).toInt, "hit") {
            exec(
              get("${base_path}", "${cachebust}-${hit}")
                .check(
                  status.is(200),
                  css("#checklist-email-signup", "action").saveAs("subscribeFormAction"),
                  css("#checklist-email-signup input[name=authenticity_token]", "value").saveAs("subscribeAuthToken")
                )
            )
            .exec(
              http("POST Subscribe")
                .post("""${subscribeFormAction}""")
                .formParam("authenticity_token", """${subscribeAuthToken}""")
            )
          }
      }

  val scn_with_duration =
    scenario("DynamicListsEmailSignup")
      .during(duration, "Soak test"){
        feed(cachebuster)
        .foreach(paths, "path") {
          exec(flattenMapIntoAttributes("${path}"))
            .repeat(session => math.ceil(session("hits").as[Int] * scale).toInt, "hit") {
              exec(
                get("${base_path}", "${cachebust}-${hit}")
                  .check(
                    css("#checklist-email-signup", "action").saveAs("subscribeFormAction"),
                    css("#checklist-email-signup input[name=authenticity_token]", "value").saveAs("subscribeAuthToken")
                  )
                  .check(status.is(200))
              )
              .exec(
                http("POST Subscribe")
                  .post("""${subscribeFormAction}""")
                  .formParam("authenticity_token", """${subscribeAuthToken}""")
                  .check(
                    css(".checklist-email-signup", "action").saveAs("frequencyLink"),
                    css(".checklist-email-signup input[name=topic_id]", "value").saveAs("emailSubscriptionFrequencyTopicId"),
                    css(".checklist-email-signup input[name=authenticity_token]", "value").saveAs("subscribeAuthToken")
                  )
                  .check(status.is(200))
              )
              .exec(
                http("POST Frequency")
                  .post("""${frequencyLink}""")
                  .formParam("authenticity_token", """${subscribeAuthToken}""")
                  .formParam("topic_id", """${emailSubscriptionFrequencyTopicId}""")
                  .check(
                    css(".checklist-email-signup", "action").saveAs("emailSubscriptionFrequency"),
                    css(".checklist-email-signup input[name=topic_id]", "value").saveAs("emailSubscriptionFrequencyTopicId"),
                    css(".checklist-email-signup input[name=authenticity_token]", "value").saveAs("subscribeAuthToken")
                  )
                  .check(status.is(200))
              )
              .exec(
                http("POST selected frequency")
                  .post("""${emailSubscriptionFrequency}""")
                  .formParam("authenticity_token", """${subscribeAuthToken}""")
                  .formParam("topic_id", """${emailSubscriptionFrequencyTopicId}""")
                  .check(
                    css(".checklist-email-signup", "action").saveAs("createEmailSubscription"),
                    css(".checklist-email-signup input[name=authenticity_token]", "value").saveAs("createEmailSubscriptionAuthToken"),
                    css(".checklist-email-signup topic_id", "value").saveAs("createEmailSubscriptionTopicId"),
                    css(".checklist-email-signup frequency", "value").saveAs("frequencyType")
                  )
                  .check(status.is(200))
              )
              .exec(
                http("POST Email address")
                  .post("""${createEmailSubscription}""")
                  .formParam("authenticity_token", """${createEmailSubscriptionAuthToken}""")
                  .formParam("topic_id", """${createEmailSubscriptionTopicId}""")
                  .formParam("frequency", """${frequencyType}""")
                  .formParam("address", "alice@example.com")
                  .check(status.is(200))
              )
            }
        }
      }

  if(duration > 0){
    run(scn_with_duration)
  } else{
    run(scn)
  }
}

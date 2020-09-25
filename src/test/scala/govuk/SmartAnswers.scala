package govuk

import io.gatling.core.Predef._
import io.gatling.http.Predef._

object LandingPage {
  val go = exec(
    http("Landing page")
        .get("/coronavirus-find-support/s")
        .check(status.is(200)))
    .pause(1)
}

object NeedHelpWith {
  val go = exec(
    http("Question: need-help-with")
        .get("/coronavirus-find-support/s/need-help-with/next")
        .queryParam("response[]", "none")
        .queryParam("next", "1")
        .check(status.is(200)))
  .pause(1)
}

object LeaveTheSite {
  val go = exec(LandingPage.go)
    .exec(NeedHelpWith.go)
    .exec(
      http("Leave the site")
        .get("/coronavirus-find-support/s/destroy_session")
        .queryParam("ext_r", "true")
        .check(status.is(200)))
}

class SmartAnswers extends Simulation {

  val nodes = csv(dataDir + java.io.File.separatorChar + "smart-answers.csv").readRecords

  val scn = scenario("Load test")
    .exec(LandingPage.go)
    .exec(NeedHelpWith.go)
    .foreach(nodes, "node") {
      exec(flattenMapIntoAttributes("${node}"))
      .exec(http("Question: ${name}")
        .get("/coronavirus-find-support/s/${name}/next")
        .queryParam("response", "${response}")
        .queryParam("next", "1"))
      .pause(1)
    }
    .exec(LeaveTheSite.go)

  run(scn)
}

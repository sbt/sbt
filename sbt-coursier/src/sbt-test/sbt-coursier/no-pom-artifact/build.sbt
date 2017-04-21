
lazy val noPomCheck = TaskKey[Unit]("noPomCheck")

noPomCheck := {

  val configReport = update.value
    .configuration("compile")
    .getOrElse {
      throw new Exception(
        "compile configuration not found in update report"
      )
    }

  val artifacts = configReport
    .modules
    .flatMap(_.artifacts)
    .map(_._1)

  val pomArtifacts = artifacts
    .filter { a =>
      a.`type` == "pom" && a.classifier.isEmpty
    }

  for (a <- pomArtifacts)
    streams.value.log.error(s"Found POM artifact $a")

  assert(pomArtifacts.isEmpty)
}

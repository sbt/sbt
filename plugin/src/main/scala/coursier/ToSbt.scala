package coursier

import sbt._

object ToSbt {

  def artifact(module: Module, artifact: Artifact): sbt.Artifact =
    sbt.Artifact(
      s"${module.organization}:${module.name}",
      artifact.attributes.`type`,
      "jar",
      Some(artifact.attributes.classifier),
      Nil,
      Some(url(artifact.url)),
      Map.empty
    )

}

package coursier

import sbt._

object ToSbt {

  def artifact(module: Module, artifact: Artifact): sbt.Artifact =
    sbt.Artifact(
      module.name,
      artifact.attributes.`type`,
      "jar",
      Some(artifact.attributes.classifier).filter(_.nonEmpty),
      Nil,
      Some(url(artifact.url)),
      Map.empty
    )

}

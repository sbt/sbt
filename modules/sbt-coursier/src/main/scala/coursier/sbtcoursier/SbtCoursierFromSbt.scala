package coursier.sbtcoursier

import lmcoursier.FromSbt
import lmcoursier.definitions.Configuration
import sbt.librarymanagement.GetClassifiersModule

object SbtCoursierFromSbt {

  def sbtClassifiersProject(
    cm: GetClassifiersModule,
    scalaVersion: String,
    scalaBinaryVersion: String
  ) = {

    val p = FromSbt.project(
      cm.id,
      cm.dependencies,
      cm.configurations.map(cfg => Configuration(cfg.name) -> cfg.extendsConfigs.map(c => Configuration(c.name))).toMap,
      scalaVersion,
      scalaBinaryVersion
    )

    // for w/e reasons, the dependencies sometimes don't land in the right config above
    // this is a loose attempt at fixing that
    cm.configurations match {
      case Seq(cfg) =>
        p.withDependencies(
          p.dependencies.map {
            case (_, d) => (Configuration(cfg.name), d)
          }
        )
      case _ =>
        p
    }
  }

}

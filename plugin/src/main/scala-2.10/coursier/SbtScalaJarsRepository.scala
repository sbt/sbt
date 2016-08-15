package coursier

import java.io.File

import coursier.core.Publication

import scalaz.{ EitherT, Monad }
import scalaz.Scalaz.ToEitherOps

object SbtScalaJarsRepository {

  // Will break in 2.11, where scala-parser-combinators_2.11 and scala-xml_2.11, with different
  // org and versions, are thrown into the mix.
  // To handle these well, we would need to fetch actual infos about the scala-* dependencies
  // from actual repos, and use that from SbtScalaJarsRepository.

  val looseDependencies = Map(
    "scala-compiler" -> Set(
      "scala-library",
      "scala-reflect"
    ),
    "scala-reflect" -> Set(
      "scala-library"
    )
  )

}

case class SbtScalaJarsRepository(
  scalaOrg: String,
  scalaVersion: String,
  jars: Seq[File]
) extends Repository { repo =>

  val foundNames = jars.collect {
    case jar if jar.getName.endsWith(".jar") =>
      jar.getName.stripSuffix(".jar")
  }.toSet

  val dependencies = SbtScalaJarsRepository.looseDependencies
    .filterKeys(foundNames)
    .mapValues(_.filter(foundNames))

  val artifacts = jars.collect {
    case jar if jar.getName.endsWith(".jar") =>
      val name = jar.getName.stripSuffix(".jar")
      val mod = Module(scalaOrg, name)

      val proj = Project(
        mod,
        scalaVersion,
        dependencies.getOrElse(name, Set.empty[String]).toVector.map { depName =>
          val dep = Dependency(Module(scalaOrg, depName), scalaVersion)
          "compile" -> dep
        },
        MavenRepository.defaultConfigurations,
        None,
        Nil,
        Nil,
        Nil,
        None,
        None,
        None,
        Seq("compile" -> Publication(name, "jar", "jar", "")),
        Info("", "", Nil, Nil, None)
      )

      (mod, scalaVersion) -> ((proj, jar))
  }.toMap

  val source: Artifact.Source = new Artifact.Source {
    def artifacts(
      dependency: Dependency,
      project: Project,
      overrideClassifiers: Option[Seq[String]]
    ) =
      if (overrideClassifiers.isEmpty)
        repo.artifacts.get(project.moduleVersion) match {
          case Some((_, f)) =>
            Seq(
              Artifact(
                f.toURI.toString,
                Map.empty,
                Map.empty,
                Attributes("jar", ""),
                changing = true,
                None
              )
            )
          case None =>
            Nil
        }
      else
        Nil
  }

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    val res = artifacts.get((module, version)) match {
      case None =>
        s"not found in internal SBT scala JARs: $module:$version".left
      case Some((p, _)) =>
        (source, p).right
    }

    EitherT(F.point(res))
  }
}

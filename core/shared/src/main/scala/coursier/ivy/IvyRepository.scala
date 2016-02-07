package coursier.ivy

import coursier.Fetch
import coursier.core._

import scalaz._

case class IvyRepository(
  pattern: String,
  metadataPatternOpt: Option[String] = None,
  changing: Option[Boolean] = None,
  properties: Map[String, String] = Map.empty,
  withChecksums: Boolean = true,
  withSignatures: Boolean = true,
  withArtifacts: Boolean = true,
  // hack for SBT putting infos in properties
  dropInfoAttributes: Boolean = false
) extends Repository {

  def metadataPattern: String = metadataPatternOpt.getOrElse(pattern)

  import Repository._

  private val pattern0 = Pattern(pattern, properties)
  private val metadataPattern0 = Pattern(metadataPattern, properties)

  // See http://ant.apache.org/ivy/history/latest-milestone/concept.html for a
  // list of variables that should be supported.
  // Some are missing (branch, conf, originalName).
  private def variables(
    module: Module,
    version: String,
    `type`: String,
    artifact: String,
    ext: String,
    classifierOpt: Option[String]
  ) =
    Map(
      "organization" -> module.organization,
      "organisation" -> module.organization,
      "orgPath" -> module.organization.replace('.', '/'),
      "module" -> module.name,
      "revision" -> version,
      "type" -> `type`,
      "artifact" -> artifact,
      "ext" -> ext
    ) ++ module.attributes ++ classifierOpt.map("classifier" -> _).toSeq


  val source: Artifact.Source =
    if (withArtifacts)
      new Artifact.Source {
        def artifacts(
          dependency: Dependency,
          project: Project,
          overrideClassifiers: Option[Seq[String]]
        ) = {

          val retained =
            overrideClassifiers match {
              case None =>
                project.publications.collect {
                  case (conf, p)
                    if (conf == "*" ||
                        conf == dependency.configuration ||
                        project.allConfigurations.getOrElse(dependency.configuration, Set.empty).contains(conf)
                       ) && p.classifier.isEmpty =>
                    p
                }
              case Some(classifiers) =>
                val classifiersSet = classifiers.toSet
                project.publications.collect {
                  case (_, p) if classifiersSet(p.classifier) =>
                    p
                }
            }

          val retainedWithUrl = retained.flatMap { p =>
            pattern0.substitute(variables(
              dependency.module,
              dependency.version,
              p.`type`,
              p.name,
              p.ext,
              Some(p.classifier).filter(_.nonEmpty)
            )).toList.map(p -> _)
          }

          retainedWithUrl.map { case (p, url) =>
            var artifact = Artifact(
              url,
              Map.empty,
              Map.empty,
              p.attributes,
              changing = changing.getOrElse(project.version.contains("-SNAPSHOT")) // could be more reliable
            )

            if (withChecksums)
              artifact = artifact.withDefaultChecksums
            if (withSignatures)
              artifact = artifact.withDefaultSignature

            artifact
          }
        }
      }
    else
      Artifact.Source.empty


  def find[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    val eitherArtifact: String \/ Artifact =
      for {
        url <- metadataPattern0.substitute(
          variables(module, version, "ivy", "ivy", "xml", None)
        )
      } yield {
        var artifact = Artifact(
          url,
          Map.empty,
          Map.empty,
          Attributes("ivy", ""),
          changing = changing.getOrElse(version.contains("-SNAPSHOT"))
        )

        if (withChecksums)
          artifact = artifact.withDefaultChecksums
        if (withSignatures)
          artifact = artifact.withDefaultSignature

        artifact
      }

    for {
      artifact <- EitherT(F.point(eitherArtifact))
      ivy <- fetch(artifact)
      proj0 <- EitherT(F.point {
        for {
          xml <- \/.fromEither(compatibility.xmlParse(ivy))
          _ <- if (xml.label == "ivy-module") \/-(()) else -\/("Module definition not found")
          proj <- IvyXml.project(xml)
        } yield proj
      })
    } yield {
      val proj =
        if (dropInfoAttributes)
          proj0.copy(
            module = proj0.module.copy(
              attributes = proj0.module.attributes.filter {
                case (k, _) => !k.startsWith("info.")
              }
            ),
            dependencies = proj0.dependencies.map {
              case (config, dep0) =>
                val dep = dep0.copy(
                  module = dep0.module.copy(
                    attributes = dep0.module.attributes.filter {
                      case (k, _) => !k.startsWith("info.")
                    }
                  )
                )

                config -> dep
            }
          )
        else
          proj0

      (source, proj)
    }
  }

}

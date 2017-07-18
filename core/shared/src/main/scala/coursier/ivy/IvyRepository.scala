package coursier.ivy

import coursier.Fetch
import coursier.core._
import coursier.util.WebPage

import scala.language.higherKinds

import scalaz._
import scalaz.Scalaz._

final case class IvyRepository(
  pattern: Pattern,
  metadataPatternOpt: Option[Pattern],
  changing: Option[Boolean],
  withChecksums: Boolean,
  withSignatures: Boolean,
  withArtifacts: Boolean,
  // hack for SBT putting infos in properties
  dropInfoAttributes: Boolean,
  authentication: Option[Authentication]
) extends Repository {

  def metadataPattern: Pattern = metadataPatternOpt.getOrElse(pattern)

  lazy val revisionListingPatternOpt: Option[Pattern] = {
    val idx = metadataPattern.chunks.indexWhere { chunk =>
      chunk == Pattern.Chunk.Var("revision")
    }

    if (idx < 0)
      None
    else
      Some(Pattern(metadataPattern.chunks.take(idx)))
  }

  import Repository._

  // See http://ant.apache.org/ivy/history/latest-milestone/concept.html for a
  // list of variables that should be supported.
  // Some are missing (branch, conf, originalName).
  private def variables(
    module: Module,
    versionOpt: Option[String],
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
      "type" -> `type`,
      "artifact" -> artifact,
      "ext" -> ext
    ) ++
    module.attributes ++
    classifierOpt.map("classifier" -> _).toSeq ++
    versionOpt.map("revision" -> _).toSeq


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

                // FIXME Some duplication with what's done in MavenSource

                if (dependency.attributes.classifier.nonEmpty)
                  // FIXME We're ignoring dependency.attributes.`type` in this case
                  project.publications.collect {
                    case (_, p) if p.classifier == dependency.attributes.classifier =>
                      p
                  }
                else if (dependency.attributes.`type`.nonEmpty)
                  project.publications.collect {
                    case (_, p)
                      if p.classifier.isEmpty && (
                        p.`type` == dependency.attributes.`type` ||
                          (p.ext == dependency.attributes.`type` && project.packagingOpt.toSeq.contains(p.`type`)) // wow
                        ) =>
                      p
                  }
                else
                  project.publications.collect {
                    case (conf, p)
                      if conf == "*" ||
                         conf == dependency.configuration ||
                         project.allConfigurations.getOrElse(dependency.configuration, Set.empty).contains(conf) =>
                      p
                  }
              case Some(classifiers) =>
                val classifiersSet = classifiers.toSet
                project.publications.collect {
                  case (_, p) if classifiersSet(p.classifier) =>
                    p
                }
            }

          val retainedWithUrl = retained.distinct.flatMap { p =>
            pattern.substituteVariables(variables(
              dependency.module,
              Some(project.actualVersion),
              p.`type`,
              p.name,
              p.ext,
              Some(p.classifier).filter(_.nonEmpty)
            )).toList.map(p -> _) // FIXME Validation errors are ignored
          }

          retainedWithUrl.map { case (p, url) =>
            var artifact = Artifact(
              url,
              Map.empty,
              Map.empty,
              p.attributes,
              changing = changing.getOrElse(project.version.contains("-SNAPSHOT")), // could be more reliable
              authentication = authentication
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

    revisionListingPatternOpt match {
      case None =>
        findNoInverval(module, version, fetch)
      case Some(revisionListingPattern) =>
        Parse.versionInterval(version)
          .orElse(Parse.multiVersionInterval(version))
          .orElse(Parse.ivyLatestSubRevisionInterval(version))
          .filter(_.isValid) match {
          case None =>
            findNoInverval(module, version, fetch)
          case Some(itv) =>
            val listingUrl = revisionListingPattern.substituteVariables(
              variables(module, None, "ivy", "ivy", "xml", None)
            ).flatMap { s =>
              if (s.endsWith("/"))
                s.right
              else
                s"Don't know how to list revisions of ${metadataPattern.string}".left
            }

            def fromWebPage(url: String, s: String) = {
              val subDirs = WebPage.listDirectories(url, s)
              val versions = subDirs.map(Parse.version).collect { case Some(v) => v }
              val versionsInItv = versions.filter(itv.contains)

              if (versionsInItv.isEmpty)
                EitherT(F.point(s"No version found for $version".left[(Artifact.Source, Project)]))
              else {
                val version0 = versionsInItv.max
                findNoInverval(module, version0.repr, fetch)
              }
            }

            def artifactFor(url: String) =
              Artifact(
                url,
                Map.empty,
                Map.empty,
                Attributes("", ""),
                changing = changing.getOrElse(version.contains("-SNAPSHOT")),
                authentication
              )

            for {
              url <- EitherT(F.point(listingUrl))
              s <- fetch(artifactFor(url))
              res <- fromWebPage(url, s)
            } yield res
        }
    }
  }

  def findNoInverval[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    val eitherArtifact: String \/ Artifact =
      for {
        url <- metadataPattern.substituteVariables(
          variables(module, Some(version), "ivy", "ivy", "xml", None)
        )
      } yield {
        var artifact = Artifact(
          url,
          Map.empty,
          Map.empty,
          Attributes("ivy", ""),
          changing = changing.getOrElse(version.contains("-SNAPSHOT")),
          authentication = authentication
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

      source -> proj.copy(
        actualVersionOpt = Some(version)
      )
    }
  }

}

object IvyRepository {
  def parse(
    pattern: String,
    metadataPatternOpt: Option[String] = None,
    changing: Option[Boolean] = None,
    properties: Map[String, String] = Map.empty,
    withChecksums: Boolean = true,
    withSignatures: Boolean = true,
    withArtifacts: Boolean = true,
    // hack for SBT putting infos in properties
    dropInfoAttributes: Boolean = false,
    authentication: Option[Authentication] = None
  ): String \/ IvyRepository =

    for {
      propertiesPattern <- PropertiesPattern.parse(pattern)
      metadataPropertiesPatternOpt <- metadataPatternOpt.fold(Option.empty[PropertiesPattern].right[String])(PropertiesPattern.parse(_).map(Some(_)))

      pattern <- propertiesPattern.substituteProperties(properties)
      metadataPatternOpt <- metadataPropertiesPatternOpt.fold(Option.empty[Pattern].right[String])(_.substituteProperties(properties).map(Some(_)))

    } yield
      IvyRepository(
        pattern,
        metadataPatternOpt,
        changing,
        withChecksums,
        withSignatures,
        withArtifacts,
        dropInfoAttributes,
        authentication
      )

  // because of the compatibility apply method below, we can't give default values
  // to the default constructor of IvyPattern
  // this method accepts the same arguments as this constructor, with default values when possible
  def fromPattern(
    pattern: Pattern,
    metadataPatternOpt: Option[Pattern] = None,
    changing: Option[Boolean] = None,
    withChecksums: Boolean = true,
    withSignatures: Boolean = true,
    withArtifacts: Boolean = true,
    // hack for SBT putting infos in properties
    dropInfoAttributes: Boolean = false,
    authentication: Option[Authentication] = None
  ): IvyRepository =
    IvyRepository(
      pattern,
      metadataPatternOpt,
      changing,
      withChecksums,
      withSignatures,
      withArtifacts,
      dropInfoAttributes,
      authentication
    )

  @deprecated("Can now raise exceptions - use parse instead", "1.0.0-M13")
  def apply(
    pattern: String,
    metadataPatternOpt: Option[String] = None,
    changing: Option[Boolean] = None,
    properties: Map[String, String] = Map.empty,
    withChecksums: Boolean = true,
    withSignatures: Boolean = true,
    withArtifacts: Boolean = true,
    // hack for SBT putting infos in properties
    dropInfoAttributes: Boolean = false,
    authentication: Option[Authentication] = None
  ): IvyRepository =
    parse(
      pattern,
      metadataPatternOpt,
      changing,
      properties,
      withChecksums,
      withSignatures,
      withArtifacts,
      dropInfoAttributes,
      authentication
    ) match {
      case \/-(repo) => repo
      case -\/(msg) =>
        throw new IllegalArgumentException(s"Error while parsing Ivy patterns: $msg")
    }
}
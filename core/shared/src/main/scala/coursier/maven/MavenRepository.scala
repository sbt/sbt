package coursier.maven

import coursier.Fetch
import coursier.core._
import coursier.core.compatibility.encodeURIComponent

import scala.language.higherKinds
import scalaz._

object MavenRepository {

  def ivyLikePath(
    org: String,
    dirName: String,
    name: String,
    version: String,
    subDir: String,
    baseSuffix: String,
    ext: String
  ) =
    Seq(
      org,
      dirName,
      version,
      subDir,
      s"$name$baseSuffix.$ext"
    )

  def mavenVersioning(
    snapshotVersioning: SnapshotVersioning,
    classifier: String,
    extension: String
  ): Option[String] =
    snapshotVersioning
      .snapshotVersions
      .find(v => v.classifier == classifier && v.extension == extension)
      .map(_.value)
      .filter(_.nonEmpty)


  val defaultConfigurations = Map(
    "compile" -> Seq.empty,
    "runtime" -> Seq("compile"),
    "default" -> Seq("runtime"),
    "test" -> Seq("runtime")
  )

  val defaultPackaging = "jar"

  def defaultPublications(moduleName: String, packaging: String) = Seq(
    "compile" -> Publication(
      moduleName,
      packaging,
      MavenSource.typeExtension(packaging),
      MavenSource.typeDefaultClassifier(packaging)
    ),
    "docs" -> Publication(moduleName, "doc", "jar", "javadoc"),
    "sources" -> Publication(moduleName, "src", "jar", "sources")
  )

  def dirModuleName(module: Module, sbtAttrStub: Boolean): String =
    if (sbtAttrStub) {
      var name = module.name
      for (scalaVersion <- module.attributes.get("scalaVersion"))
        name = name + "_" + scalaVersion
      for (sbtVersion <- module.attributes.get("sbtVersion"))
        name = name + "_" + sbtVersion
      name
    } else
      module.name

  val ignorePackaging = Set(
    Module("org.apache.zookeeper", "zookeeper", Map.empty)
  )

}

final case class MavenRepository(
  root: String,
  changing: Option[Boolean] = None,
  /** Hackish hack for sbt plugins mainly - what this does really sucks */
  sbtAttrStub: Boolean = false,
  authentication: Option[Authentication] = None,
  packagingBlacklist: Set[Module] = MavenRepository.ignorePackaging
) extends Repository {

  import Repository._
  import MavenRepository._

  val root0 = if (root.endsWith("/")) root else root + "/"
  val source = MavenSource(root0, changing, sbtAttrStub, authentication)

  def projectArtifact(
    module: Module,
    version: String,
    versioningValue: Option[String]
  ): Artifact = {

    val path = module.organization.split('.').toSeq ++ Seq(
      dirModuleName(module, sbtAttrStub),
      version,
      s"${module.name}-${versioningValue getOrElse version}.pom"
    )

    Artifact(
      root0 + path.map(encodeURIComponent).mkString("/"),
      Map.empty,
      Map.empty,
      Attributes("pom", ""),
      changing = changing.getOrElse(version.contains("-SNAPSHOT")),
      authentication = authentication
    )
    .withDefaultChecksums
    .withDefaultSignature
  }

  def versionsArtifact(module: Module): Option[Artifact] = {

    val path = module.organization.split('.').toSeq ++ Seq(
      dirModuleName(module, sbtAttrStub),
      "maven-metadata.xml"
    )

    val artifact =
      Artifact(
        root0 + path.map(encodeURIComponent).mkString("/"),
        Map.empty,
        Map.empty,
        Attributes("pom", ""),
        changing = true,
        authentication = authentication
      )
      .withDefaultChecksums
      .withDefaultSignature

    Some(artifact)
  }

  def snapshotVersioningArtifact(
    module: Module,
    version: String
  ): Option[Artifact] = {

    val path = module.organization.split('.').toSeq ++ Seq(
      dirModuleName(module, sbtAttrStub),
      version,
      "maven-metadata.xml"
    )

    val artifact =
      Artifact(
        root0 + path.map(encodeURIComponent).mkString("/"),
        Map.empty,
        Map.empty,
        Attributes("pom", ""),
        changing = true,
        authentication = authentication
      )
      .withDefaultChecksums
      .withDefaultSignature

    Some(artifact)
  }

  def versions[F[_]](
    module: Module,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Versions] =
    EitherT(
      versionsArtifact(module) match {
        case None => F.point(-\/("Not supported"))
        case Some(artifact) =>
          F.map(fetch(artifact).run)(eitherStr =>
            for {
              str <- eitherStr
              xml <- \/.fromEither(compatibility.xmlParse(str))
              _ <- if (xml.label == "metadata") \/-(()) else -\/("Metadata not found")
              versions <- Pom.versions(xml)
            } yield versions
          )
      }
    )

  def snapshotVersioning[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, SnapshotVersioning] = {

    EitherT(
      snapshotVersioningArtifact(module, version) match {
        case None => F.point(-\/("Not supported"))
        case Some(artifact) =>
          F.map(fetch(artifact).run)(eitherStr =>
            for {
              str <- eitherStr
              xml <- \/.fromEither(compatibility.xmlParse(str))
              _ <- if (xml.label == "metadata") \/-(()) else -\/("Metadata not found")
              snapshotVersioning <- Pom.snapshotVersioning(xml)
            } yield snapshotVersioning
          )
      }
    )
  }

  def findNoInterval[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Project] =
    EitherT {
      def withSnapshotVersioning =
        snapshotVersioning(module, version, fetch).flatMap { snapshotVersioning =>
          val versioningOption =
            mavenVersioning(snapshotVersioning, "", "jar")
              .orElse(mavenVersioning(snapshotVersioning, "", ""))

          versioningOption match {
            case None =>
              EitherT[F, String, Project](
                F.point(-\/("No snapshot versioning value found"))
              )
            case versioning @ Some(_) =>
              findVersioning(module, version, versioning, fetch)
                .map(_.copy(snapshotVersioning = Some(snapshotVersioning)))
          }
        }

      val res = F.bind(findVersioning(module, version, None, fetch).run) { eitherProj =>
        if (eitherProj.isLeft && version.contains("-SNAPSHOT"))
          F.map(withSnapshotVersioning.run)(eitherProj0 =>
            if (eitherProj0.isLeft)
              eitherProj
            else
              eitherProj0
          )
        else
          F.point(eitherProj)
      }

      // keep exact version used to get metadata, in case the one inside the metadata is wrong
      F.map(res)(_.map(proj => proj.copy(actualVersionOpt = Some(version))))
    }

  def findVersioning[F[_]](
    module: Module,
    version: String,
    versioningValue: Option[String],
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Project] = {

    fetch(projectArtifact(module, version, versioningValue)).flatMap { str =>
      EitherT {
        F.point[String \/ Project] {
          for {
            xml <- \/.fromEither(compatibility.xmlParse(str))
            _ <- if (xml.label == "project") \/-(()) else -\/("Project definition not found")
            proj <- Pom.project(xml)
          } yield {
            val packagingOpt =
              if (packagingBlacklist(module))
                None
              else
                Pom.packagingOpt(xml)

            proj.copy(
              configurations = defaultConfigurations,
              publications = defaultPublications(
                module.name,
                packagingOpt.getOrElse(defaultPackaging)
              )
            )
          }
        }
      }
    }
  }

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    Parse.versionInterval(version)
      .orElse(Parse.ivyLatestSubRevisionInterval(version))
      .filter(_.isValid) match {
        case None =>
          findNoInterval(module, version, fetch).map((source, _))
        case Some(itv) =>
          versions(module, fetch).flatMap { versions0 =>
            val eitherVersion = {
              val release = Version(versions0.release)

              if (itv.contains(release)) \/-(versions0.release)
              else {
                val inInterval = versions0.available
                  .map(Version(_))
                  .filter(itv.contains)

                if (inInterval.isEmpty) -\/(s"No version found for $version")
                else \/-(inInterval.max.repr)
              }
            }

            eitherVersion match {
              case -\/(reason) => EitherT[F, String, (Artifact.Source, Project)](F.point(-\/(reason)))
              case \/-(version0) =>
                findNoInterval(module, version0, fetch)
                  .map(_.copy(versions = Some(versions0)))
                  .map((source, _))
            }
          }
    }
  }

}

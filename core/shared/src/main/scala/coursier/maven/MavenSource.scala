package coursier.maven

import coursier.core._

case class MavenSource(
  root: String,
  changing: Option[Boolean] = None,
  /** See doc on MavenRepository */
  sbtAttrStub: Boolean
) extends Artifact.Source {

  import Repository._
  import MavenRepository._

  private implicit class DocSourcesArtifactExtensions(val underlying: Artifact) {
    def withJavadocSources: Artifact = {
      val base = underlying.url.stripSuffix(".jar")
      underlying.copy(extra = underlying.extra ++ Seq(
        "sources" -> Artifact(
          base + "-sources.jar",
          Map.empty,
          Map.empty,
          Attributes("jar", "src"),  // Are these the right attributes?
          changing = underlying.changing
        )
          .withDefaultChecksums
          .withDefaultSignature,
        "javadoc" -> Artifact(
          base + "-javadoc.jar",
          Map.empty,
          Map.empty,
          Attributes("jar", "javadoc"), // Same comment as above
          changing = underlying.changing
        )
          .withDefaultChecksums
          .withDefaultSignature
      ))
    }
  }

  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[String]]
  ): Seq[Artifact] = {

    def artifactOf(publication: Publication) = {

      val versioning = project
        .snapshotVersioning
        .flatMap(versioning =>
          mavenVersioning(versioning, publication.classifier, publication.`type`)
        )

      val path = dependency.module.organization.split('.').toSeq ++ Seq(
        MavenRepository.dirModuleName(dependency.module, sbtAttrStub),
        project.version,
        s"${dependency.module.name}-${versioning getOrElse project.version}${Some(publication.classifier).filter(_.nonEmpty).map("-" + _).mkString}.${publication.ext}"
      )

      val changing0 = changing.getOrElse(project.version.contains("-SNAPSHOT"))
      var artifact =
        Artifact(
          root + path.mkString("/"),
          Map.empty,
          Map.empty,
          publication.attributes,
          changing = changing0
        )
          .withDefaultChecksums

      if (publication.ext == "jar") {
        artifact = artifact.withDefaultSignature
      }

      artifact
    }

    val metadataArtifact = artifactOf(Publication(dependency.module.name, "pom", "pom", ""))

    def artifactWithExtra(publication: Publication) = {
      val a = artifactOf(publication)
      a.copy(
        extra = a.extra + ("metadata" -> metadataArtifact)
      )
    }

    val publications0 = overrideClassifiers match {
      case Some(classifiers) =>
        val classifiersSet = classifiers.toSet
        val publications = project.publications.collect {
          case (_, p) if classifiersSet(p.classifier) =>
            p
        }

        // Unlike with Ivy metadata, Maven POMs don't list the available publications (~artifacts)
        // so we give a chance to any classifier we're given by returning some publications
        // no matter what, even if we're unsure they're available.
        if (publications.isEmpty)
          classifiers.map { classifier =>
            Publication(
              dependency.module.name,
              "jar",
              "jar",
              classifier
            )
          }
        else
          publications

      case None =>

        val publications =
          if (dependency.attributes.classifier.nonEmpty)
            // FIXME We're ignoring dependency.attributes.`type` in this case
            project.publications.collect {
              case (_, p) if p.classifier == dependency.attributes.classifier =>
                p
            }
          else if (dependency.attributes.`type`.nonEmpty)
            project.publications.collect {
              case (_, p) if p.`type` == dependency.attributes.`type` =>
                p
            }
          else
            project.publications.collect {
              case (_, p) if p.classifier.isEmpty =>
                p
            }

        // See comment above
        if (publications.isEmpty) {
          val type0 = if (dependency.attributes.`type`.isEmpty) "jar" else dependency.attributes.`type`

          Seq(
            Publication(
              dependency.module.name,
              type0,
              MavenSource.typeExtension(type0),
              dependency.attributes.classifier
            )
          )
        } else
          publications
    }

    publications0.map(artifactWithExtra)
  }
}

object MavenSource {

  def typeExtension(`type`: String): String =
    `type` match {
      // see similar things in sbt-maven-resolver/src/main/scala/sbt/mavenint/MavenRepositoryResolver.scala in SBT 0.13.8
      case "eclipse-plugin" | "hk2-jar" | "orbit" | "scala-jar" | "jar" | "bundle" | "doc" | "src" => "jar"
      case other => other
    }

}
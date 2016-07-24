package coursier.maven

import coursier.core._

case class MavenSource(
  root: String,
  changing: Option[Boolean] = None,
  /** See doc on MavenRepository */
  sbtAttrStub: Boolean,
  authentication: Option[Authentication]
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
          changing = underlying.changing,
          authentication = authentication
        )
          .withDefaultChecksums
          .withDefaultSignature,
        "javadoc" -> Artifact(
          base + "-javadoc.jar",
          Map.empty,
          Map.empty,
          Attributes("jar", "javadoc"), // Same comment as above
          changing = underlying.changing,
          authentication = authentication
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
        project.actualVersion,
        s"${dependency.module.name}-${versioning getOrElse project.actualVersion}${Some(publication.classifier).filter(_.nonEmpty).map("-" + _).mkString}.${publication.ext}"
      )

      val changing0 = changing.getOrElse(project.actualVersion.contains("-SNAPSHOT"))
      var artifact =
        Artifact(
          root + path.mkString("/"),
          Map.empty,
          Map.empty,
          publication.attributes,
          changing = changing0,
          authentication = authentication
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

          val extension = MavenSource.typeExtension(type0)

          val classifier =
            if (dependency.attributes.classifier.isEmpty)
              MavenSource.typeDefaultClassifier(type0)
            else
              dependency.attributes.classifier

          Seq(
            Publication(
              dependency.module.name,
              type0,
              extension,
              classifier
            )
          )
        } else
          publications
    }

    publications0.map(artifactWithExtra)
  }
}

object MavenSource {
  
  val typeExtensions: Map[String, String] = Map(
    "eclipse-plugin" -> "jar",
    "maven-plugin"   -> "jar",
    "hk2-jar"        -> "jar",
    "orbit"          -> "jar",
    "scala-jar"      -> "jar",
    "jar"            -> "jar",
    "bundle"         -> "jar",
    "doc"            -> "jar",
    "src"            -> "jar",
    "test-jar"       -> "jar",
    "ejb-client"     -> "jar"
  )

  def typeExtension(`type`: String): String =
    typeExtensions.getOrElse(`type`, `type`)

  // see https://github.com/apache/maven/blob/c023e58104b71e27def0caa034d39ab0fa0373b6/maven-core/src/main/resources/META-INF/plexus/artifact-handlers.xml
  // discussed in https://github.com/alexarchambault/coursier/issues/298
  val typeDefaultClassifiers: Map[String, String] = Map(
    "test-jar"    -> "tests",
    "javadoc"     -> "javadoc",
    "java-source" -> "sources",
    "ejb-client"  -> "client"
  )

  def typeDefaultClassifierOpt(`type`: String): Option[String] =
    typeDefaultClassifiers.get(`type`)

  def typeDefaultClassifier(`type`: String): String =
    typeDefaultClassifierOpt(`type`).getOrElse("")

}
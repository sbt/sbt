package coursier.maven

import coursier.core._

final case class MavenSource(
  root: String,
  changing: Option[Boolean] = None,
  /** See doc on MavenRepository */
  sbtAttrStub: Boolean,
  authentication: Option[Authentication]
) extends Artifact.Source {

  import Repository._
  import MavenRepository._

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

      if (publication.ext == "jar")
        // TODO Get available signature / checksums from directory listing
        artifact = artifact.withDefaultSignature

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

        project.publications.collect {
          case (_, p) if classifiersSet(p.classifier) =>
            p
        }

      case None =>

        if (dependency.attributes.classifier.nonEmpty)
          // FIXME We're ignoring dependency.attributes.`type` in this case
          project.publications.collect {
            case (_, p) if p.classifier == dependency.attributes.classifier =>
              p
          }
        else if (dependency.attributes.`type`.nonEmpty)
          project.publications.collect {
            case (_, p)
              if p.`type` == dependency.attributes.`type` ||
                (p.ext == dependency.attributes.`type` && project.packagingOpt.toSeq.contains(p.`type`)) // wow
              =>
              p
          }
        else
          project.publications.collect {
            case (_, p) if p.classifier.isEmpty =>
              p
          }
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

  val classifierExtensionDefaultTypes: Map[(String, String), String] = Map(
    ("tests", "jar")   -> "test-jar",
    ("javadoc", "jar") -> "doc",
    ("sources", "jar") -> "src"
    // don't know much about "client" classifier, not including it here
  )

  def classifierExtensionDefaultTypeOpt(classifier: String, ext: String): Option[String] =
    classifierExtensionDefaultTypes.get((classifier, ext))

  val typeDefaultConfigs: Map[String, String] = Map(
    "doc" -> "docs",
    "src" -> "sources"
  )

  def typeDefaultConfig(`type`: String): Option[String] =
    typeDefaultConfigs.get(`type`)

}

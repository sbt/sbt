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

  private def artifactsUnknownPublications(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[String]]
  ): Seq[Artifact] = {

    val packagingOpt = project.packagingOpt.filter(_ != Pom.relocatedPackaging)

    val packagingTpeMap = packagingOpt
      .map { packaging =>
        (MavenSource.typeDefaultClassifier(packaging), MavenSource.typeExtension(packaging)) -> packaging
      }
      .toMap

    def artifactOf(publication: Publication) = {

      val versioning = project
        .snapshotVersioning
        .flatMap(versioning =>
          mavenVersioning(
            versioning,
            publication.classifier,
            MavenSource.typeExtension(publication.`type`)
          )
        )

      val path = dependency.module.organization.split('.').toSeq ++ Seq(
        MavenRepository.dirModuleName(dependency.module, sbtAttrStub),
        toBaseVersion(project.actualVersion),
        s"${dependency.module.name}-${versioning getOrElse project.actualVersion}${Some(publication.classifier).filter(_.nonEmpty).map("-" + _).mkString}.${publication.ext}"
      )

      val changing0 = changing.getOrElse(isSnapshot(project.actualVersion))

      Artifact(
        root + path.mkString("/"),
        Map.empty,
        Map.empty,
        publication.attributes,
        changing = changing0,
        authentication = authentication
      )
        .withDefaultChecksums
        .withDefaultSignature
    }

    val metadataArtifact = artifactOf(Publication(dependency.module.name, "pom", "pom", ""))

    def artifactWithExtra(publication: Publication) = {
      val a = artifactOf(publication)
      a.copy(
        extra = a.extra + ("metadata" -> metadataArtifact)
      )
    }

    lazy val defaultPublications = {

      val packagingPublicationOpt = packagingOpt
        .filter(_ => dependency.attributes.isEmpty)
        .map { packaging =>
          Publication(
            dependency.module.name,
            packaging,
            MavenSource.typeExtension(packaging),
            MavenSource.typeDefaultClassifier(packaging)
          )
        }

      val type0 = if (dependency.attributes.`type`.isEmpty) "jar" else dependency.attributes.`type`

      val ext = MavenSource.typeExtension(type0)

      val classifier =
        if (dependency.attributes.classifier.isEmpty)
          MavenSource.typeDefaultClassifier(type0)
        else
          dependency.attributes.classifier

      val tpe = packagingTpeMap.getOrElse(
        (classifier, ext),
        MavenSource.classifierExtensionDefaultTypeOpt(classifier, ext).getOrElse(ext)
      )

      val pubs = packagingPublicationOpt.toSeq :+
        Publication(
          dependency.module.name,
          tpe,
          ext,
          classifier
        )

      pubs.distinct
    }

    overrideClassifiers
      .fold(defaultPublications) { classifiers =>
        classifiers.flatMap { classifier =>
          if (classifier == dependency.attributes.classifier)
            defaultPublications
          else {
            val ext = "jar"
            val tpe = packagingTpeMap.getOrElse(
              (classifier, ext),
              MavenSource.classifierExtensionDefaultTypeOpt(classifier, ext).getOrElse(ext)
            )

            Seq(
              Publication(
                dependency.module.name,
                tpe,
                ext,
                classifier
              )
            )
          }
        }
      }
      .map(artifactWithExtra)
  }

  private val dummyArtifact = Artifact("", Map(), Map(), Attributes("", ""), changing = false, None)

  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[String]]
  ): Seq[Artifact] =
    if (project.packagingOpt.toSeq.contains(Pom.relocatedPackaging))
      Nil
    else {

      def makeOptional(a: Artifact): Artifact =
        a.copy(
          extra = a.extra.mapValues(makeOptional).iterator.toMap + (Artifact.optionalKey -> dummyArtifact)
        )

      artifactsUnknownPublications(dependency, project, overrideClassifiers)
        .map(makeOptional)
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
  // discussed in https://github.com/coursier/coursier/issues/298
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

}

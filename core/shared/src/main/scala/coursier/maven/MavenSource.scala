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
          mavenVersioning(versioning, publication.classifier, publication.`type`)
        )

      val path = dependency.module.organization.split('.').toSeq ++ Seq(
        MavenRepository.dirModuleName(dependency.module, sbtAttrStub),
        toBaseVersion(project.actualVersion),
        s"${dependency.module.name}-${versioning getOrElse project.actualVersion}${Some(publication.classifier).filter(_.nonEmpty).map("-" + _).mkString}.${publication.ext}"
      )

      val changing0 = changing.getOrElse(isSnapshot(project.actualVersion))
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

  private val types = Map("sha1" -> "SHA-1", "sha256" -> "SHA-256", "md5" -> "MD5", "asc" -> "sig")

  private def artifactsKnownPublications(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[String]]
  ): Seq[Artifact] = {

    final case class EnrichedPublication(
      publication: Publication,
      extra: Map[String, EnrichedPublication]
    ) {
      def artifact: Artifact =
        artifact(publication.`type`)
      def artifact(versioningType: String): Artifact = {

        val versioningExtension = MavenSource.typeExtensions.getOrElse(versioningType, versioningType)

        val versioning = project
          .snapshotVersioning
          .flatMap(versioning =>
            mavenVersioning(versioning, publication.classifier, versioningExtension)
          )

        val path = dependency.module.organization.split('.').toSeq ++ Seq(
          MavenRepository.dirModuleName(dependency.module, sbtAttrStub),
          project.actualVersion,
          s"${dependency.module.name}-${versioning getOrElse project.actualVersion}${Some(publication.classifier).filter(_.nonEmpty).map("-" + _).mkString}.${publication.ext}"
        )

        val changing0 = changing.getOrElse(isSnapshot(project.actualVersion))

        val extra0 = extra.mapValues(_.artifact(versioningType)).iterator.toMap

        Artifact(
          root + path.mkString("/"),
          extra0.filterKeys(MavenSource.checksumTypes).mapValues(_.url).iterator.toMap,
          extra0,
          publication.attributes,
          changing = changing0,
          authentication = authentication
        )
      }
    }

    def groupedEnrichedPublications(publications: Seq[Publication]): Seq[EnrichedPublication] = {

      def helper(publications: Seq[Publication]): Seq[EnrichedPublication] = {

        var publications0 = publications
          .map { pub =>
            pub.ext -> EnrichedPublication(pub, Map())
          }
          .toMap

        val byLength = publications0.toVector.sortBy(-_._1.length)

        for {
          (ext, _) <- byLength
          idx = ext.lastIndexOf('.')
          if idx >= 0
          subExt = ext.substring(idx + 1)
          baseExt = ext.substring(0, idx)
          tpe <- types.get(subExt)
          mainPub <- publications0.get(baseExt)
        } {
          val pub = publications0(ext)
          publications0 += baseExt -> mainPub.copy(
            extra = mainPub.extra + (tpe -> pub)
          )
          publications0 -= ext
        }

        publications0.values.toVector
      }

      publications
        .groupBy(p => (p.name, p.classifier))
        .mapValues(helper)
        .values
        .toVector
        .flatten
    }

    val enrichedPublications = groupedEnrichedPublications(project.publications.map(_._2))

    val metadataArtifactOpt = enrichedPublications.collectFirst {
      case pub if pub.publication.name == dependency.module.name &&
          pub.publication.ext == "pom" &&
          pub.publication.classifier.isEmpty =>
        pub.artifact
    }

    def withMetadataExtra(artifact: Artifact) =
      metadataArtifactOpt.fold(artifact) { metadataArtifact =>
        artifact.copy(
          extra = artifact.extra + ("metadata" -> metadataArtifact)
        )
      }

    val res = overrideClassifiers match {
      case Some(classifiers) =>
        val classifiersSet = classifiers.toSet

        enrichedPublications.collect {
          case p if classifiersSet(p.publication.classifier) =>
            p.artifact
        }

      case None =>

        if (dependency.attributes.classifier.nonEmpty)
          // FIXME We're ignoring dependency.attributes.`type` in this case
          enrichedPublications.collect {
            case p if p.publication.classifier == dependency.attributes.classifier =>
              p.artifact
          }
        else if (dependency.attributes.`type`.nonEmpty)
          enrichedPublications.collect {
            case p
              if (p.publication.classifier.isEmpty || p.publication.classifier == MavenSource.typeDefaultClassifier(dependency.attributes.`type`)) && (
                   p.publication.`type` == dependency.attributes.`type` ||
                   (p.publication.ext == dependency.attributes.`type` && project.packagingOpt.toSeq.contains(p.publication.`type`)) // wow
                ) =>
              p.artifact
          }
        else
          enrichedPublications.collect {
            case p if p.publication.classifier.isEmpty =>
              p.artifact
          }
    }

    res.map(withMetadataExtra)
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

      def merge(a: Artifact, other: Artifact): Artifact = {

        assert(a.url == other.url, s"Merging artifacts with different URLs (${a.url}, ${other.url})")

        val extra =
          a.extra.map {
            case (k, v) =>
              k -> other.extra.get(k).fold(v)(merge(v, _))
          } ++
          other.extra
            .filterKeys(k => !a.extra.contains(k) && k != Artifact.optionalKey)

        a.copy(
          checksumUrls = other.checksumUrls ++ a.checksumUrls,
          extra = extra
        )
      }

      val defaultPublications = artifactsUnknownPublications(dependency, project, overrideClassifiers)
        .map(makeOptional)

      if (project.publications.isEmpty)
        defaultPublications
      else {
        val listedPublications = artifactsKnownPublications(dependency, project, overrideClassifiers)
        val listedUrls = listedPublications.map(_.url).toSet
        val defaultPublicationsMap = defaultPublications
          .map(a => a.url -> a)
          .toMap
        val listedPublications0 = listedPublications.map { a =>
          defaultPublicationsMap
            .get(a.url)
            .fold(a)(merge(a, _))
        }
        val extraPublications = defaultPublications
          .filter(a => !listedUrls(a.url))

        listedPublications0 ++ extraPublications
      }
    }
}

object MavenSource {

  private val checksumTypes = Set("MD5", "SHA-1", "SHA-256")

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

  val typeDefaultConfigs: Map[String, String] = Map(
    "doc" -> "docs",
    "src" -> "sources"
  )

  def typeDefaultConfig(`type`: String): Option[String] =
    typeDefaultConfigs.get(`type`)

}

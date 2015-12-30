package coursier.maven

import coursier.core._

case class MavenSource(
  root: String,
  ivyLike: Boolean,
  changing: Option[Boolean] = None
) extends Artifact.Source {

  import Repository._
  import MavenRepository._

  def artifacts(
    dependency: Dependency,
    project: Project
  ): Seq[Artifact] = {

    def ivyLikePath0(subDir: String, baseSuffix: String, ext: String) =
      ivyLikePath(
        dependency.module.organization,
        dependency.module.name,
        project.version,
        subDir,
        baseSuffix,
        ext
      )

    val path =
      if (ivyLike)
        ivyLikePath0(dependency.attributes.`type` + "s", "", dependency.attributes.`type`)
      else {
        val versioning =
          project
            .snapshotVersioning
            .flatMap(versioning =>
              mavenVersioning(versioning, dependency.attributes.classifier, dependency.attributes.`type`)
            )

        dependency.module.organization.split('.').toSeq ++ Seq(
          dependency.module.name,
          project.version,
          s"${dependency.module.name}-${versioning getOrElse project.version}${Some(dependency.attributes.classifier).filter(_.nonEmpty).map("-"+_).mkString}.${dependency.attributes.`type`}"
        )
      }

    val changing0 = changing.getOrElse(project.version.contains("-SNAPSHOT"))
    var artifact =
      Artifact(
        root + path.mkString("/"),
        Map.empty,
        Map.empty,
        dependency.attributes,
        changing = changing0
      )
      .withDefaultChecksums

    if (dependency.attributes.`type` == "jar") {
      artifact = artifact.withDefaultSignature

      // FIXME Snapshot versioning of sources and javadoc is not taken into account here.
      // Will be ok if it's the same as the main JAR though.

      artifact =
        if (ivyLike) {
          val srcPath = root + ivyLikePath0("srcs", "-sources", "jar").mkString("/")
          val javadocPath = root + ivyLikePath0("docs", "-javadoc", "jar").mkString("/")

          artifact
            .copy(
              extra = artifact.extra ++ Map(
                "sources" -> Artifact(
                  srcPath,
                  Map.empty,
                  Map.empty,
                  Attributes("jar", "src"), // Are these the right attributes?
                  changing = changing0
                )
                  .withDefaultChecksums
                  .withDefaultSignature,
                "javadoc" -> Artifact(
                  javadocPath,
                  Map.empty,
                  Map.empty,
                  Attributes("jar", "javadoc"), // Same comment as above
                  changing = changing0
                )
                  .withDefaultChecksums
                  .withDefaultSignature
            ))
        } else
          artifact
            .withJavadocSources
    }

    Seq(artifact)
  }
}

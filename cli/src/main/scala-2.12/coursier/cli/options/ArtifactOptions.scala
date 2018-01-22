package coursier.cli.options

import caseapp.{ HelpMessage => Help, ValueDescription => Value, ExtraName => Short, _ }

object ArtifactOptions {
  def defaultArtifactTypes = Set("jar", "bundle", "test-jar")

  implicit val parser = Parser[ArtifactOptions]
  implicit val help = caseapp.core.help.Help[ArtifactOptions]
}

final case class ArtifactOptions(
  @Help("Artifact types that should be retained (e.g. jar, src, doc, etc.) - defaults to jar,bundle")
  @Value("type1,type2,...")
  @Short("A")
    artifactType: List[String] = Nil,
  @Help("Fetch artifacts even if the resolution is errored")
    force: Boolean = false
) {
  def artifactTypes(sources: Boolean, javadoc: Boolean) = {
    val types0 = artifactType
      .flatMap(_.split(','))
      .filter(_.nonEmpty)
      .toSet

    if (types0.isEmpty) {
      if (sources || javadoc)
        Some("src").filter(_ => sources).toSet ++ Some("doc").filter(_ => javadoc)
      else
        ArtifactOptions.defaultArtifactTypes
    } else if (types0("*"))
      Set("*")
    else
      types0
  }
}

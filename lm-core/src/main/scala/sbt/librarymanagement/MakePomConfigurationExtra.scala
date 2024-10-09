package sbt.librarymanagement

private[librarymanagement] abstract class MakePomConfigurationFunctions {
  private[sbt] lazy val constTrue: MavenRepository => Boolean = _ => true

  def apply(): MakePomConfiguration =
    MakePomConfiguration(
      None,
      None,
      None,
      None,
      identity(_: scala.xml.Node),
      constTrue,
      true,
      Set(Artifact.DefaultType, Artifact.PomType)
    )
}

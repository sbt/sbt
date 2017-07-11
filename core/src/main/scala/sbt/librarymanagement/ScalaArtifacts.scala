package sbt.librarymanagement

object ScalaArtifacts {
  val Organization = "org.scala-lang"
  val LibraryID = "scala-library"
  val CompilerID = "scala-compiler"
  val ReflectID = "scala-reflect"
  val ActorsID = "scala-actors"
  val ScalapID = "scalap"
  val Artifacts = Vector(LibraryID, CompilerID, ReflectID, ActorsID, ScalapID)
  val DottyIDPrefix = "dotty"

  def dottyID(binaryVersion: String): String = s"${DottyIDPrefix}_${binaryVersion}"

  def libraryDependency(version: String): ModuleID = ModuleID(Organization, LibraryID, version)

  private[sbt] def toolDependencies(
      org: String,
      version: String,
      isDotty: Boolean = false
  ): Seq[ModuleID] =
    if (isDotty)
      Seq(
        ModuleID(org, DottyIDPrefix, version)
          .withConfigurations(Some(Configurations.ScalaTool.name + "->default(compile)"))
          .withCrossVersion(CrossVersion.binary)
      )
    else
      Seq(
        scalaToolDependency(org, ScalaArtifacts.CompilerID, version),
        scalaToolDependency(org, ScalaArtifacts.LibraryID, version)
      )

  private[this] def scalaToolDependency(org: String, id: String, version: String): ModuleID =
    ModuleID(org, id, version).withConfigurations(
      Some(Configurations.ScalaTool.name + "->default,optional(default)")
    )
}

object SbtArtifacts {
  val Organization = "org.scala-sbt"
}

package sbt.librarymanagement

object ScalaArtifacts {
  final val Organization = "org.scala-lang"
  final val LibraryID = "scala-library"
  final val CompilerID = "scala-compiler"
  final val ReflectID = "scala-reflect"
  final val ActorsID = "scala-actors"
  final val ScalapID = "scalap"
  final val Artifacts = Vector(LibraryID, CompilerID, ReflectID, ActorsID, ScalapID)

  final val Scala3LibraryID = "scala3-library"
  final val Scala3CompilerID = "scala3-compiler"
  final val Scala3InterfacesID = "scala3-interfaces"
  final val TastyCoreID = "tasty-core"
  final val ScaladocID = "scaladoc"
  final val Scala3DocID = "scala3doc"
  final val Scala3TastyInspectorID = "scala3-tasty-inspector"

  private[sbt] final val Scala3LibraryPrefix = Scala3LibraryID + "_"
  private[sbt] final val Scala3CompilerPrefix = Scala3CompilerID + "_"
  private[sbt] final val TastyCorePrefix = TastyCoreID + "_"
  private[sbt] final val ScaladocPrefix = ScaladocID + "_"
  private[sbt] final val Scala3DocPrefix = Scala3DocID + "_"
  private[sbt] final val Scala3TastyInspectorPrefix = Scala3TastyInspectorID + "_"

  def isScala2Artifact(name: String): Boolean = {
    name == LibraryID || name == CompilerID || name == ReflectID || name == ActorsID || name == ScalapID
  }
  def isScala3Artifact(name: String): Boolean = {
    name.startsWith(Scala3LibraryPrefix) ||
    name.startsWith(Scala3CompilerPrefix) ||
    name.startsWith(TastyCorePrefix) ||
    name == Scala3InterfacesID ||
    name.startsWith(ScaladocPrefix) ||
    name.startsWith(Scala3DocPrefix) ||
    name.startsWith(Scala3TastyInspectorPrefix)
  }

  def isScala3(scalaVersion: String): Boolean = scalaVersion.startsWith("3.")

  private[sbt] def isScala3M123(scalaVersion: String): Boolean =
    (scalaVersion == "3.0.0-M1") ||
      (scalaVersion == "3.0.0-M2") ||
      (scalaVersion == "3.0.0-M3")

  def libraryIds(version: String): Array[String] = {
    if (isScala3(version))
      Array(Scala3LibraryID, LibraryID)
    else Array(LibraryID)
  }

  def compilerId(version: String): String = {
    if (isScala3(version)) Scala3CompilerID
    else CompilerID
  }

  def libraryDependency(version: String): ModuleID = libraryDependency(Organization, version)

  def libraryDependency(org: String, version: String): ModuleID =
    if isScala3(version) then
      ModuleID(org, Scala3LibraryID, version)
        .withCrossVersion(CrossVersion.binary)
        .platform(Platform.jvm)
    else ModuleID(org, LibraryID, version).platform(Platform.jvm)

  private[sbt] def docToolDependencies(
      org: String,
      version: String
  ): Seq[ModuleID] =
    if (isScala3M123(version))
      Seq(
        ModuleID(org, Scala3DocID, version)
          .withConfigurations(Some(Configurations.ScalaDocTool.name + "->default(compile)"))
          .withCrossVersion(CrossVersion.binary)
      )
    else if (isScala3(version))
      Seq(
        ModuleID(org, ScaladocID, version)
          .withConfigurations(Some(Configurations.ScalaDocTool.name + "->default(compile)"))
          .withCrossVersion(CrossVersion.binary)
          .platform(Platform.jvm)
      )
    else Seq.empty

  private[sbt] def toolDependencies(
      org: String,
      version: String
  ): Seq[ModuleID] =
    if (isScala3(version))
      Seq(
        ModuleID(org, Scala3CompilerID, version)
          .withConfigurations(Some(Configurations.ScalaTool.name + "->default(compile)"))
          .withCrossVersion(CrossVersion.binary)
          .platform(Platform.jvm)
      )
    else
      Seq(
        scala2ToolDependency(org, CompilerID, version),
        scala2ToolDependency(org, LibraryID, version)
      )

  private[this] def scala2ToolDependency(org: String, id: String, version: String): ModuleID =
    ModuleID(org, id, version)
      .withConfigurations(
        Some(Configurations.ScalaTool.name + "->default,optional(default)")
      )
      .platform(Platform.jvm)
}

object SbtArtifacts {
  val Organization = "org.scala-sbt"
}

package sbt.librarymanagement

abstract class LibraryManagementSyntax extends DependencyBuilders {
  type ExclusionRule = InclExclRule
  val ExclusionRule = InclExclRule

  type InclusionRule = InclExclRule
  val InclusionRule = InclExclRule

  implicit def richUpdateReport(ur: UpdateReport): RichUpdateReport = new RichUpdateReport(ur)

  import sbt.librarymanagement.{ Configurations => C }
  final val Compile = C.Compile
  final val Test = C.Test
  final val Runtime = C.Runtime
  final val IntegrationTest = C.IntegrationTest
  final val Default = C.Default
  final val Provided = C.Provided
  // java.lang.System is more important, so don't alias this one
  //  final val System = C.System
  final val Optional = C.Optional
}

object syntax extends LibraryManagementSyntax

package sbt.librarymanagement

import java.net.URI

trait LibraryManagementSyntax0 {
  // See http://www.scala-lang.org/news/2.12.0#traits-compile-to-interfaces
  // Avoid defining fields (val or var, but a constant is ok – final val without result type)
  // Avoid calling super
  // Avoid initializer statements in the body

  implicit def richUpdateReport(ur: UpdateReport): RichUpdateReport = new RichUpdateReport(ur)
}

trait LibraryManagementSyntax
    extends LibraryManagementSyntax0
    with DependencyBuilders
    with DependencyFilterExtra {
  // See http://www.scala-lang.org/news/2.12.0#traits-compile-to-interfaces
  // Avoid defining fields (val or var, but a constant is ok – final val without result type)
  // Avoid calling super
  // Avoid initializer statements in the body

  type ExclusionRule = InclExclRule
  final val ExclusionRule = InclExclRule

  type InclusionRule = InclExclRule
  final val InclusionRule = InclExclRule

  import sbt.librarymanagement.Configurations as C
  final val Compile = C.Compile
  final val Test = C.Test
  final val Runtime = C.Runtime
  final val Default = C.Default
  final val Provided = C.Provided
  // java.lang.System is more important, so don't alias this one
  //  final val System = C.System
  final val Optional = C.Optional

  given Conversion[(String, URI), License] with
    inline def apply(x: (String, URI)): License = License(x._1, x._2)
}

object syntax extends LibraryManagementSyntax

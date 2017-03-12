package sbt.inc

import java.io.File
import xsbti.api.Source
import xsbti.DependencyContext

/**
  * Represents the kind of dependency that exists between `sourceFile` and either `targetFile`
  * or `targetClassName`.
  *
  * `InternalDependency` represent dependencies that exist between the files of a same project,
  * while `ExternalDependency` represent cross-project dependencies.
  */
private[inc] final case class InternalDependency(sourceFile: File,
                                                 targetFile: File,
                                                 context: DependencyContext)
private[inc] final case class ExternalDependency(sourceFile: File,
                                                 targetClassName: String,
                                                 targetSource: Source,
                                                 context: DependencyContext)

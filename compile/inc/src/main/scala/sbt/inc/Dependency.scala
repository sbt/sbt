package sbt.inc

import java.io.File
import xsbti.api.Source

/**
 * Represents contextual information about particular depedency edge. See comments in
 * subtypes for examples of particular contexts.
 */
private[inc] sealed abstract class DependencyContext

/**
 * Marks dependency edge introduced by referring to a class through inheritance as in
 *
 *   class A extends B
 *
 * Each dependency by inheritance introduces corresponding dependency by member reference.
 */
private[inc] final case object DependencyByInheritance extends DependencyContext
private[inc] final case object DependencyByMemberRef extends DependencyContext

/**
 * Represents the kind of dependency that exists between `sourceFile` and either `targetFile`
 * or `targetClassName`.
 *
 * `InternalDependency` represent dependencies that exist between the files of a same project,
 * while `ExternalDependency` represent cross-project dependencies.
 */
private[inc] final case class InternalDependency(sourceFile: File, targetFile: File, context: DependencyContext)
private[inc] final case class ExternalDependency(sourceFile: File, targetClassName: String, targetSource: Source, context: DependencyContext)

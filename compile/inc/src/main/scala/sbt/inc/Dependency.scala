package sbt.inc

import java.io.File
import xsbti.api.Source

/**
 * Dependency tracked by incremental compiler consists of two parts:
 *   - `edge` which describes an edge in dependency graph
 *   - `context` which stores context information from a src file where the dependency got introduced
 *
 * The context is needed for incremental compiler to decide what kind of invalidation strategy to use.
 * It might also store some additional information useful for debugging.
 */
private[inc] final case class Dependency(edge: DependencyEdge, context: DependencyContext)

private[inc] sealed abstract class DependencyEdge
/**
 * External dependency from src file to class name. External means "outside of enclosing Analysis".
 * Typically that means a dependency coming from another subproject in sbt.
 *
 * The classApi contains snapshot of information about the class (that `toClassName` points at)
 * as observed from point of view of compilation that produced this dependency edge.
 *
 * The classApi is stored so we can detect changes to external apis by comparing snapshots
 * of the api from two Analysis instances.
 */
private[inc] final case class ExternalDepndencyEdge(fromSrc: File, toClassName: String, classApi: Source)
	extends DependencyEdge

/**
 * Represents a dependency edge between two source files in the same sbt subproject.
 */
private[inc] final case class InternalDependencyEdge(fromSrc: File, toSrc: File) extends DependencyEdge

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

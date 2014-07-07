package sbt.inc

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

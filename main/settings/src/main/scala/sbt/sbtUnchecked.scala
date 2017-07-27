package sbt

import scala.annotation.Annotation

/**
 * An annotation to designate that the annotated entity
 *  should not be considered for additional sbt compiler checks.
 *  These checks ensure that the DSL is predictable and prevents
 *  users from doing dangerous things at the cost of a stricter
 *  code structure.
 *
 *  @since 0.13.16
 */
class sbtUnchecked extends Annotation

/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.annotation.Annotation

/** An annotation to designate that the annotated entity
 *  should not be considered for additional sbt compiler checks.
 *  These checks ensure that the DSL is predictable and prevents
 *  users from doing dangerous things at the cost of a stricter
 *  code structure.
 *
 *  @since 1.0.0
 */
class sbtUnchecked extends Annotation

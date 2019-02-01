/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.dsl

/**
 * Controls the kind of linting performed during task macro expansion. The linting level is
 * controlled by implicitly bringing an instance of LinterLevel into scope:* {{{
 *   val foo = taskKey[Unit]("")
 *   val bar = taskKey[Unit]("")
 *   // This compiles because of the import in the block
 *   val fooTask = {
 *     import sbt.dsl.LinterLevel.Ignore
 *     Def.task {
 *       if (true) bar.value
 *       else bar.value
 *     }
 *   }
 *   // because the import above was in a local scope, it does not apply here, so this won't
 *   // compile:
 *   //val barTask = Def.task {
 *   //  if (true) foo.value
 *   //  else foo.value
 *   //}
 *
 *   import sbt.dsl.LinterLevel.Ignore
 *   // Both defs compile because the Ignore LinterLevel is implicitly brought into scope and
 *   // picked up by both task defs underneath
 *   val newFooTask = Def.task {
 *     if (true) bar.value
 *     else bar.value
 *   }
 *   val barTask = Def.task {
 *     if (true) foo.value
 *     else foo.value
 *   }
 * }}}
 * To make this work, the instances are all defined as implicit case objects. Moreover, the
 * the [[LinterLevel.Warn]] setting is made default by placing [[LinterLevel.Abort]] and
 * [[LinterLevel.Ignore]] using the low priority trait pattern.
 */
sealed trait LinterLevel
object LinterLevel extends LinterLevelLowPriority {

  /**
   * Apply the linter but print warnings instead of aborting macro expansion when linter violations
   * are found.
   */
  implicit case object Warn extends LinterLevel
}

private[dsl] trait LinterLevelLowPriority {

  /**
   * Abort the macro expansion if any linter check fails.
   */
  implicit case object Abort extends LinterLevel

  /**
   * Do not perform any linting.
   */
  implicit case object Ignore extends LinterLevel
}

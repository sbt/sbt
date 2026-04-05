/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import scala.annotation.compileTimeOnly

/** Implementation detail.  The wrap methods temporarily hold inputs (as a Tree, at compile time) until a task or setting macro processes it. */
object InputWrapper:
  /* The names of the wrapper methods should be obscure.
   * Wrapper checking is based solely on this name, so it must not conflict with a user method name.
   * The user should never see this method because it is compile-time only and only used internally by the task macro system.*/

  private[std] final val WrapTaskName = "wrapTask_\u2603\u2603"
  private[std] final val WrapInitName = "wrapInit_\u2603\u2603"
  private[std] final val WrapOutputName = "wrapOutput_\u2603\u2603"
  private[std] final val WrapOutputDirectoryName = "wrapOutputDirectory_\u2603\u2603"
  private[std] final val WrapInitTaskName = "wrapInitTask_\u2603\u2603"
  private[std] final val WrapInitInputName = "wrapInitInputTask_\u2603\u2603"
  private[std] final val WrapInputName = "wrapInputTask_\u2603\u2603"
  private[std] final val WrapPreviousName = "wrapPrevious_\u2603\u2603"

  @compileTimeOnly(
    "`value` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
  )
  def `wrapTask_\u2603\u2603`[T](@deprecated("unused", "") in: Any): T = implDetailError

  @compileTimeOnly(
    "`value` can only be used within a task or setting macro, such as :=, +=, ++=, Def.task, or Def.setting."
  )
  def `wrapInit_\u2603\u2603`[T](@deprecated("unused", "") in: Any): T = implDetailError

  @compileTimeOnly(
    "`declareOutput` can only be used within a task macro, such as Def.cachedTask."
  )
  def `wrapOutput_\u2603\u2603`[A](@deprecated("unused", "") in: Any): A = implDetailError

  @compileTimeOnly(
    "`declareOutputDirectory` can only be used within a task macro, such as Def.cachedTask."
  )
  def `wrapOutputDirectory_\u2603\u2603`[A](@deprecated("unused", "") in: Any): A = implDetailError

  @compileTimeOnly(
    "`value` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
  )
  def `wrapInitTask_\u2603\u2603`[T](@deprecated("unused", "") in: Any): T = implDetailError

  @compileTimeOnly(
    "`value` can only be called on an input task within a task definition macro, such as := or Def.inputTask."
  )
  def `wrapInputTask_\u2603\u2603`[T](@deprecated("unused", "") in: Any): T = implDetailError

  @compileTimeOnly(
    "`evaluated` can only be called on an input task within a task definition macro, such as := or Def.inputTask. To use an input task from a regular task, use `.toTask(\" <args>\").value` instead."
  )
  def `wrapInitInputTask_\u2603\u2603`[T](@deprecated("unused", "") in: Any): T = implDetailError

  @compileTimeOnly(
    "`previous` can only be called on a task within a task or input task definition macro, such as :=, +=, ++=, Def.task, or Def.inputTask."
  )
  def `wrapPrevious_\u2603\u2603`[T](@deprecated("unused", "") in: Any): T = implDetailError

  private def implDetailError =
    sys.error("This method is an implementation detail and should not be referenced.")

end InputWrapper

/** Implementation detail.  The wrap method temporarily holds the input parser (as a Tree, at compile time) until the input task macro processes it. */
object ParserInput:
  /* The name of the wrapper method should be obscure.
   * Wrapper checking is based solely on this name, so it must not conflict with a user method name.
   * The user should never see this method because it is compile-time only and only used internally by the task macros.*/
  private[std] val WrapName = "parser_\u2603\u2603"
  private[std] val WrapInitName = "initParser_\u2603\u2603"

  @compileTimeOnly(
    "`parsed` can only be used within an input task macro, such as := or Def.inputTask."
  )
  def `parser_\u2603\u2603`[T](@deprecated("unused", "") i: Any): T =
    sys.error("This method is an implementation detail and should not be referenced.")

  @compileTimeOnly(
    "`parsed` can only be used within an input task macro, such as := or Def.inputTask."
  )
  def `initParser_\u2603\u2603`[T](@deprecated("unused", "") i: Any): T =
    sys.error("This method is an implementation detail and should not be referenced.")

end ParserInput

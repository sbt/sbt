/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.std

import sbt.SettingKey
import sbt.internal.util.ConsoleAppender
import sbt.internal.util.appmacro.{ Convert, LinterDSL }

import scala.collection.mutable.{ HashSet => MutableSet }
import scala.io.AnsiColor
import scala.reflect.macros.blackbox

abstract class BaseTaskLinterDSL extends LinterDSL {
  def isDynamicTask: Boolean
  def convert: Convert

  override def runLinter(ctx: blackbox.Context)(tree: ctx.Tree): Unit = {
    import ctx.universe._
    val isTask = convert.asPredicate(ctx)
    class traverser extends Traverser {
      private val unchecked = symbolOf[sbt.sbtUnchecked].asClass
      private val taskKeyType = typeOf[sbt.TaskKey[_]]
      private val settingKeyType = typeOf[sbt.SettingKey[_]]
      private val inputKeyType = typeOf[sbt.InputKey[_]]
      private val uncheckedWrappers = MutableSet.empty[Tree]
      var insideIf: Boolean = false
      var insideAnon: Boolean = false
      var disableNoValueReport: Boolean = false

      def handleUncheckedAnnotation(exprAtUseSite: Tree, tt: TypeTree): Unit = {
        tt.original match {
          case Annotated(annot, _) =>
            Option(annot.tpe) match {
              case Some(AnnotatedType(annotations, _)) =>
                val tpeAnnotations = annotations.flatMap(ann => Option(ann.tree.tpe).toList)
                val symAnnotations = tpeAnnotations.map(_.typeSymbol)
                val isUnchecked = symAnnotations.contains(unchecked)
                if (isUnchecked) {
                  // Use expression at use site, arg contains the old expr
                  // Referential equality between the two doesn't hold
                  val removedSbtWrapper = exprAtUseSite match {
                    case Typed(t, _) => t
                    case _           => exprAtUseSite
                  }
                  uncheckedWrappers.add(removedSbtWrapper)
                }
              case _ =>
            }
          case _ =>
        }
      }

      @inline def isKey(tpe: Type): Boolean =
        tpe <:< taskKeyType || tpe <:< settingKeyType || tpe <:< inputKeyType

      def detectAndErrorOnKeyMissingValue(i: Ident): Unit = {
        if (isKey(i.tpe)) {
          val keyName = i.name.decodedName.toString
          ctx.error(i.pos, TaskLinterDSLFeedback.missingValueForKey(keyName))
        } else ()
      }

      override def traverse(tree: ctx.universe.Tree): Unit = {
        tree match {
          case ap @ Apply(TypeApply(Select(_, nme), tpe :: Nil), qual :: Nil) =>
            val shouldIgnore = uncheckedWrappers.contains(ap)
            val wrapperName = nme.decodedName.toString
            val (qualName, isSettingKey) =
              Option(qual.symbol)
                .map(sym => (sym.name.decodedName.toString, qual.tpe <:< typeOf[SettingKey[_]]))
                .getOrElse((ap.pos.lineContent, false))

            if (!isSettingKey && !shouldIgnore && isTask(wrapperName, tpe.tpe, qual)) {
              if (insideIf && !isDynamicTask) {
                // Error on the use of value inside the if of a regular task (dyn task is ok)
                ctx.error(ap.pos, TaskLinterDSLFeedback.useOfValueInsideIfExpression(qualName))
              }
              if (insideAnon) {
                // Error on the use of anonymous functions in any task or dynamic task
                ctx.error(ap.pos, TaskLinterDSLFeedback.useOfValueInsideAnon(qualName))
              }
            }
            traverse(qual)
          case If(condition, thenp, elsep) =>
            traverse(condition)
            val previousInsideIf = insideIf
            insideIf = true
            traverse(thenp)
            traverse(elsep)
            insideIf = previousInsideIf
          case Typed(expr, tpt: TypeTree) if tpt.original != null =>
            handleUncheckedAnnotation(expr, tpt)
            traverse(expr)
            traverse(tpt)
          case Function(vparams, body) =>
            traverseTrees(vparams)
            if (!vparams.exists(_.mods.hasFlag(Flag.SYNTHETIC))) {
              val previousInsideAnon = insideAnon
              insideAnon = true
              traverse(body)
              insideAnon = previousInsideAnon
            } else traverse(body)
          case Block(stmts, expr) =>
            if (!isDynamicTask) {
              /* The missing .value analysis is dumb on purpose because it's expensive.
               * Detecting valid use cases of idents whose type is an sbt key is difficult
               * and dangerous because we may miss some corner cases. Instead, we report
               * on the easiest cases in which we are certain that the user does not want
               * to have a stale key reference. Those are idents in the rhs of a val definition
               * whose name is `_` and those idents that are in statement position inside blocks. */
              stmts.foreach {
                // TODO: Consider using unused names analysis to be able to report on more cases
                case ValDef(_, valName, _, rhs) if valName == termNames.WILDCARD =>
                  rhs match {
                    case i: Ident => detectAndErrorOnKeyMissingValue(i)
                    case _        => ()
                  }
                case i: Ident => detectAndErrorOnKeyMissingValue(i)
                case _        => ()
              }
            }
            traverseTrees(stmts)
            traverse(expr)
          case _ => super.traverse(tree)
        }
      }
    }
    (new traverser).traverse(tree)
  }
}

object TaskLinterDSL extends BaseTaskLinterDSL {
  override val isDynamicTask: Boolean = false
  override def convert: Convert = FullConvert
}

object OnlyTaskLinterDSL extends BaseTaskLinterDSL {
  override val isDynamicTask: Boolean = false
  override def convert: Convert = TaskConvert
}

object TaskDynLinterDSL extends BaseTaskLinterDSL {
  override val isDynamicTask: Boolean = true
  override def convert: Convert = FullConvert
}

object OnlyTaskDynLinterDSL extends BaseTaskLinterDSL {
  override val isDynamicTask: Boolean = true
  override def convert: Convert = TaskConvert
}

object TaskLinterDSLFeedback {
  private final val startBold = if (ConsoleAppender.formatEnabledInEnv) AnsiColor.BOLD else ""
  private final val startRed = if (ConsoleAppender.formatEnabledInEnv) AnsiColor.RED else ""
  private final val startGreen = if (ConsoleAppender.formatEnabledInEnv) AnsiColor.GREEN else ""
  private final val reset = if (ConsoleAppender.formatEnabledInEnv) AnsiColor.RESET else ""

  private final val ProblemHeader = s"${startRed}Problem${reset}"
  private final val SolutionHeader = s"${startGreen}Solution${reset}"

  def useOfValueInsideAnon(task: String) =
    s"""${startBold}The evaluation of `$task` inside an anonymous function is prohibited.$reset
       |
       |${ProblemHeader}: Task invocations inside anonymous functions are evaluated independently of whether the anonymous function is invoked or not.
       |
       |${SolutionHeader}:
       |  1. Make `$task` evaluation explicit outside of the function body if you don't care about its evaluation.
       |  2. Use a dynamic task to evaluate `$task` and pass that value as a parameter to an anonymous function.
    """.stripMargin

  def useOfValueInsideIfExpression(task: String) =
    s"""${startBold}The evaluation of `$task` happens always inside a regular task.$reset
       |
       |${ProblemHeader}: `$task` is inside the if expression of a regular task.
       |  Regular tasks always evaluate task inside the bodies of if expressions.
       |
       |${SolutionHeader}:
       |  1. If you only want to evaluate it when the if predicate is true or false, use a dynamic task.
       |  2. Otherwise, make the static evaluation explicit by evaluating `$task` outside the if expression.
    """.stripMargin

  def missingValueForKey(key: String) =
    s"""${startBold}The key `$key` is not being invoked inside the task definition.$reset
       |
       |${ProblemHeader}: Keys missing `.value` are not initialized and their dependency is not registered.
       |
       |${SolutionHeader}: Replace `$key` by `$key.value` or remove it if unused.
    """.stripMargin
}

package sbt.std

import sbt.internal.util.ConsoleAppender
import sbt.internal.util.appmacro.LinterDSL

import scala.collection.mutable.{ HashSet => MutableSet }
import scala.io.AnsiColor
import scala.reflect.macros.blackbox

object TaskLinterDSL extends LinterDSL {
  override def runLinter(ctx: blackbox.Context)(
      tree: ctx.Tree,
      isApplicableFromContext: => Boolean
  ): Unit = {
    import ctx.universe._
    val isTask = FullConvert.asPredicate(ctx)
    object traverser extends Traverser {
      private val unchecked = symbolOf[sbt.unchecked].asClass
      private val uncheckedWrappers = MutableSet.empty[Tree]
      var insideIf: Boolean = false
      override def traverse(tree: ctx.universe.Tree): Unit = {
        tree match {
          case If(condition, thenp, elsep) =>
            super.traverse(condition)
            insideIf = true
            super.traverse(thenp)
            super.traverse(elsep)
            insideIf = false
          case s @ Apply(TypeApply(Select(_, nme), tpe :: Nil), qual :: Nil) =>
            val shouldIgnore = uncheckedWrappers.contains(s)
            val wrapperName = nme.decodedName.toString
            if (insideIf && !shouldIgnore && isTask(wrapperName, tpe.tpe, qual)) {
              val qualName = qual.symbol.name.decodedName.toString
              ctx.error(s.pos, TaskLinterDSLFeedback.useOfValueInsideIfExpression(qualName))
            }
          // Don't remove, makes the whole analysis work
          case Typed(expr, tt) =>
            super.traverse(expr)
            super.traverse(tt)
          case Typed(expr, tt: TypeTree) if tt.original != null =>
            tt.original match {
              case Annotated(annot, arg) =>
                annot.tpe match {
                  case AnnotatedType(annotations, _) =>
                    val symAnnotations = annotations.map(_.tree.tpe.typeSymbol)
                    val isUnchecked = symAnnotations.contains(unchecked)
                    if (isUnchecked) {
                      val toAdd = arg match {
                        case Typed(`expr`, _) => `expr`
                        case `tree`           => `tree`
                      }
                      uncheckedWrappers.add(toAdd)
                    }
                  case _ =>
                }
            }
            super.traverse(expr)
          case tree => super.traverse(tree)
        }
      }
    }
    if (!isApplicableFromContext) ()
    else traverser.traverse(tree)
  }
}

object TaskLinterDSLFeedback {
  private final val startBold = if (ConsoleAppender.formatEnabled) AnsiColor.BOLD else ""
  private final val startRed = if (ConsoleAppender.formatEnabled) AnsiColor.RED else ""
  private final val startGreen = if (ConsoleAppender.formatEnabled) AnsiColor.GREEN else ""
  private final val reset = if (ConsoleAppender.formatEnabled) AnsiColor.RESET else ""

  def useOfValueInsideIfExpression(offendingValue: String) =
    s"""${startBold}The evaluation of `${offendingValue}` happens always inside a regular task.$reset
       |
       |${startRed}PROBLEM${reset}: `${offendingValue}` is inside the if expression of a regular task.
       |  Regular tasks always evaluate task inside the bodies of if expressions.
       |
       |${startGreen}SOLUTION${reset}:
       |  1. If you only want to evaluate it when the if predicate is true, use a dynamic task.
       |  2. Otherwise, make the static evaluation explicit by evaluating `${offendingValue}` outside the if expression.
    """.stripMargin
}

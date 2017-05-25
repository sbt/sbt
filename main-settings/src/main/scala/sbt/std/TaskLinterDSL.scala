package sbt.std

import sbt.internal.util.ConsoleAppender
import sbt.internal.util.appmacro.{ Convert, Converted, LinterDSL }

import scala.collection.mutable.{ HashSet => MutableSet }
import scala.io.AnsiColor
import scala.reflect.internal.util.Position
import scala.reflect.macros.blackbox

abstract class BaseTaskLinterDSL extends LinterDSL {
  def isDynamicTask: Boolean
  def convert: Convert

  override def runLinter(ctx: blackbox.Context)(tree: ctx.Tree): Unit = {
    import ctx.universe._
    val isTask = convert.asPredicate(ctx)
    class traverser extends Traverser {
      private val unchecked = symbolOf[sbt.sbtUnchecked].asClass
      private val uncheckedWrappers = MutableSet.empty[Tree]
      var insideIf: Boolean = false
      var insideAnon: Boolean = false

      def handleUncheckedAnnotation(exprAtUseSite: Tree, tt: TypeTree): Unit = {
        tt.original match {
          case Annotated(annot, arg) =>
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

      override def traverse(tree: ctx.universe.Tree): Unit = {
        tree match {
          case s @ Apply(TypeApply(Select(selectQual, nme), tpe :: Nil), qual :: Nil) =>
            val shouldIgnore = uncheckedWrappers.contains(s)
            val wrapperName = nme.decodedName.toString
            if (!shouldIgnore && isTask(wrapperName, tpe.tpe, qual)) {
              val qualName =
                if (qual.symbol != null) qual.symbol.name.decodedName.toString
                else s.pos.lineContent
              if (insideIf && !isDynamicTask) {
                // Error on the use of value inside the if of a regular task (dyn task is ok)
                ctx.error(s.pos, TaskLinterDSLFeedback.useOfValueInsideIfExpression(qualName))
              }
              if (insideAnon) {
                // Error on the use of anonymous functions in any task or dynamic task
                ctx.error(s.pos, TaskLinterDSLFeedback.useOfValueInsideAnon(qualName))
              }
            } else traverse(selectQual)
            traverse(qual)
          case If(condition, thenp, elsep) =>
            traverse(condition)
            insideIf = true
            traverse(thenp)
            traverse(elsep)
            insideIf = false
          case Typed(expr, tpt: TypeTree) if tpt.original != null =>
            handleUncheckedAnnotation(expr, tpt)
            traverse(expr)
            traverse(tpt)
          case f @ Function(vparams, body) =>
            super.traverseTrees(vparams)
            if (!vparams.exists(_.mods.hasFlag(Flag.SYNTHETIC))) {
              insideAnon = true
              traverse(body)
              insideAnon = false
            } else traverse(body)
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
  private final val startBold = if (ConsoleAppender.formatEnabled) AnsiColor.BOLD else ""
  private final val startRed = if (ConsoleAppender.formatEnabled) AnsiColor.RED else ""
  private final val startGreen = if (ConsoleAppender.formatEnabled) AnsiColor.GREEN else ""
  private final val reset = if (ConsoleAppender.formatEnabled) AnsiColor.RESET else ""

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

  /*  If(
    Ident(TermName("condition")),
    Typed(
      Typed(Apply(TypeApply(Select(Ident(sbt.std.InputWrapper),
                                   TermName("wrapInitTask_$u2603$u2603")),
                            List(TypeTree())),
                  List(Ident(TermName("foo")))),
            TypeTree()),
      TypeTree().setOriginal(
        Annotated(
          Apply(Select(New(Ident(TypeName("unchecked"))), termNames.CONSTRUCTOR), List()),
          Typed(Apply(TypeApply(Select(Ident(sbt.std.InputWrapper),
                                       TermName("wrapInitTask_$u2603$u2603")),
                                List(TypeTree())),
                      List(Ident(TermName("foo")))),
                TypeTree())
        ))
    ),
    Typed(
      Typed(Apply(TypeApply(Select(Ident(sbt.std.InputWrapper),
                                   TermName("wrapInitTask_$u2603$u2603")),
                            List(TypeTree())),
                  List(Ident(TermName("bar")))),
            TypeTree()),
      TypeTree().setOriginal(
        Annotated(
          Apply(Select(New(Ident(TypeName("unchecked"))), termNames.CONSTRUCTOR), List()),
          Typed(Apply(TypeApply(Select(Ident(sbt.std.InputWrapper),
                                       TermName("wrapInitTask_$u2603$u2603")),
                                List(TypeTree())),
                      List(Ident(TermName("bar")))),
                TypeTree())
        ))
    )
  )*/

  /*  Block(
    List(
      ValDef(
        Modifiers(),
        TermName("anon"),
        TypeTree(),
        Function(
          List(),
          Apply(
            Select(
              Typed(
                Apply(TypeApply(Select(Ident(sbt.std.InputWrapper),
                                       TermName("wrapInitTask_$u2603$u2603")),
                                List(TypeTree())),
                      List(Ident(TermName("fooNeg")))),
                TypeTree()
              ),
              TermName("$plus")
            ),
            List(Literal(Constant("")))
          )
        )
      )),
    If(
      Ident(TermName("condition")),
      Apply(Select(Ident(TermName("anon")), TermName("apply")), List()),
      Apply(Select(Ident(TermName("anon")), TermName("apply")), List())
    )
  )*/
}

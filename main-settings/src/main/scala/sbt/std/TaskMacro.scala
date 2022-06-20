/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import Def.{ Initialize, Setting }
import sbt.util.{ Applicative, Monad }
import sbt.internal.util.Types.{ Id, Compose, const, idFun }
import sbt.internal.util.appmacro.{
  Cont,
  ContextUtil,
  Convert,
  // Instance,
  // LinterDSL,
  // MixedBuilder,
  // MonadInstance
}
// import Instance.Transform
import sbt.internal.util.{ AList, LinePosition, NoPosition, SourcePosition, ~> }
import sbt.internal.util.complete.Parser

import language.experimental.macros
import scala.annotation.tailrec
import scala.reflect.internal.util.UndefinedPosition
import scala.quoted.*

object TaskMacro:
  final val AssignInitName = "set"
  final val Append1InitName = "append1"
  final val AppendNInitName = "appendN"
  final val Remove1InitName = "remove1"
  final val RemoveNInitName = "removeN"
  final val TransformInitName = "transform"
  final val InputTaskCreateDynName = "createDyn"
  final val InputTaskCreateFreeName = "createFree"
  final val append1Migration =
    "`<+=` operator is removed. Try `lhs += { x.value }`\n  or see https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html."
  final val appendNMigration =
    "`<++=` operator is removed. Try `lhs ++= { x.value }`\n  or see https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html."
  final val assignMigration =
    """`<<=` operator is removed. Use `key := { x.value }` or `key ~= (old => { newValue })`.
      |See https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html""".stripMargin

  type F[x] = Initialize[Task[x]]

  object ContSyntax extends Cont
  import ContSyntax.*

  // import LinterDSL.{ Empty => EmptyLinter }

  def taskMacroImpl[A1: Type](t: Expr[A1])(using qctx: Quotes): Expr[Initialize[Task[A1]]] =
    t match
      case '{ if ($cond) then $thenp else $elsep } => mkIfS[A1](t)
      case _ =>
        val convert1 = new FullConvert(qctx)
        convert1.contMapN[A1, F, Id](t, convert1.appExpr)

  def mkIfS[A1: Type](t: Expr[A1])(using qctx: Quotes): Expr[Initialize[Task[A1]]] =
    t match
      case '{ if ($cond) then $thenp else $elsep } =>
        '{
          Def.ifS[A1](Def.task($cond))(Def.task[A1]($thenp))(Def.task[A1]($elsep))
        }

  def taskDynMacroImpl[A1: Type](
      t: Expr[Initialize[Task[A1]]]
  )(using qctx: Quotes): Expr[Initialize[Task[A1]]] =
    val convert1 = new FullConvert(qctx)
    convert1.contFlatMap[A1, F, Id](t, convert1.appExpr)

  /*
  def taskIfMacroImpl[A: Type](
      c: blackbox.Context
  )(a: c.Expr[A]): c.Expr[Initialize[Task[A]]] = {
    import c.universe._
    a.tree match {
      case Block(stat, If(cond, thenp, elsep)) =>
        c.Expr[Initialize[Task[A]]](mkIfS(c)(Block(stat, cond), thenp, elsep))
      case If(cond, thenp, elsep) =>
        c.Expr[Initialize[Task[A]]](mkIfS(c)(cond, thenp, elsep))
      case x => ContextUtil.unexpectedTree(x)
    }
  }
   */

  /** Implementation of := macro for settings. */
  def settingAssignMacroImpl[A1: Type](rec: Expr[Scoped.DefinableSetting[A1]], v: Expr[A1])(using
      qctx: Quotes
  ): Expr[Setting[A1]] =
    import qctx.reflect.*
    val init = SettingMacro.settingMacroImpl[A1](v)
    '{
      $rec.set0($init, $sourcePosition)
    }

  // Error macros (Restligeist)
  // These macros are there just so we can fail old operators like `<<=` and provide useful migration information.

  def fakeSettingAssignImpl[A1: Type](app: Expr[Initialize[A1]])(using
      qctx: Quotes
  ): Expr[Setting[A1]] =
    import qctx.reflect.*
    report.errorAndAbort(TaskMacro.assignMigration)

  def fakeSettingAppend1Position[A1: Type, A2: Type](
      @deprecated("unused", "") v: Expr[Initialize[A2]]
  )(using
      qctx: Quotes
  ): Expr[Setting[A1]] =
    import qctx.reflect.*
    report.errorAndAbort(TaskMacro.append1Migration)

  def fakeSettingAppendNPosition[A1: Type, A2: Type](
      @deprecated("unused", "") vs: Expr[Initialize[A2]]
  )(using
      qctx: Quotes
  ): Expr[Setting[A1]] =
    import qctx.reflect.*
    report.errorAndAbort(TaskMacro.appendNMigration)

  def fakeItaskAssignPosition[A1: Type](
      @deprecated("unused", "") app: Expr[Initialize[Task[A1]]]
  )(using qctx: Quotes): Expr[Setting[Task[A1]]] =
    import qctx.reflect.*
    report.errorAndAbort(TaskMacro.assignMigration)

  def fakeTaskAppend1Position[A1: Type, A2: Type](
      @deprecated("unused", "") v: Expr[Initialize[Task[A2]]]
  )(using
      qctx: Quotes
  ): Expr[Setting[Task[A1]]] =
    import qctx.reflect.*
    report.errorAndAbort(TaskMacro.append1Migration)

  def fakeTaskAppendNPosition[A1: Type, A2: Type](
      @deprecated("unused", "") vs: Expr[Initialize[Task[A2]]]
  )(using
      qctx: Quotes
  ): Expr[Setting[Task[A1]]] =
    import qctx.reflect.*
    report.errorAndAbort(TaskMacro.appendNMigration)

  // Implementations of <<= macro variations for tasks and settings.
  // These just get the source position of the call site.

  def settingSetImpl[A1: Type](
      rec: Expr[Scoped.DefinableSetting[A1]],
      app: Expr[Def.Initialize[A1]]
  )(using
      qctx: Quotes
  ): Expr[Setting[A1]] =
    '{
      $rec.set0($app, $sourcePosition)
    }

  /** Implementation of := macro for tasks. */
  def inputTaskAssignMacroImpl[A1: Type](v: Expr[A1])(using
      qctx: Quotes
  ): Expr[Setting[InputTask[A1]]] =
    val init = inputTaskMacroImpl[A1](v)
    // val assign = transformMacroImpl(init.tree)(AssignInitName)
    // Expr[Setting[InputTask[A1]]](assign)
    ???

  /** Implementation of += macro for settings. */
  def settingAppend1Impl[A1: Type, A2: Type](rec: Expr[SettingKey[A1]], v: Expr[A2])(using
      qctx: Quotes,
  ): Expr[Setting[A1]] =
    import qctx.reflect.*
    // To allow Initialize[Task[A]] in the position of += RHS, we're going to call "taskValue" automatically.
    if TypeRepr.of[A2] <:< TypeRepr.of[Def.Initialize[Task[_]]] then
      Type.of[A2] match
        case '[Def.Initialize[Task[a]]] =>
          Expr.summon[Append.Value[A1, Task[a]]] match
            case Some(ev) =>
              val v2 = v.asExprOf[Def.Initialize[Task[a]]]
              '{
                $rec.+=($v2.taskValue)(using $ev)
              }
            case _ =>
              report.errorAndAbort(s"Append.Value[${Type.of[A1]}, ${Type.of[Task[a]]}] missing")
    else
      Expr.summon[Append.Value[A1, A2]] match
        case Some(ev) =>
          val init = SettingMacro.settingMacroImpl[A2](v)
          '{
            $rec.append1[A2]($init)(using $ev)
          }
        case _ => report.errorAndAbort(s"Append.Value[${Type.of[A1]}, ${Type.of[A2]}] missing")

  /*
  private[this] def transformMacroImpl[A](using qctx: Quotes)(init: Expr[A])(
      newName: String
  ): qctx.reflect.Term = {
    import qctx.reflect.*
    // val target =
    //   c.macroApplication match {
    //     case Apply(Select(prefix, _), _) => prefix
    //     case x                           => ContextUtil.unexpectedTree(x)
    //   }
    Apply.apply(
      Select(This, TermName(newName).encodedName),
      init.asTerm :: sourcePosition.asTerm :: Nil
    )
  }
   */

  private[sbt] def sourcePosition(using qctx: Quotes): Expr[SourcePosition] =
    import qctx.reflect.*
    val pos = Position.ofMacroExpansion
    if pos.startLine >= 0 && pos.sourceCode != None then
      val name = Expr(pos.sourceCode.get)
      val line = Expr(pos.startLine)
      '{ LinePosition($name, $line) }
    else '{ NoPosition }

  /*
  private[this] def settingSource(c: blackbox.Context, path: String, name: String): String = {
    @tailrec def inEmptyPackage(s: c.Symbol): Boolean = s != c.universe.NoSymbol && (
      s.owner == c.mirror.EmptyPackage || s.owner == c.mirror.EmptyPackageClass || inEmptyPackage(
        s.owner
      )
    )
    c.internal.enclosingOwner match {
      case ec if !ec.isStatic       => name
      case ec if inEmptyPackage(ec) => path
      case ec                       => s"(${ec.fullName}) $name"
    }
  }

  private[this] def constant[A1: c.TypeTag](c: blackbox.Context, t: T): c.Expr[A1] = {
    import c.universe._
    c.Expr[A1](Literal(Constant(t)))
  }
   */

  def inputTaskMacroImpl[A1: Type](tree: Expr[A1])(using
      qctx: Quotes
  ): Expr[Initialize[InputTask[A1]]] =
    inputTaskMacro0[A1](tree)

  // def inputTaskDynMacroImpl[A1: Type](t: c.Expr[Initialize[Task[A1]]])(using qctx: Quotes): c.Expr[Initialize[InputTask[A1]]] =
  //   inputTaskDynMacro0[A1](c)(t)

  private[this] def inputTaskMacro0[A1: Type](tree: Expr[A1])(using
      qctx: Quotes
  ): Expr[Initialize[InputTask[A1]]] =
    iInitializeMacro(tree) { et =>
      val pt = iParserMacro(et) { pt =>
        iTaskMacro(pt)
      }
      '{ InputTask.make($pt) }
    }

  private[this] def iInitializeMacro[F1[_]: Type, A1: Type](tree: Expr[A1])(
      f: Expr[A1] => Expr[F1[A1]]
  )(using qctx: Quotes): Expr[Initialize[F1[A1]]] =
    import qctx.reflect.*
    import InputWrapper.*
    val convert1 = new InputInitConvert(qctx)
    import convert1.Converted

    def wrapInitTask[A2: Type](tree: Term): Term =
      val expr = tree.asExprOf[Initialize[Task[A2]]]
      '{
        InputWrapper.wrapTask[A2](InputWrapper.wrapInit[A2]($expr))
      }.asTerm

    def wrapInitParser[A2: Type](tree: Term): Term =
      val expr = tree.asExprOf[Initialize[State => Parser[A2]]]
      '{
        ParserInput.wrap[A2](InputWrapper.wrapInit[State => Parser[A2]]($expr))
      }.asTerm

    def wrapInitInput[A2: Type](tree: Term): Term =
      val expr = tree.asExprOf[Initialize[InputTask[A2]]]
      wrapInput[A2]('{
        InputWrapper.wrapInit[InputTask[A2]]($expr)
      }.asTerm)

    def wrapInput[A2: Type](tree: Term): Term =
      val expr = tree.asExprOf[InputTask[A1]]
      '{
        InputWrapper.wrapTask[A2](ParserInput.wrap[Task[A2]]($expr.parser))
      }.asTerm

    def expand(nme: String, tpeRepr: TypeRepr, tree: Term): Converted =
      tpeRepr.asType match
        case '[tpe] =>
          nme match
            case WrapInitTaskName         => Converted.success(wrapInitTask[tpe](tree))
            case WrapPreviousName         => Converted.success(wrapInitTask[tpe](tree))
            case ParserInput.WrapInitName => Converted.success(wrapInitParser[tpe](tree))
            case WrapInitInputName        => Converted.success(wrapInitInput[tpe](tree))
            case WrapInputName            => Converted.success(wrapInput[tpe](tree))
            case _                        => Converted.NotApplicable()

    def conditionInputTaskTree(t: Term): Term =
      convert1.transformWrappers(
        tree = t,
        subWrapper = (nme, tpe, tree, original) => expand(nme, tpe, tree),
        owner = Symbol.spliceOwner,
      )

    val inner: convert1.TermTransform[F1] = (in: Term) => f(in.asExprOf[A1]).asTerm
    val cond = conditionInputTaskTree(tree.asTerm).asExprOf[A1]
    convert1.contMapN[A1, Def.Initialize, F1](cond, convert1.appExpr, inner)

  private[this] def iParserMacro[F1[_]: Type, A1: Type](tree: Expr[A1])(
      f: Expr[A1] => Expr[F1[A1]]
  )(using qctx: Quotes): Expr[State => Parser[F1[A1]]] =
    import qctx.reflect.*
    val convert1 = new ParserConvert(qctx)
    val inner: convert1.TermTransform[F1] = (in: Term) => f(in.asExprOf[A1]).asTerm
    convert1.contMapN[A1, ParserInstance.F1, F1](tree, convert1.appExpr, inner)

  private[this] def iTaskMacro[A1: Type](tree: Expr[A1])(using qctx: Quotes): Expr[Task[A1]] =
    import qctx.reflect.*
    val convert1 = new TaskConvert(qctx)
    convert1.contMapN[A1, Task, Id](tree, convert1.appExpr)

  /*
  private[this] def inputTaskDynMacro0[A1: Type](
      t: Expr[Initialize[Task[A1]]]
  )(using qctx: Quotes): Expr[Initialize[InputTask[A1]]] = {
    import c.universe.{ Apply => ApplyTree, _ }
    import internal.decorators._

    val tag = implicitly[Type[A1]]
    val util = ContextUtil[c.type](c)
    val it = Ident(util.singleton(InputTask))
    val isParserWrapper = InitParserConvert.asPredicate(c)
    val isTaskWrapper = FullConvert.asPredicate(c)
    val isAnyWrapper = (n: String, tpe: Type, tr: Tree) =>
      isParserWrapper(n, tpe, tr) || isTaskWrapper(n, tpe, tr)
    val ttree = t.tree
    val defs = util.collectDefs(ttree, isAnyWrapper)
    val checkQual = util.checkReferences(defs, isAnyWrapper, weakTypeOf[Initialize[InputTask[Any]]])

    // the Symbol for the anonymous function passed to the appropriate Instance.map/flatMap/pure method
    // this Symbol needs to be known up front so that it can be used as the owner of synthetic vals
    val functionSym = util.functionSymbol(ttree.pos)
    var result: Option[(Tree, Type, ValDef)] = None

    // original is the Tree being replaced.  It is needed for preserving attributes.
    def subWrapper(tpe: Type, qual: Tree, original: Tree): Tree =
      if (result.isDefined) {
        c.error(
          qual.pos,
          "Implementation restriction: a dynamic InputTask can only have a single input parser."
        )
        EmptyTree
      } else {
        qual.foreach(checkQual)
        val vd = util.freshValDef(tpe, qual.symbol.pos, functionSym) // val $x: <tpe>
        result = Some((qual, tpe, vd))
        val tree = util.refVal(original, vd) // $x
        tree.setPos(
          qual.pos
        ) // position needs to be set so that wrapKey passes the position onto the wrapper
        assert(tree.tpe != null, "Null type: " + tree)
        tree.setType(tpe)
        tree
      }
    // Tree for InputTask.<name>[<tpeA>, <tpeB>](arg1)(arg2)
    def inputTaskCreate(name: String, tpeA: Type, tpeB: Type, arg1: Tree, arg2: Tree) = {
      val typedApp = TypeApply(util.select(it, name), TypeTree(tpeA) :: TypeTree(tpeB) :: Nil)
      val app = ApplyTree(ApplyTree(typedApp, arg1 :: Nil), arg2 :: Nil)
      c.Expr[Initialize[InputTask[A1]]](app)
    }
    // Tree for InputTask.createFree[<tpe>](arg1)
    def inputTaskCreateFree(tpe: Type, arg: Tree) = {
      val typedApp = TypeApply(util.select(it, InputTaskCreateFreeName), TypeTree(tpe) :: Nil)
      val app = ApplyTree(typedApp, arg :: Nil)
      c.Expr[Initialize[InputTask[A1]]](app)
    }
    def expandTask[I: WeakTypeTag](dyn: Boolean, tx: Tree): c.Expr[Initialize[Task[I]]] =
      if (dyn)
        taskDynMacroImpl[I](c)(c.Expr[Initialize[Task[I]]](tx))
      else
        taskMacroImpl[I](c)(c.Expr[I](tx))
    def wrapTag[I: WeakTypeTag]: WeakTypeTag[Initialize[Task[I]]] = weakTypeTag

    def sub(name: String, tpe: Type, qual: Tree, selection: Tree): Converted[c.type] = {
      val tag = Type[A1](tpe)
      InitParserConvert(c)(name, qual)(tag) transform { tree =>
        subWrapper(tpe, tree, selection)
      }
    }

    val tx = util.transformWrappers(ttree, (n, tpe, tree, replace) => sub(n, tpe, tree, replace))
    result match {
      case Some((p, tpe, param)) =>
        val fCore = util.createFunction(param :: Nil, tx, functionSym)
        val bodyTpe = wrapTag(tag).tpe
        val fTpe = util.functionType(tpe :: Nil, bodyTpe)
        val fTag = Type[Any](fTpe) // don't know the actual type yet, so use Any
        val fInit = expandTask(false, fCore)(fTag).tree
        inputTaskCreate(InputTaskCreateDynName, tpe, tag.tpe, p, fInit)
      case None =>
        val init = expandTask[A1](true, tx).tree
        inputTaskCreateFree(tag.tpe, init)
    }
  }
   */
end TaskMacro

object DefinableTaskMacro:
  def taskSetImpl[A1: Type](
      rec: Expr[Scoped.DefinableTask[A1]],
      app: Expr[Def.Initialize[Task[A1]]]
  )(using
      qctx: Quotes
  ): Expr[Setting[Task[A1]]] =
    val pos = TaskMacro.sourcePosition
    '{
      $rec.set0($app, $pos)
    }
end DefinableTaskMacro

/*
object PlainTaskMacro:
  def task[A1](t: T): Task[A1] = macro taskImpl[A1]
  def taskImpl[A1: Type](c: blackbox.Context)(t: c.Expr[A1]): c.Expr[Task[A1]] =
    Instance.contImpl[A1, Id](c, TaskInstance, TaskConvert, MixedBuilder, OnlyTaskLinterDSL)(
      Left(t),
      Instance.idTransform[c.type]
    )

  def taskDyn[A1](t: Task[A1]): Task[A1] = macro taskDynImpl[A1]
  def taskDynImpl[A1: Type](c: blackbox.Context)(t: c.Expr[Task[A1]]): c.Expr[Task[A1]] =
    Instance.contImpl[A1, Id](c, TaskInstance, TaskConvert, MixedBuilder, OnlyTaskDynLinterDSL)(
      Right(t),
      Instance.idTransform[c.type]
    )

end PlainTaskMacro
 */

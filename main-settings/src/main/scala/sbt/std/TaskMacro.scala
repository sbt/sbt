/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import Def.{ Initialize, Setting }
import sbt.internal.util.Types.Id
import sbt.internal.util.appmacro.{
  Cont,
  // Instance,
  // LinterDSL,
  // MixedBuilder,
  // MonadInstance
}
// import Instance.Transform
import sbt.internal.util.{ LinePosition, NoPosition, SourcePosition }

import language.experimental.macros
import scala.quoted.*
import sjsonnew.JsonFormat
import sbt.util.BuildWideCacheConfiguration

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
    """`<<=` operator is removed. Use `key := { x.value }` or `key ~= {old => newValue }`.
      |See https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html""".stripMargin

  type F[x] = Initialize[Task[x]]

  object ContSyntax extends Cont
  import ContSyntax.*

  // import LinterDSL.{ Empty => EmptyLinter }

  def taskMacroImpl[A1: Type](t: Expr[A1], cached: Boolean)(using
      qctx: Quotes
  ): Expr[Initialize[Task[A1]]] =
    t match
      case '{ if ($cond) then $thenp else $elsep } => taskIfImpl[A1](t, cached)
      case _ =>
        val convert1 = new FullConvert(qctx, 0)
        if cached then
          convert1.contMapN[A1, F, Id](
            t,
            convert1.appExpr,
            Some('{
              InputWrapper.`wrapInitTask_\u2603\u2603`[BuildWideCacheConfiguration](
                Def.cacheConfiguration
              )
            })
          )
        else convert1.contMapN[A1, F, Id](t, convert1.appExpr, None)

  def taskIfImpl[A1: Type](expr: Expr[A1], cached: Boolean)(using
      qctx: Quotes
  ): Expr[Initialize[Task[A1]]] =
    import qctx.reflect.*
    val convert1 = new FullConvert(qctx, 1000)
    expr match
      case '{ if ($cond) then $thenp else $elsep } =>
        '{
          Def.ifS[A1](Def.task($cond))(Def.task[A1]($thenp))(Def.task[A1]($elsep))
        }
      case '{ ${ stats }: a; if ($cond) then $thenp else $elsep } =>
        '{
          Def.ifS[A1](Def.task { $stats; $cond })(Def.task[A1]($thenp))(Def.task[A1]($elsep))
        }
      case _ =>
        report.errorAndAbort(s"Def.taskIf(...) must contain if expression but found ${expr.asTerm}")

  def taskDynMacroImpl[A1: Type](
      t: Expr[Initialize[Task[A1]]]
  )(using qctx: Quotes): Expr[Initialize[Task[A1]]] =
    val convert1 = new FullConvert(qctx, 1000)
    convert1.contFlatMap[A1, F, Id](t, convert1.appExpr, None)

  /** Translates <task: TaskKey[T]>.previous(format) to Previous.runtime(<task>)(format).value */
  def previousImpl[A1: Type](t: Expr[TaskKey[A1]])(using
      qctx: Quotes
  ): Expr[Option[A1]] =
    import qctx.reflect.*
    Expr.summon[JsonFormat[A1]] match
      case Some(ev) =>
        '{
          InputWrapper.`wrapInitTask_\u2603\u2603`[Option[A1]](Previous.runtime[A1]($t)(using $ev))
        }
      case _ => report.errorAndAbort(s"JsonFormat[${Type.show[A1]}] missing")

  /** Implementation of := macro for settings. */
  def settingAssignMacroImpl[A1: Type](rec: Expr[Scoped.DefinableSetting[A1]], v: Expr[A1])(using
      qctx: Quotes
  ): Expr[Setting[A1]] =
    val init = SettingMacro.settingMacroImpl[A1](v)
    '{
      $rec.set0($init, $sourcePosition)
    }

  // Error macros (Restligeist)
  // These macros are there just so we can fail old operators like `<<=` and provide useful migration information.

  def errorAndAbort(message: String)(using quotes: Quotes): Nothing =
    quotes.reflect.report.errorAndAbort(message)

  def fakeAssignImpl(using qctx: Quotes): Nothing =
    qctx.reflect.report.errorAndAbort(assignMigration)

  def fakeAppend1Impl(using qctx: Quotes): Nothing =
    qctx.reflect.report.errorAndAbort(append1Migration)

  def fakeAppendNImpl(using qctx: Quotes): Nothing =
    qctx.reflect.report.errorAndAbort(appendNMigration)

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

  /** Implementation of += macro for settings. */
  def settingAppend1Impl[A1: Type, A2: Type](rec: Expr[SettingKey[A1]], v: Expr[A2])(using
      qctx: Quotes,
  ): Expr[Setting[A1]] =
    import qctx.reflect.*
    // To allow Initialize[Task[A]] in the position of += RHS, we're going to call "taskValue" automatically.
    Type.of[A2] match
      case '[Def.Initialize[Task[a]]] =>
        Expr.summon[Append.Value[A1, Task[a]]] match
          case Some(ev) =>
            val v2 = v.asExprOf[Def.Initialize[Task[a]]]
            '{
              $rec.+=($v2.taskValue)(using $ev)
            }
          case _ =>
            report.errorAndAbort(s"Append.Value[${Type.show[A1]}, ${Type.show[Task[a]]}] missing")
      case _ =>
        Expr.summon[Append.Value[A1, A2]] match
          case Some(ev) =>
            val init = SettingMacro.settingMacroImpl[A2](v)
            '{
              $rec.append1[A2]($init)(using $ev)
            }
          case _ =>
            report.errorAndAbort(s"Append.Value[${Type.show[A1]}, ${Type.show[A2]}] missing")

  /*
  private def transformMacroImpl[A](using qctx: Quotes)(init: Expr[A])(
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
  private def settingSource(c: blackbox.Context, path: String, name: String): String = {
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

  private def constant[A1: c.TypeTag](c: blackbox.Context, t: T): c.Expr[A1] = {
    import c.universe._
    c.Expr[A1](Literal(Constant(t)))
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

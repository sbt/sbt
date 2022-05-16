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

import language.experimental.macros
import scala.annotation.tailrec
import scala.reflect.internal.util.UndefinedPosition
import scala.quoted.*

/** Instance for the monad/applicative functor for plain Tasks. */
/*
object TaskInstance:
  import TaskExtra._

  given taskMonad: Monad[Task] with
    type F[a] = Task[a]
    override def pure[A1](a: () => A1): Task[A1] = toTask(a)

    override def ap[A1, A2](ff: Task[A1 => A2])(in: Task[A1]): Task[A2] =
      multT2Task((in, ff)).map { case (x, f) =>
        f(x)
      }

    override def map[A1, A2](in: Task[A1])(f: A1 => A2): Task[A2] = in.map(f)
    override def flatMap[A1, A2](in: F[A1])(f: A1 => F[A2]): F[A2] = in.flatMap(f)
    override def flatten[A1](in: Task[Task[A1]]): Task[A1] = in.flatMap(idFun[Task[A1]])
end TaskInstance
 */

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
        val convert1: Convert[qctx.type] = new FullConvert(qctx)
        convert1.contMapN[A1, F, Id](t, convert1.idTransform)

  def mkIfS[A1: Type](using
      qctx: Quotes
  )(t: Expr[A1]): Expr[Initialize[Task[A1]]] =
    t match
      case '{ if ($cond) then $thenp else $elsep } =>
        '{
          Def.ifS[A1](Def.task($cond))(Def.task[A1]($thenp))(Def.task[A1]($elsep))
        }

/*
  def taskDynMacroImpl[A1: Type](
      c: blackbox.Context
  )(t: c.Expr[Initialize[Task[A1]]]): c.Expr[Initialize[Task[A1]]] =
    Instance.contImpl[A1, Id](c, FullInstance, FullConvert, MixedBuilder, TaskDynLinterDSL)(
      Right(t),
      Instance.idTransform[c.type]
    )

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

  /** Implementation of := macro for settings. */
  def settingAssignMacroImpl[A1: Type](
      c: blackbox.Context
  )(v: c.Expr[A1]): c.Expr[Setting[A1]] = {
    val init = SettingMacro.settingMacroImpl[A1](c)(v)
    val assign = transformMacroImpl(c)(init.tree)(AssignInitName)
    c.Expr[Setting[A1]](assign)
  }

  /** Implementation of := macro for tasks. */
  def taskAssignMacroImpl[A1: Type](
      c: blackbox.Context
  )(v: c.Expr[A1]): c.Expr[Setting[Task[A1]]] = {
    val init = taskMacroImpl[A1](c)(v)
    val assign = transformMacroImpl(c)(init.tree)(AssignInitName)
    c.Expr[Setting[Task[A1]]](assign)
  }

  // Error macros (Restligeist)
  // These macros are there just so we can fail old operators like `<<=` and provide useful migration information.

  def fakeSettingAssignPosition[A1: Type](c: blackbox.Context)(
      @deprecated("unused", "") app: c.Expr[Initialize[A1]]
  ): c.Expr[Setting[A1]] =
    ContextUtil.selectMacroImpl[Setting[A1]](c)((_, pos) => c.abort(pos, assignMigration))

  def fakeSettingAppend1Position[S: Type, V: Type](
      c: blackbox.Context
  )(@deprecated("unused", "") v: c.Expr[Initialize[V]])(
      @deprecated("unused", "") a: c.Expr[Append.Value[S, V]]
  ): c.Expr[Setting[S]] =
    ContextUtil.selectMacroImpl[Setting[S]](c)((_, pos) => c.abort(pos, append1Migration))

  def fakeSettingAppendNPosition[S: Type, V: Type](
      c: blackbox.Context
  )(@deprecated("unused", "") vs: c.Expr[Initialize[V]])(
      @deprecated("unused", "") a: c.Expr[Append.Values[S, V]]
  ): c.Expr[Setting[S]] =
    ContextUtil.selectMacroImpl[Setting[S]](c)((_, pos) => c.abort(pos, appendNMigration))

  def fakeItaskAssignPosition[A1: Type](c: blackbox.Context)(
      @deprecated("unused", "") app: c.Expr[Initialize[Task[A1]]]
  ): c.Expr[Setting[Task[A1]]] =
    ContextUtil.selectMacroImpl[Setting[Task[A1]]](c)((_, pos) => c.abort(pos, assignMigration))

  def fakeTaskAppend1Position[S: Type, V: Type](
      c: blackbox.Context
  )(@deprecated("unused", "") v: c.Expr[Initialize[Task[V]]])(
      @deprecated("unused", "") a: c.Expr[Append.Value[S, V]]
  ): c.Expr[Setting[Task[S]]] =
    ContextUtil.selectMacroImpl[Setting[Task[S]]](c)((_, pos) => c.abort(pos, append1Migration))

  def fakeTaskAppendNPosition[S: Type, V: Type](
      c: blackbox.Context
  )(@deprecated("unused", "") vs: c.Expr[Initialize[Task[V]]])(
      @deprecated("unused", "") a: c.Expr[Append.Values[S, V]]
  ): c.Expr[Setting[Task[S]]] =
    ContextUtil.selectMacroImpl[Setting[Task[S]]](c)((_, pos) => c.abort(pos, appendNMigration))

  // Implementations of <<= macro variations for tasks and settings.
  // These just get the source position of the call site.

  def itaskAssignPosition[A1: Type](
      c: blackbox.Context
  )(app: c.Expr[Initialize[Task[A1]]]): c.Expr[Setting[Task[A1]]] =
    settingAssignPosition(c)(app)

  def taskAssignPositionT[A1: Type](
      c: blackbox.Context
  )(app: c.Expr[Task[A1]]): c.Expr[Setting[Task[A1]]] =
    itaskAssignPosition(c)(c.universe.reify { Def.valueStrict(app.splice) })

  def taskAssignPositionPure[A1: Type](
      c: blackbox.Context
  )(app: c.Expr[A1]): c.Expr[Setting[Task[A1]]] =
    taskAssignPositionT(c)(c.universe.reify { TaskExtra.constant(app.splice) })

  def taskTransformPosition[S: Type](
      c: blackbox.Context
  )(f: c.Expr[S => S]): c.Expr[Setting[Task[S]]] =
    c.Expr[Setting[Task[S]]](transformMacroImpl(c)(f.tree)(TransformInitName))

  def settingTransformPosition[S: Type](
      c: blackbox.Context
  )(f: c.Expr[S => S]): c.Expr[Setting[S]] =
    c.Expr[Setting[S]](transformMacroImpl(c)(f.tree)(TransformInitName))

  def itaskTransformPosition[S: Type](
      c: blackbox.Context
  )(f: c.Expr[S => S]): c.Expr[Setting[S]] =
    c.Expr[Setting[S]](transformMacroImpl(c)(f.tree)(TransformInitName))

  def settingAssignPure[A1: Type](c: blackbox.Context)(app: c.Expr[A1]): c.Expr[Setting[A1]] =
    settingAssignPosition(c)(c.universe.reify { Def.valueStrict(app.splice) })

  def settingAssignPosition[A1: Type](
      c: blackbox.Context
  )(app: c.Expr[Initialize[A1]]): c.Expr[Setting[A1]] =
    c.Expr[Setting[A1]](transformMacroImpl(c)(app.tree)(AssignInitName))

  /** Implementation of := macro for tasks. */
  def inputTaskAssignMacroImpl[A1: Type](
      c: blackbox.Context
  )(v: c.Expr[A1]): c.Expr[Setting[InputTask[A1]]] = {
    val init = inputTaskMacroImpl[A1](c)(v)
    val assign = transformMacroImpl(c)(init.tree)(AssignInitName)
    c.Expr[Setting[InputTask[A1]]](assign)
  }

  /** Implementation of += macro for tasks. */
  def taskAppend1Impl[A1: Type, U: Type](
      c: blackbox.Context
  )(v: c.Expr[U])(a: c.Expr[Append.Value[A1, U]]): c.Expr[Setting[Task[A1]]] = {
    val init = taskMacroImpl[U](c)(v)
    val append = appendMacroImpl(c)(init.tree, a.tree)(Append1InitName)
    c.Expr[Setting[Task[A1]]](append)
  }

  /** Implementation of += macro for settings. */
  def settingAppend1Impl[A1: Type, U: Type](
      c: blackbox.Context
  )(v: c.Expr[U])(a: c.Expr[Append.Value[A1, U]]): c.Expr[Setting[A1]] = {
    import c.universe._
    val ttpe = c.weakTypeOf[A1]
    val typeArgs = ttpe.typeArgs
    v.tree.tpe match {
      // To allow Initialize[Task[A]] in the position of += RHS, we're going to call "taskValue" automatically.
      case tpe
          if typeArgs.nonEmpty && (typeArgs.head weak_<:< c.weakTypeOf[Task[_]])
            && (tpe weak_<:< c.weakTypeOf[Initialize[_]]) =>
        c.macroApplication match {
          case Apply(Apply(TypeApply(Select(preT, _), _), _), _) =>
            val tree = Apply(
              TypeApply(Select(preT, TermName("+=").encodedName), TypeTree(typeArgs.head) :: Nil),
              Select(v.tree, TermName("taskValue").encodedName) :: Nil
            )
            c.Expr[Setting[A1]](tree)
          case x => ContextUtil.unexpectedTree(x)
        }
      case _ =>
        val init = SettingMacro.settingMacroImpl[U](c)(v)
        val append = appendMacroImpl(c)(init.tree, a.tree)(Append1InitName)
        c.Expr[Setting[A1]](append)
    }
  }

  /** Implementation of ++= macro for tasks. */
  def taskAppendNImpl[A1: Type, U: Type](
      c: blackbox.Context
  )(vs: c.Expr[U])(a: c.Expr[Append.Values[A1, U]]): c.Expr[Setting[Task[A1]]] = {
    val init = taskMacroImpl[U](c)(vs)
    val append = appendMacroImpl(c)(init.tree, a.tree)(AppendNInitName)
    c.Expr[Setting[Task[A1]]](append)
  }

  /** Implementation of ++= macro for settings. */
  def settingAppendNImpl[A1: Type, U: Type](
      c: blackbox.Context
  )(vs: c.Expr[U])(a: c.Expr[Append.Values[A1, U]]): c.Expr[Setting[A1]] = {
    val init = SettingMacro.settingMacroImpl[U](c)(vs)
    val append = appendMacroImpl(c)(init.tree, a.tree)(AppendNInitName)
    c.Expr[Setting[A1]](append)
  }

  /** Implementation of -= macro for tasks. */
  def taskRemove1Impl[A1: Type, U: Type](
      c: blackbox.Context
  )(v: c.Expr[U])(r: c.Expr[Remove.Value[A1, U]]): c.Expr[Setting[Task[A1]]] = {
    val init = taskMacroImpl[U](c)(v)
    val remove = removeMacroImpl(c)(init.tree, r.tree)(Remove1InitName)
    c.Expr[Setting[Task[A1]]](remove)
  }

  /** Implementation of -= macro for settings. */
  def settingRemove1Impl[A1: Type, U: Type](
      c: blackbox.Context
  )(v: c.Expr[U])(r: c.Expr[Remove.Value[A1, U]]): c.Expr[Setting[A1]] = {
    val init = SettingMacro.settingMacroImpl[U](c)(v)
    val remove = removeMacroImpl(c)(init.tree, r.tree)(Remove1InitName)
    c.Expr[Setting[A1]](remove)
  }

  /** Implementation of --= macro for tasks. */
  def taskRemoveNImpl[A1: Type, U: Type](
      c: blackbox.Context
  )(vs: c.Expr[U])(r: c.Expr[Remove.Values[A1, U]]): c.Expr[Setting[Task[A1]]] = {
    val init = taskMacroImpl[U](c)(vs)
    val remove = removeMacroImpl(c)(init.tree, r.tree)(RemoveNInitName)
    c.Expr[Setting[Task[A1]]](remove)
  }

  /** Implementation of --= macro for settings. */
  def settingRemoveNImpl[A1: Type, U: Type](
      c: blackbox.Context
  )(vs: c.Expr[U])(r: c.Expr[Remove.Values[A1, U]]): c.Expr[Setting[A1]] = {
    val init = SettingMacro.settingMacroImpl[U](c)(vs)
    val remove = removeMacroImpl(c)(init.tree, r.tree)(RemoveNInitName)
    c.Expr[Setting[A1]](remove)
  }

  private[A1his] def appendMacroImpl(
      c: blackbox.Context
  )(init: c.Tree, append: c.Tree)(newName: String): c.Tree = {
    import c.universe._
    c.macroApplication match {
      case Apply(Apply(TypeApply(Select(preT, _), targs), _), _) =>
        Apply(
          Apply(
            TypeApply(Select(preT, TermName(newName).encodedName), targs),
            init :: sourcePosition(c).tree :: Nil
          ),
          append :: Nil
        )
      case x => ContextUtil.unexpectedTree(x)
    }
  }

  private[A1his] def removeMacroImpl(
      c: blackbox.Context
  )(init: c.Tree, remove: c.Tree)(newName: String): c.Tree = {
    import c.universe._
    c.macroApplication match {
      case Apply(Apply(TypeApply(Select(preT, _), targs), _), _) =>
        Apply(
          Apply(
            TypeApply(Select(preT, TermName(newName).encodedName), targs),
            init :: sourcePosition(c).tree :: Nil
          ),
          remove :: Nil
        )
      case x => ContextUtil.unexpectedTree(x)
    }
  }

  private[A1his] def transformMacroImpl(c: blackbox.Context)(init: c.Tree)(
      newName: String
  ): c.Tree = {
    import c.universe._
    val target =
      c.macroApplication match {
        case Apply(Select(prefix, _), _) => prefix
        case x                           => ContextUtil.unexpectedTree(x)
      }
    Apply.apply(
      Select(target, TermName(newName).encodedName),
      init :: sourcePosition(c).tree :: Nil
    )
  }

  private[A1his] def sourcePosition(c: blackbox.Context): c.Expr[SourcePosition] = {
    import c.universe.reify
    val pos = c.enclosingPosition
    if (!pos.isInstanceOf[UndefinedPosition] && pos.line >= 0 && pos.source != null) {
      val f = pos.source.file
      val name = constant[String](c, settingSource(c, f.path, f.name))
      val line = constant[Int](c, pos.line)
      reify { LinePosition(name.splice, line.splice) }
    } else reify { NoPosition }
  }

  private[A1his] def settingSource(c: blackbox.Context, path: String, name: String): String = {
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

  private[A1his] def constant[A1: c.TypeTag](c: blackbox.Context, t: T): c.Expr[A1] = {
    import c.universe._
    c.Expr[A1](Literal(Constant(t)))
  }

  def inputTaskMacroImpl[A1: Type](
      c: blackbox.Context
  )(t: c.Expr[A1]): c.Expr[Initialize[InputTask[A1]]] =
    inputTaskMacro0[A1](c)(t)

  def inputTaskDynMacroImpl[A1: Type](
      c: blackbox.Context
  )(t: c.Expr[Initialize[Task[A1]]]): c.Expr[Initialize[InputTask[A1]]] =
    inputTaskDynMacro0[A1](c)(t)

  private[A1his] def inputTaskMacro0[A1: Type](
      c: blackbox.Context
  )(t: c.Expr[A1]): c.Expr[Initialize[InputTask[A1]]] =
    iInitializeMacro(c)(t) { et =>
      val pt = iParserMacro(c)(et) { pt =>
        iTaskMacro(c)(pt)
      }
      c.universe.reify { InputTask.make(pt.splice) }
    }

  private[A1his] def iInitializeMacro[M[_], T](c: blackbox.Context)(t: c.Expr[A1])(
      f: c.Expr[A1] => c.Expr[M[A1]]
  )(implicit tt: Type[A1], mt: Type[M[A1]]): c.Expr[Initialize[M[A1]]] = {
    val inner: Transform[c.type, M] = (in: c.Tree) => f(c.Expr[A1](in)).tree
    val cond = c.Expr[A1](conditionInputTaskTree(c)(t.tree))
    Instance
      .contImpl[A1, M](c, InitializeInstance, InputInitConvert, MixedBuilder, EmptyLinter)(
        Left(cond),
        inner
      )
  }

  private[A1his] def conditionInputTaskTree(c: blackbox.Context)(t: c.Tree): c.Tree = {
    import c.universe._
    import InputWrapper._
    def wrapInitTask[A1: Type](tree: Tree) = {
      val e = c.Expr[Initialize[Task[A1]]](tree)
      wrapTask[A1](c)(wrapInit[Task[A1]](c)(e, tree.pos), tree.pos).tree
    }
    def wrapInitParser[A1: Type](tree: Tree) = {
      val e = c.Expr[Initialize[State => Parser[A1]]](tree)
      ParserInput.wrap[A1](c)(wrapInit[State => Parser[A1]](c)(e, tree.pos), tree.pos).tree
    }
    def wrapInitInput[A1: Type](tree: Tree) = {
      val e = c.Expr[Initialize[InputTask[A1]]](tree)
      wrapInput[A1](wrapInit[InputTask[A1]](c)(e, tree.pos).tree)
    }
    def wrapInput[A1: Type](tree: Tree) = {
      val e = c.Expr[InputTask[A1]](tree)
      val p = ParserInput.wrap[Task[A1]](c)(ParserInput.inputParser(c)(e), tree.pos)
      wrapTask[A1](c)(p, tree.pos).tree
    }

    def expand(nme: String, tpe: Type, tree: Tree): Converted[c.type] = nme match {
      case WrapInitTaskName         => Converted.Success(wrapInitTask(tree)(Type(tpe)))
      case WrapPreviousName         => Converted.Success(wrapInitTask(tree)(Type(tpe)))
      case ParserInput.WrapInitName => Converted.Success(wrapInitParser(tree)(Type(tpe)))
      case WrapInitInputName        => Converted.Success(wrapInitInput(tree)(Type(tpe)))
      case WrapInputName            => Converted.Success(wrapInput(tree)(Type(tpe)))
      case _                        => Converted.NotApplicable
    }
    val util = ContextUtil[c.type](c)
    util.transformWrappers(t, (nme, tpe, tree, original) => expand(nme, tpe, tree))
  }

  private[A1his] def iParserMacro[M[_], T](c: blackbox.Context)(t: c.Expr[A1])(
      f: c.Expr[A1] => c.Expr[M[A1]]
  )(implicit tt: Type[A1], mt: Type[M[A1]]): c.Expr[State => Parser[M[A1]]] = {
    val inner: Transform[c.type, M] = (in: c.Tree) => f(c.Expr[A1](in)).tree
    Instance.contImpl[A1, M](c, ParserInstance, ParserConvert, MixedBuilder, LinterDSL.Empty)(
      Left(t),
      inner
    )
  }

  private[A1his] def iTaskMacro[A1: Type](
      c: blackbox.Context
  )(t: c.Expr[A1]): c.Expr[Task[A1]] =
    Instance
      .contImpl[A1, Id](c, TaskInstance, TaskConvert, MixedBuilder, EmptyLinter)(
        Left(t),
        Instance.idTransform
      )

  private[A1his] def inputTaskDynMacro0[A1: Type](
      c: blackbox.Context
  )(t: c.Expr[Initialize[Task[A1]]]): c.Expr[Initialize[InputTask[A1]]] = {
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

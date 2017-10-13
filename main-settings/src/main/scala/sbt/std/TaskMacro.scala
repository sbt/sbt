/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package std

import Def.{ Initialize, Setting }
import sbt.internal.util.Types.{ Id, const, idFun }
import sbt.internal.util.appmacro.{
  ContextUtil,
  Converted,
  Instance,
  LinterDSL,
  MixedBuilder,
  MonadInstance
}
import Instance.Transform
import sbt.internal.util.complete.{ DefaultParsers, Parser }
import sbt.internal.util.{ AList, LinePosition, NoPosition, SourcePosition }

import language.experimental.macros
import scala.annotation.tailrec
import reflect.macros._
import scala.reflect.internal.util.UndefinedPosition

/** Instance for the monad/applicative functor for plain Tasks. */
object TaskInstance extends MonadInstance {
  import TaskExtra._

  final type M[x] = Task[x]
  def app[K[L[x]], Z](in: K[Task], f: K[Id] => Z)(implicit a: AList[K]): Task[Z] = in map f
  def map[S, T](in: Task[S], f: S => T): Task[T] = in map f
  def flatten[T](in: Task[Task[T]]): Task[T] = in flatMap idFun[Task[T]]
  def pure[T](t: () => T): Task[T] = toTask(t)
}
object ParserInstance extends Instance {
  import sbt.internal.util.Classes.Applicative
  private[this] implicit val parserApplicative: Applicative[M] = new Applicative[M] {
    def apply[S, T](f: M[S => T], v: M[S]): M[T] = s => (f(s) ~ v(s)) map { case (a, b) => a(b) }
    def pure[S](s: => S) = const(Parser.success(s))
    def map[S, T](f: S => T, v: M[S]) = s => v(s).map(f)
  }

  final type M[x] = State => Parser[x]
  def app[K[L[x]], Z](in: K[M], f: K[Id] => Z)(implicit a: AList[K]): M[Z] = a.apply(in, f)
  def map[S, T](in: M[S], f: S => T): M[T] = s => in(s) map f
  def pure[T](t: () => T): State => Parser[T] = const(DefaultParsers.success(t()))
}

/** Composes the Task and Initialize Instances to provide an Instance for [T] Initialize[Task[T]].*/
object FullInstance
    extends Instance.Composed[Initialize, Task](InitializeInstance, TaskInstance)
    with MonadInstance {
  type SS = sbt.internal.util.Settings[Scope]
  val settingsData = TaskKey[SS]("settings-data",
                                 "Provides access to the project data for the build.",
                                 KeyRanks.DTask)

  def flatten[T](in: Initialize[Task[Initialize[Task[T]]]]): Initialize[Task[T]] = {
    import TupleSyntax._
    (in, settingsData, Def.capturedTransformations) {
      (a: Task[Initialize[Task[T]]], data: Task[SS], f) =>
        import TaskExtra.multT2Task
        (a, data) flatMap { case (a, d) => f(a) evaluate d }
    }
  }

  def flattenFun[S, T](in: Initialize[Task[S => Initialize[Task[T]]]]): Initialize[S => Task[T]] = {
    import TupleSyntax._
    (in, settingsData, Def.capturedTransformations) {
      (a: Task[S => Initialize[Task[T]]], data: Task[SS], f) => (s: S) =>
        import TaskExtra.multT2Task
        (a, data) flatMap { case (af, d) => f(af(s)) evaluate d }
    }
  }
}

object TaskMacro {
  final val AssignInitName = "set"
  final val Append1InitName = "append1"
  final val AppendNInitName = "appendN"
  final val Remove1InitName = "remove1"
  final val RemoveNInitName = "removeN"
  final val TransformInitName = "transform"
  final val InputTaskCreateDynName = "createDyn"
  final val InputTaskCreateFreeName = "createFree"
  final val append1Migration =
    "`<+=` operator is removed. Try `lhs += { x.value }`\n  or see http://www.scala-sbt.org/1.0/docs/Migrating-from-sbt-012x.html."
  final val appendNMigration =
    "`<++=` operator is removed. Try `lhs ++= { x.value }`\n  or see http://www.scala-sbt.org/1.0/docs/Migrating-from-sbt-012x.html."
  final val assignMigration =
    """`<<=` operator is removed. Use `key := { x.value }` or `key ~= (old => { newValue })`.
      |See http://www.scala-sbt.org/1.0/docs/Migrating-from-sbt-012x.html""".stripMargin

  import LinterDSL.{ Empty => EmptyLinter }

  def taskMacroImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      t: c.Expr[T]): c.Expr[Initialize[Task[T]]] =
    Instance.contImpl[T, Id](c, FullInstance, FullConvert, MixedBuilder, TaskLinterDSL)(
      Left(t),
      Instance.idTransform[c.type])

  def taskDynMacroImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      t: c.Expr[Initialize[Task[T]]]): c.Expr[Initialize[Task[T]]] =
    Instance.contImpl[T, Id](c, FullInstance, FullConvert, MixedBuilder, TaskDynLinterDSL)(
      Right(t),
      Instance.idTransform[c.type])

  /** Implementation of := macro for settings. */
  def settingAssignMacroImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      v: c.Expr[T]): c.Expr[Setting[T]] = {
    val init = SettingMacro.settingMacroImpl[T](c)(v)
    val assign = transformMacroImpl(c)(init.tree)(AssignInitName)
    c.Expr[Setting[T]](assign)
  }

  /** Implementation of := macro for tasks. */
  def taskAssignMacroImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      v: c.Expr[T]): c.Expr[Setting[Task[T]]] = {
    val init = taskMacroImpl[T](c)(v)
    val assign = transformMacroImpl(c)(init.tree)(AssignInitName)
    c.Expr[Setting[Task[T]]](assign)
  }

  // Error macros (Restligeist)
  // These macros are there just so we can fail old operators like `<<=` and provide useful migration information.

  def fakeSettingAssignPosition[T: c.WeakTypeTag](c: blackbox.Context)(
      app: c.Expr[Initialize[T]]): c.Expr[Setting[T]] =
    ContextUtil.selectMacroImpl[Setting[T]](c) { (ts, pos) =>
      c.abort(pos, assignMigration)
    }
  def fakeSettingAppend1Position[S: c.WeakTypeTag, V: c.WeakTypeTag](c: blackbox.Context)(
      v: c.Expr[Initialize[V]])(a: c.Expr[Append.Value[S, V]]): c.Expr[Setting[S]] =
    ContextUtil.selectMacroImpl[Setting[S]](c) { (ts, pos) =>
      c.abort(pos, append1Migration)
    }
  def fakeSettingAppendNPosition[S: c.WeakTypeTag, V: c.WeakTypeTag](c: blackbox.Context)(
      vs: c.Expr[Initialize[V]])(a: c.Expr[Append.Values[S, V]]): c.Expr[Setting[S]] =
    ContextUtil.selectMacroImpl[Setting[S]](c) { (ts, pos) =>
      c.abort(pos, appendNMigration)
    }
  def fakeItaskAssignPosition[T: c.WeakTypeTag](c: blackbox.Context)(
      app: c.Expr[Initialize[Task[T]]]): c.Expr[Setting[Task[T]]] =
    ContextUtil.selectMacroImpl[Setting[Task[T]]](c) { (ts, pos) =>
      c.abort(pos, assignMigration)
    }
  def fakeTaskAppend1Position[S: c.WeakTypeTag, V: c.WeakTypeTag](c: blackbox.Context)(
      v: c.Expr[Initialize[Task[V]]])(a: c.Expr[Append.Value[S, V]]): c.Expr[Setting[Task[S]]] =
    ContextUtil.selectMacroImpl[Setting[Task[S]]](c) { (ts, pos) =>
      c.abort(pos, append1Migration)
    }
  def fakeTaskAppendNPosition[S: c.WeakTypeTag, V: c.WeakTypeTag](c: blackbox.Context)(
      vs: c.Expr[Initialize[Task[V]]])(a: c.Expr[Append.Values[S, V]]): c.Expr[Setting[Task[S]]] =
    ContextUtil.selectMacroImpl[Setting[Task[S]]](c) { (ts, pos) =>
      c.abort(pos, appendNMigration)
    }

  /* Implementations of <<= macro variations for tasks and settings. These just get the source position of the call site.*/

  def itaskAssignPosition[T: c.WeakTypeTag](c: blackbox.Context)(
      app: c.Expr[Initialize[Task[T]]]): c.Expr[Setting[Task[T]]] =
    settingAssignPosition(c)(app)

  def taskAssignPositionT[T: c.WeakTypeTag](c: blackbox.Context)(
      app: c.Expr[Task[T]]): c.Expr[Setting[Task[T]]] =
    itaskAssignPosition(c)(c.universe.reify { Def.valueStrict(app.splice) })

  def taskAssignPositionPure[T: c.WeakTypeTag](c: blackbox.Context)(
      app: c.Expr[T]): c.Expr[Setting[Task[T]]] =
    taskAssignPositionT(c)(c.universe.reify { TaskExtra.constant(app.splice) })

  def taskTransformPosition[S: c.WeakTypeTag](c: blackbox.Context)(
      f: c.Expr[S => S]): c.Expr[Setting[Task[S]]] =
    c.Expr[Setting[Task[S]]](transformMacroImpl(c)(f.tree)(TransformInitName))

  def settingTransformPosition[S: c.WeakTypeTag](c: blackbox.Context)(
      f: c.Expr[S => S]): c.Expr[Setting[S]] =
    c.Expr[Setting[S]](transformMacroImpl(c)(f.tree)(TransformInitName))

  def itaskTransformPosition[S: c.WeakTypeTag](c: blackbox.Context)(
      f: c.Expr[S => S]): c.Expr[Setting[S]] =
    c.Expr[Setting[S]](transformMacroImpl(c)(f.tree)(TransformInitName))

  def settingAssignPure[T: c.WeakTypeTag](c: blackbox.Context)(app: c.Expr[T]): c.Expr[Setting[T]] =
    settingAssignPosition(c)(c.universe.reify { Def.valueStrict(app.splice) })

  def settingAssignPosition[T: c.WeakTypeTag](c: blackbox.Context)(
      app: c.Expr[Initialize[T]]): c.Expr[Setting[T]] =
    c.Expr[Setting[T]](transformMacroImpl(c)(app.tree)(AssignInitName))

  /** Implementation of := macro for tasks. */
  def inputTaskAssignMacroImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      v: c.Expr[T]): c.Expr[Setting[InputTask[T]]] = {
    val init = inputTaskMacroImpl[T](c)(v)
    val assign = transformMacroImpl(c)(init.tree)(AssignInitName)
    c.Expr[Setting[InputTask[T]]](assign)
  }

  /** Implementation of += macro for tasks. */
  def taskAppend1Impl[T: c.WeakTypeTag, U: c.WeakTypeTag](c: blackbox.Context)(v: c.Expr[U])(
      a: c.Expr[Append.Value[T, U]]): c.Expr[Setting[Task[T]]] = {
    val init = taskMacroImpl[U](c)(v)
    val append = appendMacroImpl(c)(init.tree, a.tree)(Append1InitName)
    c.Expr[Setting[Task[T]]](append)
  }

  /** Implementation of += macro for settings. */
  def settingAppend1Impl[T: c.WeakTypeTag, U: c.WeakTypeTag](c: blackbox.Context)(v: c.Expr[U])(
      a: c.Expr[Append.Value[T, U]]): c.Expr[Setting[T]] = {
    import c.universe._
    val ttpe = c.weakTypeOf[T]
    val typeArgs = ttpe.typeArgs
    v.tree.tpe match {
      // To allow Initialize[Task[A]] in the position of += RHS, we're going to call "taskValue" automatically.
      case tpe
          if typeArgs.nonEmpty && (typeArgs.head weak_<:< c.weakTypeOf[Task[_]])
            && (tpe weak_<:< c.weakTypeOf[Initialize[_]]) =>
        c.macroApplication match {
          case Apply(Apply(TypeApply(Select(preT, nmeT), targs), _), _) =>
            val tree = Apply(
              TypeApply(Select(preT, TermName("+=").encodedName), TypeTree(typeArgs.head) :: Nil),
              Select(v.tree, TermName("taskValue").encodedName) :: Nil)
            c.Expr[Setting[T]](tree)
          case x => ContextUtil.unexpectedTree(x)
        }
      case _ =>
        val init = SettingMacro.settingMacroImpl[U](c)(v)
        val append = appendMacroImpl(c)(init.tree, a.tree)(Append1InitName)
        c.Expr[Setting[T]](append)
    }
  }

  /** Implementation of ++= macro for tasks. */
  def taskAppendNImpl[T: c.WeakTypeTag, U: c.WeakTypeTag](c: blackbox.Context)(vs: c.Expr[U])(
      a: c.Expr[Append.Values[T, U]]): c.Expr[Setting[Task[T]]] = {
    val init = taskMacroImpl[U](c)(vs)
    val append = appendMacroImpl(c)(init.tree, a.tree)(AppendNInitName)
    c.Expr[Setting[Task[T]]](append)
  }

  /** Implementation of ++= macro for settings. */
  def settingAppendNImpl[T: c.WeakTypeTag, U: c.WeakTypeTag](c: blackbox.Context)(vs: c.Expr[U])(
      a: c.Expr[Append.Values[T, U]]): c.Expr[Setting[T]] = {
    val init = SettingMacro.settingMacroImpl[U](c)(vs)
    val append = appendMacroImpl(c)(init.tree, a.tree)(AppendNInitName)
    c.Expr[Setting[T]](append)
  }

  /** Implementation of -= macro for tasks. */
  def taskRemove1Impl[T: c.WeakTypeTag, U: c.WeakTypeTag](c: blackbox.Context)(v: c.Expr[U])(
      r: c.Expr[Remove.Value[T, U]]): c.Expr[Setting[Task[T]]] = {
    val init = taskMacroImpl[U](c)(v)
    val remove = removeMacroImpl(c)(init.tree, r.tree)(Remove1InitName)
    c.Expr[Setting[Task[T]]](remove)
  }

  /** Implementation of -= macro for settings. */
  def settingRemove1Impl[T: c.WeakTypeTag, U: c.WeakTypeTag](c: blackbox.Context)(v: c.Expr[U])(
      r: c.Expr[Remove.Value[T, U]]): c.Expr[Setting[T]] = {
    val init = SettingMacro.settingMacroImpl[U](c)(v)
    val remove = removeMacroImpl(c)(init.tree, r.tree)(Remove1InitName)
    c.Expr[Setting[T]](remove)
  }

  /** Implementation of --= macro for tasks. */
  def taskRemoveNImpl[T: c.WeakTypeTag, U: c.WeakTypeTag](c: blackbox.Context)(vs: c.Expr[U])(
      r: c.Expr[Remove.Values[T, U]]): c.Expr[Setting[Task[T]]] = {
    val init = taskMacroImpl[U](c)(vs)
    val remove = removeMacroImpl(c)(init.tree, r.tree)(RemoveNInitName)
    c.Expr[Setting[Task[T]]](remove)
  }

  /** Implementation of --= macro for settings. */
  def settingRemoveNImpl[T: c.WeakTypeTag, U: c.WeakTypeTag](c: blackbox.Context)(vs: c.Expr[U])(
      r: c.Expr[Remove.Values[T, U]]): c.Expr[Setting[T]] = {
    val init = SettingMacro.settingMacroImpl[U](c)(vs)
    val remove = removeMacroImpl(c)(init.tree, r.tree)(RemoveNInitName)
    c.Expr[Setting[T]](remove)
  }

  private[this] def appendMacroImpl(c: blackbox.Context)(init: c.Tree, append: c.Tree)(
      newName: String): c.Tree = {
    import c.universe._
    c.macroApplication match {
      case Apply(Apply(TypeApply(Select(preT, nmeT), targs), _), _) =>
        Apply(Apply(TypeApply(Select(preT, TermName(newName).encodedName), targs),
                    init :: sourcePosition(c).tree :: Nil),
              append :: Nil)
      case x => ContextUtil.unexpectedTree(x)
    }
  }

  private[this] def removeMacroImpl(c: blackbox.Context)(init: c.Tree, remove: c.Tree)(
      newName: String): c.Tree = {
    import c.universe._
    c.macroApplication match {
      case Apply(Apply(TypeApply(Select(preT, nmeT), targs), _), r) =>
        Apply(Apply(TypeApply(Select(preT, TermName(newName).encodedName), targs),
                    init :: sourcePosition(c).tree :: Nil),
              r)
      case x => ContextUtil.unexpectedTree(x)
    }
  }

  private[this] def transformMacroImpl(c: blackbox.Context)(init: c.Tree)(
      newName: String
  ): c.Tree = {
    import c.universe._
    val target =
      c.macroApplication match {
        case Apply(Select(prefix, _), _) => prefix
        case x                           => ContextUtil.unexpectedTree(x)
      }
    Apply.apply(Select(target, TermName(newName).encodedName),
                init :: sourcePosition(c).tree :: Nil)
  }

  private[this] def sourcePosition(c: blackbox.Context): c.Expr[SourcePosition] = {
    import c.universe.reify
    val pos = c.enclosingPosition
    if (!pos.isInstanceOf[UndefinedPosition] && pos.line >= 0 && pos.source != null) {
      val f = pos.source.file
      val name = constant[String](c, settingSource(c, f.path, f.name))
      val line = constant[Int](c, pos.line)
      reify { LinePosition(name.splice, line.splice) }
    } else
      reify { NoPosition }
  }

  private[this] def settingSource(c: blackbox.Context, path: String, name: String): String = {
    @tailrec def inEmptyPackage(s: c.Symbol): Boolean = s != c.universe.NoSymbol && (
      s.owner == c.mirror.EmptyPackage || s.owner == c.mirror.EmptyPackageClass || inEmptyPackage(
        s.owner)
    )
    c.internal.enclosingOwner match {
      case ec if !ec.isStatic       => name
      case ec if inEmptyPackage(ec) => path
      case ec                       => s"(${ec.fullName}) $name"
    }
  }

  private[this] def constant[T: c.TypeTag](c: blackbox.Context, t: T): c.Expr[T] = {
    import c.universe._
    c.Expr[T](Literal(Constant(t)))
  }

  def inputTaskMacroImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      t: c.Expr[T]): c.Expr[Initialize[InputTask[T]]] =
    inputTaskMacro0[T](c)(t)

  def inputTaskDynMacroImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      t: c.Expr[Initialize[Task[T]]]): c.Expr[Initialize[InputTask[T]]] =
    inputTaskDynMacro0[T](c)(t)

  private[this] def inputTaskMacro0[T: c.WeakTypeTag](c: blackbox.Context)(
      t: c.Expr[T]): c.Expr[Initialize[InputTask[T]]] =
    iInitializeMacro(c)(t) { et =>
      val pt = iParserMacro(c)(et) { pt =>
        iTaskMacro(c)(pt)
      }
      c.universe.reify { InputTask.make(pt.splice) }
    }

  private[this] def iInitializeMacro[M[_], T](c: blackbox.Context)(t: c.Expr[T])(
      f: c.Expr[T] => c.Expr[M[T]])(implicit tt: c.WeakTypeTag[T],
                                    mt: c.WeakTypeTag[M[T]]): c.Expr[Initialize[M[T]]] = {
    val inner: Transform[c.type, M] = new Transform[c.type, M] {
      def apply(in: c.Tree): c.Tree = f(c.Expr[T](in)).tree
    }
    val cond = c.Expr[T](conditionInputTaskTree(c)(t.tree))
    Instance
      .contImpl[T, M](c, InitializeInstance, InputInitConvert, MixedBuilder, EmptyLinter)(
        Left(cond),
        inner)
  }

  private[this] def conditionInputTaskTree(c: blackbox.Context)(t: c.Tree): c.Tree = {
    import c.universe._
    import InputWrapper._
    def wrapInitTask[T: c.WeakTypeTag](tree: Tree) = {
      val e = c.Expr[Initialize[Task[T]]](tree)
      wrapTask[T](c)(wrapInit[Task[T]](c)(e, tree.pos), tree.pos).tree
    }
    def wrapInitParser[T: c.WeakTypeTag](tree: Tree) = {
      val e = c.Expr[Initialize[State => Parser[T]]](tree)
      ParserInput.wrap[T](c)(wrapInit[State => Parser[T]](c)(e, tree.pos), tree.pos).tree
    }
    def wrapInitInput[T: c.WeakTypeTag](tree: Tree) = {
      val e = c.Expr[Initialize[InputTask[T]]](tree)
      wrapInput[T](wrapInit[InputTask[T]](c)(e, tree.pos).tree)
    }
    def wrapInput[T: c.WeakTypeTag](tree: Tree) = {
      val e = c.Expr[InputTask[T]](tree)
      val p = ParserInput.wrap[Task[T]](c)(ParserInput.inputParser(c)(e), tree.pos)
      wrapTask[T](c)(p, tree.pos).tree
    }

    def expand(nme: String, tpe: Type, tree: Tree): Converted[c.type] = nme match {
      case WrapInitTaskName         => Converted.Success(wrapInitTask(tree)(c.WeakTypeTag(tpe)))
      case ParserInput.WrapInitName => Converted.Success(wrapInitParser(tree)(c.WeakTypeTag(tpe)))
      case WrapInitInputName        => Converted.Success(wrapInitInput(tree)(c.WeakTypeTag(tpe)))
      case WrapInputName            => Converted.Success(wrapInput(tree)(c.WeakTypeTag(tpe)))
      case _                        => Converted.NotApplicable
    }
    val util = ContextUtil[c.type](c)
    util.transformWrappers(t, (nme, tpe, tree, original) => expand(nme, tpe, tree))
  }

  private[this] def iParserMacro[M[_], T](c: blackbox.Context)(t: c.Expr[T])(
      f: c.Expr[T] => c.Expr[M[T]])(implicit tt: c.WeakTypeTag[T],
                                    mt: c.WeakTypeTag[M[T]]): c.Expr[State => Parser[M[T]]] = {
    val inner: Transform[c.type, M] = new Transform[c.type, M] {
      def apply(in: c.Tree): c.Tree = f(c.Expr[T](in)).tree
    }
    Instance.contImpl[T, M](c, ParserInstance, ParserConvert, MixedBuilder, LinterDSL.Empty)(
      Left(t),
      inner)
  }

  private[this] def iTaskMacro[T: c.WeakTypeTag](c: blackbox.Context)(
      t: c.Expr[T]): c.Expr[Task[T]] =
    Instance
      .contImpl[T, Id](c, TaskInstance, TaskConvert, MixedBuilder, EmptyLinter)(
        Left(t),
        Instance.idTransform)

  private[this] def inputTaskDynMacro0[T: c.WeakTypeTag](c: blackbox.Context)(
      t: c.Expr[Initialize[Task[T]]]): c.Expr[Initialize[InputTask[T]]] = {
    import c.universe.{ Apply => ApplyTree, _ }
    import internal.decorators._

    val tag = implicitly[c.WeakTypeTag[T]]
    val util = ContextUtil[c.type](c)
    val it = Ident(util.singleton(InputTask))
    val isParserWrapper = InitParserConvert.asPredicate(c)
    val isTaskWrapper = FullConvert.asPredicate(c)
    val isAnyWrapper = (n: String, tpe: Type, tr: Tree) =>
      isParserWrapper(n, tpe, tr) || isTaskWrapper(n, tpe, tr)
    val ttree = t.tree
    val defs = util.collectDefs(ttree, isAnyWrapper)
    val checkQual = util.checkReferences(defs, isAnyWrapper)

    // the Symbol for the anonymous function passed to the appropriate Instance.map/flatMap/pure method
    // this Symbol needs to be known up front so that it can be used as the owner of synthetic vals
    val functionSym = util.functionSymbol(ttree.pos)
    var result: Option[(Tree, Type, ValDef)] = None

    // original is the Tree being replaced.  It is needed for preserving attributes.
    def subWrapper(tpe: Type, qual: Tree, original: Tree): Tree =
      if (result.isDefined) {
        c.error(
          qual.pos,
          "Implementation restriction: a dynamic InputTask can only have a single input parser.")
        EmptyTree
      } else {
        qual.foreach(checkQual)
        val vd = util.freshValDef(tpe, qual.symbol.pos, functionSym) // val $x: <tpe>
        result = Some((qual, tpe, vd))
        val tree = util.refVal(original, vd) // $x
        tree.setPos(qual.pos) // position needs to be set so that wrapKey passes the position onto the wrapper
        assert(tree.tpe != null, "Null type: " + tree)
        tree.setType(tpe)
        tree
      }
    // Tree for InputTask.<name>[<tpeA>, <tpeB>](arg1)(arg2)
    def inputTaskCreate(name: String, tpeA: Type, tpeB: Type, arg1: Tree, arg2: Tree) = {
      val typedApp = TypeApply(util.select(it, name), TypeTree(tpeA) :: TypeTree(tpeB) :: Nil)
      val app = ApplyTree(ApplyTree(typedApp, arg1 :: Nil), arg2 :: Nil)
      c.Expr[Initialize[InputTask[T]]](app)
    }
    // Tree for InputTask.createFree[<tpe>](arg1)
    def inputTaskCreateFree(tpe: Type, arg: Tree) = {
      val typedApp = TypeApply(util.select(it, InputTaskCreateFreeName), TypeTree(tpe) :: Nil)
      val app = ApplyTree(typedApp, arg :: Nil)
      c.Expr[Initialize[InputTask[T]]](app)
    }
    def expandTask[I: WeakTypeTag](dyn: Boolean, tx: Tree): c.Expr[Initialize[Task[I]]] =
      if (dyn)
        taskDynMacroImpl[I](c)(c.Expr[Initialize[Task[I]]](tx))
      else
        taskMacroImpl[I](c)(c.Expr[I](tx))
    def wrapTag[I: WeakTypeTag]: WeakTypeTag[Initialize[Task[I]]] = weakTypeTag

    def sub(name: String, tpe: Type, qual: Tree, selection: Tree): Converted[c.type] = {
      val tag = c.WeakTypeTag[T](tpe)
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
        val fTag = c.WeakTypeTag[Any](fTpe) // don't know the actual type yet, so use Any
        val fInit = expandTask(false, fCore)(fTag).tree
        inputTaskCreate(InputTaskCreateDynName, tpe, tag.tpe, p, fInit)
      case None =>
        val init = expandTask[T](true, tx).tree
        inputTaskCreateFree(tag.tpe, init)
    }
  }
}

object PlainTaskMacro {
  def task[T](t: T): Task[T] = macro taskImpl[T]
  def taskImpl[T: c.WeakTypeTag](c: blackbox.Context)(t: c.Expr[T]): c.Expr[Task[T]] =
    Instance.contImpl[T, Id](c, TaskInstance, TaskConvert, MixedBuilder, OnlyTaskLinterDSL)(
      Left(t),
      Instance.idTransform[c.type])

  def taskDyn[T](t: Task[T]): Task[T] = macro taskDynImpl[T]
  def taskDynImpl[T: c.WeakTypeTag](c: blackbox.Context)(t: c.Expr[Task[T]]): c.Expr[Task[T]] =
    Instance.contImpl[T, Id](c, TaskInstance, TaskConvert, MixedBuilder, OnlyTaskDynLinterDSL)(
      Right(t),
      Instance.idTransform[c.type])
}

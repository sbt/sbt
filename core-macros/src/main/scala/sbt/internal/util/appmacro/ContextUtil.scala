/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package appmacro

import scala.reflect._
import macros._
import ContextUtil.{ DynamicDependencyError, DynamicReferenceError }

object ContextUtil {
  final val DynamicDependencyError = "Illegal dynamic dependency"
  final val DynamicReferenceError = "Illegal dynamic reference"

  /**
   * Constructs an object with utility methods for operating in the provided macro context `c`.
   * Callers should explicitly specify the type parameter as `c.type` in order to preserve the path dependent types.
   */
  def apply[C <: blackbox.Context with Singleton](c: C): ContextUtil[C] = new ContextUtil(c)

  /**
   * Helper for implementing a no-argument macro that is introduced via an implicit.
   * This method removes the implicit conversion and evaluates the function `f` on the target of the conversion.
   *
   * Given `myImplicitConversion(someValue).extensionMethod`, where `extensionMethod` is a macro that uses this
   * method, the result of this method is `f(<Tree of someValue>)`.
   */
  def selectMacroImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      f: (c.Expr[Any], c.Position) => c.Expr[T]): c.Expr[T] = {
    import c.universe._
    c.macroApplication match {
      case s @ Select(Apply(_, t :: Nil), tp) => f(c.Expr[Any](t), s.pos)
      case a @ Apply(_, t :: Nil)             => f(c.Expr[Any](t), a.pos)
      case x                                  => unexpectedTree(x)
    }
  }

  def unexpectedTree[C <: blackbox.Context](tree: C#Tree): Nothing =
    sys.error("Unexpected macro application tree (" + tree.getClass + "): " + tree)
}

/**
 * Utility methods for macros.  Several methods assume that the context's universe is a full compiler
 * (`scala.tools.nsc.Global`).
 * This is not thread safe due to the underlying Context and related data structures not being thread safe.
 * Use `ContextUtil[c.type](c)` to construct.
 */
final class ContextUtil[C <: blackbox.Context](val ctx: C) {
  import ctx.universe.{ Apply => ApplyTree, _ }
  import internal.decorators._

  val powerContext = ctx.asInstanceOf[reflect.macros.runtime.Context]
  val global: powerContext.universe.type = powerContext.universe
  def callsiteTyper: global.analyzer.Typer = powerContext.callsiteTyper
  val initialOwner: Symbol = callsiteTyper.context.owner.asInstanceOf[ctx.universe.Symbol]

  lazy val alistType = ctx.typeOf[AList[KList]]
  lazy val alist: Symbol = alistType.typeSymbol.companion
  lazy val alistTC: Type = alistType.typeConstructor

  /** Modifiers for a local val.*/
  lazy val localModifiers = Modifiers(NoFlags)

  def getPos(sym: Symbol) = if (sym eq null) NoPosition else sym.pos

  /**
   * Constructs a unique term name with the given prefix within this Context.
   * (The current implementation uses Context.freshName, which increments
   */
  def freshTermName(prefix: String) = TermName(ctx.freshName("$" + prefix))

  /**
   * Constructs a new, synthetic, local ValDef Type `tpe`, a unique name,
   * Position `pos`, an empty implementation (no rhs), and owned by `owner`.
   */
  def freshValDef(tpe: Type, pos: Position, owner: Symbol): ValDef = {
    val SYNTHETIC = (1 << 21).toLong.asInstanceOf[FlagSet]
    val sym = owner.newTermSymbol(freshTermName("q"), pos, SYNTHETIC)
    setInfo(sym, tpe)
    val vd = internal.valDef(sym, EmptyTree)
    vd.setPos(pos)
    vd
  }

  lazy val parameterModifiers = Modifiers(Flag.PARAM)

  /**
   * Collects all definitions in the tree for use in checkReferences.
   * This excludes definitions in wrapped expressions because checkReferences won't allow nested dereferencing anyway.
   */
  def collectDefs(
      tree: Tree,
      isWrapper: (String, Type, Tree) => Boolean
  ): collection.Set[Symbol] = {
    val defs = new collection.mutable.HashSet[Symbol]
    // adds the symbols for all non-Ident subtrees to `defs`.
    val process = new Traverser {
      override def traverse(t: Tree) = t match {
        case _: Ident => ()
        case ApplyTree(TypeApply(Select(_, nme), tpe :: Nil), qual :: Nil)
            if isWrapper(nme.decodedName.toString, tpe.tpe, qual) =>
          ()
        case tree =>
          if (tree.symbol ne null) defs += tree.symbol;
          super.traverse(tree)
      }
    }
    process.traverse(tree)
    defs
  }

  /**
   * A reference is illegal if it is to an M instance defined within the scope of the macro call.
   * As an approximation, disallow referenced to any local definitions `defs`.
   */
  def illegalReference(defs: collection.Set[Symbol], sym: Symbol): Boolean =
    sym != null && sym != NoSymbol && defs.contains(sym)

  type PropertyChecker = (String, Type, Tree) => Boolean

  /**
   * A function that checks the provided tree for illegal references to M instances defined in the
   *  expression passed to the macro and for illegal dereferencing of M instances.
   */
  def checkReferences(defs: collection.Set[Symbol], isWrapper: PropertyChecker): Tree => Unit = {
    case s @ ApplyTree(TypeApply(Select(_, nme), tpe :: Nil), qual :: Nil) =>
      if (isWrapper(nme.decodedName.toString, tpe.tpe, qual))
        ctx.error(s.pos, DynamicDependencyError)
    case id @ Ident(name) if illegalReference(defs, id.symbol) =>
      ctx.error(id.pos, DynamicReferenceError + ": " + name)
    case _ => ()
  }

  /** Constructs a ValDef with a parameter modifier, a unique name, with the provided Type and with an empty rhs. */
  def freshMethodParameter(tpe: Type): ValDef =
    ValDef(parameterModifiers, freshTermName("p"), TypeTree(tpe), EmptyTree)

  /** Constructs a ValDef with local modifiers and a unique name. */
  def localValDef(tpt: Tree, rhs: Tree): ValDef =
    ValDef(localModifiers, freshTermName("q"), tpt, rhs)

  /** Constructs a tuple value of the right TupleN type from the provided inputs.*/
  def mkTuple(args: List[Tree]): Tree =
    global.gen.mkTuple(args.asInstanceOf[List[global.Tree]]).asInstanceOf[ctx.universe.Tree]

  def setSymbol[_Tree](t: _Tree, sym: Symbol): Unit = {
    t.asInstanceOf[global.Tree].setSymbol(sym.asInstanceOf[global.Symbol])
    ()
  }
  def setInfo(sym: Symbol, tpe: Type): Unit = {
    sym.asInstanceOf[global.Symbol].setInfo(tpe.asInstanceOf[global.Type])
    ()
  }

  /** Creates a new, synthetic type variable with the specified `owner`. */
  def newTypeVariable(owner: Symbol, prefix: String = "T0"): TypeSymbol =
    owner
      .asInstanceOf[global.Symbol]
      .newSyntheticTypeParam(prefix, 0L)
      .asInstanceOf[ctx.universe.TypeSymbol]

  /** The type representing the type constructor `[X] X` */
  lazy val idTC: Type = {
    val tvar = newTypeVariable(NoSymbol)
    internal.polyType(tvar :: Nil, refVar(tvar))
  }

  /** A Type that references the given type variable. */
  def refVar(variable: TypeSymbol): Type = variable.toTypeConstructor

  /** Constructs a new, synthetic type variable that is a type constructor. For example, in type Y[L[x]], L is such a type variable. */
  def newTCVariable(owner: Symbol): TypeSymbol = {
    val tc = newTypeVariable(owner)
    val arg = newTypeVariable(tc, "x");
    tc.setInfo(internal.polyType(arg :: Nil, emptyTypeBounds))
    tc
  }

  /** >: Nothing <: Any */
  def emptyTypeBounds: TypeBounds =
    internal.typeBounds(definitions.NothingClass.toType, definitions.AnyClass.toType)

  /** Creates a new anonymous function symbol with Position `pos`. */
  def functionSymbol(pos: Position): Symbol =
    callsiteTyper.context.owner
      .newAnonymousFunctionValue(pos.asInstanceOf[global.Position])
      .asInstanceOf[ctx.universe.Symbol]

  def functionType(args: List[Type], result: Type): Type = {
    val tpe = global.definitions
      .functionType(args.asInstanceOf[List[global.Type]], result.asInstanceOf[global.Type])
    tpe.asInstanceOf[Type]
  }

  /** Create a Tree that references the `val` represented by `vd`, copying attributes from `replaced`. */
  def refVal(replaced: Tree, vd: ValDef): Tree =
    treeCopy.Ident(replaced, vd.name).setSymbol(vd.symbol)

  /** Creates a Function tree using `functionSym` as the Symbol and changing `initialOwner` to `functionSym` in `body`.*/
  def createFunction(params: List[ValDef], body: Tree, functionSym: Symbol): Tree = {
    changeOwner(body, initialOwner, functionSym)
    val f = Function(params, body)
    setSymbol(f, functionSym)
    f
  }

  def changeOwner(tree: Tree, prev: Symbol, next: Symbol): Unit =
    new ChangeOwnerAndModuleClassTraverser(
      prev.asInstanceOf[global.Symbol],
      next.asInstanceOf[global.Symbol]).traverse(tree.asInstanceOf[global.Tree])

  // Workaround copied from scala/async:can be removed once https://github.com/scala/scala/pull/3179 is merged.
  private[this] class ChangeOwnerAndModuleClassTraverser(oldowner: global.Symbol,
                                                         newowner: global.Symbol)
      extends global.ChangeOwnerTraverser(oldowner, newowner) {
    override def traverse(tree: global.Tree): Unit = {
      tree match {
        case _: global.DefTree => change(tree.symbol.moduleClass)
        case _                 =>
      }
      super.traverse(tree)
    }
  }

  /** Returns the Symbol that references the statically accessible singleton `i`. */
  def singleton[T <: AnyRef with Singleton](i: T)(implicit it: ctx.TypeTag[i.type]): Symbol =
    it.tpe match {
      case SingleType(_, sym) if !sym.isFreeTerm && sym.isStatic => sym
      case x                                                     => sys.error("Instance must be static (was " + x + ").")
    }

  def select(t: Tree, name: String): Tree = Select(t, TermName(name))

  /** Returns the symbol for the non-private method named `name` for the class/module `obj`. */
  def method(obj: Symbol, name: String): Symbol = {
    val ts: Type = obj.typeSignature
    val m: global.Symbol = ts.asInstanceOf[global.Type].nonPrivateMember(global.newTermName(name))
    m.asInstanceOf[Symbol]
  }

  /**
   * Returns a Type representing the type constructor tcp.<name>.  For example, given
   *  `object Demo { type M[x] = List[x] }`, the call `extractTC(Demo, "M")` will return a type representing
   * the type constructor `[x] List[x]`.
   */
  def extractTC(tcp: AnyRef with Singleton, name: String)(
      implicit it: ctx.TypeTag[tcp.type]): ctx.Type = {
    val itTpe = it.tpe.asInstanceOf[global.Type]
    val m = itTpe.nonPrivateMember(global.newTypeName(name))
    val tc = itTpe.memberInfo(m).asInstanceOf[ctx.universe.Type]
    assert(tc != NoType && tc.takesTypeArgs, "Invalid type constructor: " + tc)
    tc
  }

  /**
   * Substitutes wrappers in tree `t` with the result of `subWrapper`.
   * A wrapper is a Tree of the form `f[T](v)` for which isWrapper(<Tree of f>, <Underlying Type>, <qual>.target) returns true.
   * Typically, `f` is a `Select` or `Ident`.
   * The wrapper is replaced with the result of `subWrapper(<Type of T>, <Tree of v>, <wrapper Tree>)`
   */
  def transformWrappers(t: Tree,
                        subWrapper: (String, Type, Tree, Tree) => Converted[ctx.type]): Tree = {
    // the main tree transformer that replaces calls to InputWrapper.wrap(x) with
    //  plain Idents that reference the actual input value
    object appTransformer extends Transformer {
      override def transform(tree: Tree): Tree =
        tree match {
          case ApplyTree(TypeApply(Select(_, nme), targ :: Nil), qual :: Nil) =>
            subWrapper(nme.decodedName.toString, targ.tpe, qual, tree) match {
              case Converted.Success(t, finalTx) =>
                changeOwner(qual, currentOwner, initialOwner) // Fixes https://github.com/sbt/sbt/issues/1150
                finalTx(t)
              case Converted.Failure(p, m)       => ctx.abort(p, m)
              case _: Converted.NotApplicable[_] => super.transform(tree)
            }
          case _ => super.transform(tree)
        }
    }
    appTransformer.atOwner(initialOwner) {
      appTransformer.transform(t)
    }
  }
}

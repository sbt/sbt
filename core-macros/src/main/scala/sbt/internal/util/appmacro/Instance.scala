/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package appmacro

import Classes.Applicative
import Types.Id

/**
 * The separate hierarchy from Applicative/Monad is for two reasons.
 *
 * 1. The type constructor is represented as an abstract type because a TypeTag cannot represent a type constructor directly.
 * 2. The applicative interface is uncurried.
 */
trait Instance {
  type M[x]
  def app[K[L[x]], Z](in: K[M], f: K[Id] => Z)(implicit a: AList[K]): M[Z]
  def map[S, T](in: M[S], f: S => T): M[T]
  def pure[T](t: () => T): M[T]
}

trait MonadInstance extends Instance {
  def flatten[T](in: M[M[T]]): M[T]
}

import scala.reflect._
import macros._

object Instance {
  type Aux[M0[_]] = Instance { type M[x] = M0[x] }
  type Aux2[M0[_], N[_]] = Instance { type M[x] = M0[N[x]] }

  final val ApplyName = "app"
  final val FlattenName = "flatten"
  final val PureName = "pure"
  final val MapName = "map"
  final val InstanceTCName = "M"

  final class Input[U <: Universe with Singleton](val tpe: U#Type,
                                                  val expr: U#Tree,
                                                  val local: U#ValDef)
  trait Transform[C <: blackbox.Context with Singleton, N[_]] {
    def apply(in: C#Tree): C#Tree
  }
  def idTransform[C <: blackbox.Context with Singleton]: Transform[C, Id] = new Transform[C, Id] {
    def apply(in: C#Tree): C#Tree = in
  }

  /**
   * Implementation of a macro that provides a direct syntax for applicative functors and monads.
   * It is intended to be used in conjunction with another macro that conditions the inputs.
   *
   * This method processes the Tree `t` to find inputs of the form `wrap[T]( input )`
   * This form is typically constructed by another macro that pretends to be able to get a value of type `T`
   * from a value convertible to `M[T]`.  This `wrap(input)` form has two main purposes.
   * First, it identifies the inputs that should be transformed.
   * Second, it allows the input trees to be wrapped for later conversion into the appropriate `M[T]` type by `convert`.
   * This wrapping is necessary because applying the first macro must preserve the original type,
   * but it is useful to delay conversion until the outer, second macro is called.  The `wrap` method accomplishes this by
   * allowing the original `Tree` and `Type` to be hidden behind the raw `T` type.  This method will remove the call to `wrap`
   * so that it is not actually called at runtime.
   *
   * Each `input` in each expression of the form `wrap[T]( input )` is transformed by `convert`.
   * This transformation converts the input Tree to a Tree of type `M[T]`.
   * The original wrapped expression `wrap(input)` is replaced by a reference to a new local `val $x: T`, where `$x` is a fresh name.
   * These converted inputs are passed to `builder` as well as the list of these synthetic `ValDef`s.
   * The `TupleBuilder` instance constructs a tuple (Tree) from the inputs and defines the right hand side of the vals
   * that unpacks the tuple containing the results of the inputs.
   *
   * The constructed tuple of inputs and the code that unpacks the results of the inputs are then passed to the `i`,
   * which is an implementation of `Instance` that is statically accessible.
   * An Instance defines a applicative functor associated with a specific type constructor and, if it implements MonadInstance as well, a monad.
   * Typically, it will be either a top-level module or a stable member of a top-level module (such as a val or a nested module).
   * The `with Singleton` part of the type verifies some cases at macro compilation time,
   *  while the full check for static accessibility is done at macro expansion time.
   * Note: Ideally, the types would verify that `i: MonadInstance` when `t.isRight`.
   * With the various dependent types involved, this is not worth it.
   *
   * The `t` argument is the argument of the macro that will be transformed as described above.
   * If the macro that calls this method is for a multi-input map (app followed by map),
   * `t` should be the argument wrapped in Left.
   * If this is for multi-input flatMap (app followed by flatMap),
   *  this should be the argument wrapped in Right.
   */
  def contImpl[T, N[_]](
      c: blackbox.Context,
      i: Instance with Singleton,
      convert: Convert,
      builder: TupleBuilder,
      linter: LinterDSL
  )(
      t: Either[c.Expr[T], c.Expr[i.M[T]]],
      inner: Transform[c.type, N]
  )(
      implicit tt: c.WeakTypeTag[T],
      nt: c.WeakTypeTag[N[T]],
      it: c.TypeTag[i.type]
  ): c.Expr[i.M[N[T]]] = {
    import c.universe.{ Apply => ApplyTree, _ }

    val util = ContextUtil[c.type](c)
    val mTC: Type = util.extractTC(i, InstanceTCName)
    val mttpe: Type = appliedType(mTC, nt.tpe :: Nil).dealias

    // the tree for the macro argument
    val (tree, treeType) = t match {
      case Left(l)  => (l.tree, nt.tpe.dealias)
      case Right(r) => (r.tree, mttpe)
    }
    // the Symbol for the anonymous function passed to the appropriate Instance.map/flatMap/pure method
    // this Symbol needs to be known up front so that it can be used as the owner of synthetic vals
    val functionSym = util.functionSymbol(tree.pos)

    val instanceSym = util.singleton(i)
    // A Tree that references the statically accessible Instance that provides the actual implementations of map, flatMap, ...
    val instance = Ident(instanceSym)

    val isWrapper: (String, Type, Tree) => Boolean = convert.asPredicate(c)

    // Local definitions `defs` in the macro.  This is used to ensure references are to M instances defined outside of the macro call.
    // Also `refCount` is the number of references, which is used to create the private, synthetic method containing the body
    val defs = util.collectDefs(tree, isWrapper)
    val checkQual: Tree => Unit = util.checkReferences(defs, isWrapper)

    type In = Input[c.universe.type]
    var inputs = List[In]()

    // transforms the original tree into calls to the Instance functions pure, map, ...,
    //  resulting in a value of type M[T]
    def makeApp(body: Tree): Tree =
      inputs match {
        case Nil      => pure(body)
        case x :: Nil => single(body, x)
        case xs       => arbArity(body, xs)
      }

    // no inputs, so construct M[T] via Instance.pure or pure+flatten
    def pure(body: Tree): Tree = {
      val typeApplied = TypeApply(util.select(instance, PureName), TypeTree(treeType) :: Nil)
      val f = util.createFunction(Nil, body, functionSym)
      val p = ApplyTree(typeApplied, f :: Nil)
      if (t.isLeft) p else flatten(p)
    }
    // m should have type M[M[T]]
    // the returned Tree will have type M[T]
    def flatten(m: Tree): Tree = {
      val typedFlatten = TypeApply(util.select(instance, FlattenName), TypeTree(tt.tpe) :: Nil)
      ApplyTree(typedFlatten, m :: Nil)
    }

    // calls Instance.map or flatmap directly, skipping the intermediate Instance.app that is unnecessary for a single input
    def single(body: Tree, input: In): Tree = {
      val variable = input.local
      val param =
        treeCopy.ValDef(variable, util.parameterModifiers, variable.name, variable.tpt, EmptyTree)
      val typeApplied =
        TypeApply(util.select(instance, MapName), variable.tpt :: TypeTree(treeType) :: Nil)
      val f = util.createFunction(param :: Nil, body, functionSym)
      val mapped = ApplyTree(typeApplied, input.expr :: f :: Nil)
      if (t.isLeft) mapped else flatten(mapped)
    }

    // calls Instance.app to get the values for all inputs and then calls Instance.map or flatMap to evaluate the body
    def arbArity(body: Tree, inputs: List[In]): Tree = {
      val result = builder.make(c)(mTC, inputs)
      val param = util.freshMethodParameter(appliedType(result.representationC, util.idTC :: Nil))
      val bindings = result.extract(param)
      val f = util.createFunction(param :: Nil, Block(bindings, body), functionSym)
      val ttt = TypeTree(treeType)
      val typedApp =
        TypeApply(util.select(instance, ApplyName), TypeTree(result.representationC) :: ttt :: Nil)
      val app =
        ApplyTree(ApplyTree(typedApp, result.input :: f :: Nil), result.alistInstance :: Nil)
      if (t.isLeft) app else flatten(app)
    }

    // Called when transforming the tree to add an input.
    //  For `qual` of type M[A], and a `selection` qual.value,
    //  the call is addType(Type A, Tree qual)
    // The result is a Tree representing a reference to
    //  the bound value of the input.
    def addType(tpe: Type, qual: Tree, selection: Tree): Tree = {
      qual.foreach(checkQual)
      val vd = util.freshValDef(tpe, qual.pos, functionSym)
      inputs ::= new Input(tpe, qual, vd)
      util.refVal(selection, vd)
    }
    def sub(name: String, tpe: Type, qual: Tree, replace: Tree): Converted[c.type] = {
      val tag = c.WeakTypeTag[T](tpe)
      convert[T](c)(name, qual)(tag) transform { tree =>
        addType(tpe, tree, replace)
      }
    }

    // applies the transformation
    linter.runLinter(c)(tree)
    val tx = util.transformWrappers(tree, (n, tpe, t, replace) => sub(n, tpe, t, replace))
    // resetting attributes must be: a) local b) done here and not wider or else there are obscure errors
    val tr = makeApp(inner(tx))
    c.Expr[i.M[N[T]]](tr)
  }

  import Types._

  implicit def applicativeInstance[A[_]](implicit ap: Applicative[A]): Instance.Aux[A] =
    new Instance {
      type M[x] = A[x]
      def app[K[L[x]], Z](in: K[A], f: K[Id] => Z)(implicit a: AList[K]) = a.apply[A, Z](in, f)
      def map[S, T](in: A[S], f: S => T) = ap.map(f, in)
      def pure[S](s: () => S): M[S] = ap.pure(s())
    }

  def compose[A[_], B[_]](implicit a: Aux[A], b: Aux[B]): Instance.Aux2[A, B] =
    new Composed[A, B](a, b)
  // made a public, named, unsealed class because of trouble with macros and inference when the Instance is not an object
  class Composed[A[_], B[_]](a: Aux[A], b: Aux[B]) extends Instance {
    type M[x] = A[B[x]]
    def pure[S](s: () => S): A[B[S]] = a.pure(() => b.pure(s))
    def map[S, T](in: M[S], f: S => T): M[T] = a.map(in, (bv: B[S]) => b.map(bv, f))
    def app[K[L[x]], Z](in: K[M], f: K[Id] => Z)(implicit alist: AList[K]): A[B[Z]] = {
      val g: K[B] => B[Z] = in => b.app[K, Z](in, f)
      type Split[L[x]] = K[(L ∙ B)#l]
      a.app[Split, B[Z]](in, g)(AList.asplit(alist))
    }
  }
}

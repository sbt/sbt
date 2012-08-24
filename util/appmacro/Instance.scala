package sbt
package appmacro

	import Classes.Applicative
	import Types.Id

/** The separate hierarchy from Applicative/Monad is for two reasons.
*
* 1. The type constructor is represented as an abstract type because a TypeTag cannot represent a type constructor directly.
* 2. The applicative interface is uncurried.
*/
trait Instance
{
	type M[x]
	def app[K[L[x]], Z](in: K[M], f: K[Id] => Z)(implicit a: AList[K]): M[Z]
	def map[S,T](in: M[S], f: S => T): M[T]
	def pure[T](t: () => T): M[T]
}
trait Convert
{
	def apply[T: c.AbsTypeTag](c: scala.reflect.macros.Context)(in: c.Tree): c.Tree
}
trait MonadInstance extends Instance
{
	def flatten[T](in: M[M[T]]): M[T]
}
object InputWrapper
{
	def wrap[T](in: Any): T = error("This method is an implementation detail and should not be referenced.")
}

	import scala.reflect._
	import macros._

object Instance
{
	final val DynamicDependencyError = "Illegal dynamic dependency."
	final val DynamicReferenceError = "Illegal dynamic reference."
	final val ApplyName = "app"
	final val FlattenName = "flatten"
	final val PureName = "pure"
	final val MapName = "map"
	final val InstanceTCName = "M"
	final val WrapName = "wrap"

	final class Input[U <: Universe with Singleton](val tpe: U#Type, val expr: U#Tree, val local: U#ValDef)

	/** Implementation of a macro that provides a direct syntax for applicative functors and monads.
	* It is intended to be used in conjunction with another macro that conditions the inputs.
	*
	* This method processes the Tree `t` to find inputs of the form `InputWrapper.wrap[T]( input )`
	* This form is typically constructed by another macro that pretends to be able to get a value of type `T`
	* from a value convertible to `M[T]`.  This `wrap(input)` form has two main purposes.
	* First, it identifies the inputs that should be transformed.
	* Second, it allows the input trees to be wrapped for later conversion into the appropriate `M[T]` type by `convert`.
	* This wrapping is necessary because applying the first macro must preserve the original type,
	* but it is useful to delay conversion until the outer, second macro is called.  The `wrap` method accomplishes this by
	* allowing the original `Tree` and `Type` to be hidden behind the raw `T` type.  This method will remove the call to `wrap`
	* so that it is not actually called at runtime.
	*
	* Each `input` in each expression of the form `InputWrapper.wrap[T]( input )` is transformed by `convert`.
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
	def contImpl[T](c: Context, i: Instance with Singleton, convert: Convert, builder: TupleBuilder)(t: Either[c.Expr[T], c.Expr[i.M[T]]])(
		implicit tt: c.AbsTypeTag[T], it: c.TypeTag[i.type]): c.Expr[i.M[T]] =
	{
			import c.universe.{Apply=>ApplyTree,_}
		
		val util = ContextUtil[c.type](c)
		val mTC: Type = util.extractTC(i, InstanceTCName)
		val mttpe: Type = appliedType(mTC, tt.tpe :: Nil).normalize

		// the tree for the macro argument
		val (tree, treeType) = t match {
			case Left(l) => (l.tree, tt.tpe.normalize)
			case Right(r) => (r.tree, mttpe)
		}

		val instanceSym = util.singleton(i)
		// A Tree that references the statically accessible Instance that provides the actual implementations of map, flatMap, ... 
		val instance = Ident(instanceSym)

		val parameterModifiers = Modifiers(Flag.PARAM)

		val wrapperSym = util.singleton(InputWrapper)
		val wrapMethodSymbol = util.method(wrapperSym, WrapName)
		def isWrapper(fun: Tree) = fun.symbol == wrapMethodSymbol

		type In = Input[c.universe.type]
		var inputs = List[In]()

		// constructs a ValDef with a parameter modifier, a unique name, with the provided Type and with an empty rhs
		def freshMethodParameter(tpe: Type): ValDef =
			ValDef(parameterModifiers, freshTermName("p"), TypeTree(tpe), EmptyTree)

		def freshTermName(prefix: String) = newTermName(c.fresh("$" + prefix))

		/* Local definitions in the macro.  This is used to ensure
		* references are to M instances defined outside of the macro call.*/
		val defs = new collection.mutable.HashSet[Symbol]

		// a reference is illegal if it is to an M instance defined within the scope of the macro call
		def illegalReference(sym: Symbol): Boolean =
			sym != null && sym != NoSymbol && defs.contains(sym)

		// a function that checks the provided tree for illegal references to M instances defined in the
		//  expression passed to the macro and for illegal dereferencing of M instances.
		val checkQual: Tree => Unit = {
			case s @ ApplyTree(fun, qual :: Nil) => if(isWrapper(fun)) c.error(s.pos, DynamicDependencyError)
			case id @ Ident(name) if illegalReference(id.symbol) => c.error(id.pos, DynamicReferenceError)
			case _ => ()
		}
		// adds the symbols for all non-Ident subtrees to `defs`.
		val defSearch: Tree => Unit = {
			case _: Ident => ()
			case tree => if(tree.symbol ne null) defs += tree.symbol; 
		}

		// transforms the original tree into calls to the Instance functions pure, map, ...,
		//  resulting in a value of type M[T]
		def makeApp(body: Tree): Tree =
			inputs match {
				case Nil => pure(body)
				case x :: Nil => single(body, x)
				case xs => arbArity(body, xs)
			}

		// no inputs, so construct M[T] via Instance.pure or pure+flatten
		def pure(body: Tree): Tree =
		{
			val typeApplied = TypeApply(Select(instance, PureName), TypeTree(treeType) :: Nil)
			val p = ApplyTree(typeApplied, Function(Nil, body) :: Nil)
			if(t.isLeft) p else flatten(p)
		}
		// m should have type M[M[T]]
		// the returned Tree will have type M[T]
		def flatten(m: Tree): Tree =
		{
			val typedFlatten = TypeApply(Select(instance, FlattenName), TypeTree(tt.tpe) :: Nil)
			ApplyTree(typedFlatten, m :: Nil)
		}

		// calls Instance.map or flatmap directly, skipping the intermediate Instance.app that is unnecessary for a single input
		def single(body: Tree, input: In): Tree =
		{
			val variable = input.local
			val param = ValDef(parameterModifiers, variable.name, variable.tpt, EmptyTree)
			val typeApplied = TypeApply(Select(instance, MapName), variable.tpt :: TypeTree(treeType) :: Nil)
			val mapped = ApplyTree(typeApplied, input.expr :: Function(param :: Nil, body) :: Nil)
			if(t.isLeft) mapped else flatten(mapped)
		}

		// calls Instance.app to get the values for all inputs and then calls Instance.map or flatMap to evaluate the body
		def arbArity(body: Tree, inputs: List[In]): Tree =
		{
			val result = builder.make(c)(mTC, inputs)
			val param = freshMethodParameter( appliedType(result.representationC, util.idTC :: Nil) )
			val bindings = result.extract(param)
			val f = Function(param :: Nil, Block(bindings, body))
			val ttt = TypeTree(treeType)
			val typedApp = TypeApply(Select(instance, ApplyName), TypeTree(result.representationC) :: ttt :: Nil)
			val app = ApplyTree(ApplyTree(typedApp, result.input :: f :: Nil), result.alistInstance :: Nil)
			if(t.isLeft) app else flatten(app)
		}

		// called when transforming the tree to add an input
		//  for `qual` of type M[A], and a selection qual.value,
		//  the call is addType(Type A, Tree qual)
		// the result is a Tree representing a reference to
		//  the bound value of the input
		def addType(tpe: Type, qual: Tree): Tree =
		{
			qual.foreach(checkQual)
			val vd = util.freshValDef(tpe, qual.symbol)
			inputs ::= new Input(tpe, qual, vd)
			Ident(vd.name)
		}

		// the main tree transformer that replaces calls to InputWrapper.wrap(x) with
		//  plain Idents that reference the actual input value
		object appTransformer extends Transformer
		{
			override def transform(tree: Tree): Tree =
				tree match
				{
					case ApplyTree(TypeApply(fun, t :: Nil), qual :: Nil) if isWrapper(fun) =>
						val tag = c.AbsTypeTag(t.tpe)
						addType(t.tpe, convert(c)(qual)(tag) )
					case _ => super.transform(tree)
				}
		}

		// collects all definitions in the tree.  used for finding illegal references
		tree.foreach(defSearch)

		// applies the transformation
		//   resetting attributes: a) must be local b) must be done
		//   on the transformed tree and not the wrapped tree or else there are obscure errors
		val tr = makeApp( c.resetLocalAttrs(appTransformer.transform(tree)) )
		c.Expr[i.M[T]](tr)
	}

		import Types._

	implicit def applicativeInstance[A[_]](implicit ap: Applicative[A]): Instance { type M[x] = A[x] } = new Instance
	{
		type M[x] = A[x]
		def app[ K[L[x]], Z ](in: K[A], f: K[Id] => Z)(implicit a: AList[K]) = a.apply[A,Z](in, f)
		def map[S,T](in: A[S], f: S => T) = ap.map(f, in)
		def pure[S](s: () => S): M[S] = ap.pure(s())
	}

	type AI[A[_]] = Instance { type M[x] = A[x] }
	def compose[A[_], B[_]](implicit a: AI[A], b: AI[B]): Instance { type M[x] = A[B[x]] } = new Composed[A,B](a,b)
	// made a public, named, unsealed class because of trouble with macros and inference when the Instance is not an object
	class Composed[A[_], B[_]](a: AI[A], b: AI[B]) extends Instance
	{
		type M[x] = A[B[x]]
		def pure[S](s: () => S): A[B[S]] = a.pure(() => b.pure(s))
		def map[S,T](in: M[S], f: S => T): M[T] = a.map(in, (bv: B[S]) => b.map(bv, f))
		def app[ K[L[x]], Z ](in: K[M], f: K[Id] => Z)(implicit alist: AList[K]): A[B[Z]] =
		{
			val g: K[B] => B[Z] = in => b.app[K, Z](in, f)
			type Split[ L[x] ] = K[ (L ∙ B)#l ]
			a.app[Split, B[Z]](in, g)(AList.asplit(alist))
		}
	}
}

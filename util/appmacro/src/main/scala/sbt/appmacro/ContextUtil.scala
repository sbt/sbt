package sbt
package appmacro

	import scala.reflect._
	import macros._
	import scala.tools.nsc.Global
	import ContextUtil.{DynamicDependencyError, DynamicReferenceError}

object ContextUtil {
	final val DynamicDependencyError = "Illegal dynamic dependency"
	final val DynamicReferenceError = "Illegal dynamic reference"

	/** Constructs an object with utility methods for operating in the provided macro context `c`.
	* Callers should explicitly specify the type parameter as `c.type` in order to preserve the path dependent types. */
	def apply[C <: Context with Singleton](c: C): ContextUtil[C] = new ContextUtil(c)


	/** Helper for implementing a no-argument macro that is introduced via an implicit.
	* This method removes the implicit conversion and evaluates the function `f` on the target of the conversion.
	*
	* Given `myImplicitConversion(someValue).extensionMethod`, where `extensionMethod` is a macro that uses this
	* method, the result of this method is `f(<Tree of someValue>)`. */
	def selectMacroImpl[T: c.WeakTypeTag, S: c.WeakTypeTag](c: Context)(f: c.Expr[S] => c.Expr[T]): c.Expr[T] =
	{
			import c.universe._
		c.macroApplication match {
			case Select(Apply(_, t :: Nil), _) => f( c.Expr[S](t) )
			case x => unexpectedTree(x)
		}
	}

	def unexpectedTree[C <: Context](tree: C#Tree): Nothing = error("Unexpected macro application tree (" + tree.getClass + "): " + tree)
}

/** Utility methods for macros.  Several methods assume that the context's universe is a full compiler (`scala.tools.nsc.Global`).
* This is not thread safe due to the underlying Context and related data structures not being thread safe.
* Use `ContextUtil[c.type](c)` to construct. */
final class ContextUtil[C <: Context](val ctx: C) 
{
		import ctx.universe.{Apply=>ApplyTree,_}

	lazy val alistType = ctx.typeOf[AList[KList]]
	lazy val alist: Symbol = alistType.typeSymbol.companionSymbol
	lazy val alistTC: Type = alistType.typeConstructor

	/** Modifiers for a local val.*/
	lazy val localModifiers = Modifiers(NoFlags)

	def getPos(sym: Symbol) = if(sym eq null) NoPosition else sym.pos

	def atypeOf[T](implicit att: WeakTypeTag[T]): Type = att.tpe

	/** Constructs a unique term name with the given prefix within this Context.
	* (The current implementation uses Context.fresh, which increments*/
	def freshTermName(prefix: String) = newTermName(ctx.fresh("$" + prefix))

	/** Constructs a new, local ValDef with the given Type, a unique name, 
	* the same position as `sym`, and an empty implementation (no rhs). */
	def freshValDef(tpe: Type, sym: Symbol): ValDef =
	{
		val vd = localValDef(TypeTree(tpe), EmptyTree)
		vd setPos getPos(sym)
		vd
	}

	/* Tests whether a Tree is a Select on `methodName`. */
	def isWrapper(methodName: String): Tree => Boolean = {
		case Select(_, nme) => nme.decoded == methodName
		case _ => false
	}

	lazy val parameterModifiers = Modifiers(Flag.PARAM)

	/** Collects all definitions in the tree for use in checkReferences.
	* This excludes definitions in wrapped expressions because checkReferences won't allow nested dereferencing anyway. */
	def collectDefs(tree: Tree, isWrapper: Tree => Boolean): collection.Set[Symbol] = 
	{
		val defs = new collection.mutable.HashSet[Symbol]
		// adds the symbols for all non-Ident subtrees to `defs`.
		val process = new Traverser {
			override def traverse(t: Tree) = t match {
				case _: Ident => ()
				case ApplyTree(TypeApply(fun, tpe :: Nil), qual :: Nil) if isWrapper(fun) => ()
				case tree =>
					if(tree.symbol ne null) defs += tree.symbol;
					super.traverse(tree)
			}
		}
		process.traverse(tree)
		defs
	}

	/** A reference is illegal if it is to an M instance defined within the scope of the macro call.
	* As an approximation, disallow referenced to any local definitions `defs`. */
	def illegalReference(defs: collection.Set[Symbol], sym: Symbol): Boolean =
		sym != null && sym != NoSymbol && defs.contains(sym)

	/** A function that checks the provided tree for illegal references to M instances defined in the
	*  expression passed to the macro and for illegal dereferencing of M instances. */
	def checkReferences(defs: collection.Set[Symbol], isWrapper: Tree => Boolean): Tree => Unit = {
		case s @ ApplyTree(TypeApply(fun, tpe :: Nil), qual :: Nil) => if(isWrapper(fun)) ctx.error(s.pos, DynamicDependencyError)
		case id @ Ident(name) if illegalReference(defs, id.symbol) => ctx.error(id.pos, DynamicReferenceError + ": " + name)
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
	{
		val global: Global = ctx.universe.asInstanceOf[Global]
		global.gen.mkTuple(args.asInstanceOf[List[global.Tree]]).asInstanceOf[ctx.universe.Tree]
	}

	/** Creates a new, synthetic type variable with the specified `owner`. */
	def newTypeVariable(owner: Symbol, prefix: String = "T0"): TypeSymbol =
	{
		val global: Global = ctx.universe.asInstanceOf[Global]
		owner.asInstanceOf[global.Symbol].newSyntheticTypeParam(prefix, 0L).asInstanceOf[ctx.universe.TypeSymbol]
	}
	/** The type representing the type constructor `[X] X` */
	lazy val idTC: Type =
	{
		val tvar = newTypeVariable(NoSymbol)
		polyType(tvar :: Nil, refVar(tvar))
	}
	/** A Type that references the given type variable. */
	def refVar(variable: TypeSymbol): Type = variable.toTypeConstructor
	/** Constructs a new, synthetic type variable that is a type constructor. For example, in type Y[L[x]], L is such a type variable. */
	def newTCVariable(owner: Symbol): TypeSymbol =
	{
		val tc = newTypeVariable(owner)
		val arg = newTypeVariable(tc, "x")
		tc.setTypeSignature(PolyType(arg :: Nil, emptyTypeBounds))
		tc
	}
	/** >: Nothing <: Any */
	def emptyTypeBounds: TypeBounds = TypeBounds(definitions.NothingClass.toType, definitions.AnyClass.toType)

	def functionType(args: List[Type], result: Type): Type =
	{
		val global: Global = ctx.universe.asInstanceOf[Global]
		val tpe = global.definitions.functionType(args.asInstanceOf[List[global.Type]], result.asInstanceOf[global.Type])
		tpe.asInstanceOf[Type]
	}

	/** Create a Tree that references the `val` represented by `vd`. */
	def refVal(vd: ValDef): Tree =
	{
		val t = Ident(vd.name)
		assert(vd.tpt.tpe != null, "val type is null: " + vd + ", tpt: " + vd.tpt.tpe)
		t.setType(vd.tpt.tpe)
		t
	}

	/** Returns the Symbol that references the statically accessible singleton `i`. */
	def singleton[T <: AnyRef with Singleton](i: T)(implicit it: ctx.TypeTag[i.type]): Symbol =
		it.tpe match {
			case SingleType(_, sym) if !sym.isFreeTerm && sym.isStatic => sym
			case x => error("Instance must be static (was " + x + ").")
		}

	/** Returns the symbol for the non-private method named `name` for the class/module `obj`. */
	def method(obj: Symbol, name: String): Symbol = {
		val global: Global = ctx.universe.asInstanceOf[Global]
		val ts: Type = obj.typeSignature
		val m: global.Symbol = ts.asInstanceOf[global.Type].nonPrivateMember(global.newTermName(name))
		m.asInstanceOf[Symbol]
	}

	/** Returns a Type representing the type constructor tcp.<name>.  For example, given
	*  `object Demo { type M[x] = List[x] }`, the call `extractTC(Demo, "M")` will return a type representing
	* the type constructor `[x] List[x]`.
	**/
	def extractTC(tcp: AnyRef with Singleton, name: String)(implicit it: ctx.TypeTag[tcp.type]): ctx.Type =
	{
		val global: Global = ctx.universe.asInstanceOf[Global]
		val itTpe = it.tpe.asInstanceOf[global.Type]
		val m = itTpe.nonPrivateMember(global.newTypeName(name))
		val tc = itTpe.memberInfo(m).asInstanceOf[ctx.universe.Type]
		assert(tc != NoType && tc.takesTypeArgs, "Invalid type constructor: " + tc)
		tc
	}

	/** Substitutes wrappers in tree `t` with the result of `subWrapper`.
	* A wrapper is a Tree of the form `f[T](v)` for which isWrapper(<Tree of f>) returns true.
	* Typically, `f` is a `Select` or `Ident`.
	* The wrapper is replaced with the result of `subWrapper(<Type of T>, <Tree of v>)` */
	def transformWrappers(t: Tree, isWrapper: Tree => Boolean, subWrapper: (Type, Tree) => Tree): Tree =
	{
		// the main tree transformer that replaces calls to InputWrapper.wrap(x) with
		//  plain Idents that reference the actual input value
		object appTransformer extends Transformer
		{
			override def transform(tree: Tree): Tree =
				tree match
				{
					case ApplyTree(TypeApply(fun, targ :: Nil), qual :: Nil) if isWrapper(fun) =>
						assert(qual.tpe != null, "Internal error: null type for wrapped tree with " + qual.getClass + "\n\t" + qual + "\n in " + t)
						subWrapper(targ.tpe, qual)
					case _ => super.transform(tree)
				}
		}

		appTransformer.transform(t)
	}
}
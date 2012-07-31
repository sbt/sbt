package sbt
package appmacro

	import scala.reflect._
	import makro._
	import scala.tools.nsc.Global

object ContextUtil {
	/** Constructs an object with utility methods for operating in the provided macro context `c`.
	* Callers should explicitly specify the type parameter as `c.type` in order to preserve the path dependent types. */
	def apply[C <: Context with Singleton](c: C): ContextUtil[C] = new ContextUtil(c)
}

/** Utility methods for macros.  Several methods assume that the context's universe is a full compiler (`scala.tools.nsc.Global`).
* This is not thread safe due to the underlying Context and related data structures not being thread safe.
* Use `ContextUtil[c.type](c)` to construct. */
final class ContextUtil[C <: Context with Singleton](val ctx: C) 
{
		import ctx.universe.{Apply=>ApplyTree,_}

	val alistType = ctx.typeOf[AList[KList]]
	val alist: Symbol = alistType.typeSymbol.companionSymbol
	val alistTC: Type = alistType.typeConstructor

	/** Modifiers for a local val.*/
	val localModifiers = Modifiers(NoFlags)

	def getPos(sym: Symbol) = if(sym eq null) NoPosition else sym.pos

	/** Constructs a unique term name with the given prefix within this Context.
	* (The current implementation uses Context.fresh, which increments*/
	def freshTermName(prefix: String) = newTermName(ctx.fresh("$" + prefix))

	def typeTree(tpe: Type) = TypeTree().setType(tpe)

	/** Constructs a new, local ValDef with the given Type, a unique name, 
	* the same position as `sym`, and an empty implementation (no rhs). */
	def freshValDef(tpe: Type, sym: Symbol): ValDef =
	{
		val vd = localValDef(typeTree(tpe), EmptyTree)
		vd setPos getPos(sym)
		vd
	}

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
	def newTypeVariable(owner: Symbol): Symbol =
	{
		val global: Global = ctx.universe.asInstanceOf[Global]
		owner.asInstanceOf[global.Symbol].newSyntheticTypeParam().asInstanceOf[ctx.universe.Symbol]
	}
	/** The type representing the type constructor `[X] X` */
	val idTC: Type =
	{
		val tvar = newTypeVariable(NoSymbol)
		polyType(tvar :: Nil, refVar(tvar))
	}
	/** Constructs a new, synthetic type variable that is a type constructor. For example, in type Y[L[x]], L is such a type variable. */
	def newTCVariable(owner: Symbol): Symbol =
	{
		val global: Global = ctx.universe.asInstanceOf[Global]
		val tc = owner.asInstanceOf[global.Symbol].newSyntheticTypeParam()
		val arg = tc.newSyntheticTypeParam("x", 0L)
		tc.setInfo(global.PolyType(arg :: Nil, global.TypeBounds.empty)).asInstanceOf[ctx.universe.Symbol]
	}
	/** Returns the Symbol that references the statically accessible singleton `i`. */
	def singleton[T <: AnyRef with Singleton](i: T)(implicit it: ctx.TypeTag[i.type]): Symbol =
		it.tpe match {
			case SingleType(_, sym) if !sym.isFreeTerm && sym.isStatic => sym
			case x => error("Instance must be static (was " + x + ").")
		}
	/** Constructs a Type that references the given type variable. */
	def refVar(variable: Symbol): Type = typeRef(NoPrefix, variable, Nil)

	/** Returns the symbol for the non-private method named `name` for the class/module `obj`. */
	def method(obj: Symbol, name: String): Symbol = {
		val global: Global = ctx.universe.asInstanceOf[Global]
		obj.asInstanceOf[global.Symbol].info.nonPrivateMember(global.newTermName(name)).asInstanceOf[ctx.universe.Symbol]
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
		assert(tc != NoType && tc.isHigherKinded, "Invalid type constructor: " + tc)
		tc
	}
}
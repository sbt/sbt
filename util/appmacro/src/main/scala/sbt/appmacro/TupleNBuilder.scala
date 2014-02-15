package sbt
package appmacro

	import Types.Id
	import scala.reflect._
	import macros._

/** A builder that uses a TupleN as the tuple representation.
* It is limited to tuples of size 2 to `MaxInputs`. */
object TupleNBuilder extends TupleBuilder
{
	/** The largest number of inputs that this builder can handle. */
	final val MaxInputs = 11
	final val TupleMethodName = "tuple"

	def make(c: Context)(mt: c.Type, inputs: Inputs[c.universe.type]): BuilderResult[c.type] = new BuilderResult[c.type]
	{
		import c.universe.{Apply=>ApplyTree,_}
		import c.internal._
		import decorators._
		val util = ContextUtil[c.type](c)

		val ctx: c.type = c
		val representationC: PolyType = {
			val tcVariable: Symbol = util.newTCVariable(enclosingOwner)
			val tupleTypeArgs = inputs.map(in => typeRef(NoPrefix, tcVariable, in.tpe :: Nil))
			val tuple = appliedType(definitions.TupleClass(tupleTypeArgs.length), tupleTypeArgs)
			polyType(tcVariable :: Nil, tuple)
		}
		val resultType = appliedType(representationC, util.idTC :: Nil)

		val input: Tree = util.mkTuple(inputs.map(_.expr))
		val alistInstance: Tree = {
			val selectTree = util.select(Ident(util.alist), TupleMethodName + inputs.size.toString)
			TypeApply(selectTree, inputs.map(in => TypeTree(in.tpe)))
		}
		def extract(param: ValDef): List[ValDef] = bindTuple(param, Nil, inputs.map(_.local), 1)

		def bindTuple(param: ValDef, revBindings: List[ValDef], params: List[ValDef], i: Int): List[ValDef] =
			params match
			{
				case (x @ ValDef(mods, name, tpt, _)) :: xs =>
					val rhs = util.select(Ident(param.name), "_" + i.toString)
					val newVal = treeCopy.ValDef(x, mods, name, tpt, rhs)
					newVal.setSymbol(x.symbol)
					bindTuple(param, newVal :: revBindings, xs, i+1)
				case Nil => revBindings.reverse
			}
	}
}

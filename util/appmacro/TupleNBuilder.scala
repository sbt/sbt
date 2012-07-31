package sbt
package appmacro

	import Types.Id
	import scala.tools.nsc.Global
	import scala.reflect._
	import makro._

/** A builder that uses a TupleN as the tuple representation.
* It is limited to tuples of size 2 to `MaxInputs`. */
object TupleNBuilder extends TupleBuilder
{
	/** The largest number of inputs that this builder can handle. */
	final val MaxInputs = 11
	final val TupleMethodName = "tuple"

	def make(c: Context)(mt: c.Type, inputs: Inputs[c.universe.type]): BuilderResult[c.type] = new BuilderResult[c.type]
	{
		val util = ContextUtil[c.type](c)
			import c.universe.{Apply=>ApplyTree,_}
			import util._

		val global: Global = c.universe.asInstanceOf[Global]
		val mTC: Type = mt.asInstanceOf[c.universe.Type]

		val ctx: c.type = c
		val representationC: PolyType = {
			val tcVariable: Symbol = newTCVariable(NoSymbol)
			val tupleTypeArgs = inputs.map(in => typeRef(NoPrefix, tcVariable, in.tpe :: Nil).asInstanceOf[global.Type])
			val tuple = global.definitions.tupleType(tupleTypeArgs)
			PolyType(tcVariable :: Nil, tuple.asInstanceOf[Type] )
		}
		val resultType = appliedType(representationC, idTC :: Nil)

		val input: Tree = mkTuple(inputs.map(_.expr))
		val alistInstance: Tree = {
			val select = Select(Ident(alist), TupleMethodName + inputs.size.toString)
			TypeApply(select, inputs.map(in => typeTree(in.tpe)))
		}
		def extract(param: ValDef): List[ValDef] = bindTuple(param, Nil, inputs.map(_.local), 1)

		def bindTuple(param: ValDef, revBindings: List[ValDef], params: List[ValDef], i: Int): List[ValDef] =
			params match
			{
				case ValDef(mods, name, tpt, _) :: xs =>
					val x = ValDef(mods, name, tpt, Select(Ident(param.name), "_" + i.toString))
					bindTuple(param, x :: revBindings, xs, i+1)
				case Nil => revBindings.reverse
			}
	}
}

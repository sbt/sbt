package xsbt.api

	import xsbti.api._
	import scala.collection.mutable.HashSet

object APIUtil
{
	val modifiersToByte = (m: Modifiers) => {
			import m._
		def x(b: Boolean, bit: Int) = if(b) 1 << bit else 0
		( x(isAbstract, 0) | x(isOverride, 1) | x(isFinal, 2) | x(isSealed, 3) | x(isImplicit, 4) | x(isLazy, 5) ).toByte
	}
	val byteToModifiers = (b: Byte) => {
		def x(bit: Int) = (b & (1 << bit)) != 0
		new Modifiers( x(0), x(1), x(2), x(3), x(4), x(5) )
	}

	def verifyTypeParameters(s: Source): Boolean =
	{
		val check = new CheckTypeParameters
		val invalid = check(s)
		if(!invalid.isEmpty) println("References to undefined type parameters: " + invalid.mkString(", "))
		invalid.isEmpty
	}
	private[this] class CheckTypeParameters extends Visit
	{
		private val defined = new HashSet[Int]
		private val referenced = new HashSet[Int]
		def apply(s: Source): List[Int] =
		{
			super.visit(s)
			(referenced filterNot defined).toList
		}
		override def visitTypeParameter(parameter: TypeParameter)
		{
			defined += parameter.id
			super.visitTypeParameter(parameter)
		}
		override def visitParameterRef(ref: ParameterRef)
		{
			referenced += ref.id
			super.visitParameterRef(ref)
		}
	}
}
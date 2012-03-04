package xsbt.api

	import xsbti.SafeLazy
	import xsbti.api._
	import scala.collection.mutable.HashSet

object APIUtil
{
	val modifiersToByte = (m: Modifiers) => {
			import m._
		def x(b: Boolean, bit: Int) = if(b) 1 << bit else 0
		( x(isAbstract, 0) | x(isOverride, 1) | x(isFinal, 2) | x(isSealed, 3) | x(isImplicit, 4) | x(isLazy, 5) | x(isMacro, 6) ).toByte
	}
	val byteToModifiers = (b: Byte) => {
		def x(bit: Int) = (b & (1 << bit)) != 0
		new Modifiers( x(0), x(1), x(2), x(3), x(4), x(5), x(6) )
	}

	def verifyTypeParameters(s: SourceAPI): Boolean =
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
		def apply(s: SourceAPI): List[Int] =
		{
			super.visitAPI(s)
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

	def hasMacro(s: SourceAPI): Boolean =
	{
		val check = new HasMacro
		check.visitAPI(s)
		check.hasMacro
	}

	private[this] class HasMacro extends Visit
	{
		var hasMacro = false

		override def visitModifiers(m: Modifiers)
		{
			hasMacro ||= m.isMacro
			super.visitModifiers(m)
		}
	}

	def minimize(api: SourceAPI): SourceAPI =
		new SourceAPI(api.packages, minimizeDefinitions(api.definitions))
	def minimizeDefinitions(ds: Array[Definition]): Array[Definition] =
		ds flatMap minimizeDefinition
	def minimizeDefinition(d: Definition): Array[Definition] =
		d match
		{
			case c: ClassLike => Array(minimizeClass(c))
			case _ => Array()
		}
	def minimizeClass(c: ClassLike): ClassLike =
	{
		val savedAnnotations = Discovery.defAnnotations(c.structure, (_: Any) => true).toArray[String]
		val struct = minimizeStructure(c.structure, c.definitionType == DefinitionType.Module)
		new ClassLike(c.definitionType, lzy(emptyType), lzy(struct), savedAnnotations, c.typeParameters, c.name, c.access, c.modifiers, c.annotations)
	}

	def minimizeStructure(s: Structure, isModule: Boolean): Structure =
		new Structure(lzy(s.parents), filterDefinitions(s.declared, isModule), filterDefinitions(s.inherited, isModule))
	def filterDefinitions(ds: Array[Definition], isModule: Boolean): Lazy[Array[Definition]] =
		lzy(if(isModule) ds filter Discovery.isMainMethod else Array())
	private[this] def lzy[T <: AnyRef](t: T): Lazy[T] = SafeLazy.strict(t)

	private[this] val emptyType = new EmptyType
}
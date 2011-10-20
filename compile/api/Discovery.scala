/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package xsbt.api

	import xsbti.api.{Path => APath, _}

import Discovery._

class Discovery(baseClasses: Set[String], annotations: Set[String])
{
	def apply(s: Seq[Definition]): Seq[(Definition, Discovered)] =
		s.map { d => (d, apply(d)) }
	def apply(d: Definition): Discovered =
		d match
		{
			case c: ClassLike if isPublic(c) && isConcrete(c.modifiers) => discover(c)
			case _ => Discovered.empty
		}
	def discover(c: ClassLike): Discovered =
	{
		val onClass = Discovery.findAnnotations(c.annotations, annotations)
		val onDefs = Discovery.defAnnotations(c.structure, annotations) ++ c.savedAnnotations.filter(annotations)
		val module = isModule(c)
		new Discovered( bases(c.name, c.structure.parents), onClass ++ onDefs, module && hasMainMethod(c), module )
	}
	def bases(own: String, c: Seq[Type]): Set[String] =
		(own +: c.flatMap(simpleName)).filter(baseClasses).toSet

}
object Discovery
{
	def apply(subclasses: Set[String], annotations: Set[String])(definitions: Seq[Definition]): Seq[(Definition, Discovered)] =
	{
		val d = new Discovery(subclasses, annotations)
		d(definitions)
	}
	def applications(definitions: Seq[Definition]): Seq[(Definition, Discovered)] =
		apply(Set.empty, Set.empty)( definitions )

	def findAnnotations(as: Seq[Annotation], pred: String => Boolean): Set[String] =
		as.flatMap { a => simpleName(a.base).filter(pred) }.toSet
	def defAnnotations(s: Structure, pred: String => Boolean): Set[String] =
		defAnnotations(s.declared, pred) ++ defAnnotations(s.inherited, pred)
	def defAnnotations(defs: Seq[Definition], pred: String => Boolean): Set[String] =
		findAnnotations( defs.flatMap { case d: Def if isPublic(d) => d.annotations.toSeq; case _ => Nil }, pred )

	def isConcrete(a: Definition): Boolean = isConcrete(a.modifiers)
	def isConcrete(m: Modifiers) = !m.isAbstract
	def isPublic(a: Definition): Boolean = isPublic(a.access)
	def isPublic(a: Access): Boolean = a.isInstanceOf[Public]
	def isModule(c: ClassLike) = c.definitionType == DefinitionType.Module

	def hasMainMethod(c: ClassLike): Boolean =
		hasMainMethod(c.structure.declared) || hasMainMethod(c.structure.inherited)
	def hasMainMethod(defs: Seq[Definition]): Boolean =
		defs.exists(isMainMethod)
	def isMainMethod(d: Definition): Boolean =
		d match {
			case d: Def => d.name == "main" && isPublic(d) && isConcrete(d) && isUnit(d.returnType) && isStringArray(d.valueParameters)
			case _ => false
		}
	def isStringArray(vp: IndexedSeq[ParameterList]): Boolean = vp.length == 1 && isStringArray(vp(0).parameters)
	def isStringArray(params: Seq[MethodParameter]): Boolean = params.length == 1 && isStringArray(params(0))
	def isStringArray(p: MethodParameter): Boolean = p.modifier == ParameterModifier.Plain && isStringArray(p.tpe)
	def isStringArray(t: Type): Boolean = isParameterized(t, "scala.Array", "java.lang.String") // doesn't handle scala.this#Predef#String, should API phase dealias?

	def isParameterized(t: Type, base: String, args: String*): Boolean = t match {
		case p: Parameterized =>
			named(p.baseType, base) && p.typeArguments.length == args.length && p.typeArguments.flatMap(simpleName).sameElements(args)
		case _ => false
	}
	def named(t: Type, nme: String) = simpleName(t) == Some(nme)

	def simpleName(t: Type): Option[String] = t match {
		case a: Annotated => simpleName(a.baseType)
		case sing: Singleton => None
		case p: Projection =>
			p.prefix match {
				case s: Singleton => pathName(s.path, p.id)
				case e: EmptyType => Some( p.id )
				case _ => None
			}
		case _ => None
	}

	def pathName(p: APath, id: String): Option[String] =
	{
		val cs = p.components
		cs.last match
		{
			case _: This =>
				val ids = cs.init.collect { case i: Id => i.id }
				if(ids.length == cs.length - 1) Some( (ids ++ Seq(id)).mkString(".") ) else None
			case _ => None
		}
	}

	def isUnit(t: Type): Boolean = named(t, "scala.Unit")
}
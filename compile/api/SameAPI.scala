/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package xsbt.api

import xsbti.api._

import Function.tupled
import scala.collection.{immutable, mutable}

object SameAPI
{
	def apply(a: Source, b: Source) =
	{
		val start = System.currentTimeMillis
		val result = (new SameAPI).check(a,b)
		val end = System.currentTimeMillis
		println(" API comparison took: " + (end - start) / 1000.0 + " s")
		result
	}
}
private class SameAPI
{
	abstract class Result(val referencesMap: immutable.Map[Int, Int], val parameterMap: immutable.Map[Int, Int]) extends NotNull
	{
		def &&(o: => Result): Result
		def value: Boolean
	}
	final object False extends Result(Map.empty, Map.empty)
	{
		def &&(o: => Result): Result = this
		def value = false
	}
	final class True(references: immutable.Map[Int, Int], parameters: immutable.Map[Int, Int]) extends Result(references, parameters)
	{
		def value = true
		def &&(o: => Result): Result = o match { case False => o; case t: True => newTrue(t) }
		private def newTrue(t: True): True =
		{
			def equalIntersection(a: Map[Int, Int], b: Map[Int, Int]) =
			{
				val sameKeys = a.keySet ** b.keySet
				assume(sameKeys.forall(key => a(key) == b(key)))
			}
			equalIntersection(referencesMap, t.referencesMap)
			equalIntersection(parameterMap, t.parameterMap)
			new True(referencesMap ++ t.referencesMap, parameterMap ++ t.parameterMap)
		}
	}
	implicit def b2b(b: Boolean): Result = if(b) new True(Map.empty, Map.empty) else False

	def debug(flag: Result, msg: => String): Result =
	{
		if(!flag.value) println(msg)
		flag
	}

	def printMaps(b: Result) = println(mapStrings(b))
	def mapStrings(b: Result) =
	{
		"parameter map:\n\t" +
		b.parameterMap.mkString("\n\t") +
		"\nreference map:\n\t" +
		b.referencesMap.mkString("\n\t")
	}
	def check(a: Source, b: Source): Boolean =
	{
		val initialResult =
			samePackages(a, b) &&
			debug(sameDefinitions(a, b), "Definitions differed")
		initialResult.value && 
		debug(sameReferences(initialResult.referencesMap, initialResult.parameterMap), "References differed: \n" + mapStrings(initialResult)).value
	}

	def sameReferences(referencesMap: Map[Int,Int], parameterMap: Map[Int,Int]): Boolean =
		referencesMap.forall { case (a, b) => parameterMap(a) == b }

	def samePackages(a: Source, b: Source): Result =
		sameStrings(packages(a), packages(b))
	def packages(s: Source): Set[String] =
		Set() ++ s.packages.map(_.name)

	def sameDefinitions(a: Source, b: Source): Result =
		sameDefinitions(a.definitions, b.definitions)
	def sameDefinitions(a: Seq[Definition], b: Seq[Definition]): Result =
	{
		val (avalues, atypes) = separateDefinitions(a)
		val (bvalues, btypes) = separateDefinitions(b)
		debug(sameDefinitions(byName(avalues), byName(bvalues)), "Value definitions differed") &&
		debug(sameDefinitions(byName(atypes), byName(btypes)), "Type definitions differed")
	}
	def separateDefinitions(s: Seq[Definition]): (Seq[Definition], Seq[Definition]) =
		s.toArray.partition(isValueDefinition)
	def sameDefinitions(a: scala.collection.Map[String, List[Definition]], b: scala.collection.Map[String, List[Definition]]): Result =
		debug(sameStrings(a.keySet, b.keySet), "\tDefinition strings differed") && forall(zippedEntries(a,b))(tupled(sameNamedDefinitions))

	def sameNamedDefinitions(a: List[Definition], b: List[Definition]): Result =
	{
		println("Comparing " + a.size + " defs against " + b.size + " defs")
		def sameDefs(a: List[Definition], b: List[Definition], v: Result): Result =
		{
			println("\t " + a.size + " to " + b.size + "")
			a match
			{
				case adef :: atail =>
					def sameDef(seen: List[Definition], remaining: List[Definition]): Result =
						remaining match
						{
							case Nil => debug(false, "Definition different in new API: \n" + adef.name )
							case bdef :: btail =>
								val eq = v && sameDefinitionContent(adef, bdef)
								if(eq.value) printMaps(eq)
								if(eq.value) sameDefs(atail, seen ::: btail, eq) else sameDef(bdef :: seen, btail)
						}
					sameDef(Nil, b)
				case Nil => v
			}
		}
		//if(a.length > 1) println("Comparing\n" + a.mkString("\n\t") + "\nagainst\n" + b.mkString("\n\t") + "\n\n")
		debug((a.length == b.length), "\t\tLength differed for " + a.headOption.map(_.name).getOrElse("empty")) && sameDefs(a, b, true)
	}

	def isValueDefinition(d: Definition): Boolean =
		d match
		{
			case _: FieldLike | _: Def=> true
			case c: ClassLike => isValue(c.definitionType)
			case _ => false
		}
	def isValue(d: DefinitionType): Boolean =
		d == DefinitionType.Module || d == DefinitionType.PackageModule
	def byName(s: Seq[Definition]): scala.collection.Map[String, List[Definition]] =
	{
		val map = new mutable.HashMap[String, List[Definition]]
		for(d <- s; name = d.name)
			map(name) = d :: map.getOrElse(name, Nil)
		map.readOnly
	}

	// doesn't check name
	def sameDefinitionContent(a: Definition, b: Definition): Result =
		//a.name == b.name &&
		debug(sameAccess(a.access, b.access), "Access differed") &&
		debug(sameModifiers(a.modifiers, b.modifiers), "Modifiers differed") &&
		debug(sameAnnotations(a.annotations, b.annotations), "Annotations differed") &&
		debug(sameDefinitionSpecificAPI(a, b), "Definition-specific differed")

	def sameAccess(a: Access, b: Access): Result =
		(a, b) match
		{
			case (_: Public, _: Public) => true
			case (qa: Protected, qb: Protected) => sameQualifier(qa, qb)
			case (qa: Private, qb: Private) => sameQualifier(qa, qb)
			case (qa: Pkg, qb: Pkg) => sameQualifier(qa, qb)
			case _ => debug(false, "Different access categories")
		}
	def sameQualifier(a: Qualified, b: Qualified): Result =
		sameQualifier(a.qualifier, b.qualifier)
	def sameQualifier(a: Qualifier, b: Qualifier): Result =
		(a, b) match
		{
			case (_: Unqualified, _: Unqualified) => true
			case (_: ThisQualifier, _: ThisQualifier) => true
			case (ia: IdQualifier, ib: IdQualifier) => debug(ia.value == ib.value, "Different qualifiers")
			case _ => debug(false, "Different qualifier categories: " + a.getClass.getName +  " -- " +b.getClass.getName)
		}

	def sameModifiers(a: Modifiers, b: Modifiers): Result =
		bitSet(a) == bitSet(b)

	def bitSet(m: Modifiers): immutable.BitSet =
	{
		import m._
		val bs = new mutable.BitSet
		setIf(bs, isAbstract, 0)
		setIf(bs, isDeferred, 1)
		setIf(bs, isOverride, 2)
		setIf(bs, isFinal, 3)
		setIf(bs, isSealed, 4)
		setIf(bs, isImplicit, 5)
		setIf(bs, isLazy, 6)
		setIf(bs, isSynthetic, 7)
		bs.toImmutable
	}
	def setIf(bs: mutable.BitSet, flag: Result, i: Int): Unit =
		if(flag.value) bs += i

	def sameAnnotations(a: Seq[Annotation], b: Seq[Annotation]): Result =
		sameSeq(a, b)(sameAnnotation)
	def sameAnnotation(a: Annotation, b: Annotation): Result =
		debug(sameSimpleType(a.base, b.base), "Annotation base type differed") &&
		debug(sameAnnotationArguments(a.arguments, b.arguments), "Annotation arguments differed (" + a + ") and (" + b + ")")
	def sameAnnotationArguments(a: Seq[AnnotationArgument], b: Seq[AnnotationArgument]): Result =
		argumentMap(a) == argumentMap(b)
	def argumentMap(a: Seq[AnnotationArgument]): Map[String,String] =
		Map() ++ a.map(arg => (arg.name, arg.value))
		
	def sameDefinitionSpecificAPI(a: Definition, b: Definition): Result =
		(a, b) match
		{
			case (fa: FieldLike, fb: FieldLike) => sameFieldSpecificAPI(fa, fb)
			case (pa: ParameterizedDefinition, pb: ParameterizedDefinition) => sameParameterizedDefinition(pa, pb)
			case _ => false
		}

	def sameParameterizedDefinition(a: ParameterizedDefinition, b: ParameterizedDefinition): Result =
		debug(sameTypeParameters(a.typeParameters, b.typeParameters), "Different type parameters for " + a.name) &&
		sameParameterizedSpecificAPI(a, b)

	def sameParameterizedSpecificAPI(a: ParameterizedDefinition, b: ParameterizedDefinition): Result =
		(a, b) match
		{
			case (da: Def, db: Def) => sameDefSpecificAPI(da, db)
			case (ca: ClassLike, cb: ClassLike) => sameClassLikeSpecificAPI(ca, cb)
			case (ta: TypeAlias, tb: TypeAlias) => sameAliasSpecificAPI(ta, tb)
			case (ta: TypeDeclaration, tb: TypeDeclaration) => sameDeclarationSpecificAPI(ta, tb)
			case _ => false
		}

	def sameDefSpecificAPI(a: Def, b: Def): Result =
		debug(sameValueParameters(a.valueParameters, b.valueParameters), "Different def value parameters for " + a.name) &&
		debug(sameType(a.returnType, b.returnType), "Different def return type for " + a.name)
	def sameAliasSpecificAPI(a: TypeAlias, b: TypeAlias): Result =
		debug(sameType(a.tpe, b.tpe), "Different alias type for " + a.name)
	def sameDeclarationSpecificAPI(a: TypeDeclaration, b: TypeDeclaration): Result =
		debug(sameType(a.lowerBound, b.lowerBound), "Different lower bound for declaration " + a.name) &&
		debug(sameType(a.upperBound, b.upperBound), "Different upper bound for declaration " + a.name)
	def sameFieldSpecificAPI(a: FieldLike, b: FieldLike): Result =
		debug(sameFieldCategory(a, b), "Different field categories (" + a.name + "=" + a.getClass.getName +  " -- " +a.name + "=" + a.getClass.getName + ")")&&
		debug(sameType(a.tpe, b.tpe), "Different field type for " + a.name)

	def sameFieldCategory(a: FieldLike, b: FieldLike): Result =
		(a,b) match
		{
			case (_: Val, _: Val) => true
			case (_: Var, _: Var) => true
			case _=> false
		}

	def sameClassLikeSpecificAPI(a: ClassLike, b: ClassLike): Result =
		sameDefinitionType(a.definitionType, b.definitionType) &&
		sameType(a.selfType, b.selfType) &&
		sameStructure(a.structure, b.structure)

	def sameValueParameters(a: Seq[ParameterList], b: Seq[ParameterList]): Result =
		sameSeq(a, b)(sameParameterList)

	def sameParameterList(a: ParameterList, b: ParameterList): Result =
		((a.isImplicit == b.isImplicit): Result) &&
		sameParameters(a.parameters, b.parameters)
	def sameParameters(a: Seq[MethodParameter], b: Seq[MethodParameter]): Result =
		sameSeq(a, b)(sameMethodParameter)
	def sameMethodParameter(a: MethodParameter, b: MethodParameter): Result =
		((a.name == b.name): Result) &&
		sameType(a.tpe, b.tpe) &&
		(a.hasDefault == b.hasDefault) &&
		sameParameterModifier(a.modifier, b.modifier)
	def sameParameterModifier(a: ParameterModifier, b: ParameterModifier) =
		a == b
	def sameDefinitionType(a: DefinitionType, b: DefinitionType): Result =
		a == b
	def sameVariance(a: Variance, b: Variance): Result =
		a == b

	def sameTypeParameters(a: Seq[TypeParameter], b: Seq[TypeParameter]): Result =
		debug(sameSeq(a, b)(sameTypeParameter), "Different type parameters")
	def sameTypeParameter(a: TypeParameter, b: TypeParameter): Result =
		sameTypeParameters(a.typeParameters, b.typeParameters) &&
		debug(sameVariance(a.variance, b.variance), "Different variance") &&
		debug(sameType(a.lowerBound, b.lowerBound), "Different lower bound") &&
		debug(sameType(a.upperBound, b.upperBound), "Different upper bound") &&
		debug(mapSameParameters(a, b), "Different type parameter bindings")

	def mapSameParameters(a: TypeParameter, b: TypeParameter): Result =
		mapParameters(a.id, b.id)
	def mapParameters(a: Int, b: Int) = new True(Map.empty, Map( a -> b ))

	def sameType(a: Type, b: Type): Result =
		(a, b) match
		{
			case (sa: SimpleType, sb: SimpleType) => debug(sameSimpleType(sa, sb), "Different simple types: " + sa + " and " + {Thread.dumpStack; sb})
			case (aa: Annotated, ab: Annotated) => debug(sameAnnotatedType(aa, ab), "Different annotated types")
			case (sa: Structure, sb: Structure) => debug(sameStructure(sa, sb), "Different structure type")
			case (ea: Existential, eb: Existential) => debug(sameExistentialType(ea, eb), "Different existential type")
			case (pa: Polymorphic, pb: Polymorphic) => debug(samePolymorphicType(pa, pb), "Different polymorphic type")
			case _ => false
		}

	def sameExistentialType(a: Existential, b: Existential): Result =
		sameTypeParameters(a.clause, b.clause) &&
		sameType(a.baseType, b.baseType)
	def samePolymorphicType(a: Polymorphic, b: Polymorphic): Result =
		sameTypeParameters(a.parameters, b.parameters) &&
		sameType(a.baseType, b.baseType)
	def sameAnnotatedType(a: Annotated, b: Annotated): Result =
		sameSimpleType(a.baseType, b.baseType) &&
		sameAnnotations(a.annotations, b.annotations)
	def sameStructure(a: Structure, b: Structure): Result =
		sameSeq(a.parents, b.parents)(sameType) &&
		sameMembers(a.declarations, b.declarations) &&
		sameMembers(a.inherited, b.inherited)

	def sameMembers(a: Seq[Definition], b: Seq[Definition]): Result =
		sameDefinitions(a, b)

	def sameSimpleType(a: SimpleType, b: SimpleType): Result =
		(a, b) match
		{
			case (pa: Projection, pb: Projection) => debug(sameProjection(pa, pb), "Different projection")
			case (pa: ParameterRef, pb: ParameterRef) => debug(sameParameterRef(pa, pb), "Different parameter ref")
			case (sa: Singleton, sb: Singleton) => debug(sameSingleton(sa, sb), "Different singleton")
			case (_: EmptyType, _: EmptyType) => true
			case (pa: Parameterized, pb: Parameterized) => debug(sameParameterized(pa, pb), "Different parameterized")
			case _ => debug(false, "Different category of simple type (" + a.getClass.getName + " and " + b.getClass.getName + ") for (" + a + " and " + b + ")")
		}

	def sameParameterized(a: Parameterized, b: Parameterized): Result =
		sameSimpleType(a.baseType, b.baseType) &&
		sameSeq(a.typeArguments, b.typeArguments)(sameType)
	def sameParameterRef(a: ParameterRef, b: ParameterRef): Result =
		mapReferences(a.id, b.id)
	def mapReferences(a: Int, b: Int) = new True(Map( a -> b ), Map.empty)
	def sameSingleton(a: Singleton, b: Singleton): Result =
		samePath(a.path, b.path)
	def sameProjection(a: Projection, b: Projection): Result =
		sameSimpleType(a.prefix, b.prefix) &&
		(a.id == b.id)

	def samePath(a: Path, b: Path): Result =
		samePathComponents(a.components, b.components)
	def samePathComponents(a: Seq[PathComponent], b: Seq[PathComponent]): Result =
		sameSeq(a, b)(samePathComponent)
	def samePathComponent(a: PathComponent, b: PathComponent): Result =
		(a, b) match
		{
			case (_: This, _: This) => true
			case (sa: Super, sb: Super) => samePathSuper(sa, sb)
			case (ia: Id, ib: Id) => samePathId(ia, ib)
			case _ => false
		}
	def samePathSuper(a: Super, b: Super): Result =
		samePath(a.qualifier, b.qualifier)
	def samePathId(a: Id, b: Id): Result =
		a.id == b.id

	// precondition: a.keySet == b.keySet
	protected def zippedEntries[A,B](a: scala.collection.Map[A,B], b: scala.collection.Map[A,B]): Iterable[(B,B)] =
		for( (key, avalue) <- a) yield (avalue, b(key))

	def sameStrings(a: scala.collection.Set[String], b: scala.collection.Set[String]): Result =
		a == b
	final def sameSeq[T](a: Seq[T], b: Seq[T])(eq: (T,T) => Result): Result =
		sameArray(a.toArray, b.toArray)(eq)
	final def sameArray[T](a: Array[T], b: Array[T])(eq: (T,T) => Result): Result =
		((a.length == b.length): Result) && forall(a.zip(b))(tupled(eq))
	final def forall[T](a: Iterable[T])(f: T => Result): Result = forallList(a.toList, true)(f)
	final def forallList[T](a: List[T], v: Result)(f: T => Result): Result =
		if(v.value) ( a match { case Nil => v; case x :: xs => forallList(xs, v && f(x) )(f) } )
		else v

	def defaultEquals[T <: AnyRef] = (a: T, b: T) => a == b
}
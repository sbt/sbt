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
		(new SameAPI).check(a,b)
}
private class SameAPI
{
	def debug(flag: Boolean, msg: => String): Boolean =
	{
		if(!flag) println(msg)
		flag
	}

	private val parameterMap = new mutable.HashMap[Int, Int]
	private val referencesMap = new mutable.HashMap[Int, Int]

	private def mapID(map: mutable.Map[Int, Int], key: Int, value: Int): Boolean =
		equalOrEmpty(map.put(key, value), value)
	private def equalOrEmpty[T](o: Option[T], nonemptyValue: T): Boolean =
		o match { case Some(v) => v == nonemptyValue; case None => true }

	def check(a: Source, b: Source) =
		samePackages(a, b) &&
		debug(sameDefinitions(a, b), "Definitions differed") &&
		debug(sameReferences, "References differed")

	def sameReferences: Boolean =
		referencesMap.forall(tupled(sameReference))
	def sameReference(a: Int, b: Int): Boolean =
		parameterMap(a) == b

	def samePackages(a: Source, b: Source): Boolean =
		sameStrings(packages(a), packages(b))
	def packages(s: Source): Set[String] =
		Set() ++ s.packages.map(_.name)

	def sameDefinitions(a: Source, b: Source): Boolean =
		sameDefinitions(a.definitions, b.definitions)
	def sameDefinitions(a: Seq[Definition], b: Seq[Definition]): Boolean =
	{
		val (avalues, atypes) = separateDefinitions(a)
		val (bvalues, btypes) = separateDefinitions(b)
		debug(sameDefinitions(byName(avalues), byName(bvalues)), "Value definitions differed") &&
		debug(sameDefinitions(byName(atypes), byName(btypes)), "Type definitions differed")
	}
	def separateDefinitions(s: Seq[Definition]): (Seq[Definition], Seq[Definition]) =
		s.toArray.partition(isValueDefinition)
	def sameDefinitions(a: scala.collection.Map[String, List[Definition]], b: scala.collection.Map[String, List[Definition]]): Boolean =
		debug(sameStrings(a.keySet, b.keySet), "\tDefinition strings differed") && zippedEntries(a,b).forall(tupled(sameNamedDefinitions))

	def sameNamedDefinitions(a: List[Definition], b: List[Definition]): Boolean =
	{
		def sameDefs(a: List[Definition], b: List[Definition]): Boolean =
			a match
			{
				case xdef :: tail =>
					b.find(bdef => sameDefinitionContent(xdef, bdef)) match
					{
						case Some(bdef) => sameDefs(tail, b - bdef)
						case None => debug(false, "Definition not in new API: \n" + xdef.name )
					}
				case Nil => true
			}
		debug((a.length == b.length), "\t\tLength differed for " + a.headOption.map(_.name).getOrElse("empty")) && sameDefs(a, b)
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
	def sameDefinitionContent(a: Definition, b: Definition): Boolean =
		//a.name == b.name &&
		debug(sameAccess(a.access, b.access), "Access differed") &&
		debug(sameModifiers(a.modifiers, b.modifiers), "Modifiers differed") &&
		debug(sameAnnotations(a.annotations, b.annotations), "Annotations differed") &&
		debug(sameDefinitionSpecificAPI(a, b), "Definition-specific differed")

	def sameAccess(a: Access, b: Access): Boolean =
		(a, b) match
		{
			case (_: Public, _: Public) => true
			case (qa: Protected, qb: Protected) => sameQualifier(qa, qb)
			case (qa: Private, qb: Private) => sameQualifier(qa, qb)
			case (qa: Pkg, qb: Pkg) => sameQualifier(qa, qb)
			case _ => debug(false, "Different access categories")
		}
	def sameQualifier(a: Qualified, b: Qualified): Boolean =
		sameQualifier(a.qualifier, b.qualifier)
	def sameQualifier(a: Qualifier, b: Qualifier): Boolean =
		(a, b) match
		{
			case (_: Unqualified, _: Unqualified) => true
			case (_: ThisQualifier, _: ThisQualifier) => true
			case (ia: IdQualifier, ib: IdQualifier) => debug(ia.value == ib.value, "Different qualifiers")
			case _ => debug(false, "Different qualifier categories: " + a.getClass.getName +  " -- " +b.getClass.getName)
		}

	def sameModifiers(a: Modifiers, b: Modifiers): Boolean =
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
	def setIf(bs: mutable.BitSet, flag: Boolean, i: Int): Unit =
		if(flag) bs += i

	def sameAnnotations(a: Seq[Annotation], b: Seq[Annotation]): Boolean =
		sameSeq(a, b)(sameAnnotation)
	def sameAnnotation(a: Annotation, b: Annotation): Boolean =
		sameSimpleType(a.base, b.base) &&
		sameSeq(a.arguments, b.arguments)(defaultEquals)
		
	def sameDefinitionSpecificAPI(a: Definition, b: Definition): Boolean =
		(a, b) match
		{
			case (fa: FieldLike, fb: FieldLike) => sameFieldSpecificAPI(fa, fb)
			case (pa: ParameterizedDefinition, pb: ParameterizedDefinition) => sameParameterizedDefinition(pa, pb)
			case _ => false
		}

	def sameParameterizedDefinition(a: ParameterizedDefinition, b: ParameterizedDefinition): Boolean =
		debug(sameTypeParameters(a.typeParameters, b.typeParameters), "Different type parameters for " + a.name) &&
		sameParameterizedSpecificAPI(a, b)

	def sameParameterizedSpecificAPI(a: ParameterizedDefinition, b: ParameterizedDefinition): Boolean =
		(a, b) match
		{
			case (da: Def, db: Def) => sameDefSpecificAPI(da, db)
			case (ca: ClassLike, cb: ClassLike) => sameClassLikeSpecificAPI(ca, cb)
			case (ta: TypeAlias, tb: TypeAlias) => sameAliasSpecificAPI(ta, tb)
			case (ta: TypeDeclaration, tb: TypeDeclaration) => sameDeclarationSpecificAPI(ta, tb)
			case _ => false
		}

	def sameDefSpecificAPI(a: Def, b: Def): Boolean =
		debug(sameValueParameters(a.valueParameters, b.valueParameters), "Different def value parameters for " + a.name) &&
		debug(sameType(a.returnType, b.returnType), "Different def return type for " + a.name)
	def sameAliasSpecificAPI(a: TypeAlias, b: TypeAlias): Boolean =
		debug(sameType(a.tpe, b.tpe), "Different alias type for " + a.name)
	def sameDeclarationSpecificAPI(a: TypeDeclaration, b: TypeDeclaration): Boolean =
		debug(sameType(a.lowerBound, b.lowerBound), "Different lower bound for declaration " + a.name) &&
		debug(sameType(a.upperBound, b.upperBound), "Different upper bound for declaration " + a.name)
	def sameFieldSpecificAPI(a: FieldLike, b: FieldLike): Boolean =
		debug(sameFieldCategory(a, b), "Different field categories (" + a.name + "=" + a.getClass.getName +  " -- " +a.name + "=" + a.getClass.getName + ")")&&
		debug(sameType(a.tpe, b.tpe), "Different field type for " + a.name)

	def sameFieldCategory(a: FieldLike, b: FieldLike): Boolean =
		(a,b) match
		{
			case (_: Val, _: Val) => true
			case (_: Var, _: Var) => true
			case _=> false
		}

	def sameClassLikeSpecificAPI(a: ClassLike, b: ClassLike): Boolean =
		sameDefinitionType(a.definitionType, b.definitionType) &&
		sameType(a.selfType, b.selfType) &&
		sameStructure(a.structure, b.structure)

	def sameValueParameters(a: Seq[ParameterList], b: Seq[ParameterList]): Boolean =
		sameSeq(a, b)(sameParameterList)

	def sameParameterList(a: ParameterList, b: ParameterList): Boolean =
		(a.isImplicit == b.isImplicit) &&
		sameParameters(a.parameters, b.parameters)
	def sameParameters(a: Seq[MethodParameter], b: Seq[MethodParameter]): Boolean =
		sameSeq(a, b)(sameMethodParameter)
	def sameMethodParameter(a: MethodParameter, b: MethodParameter): Boolean =
		(a.name == b.name) &&
		sameType(a.tpe, b.tpe) &&
		(a.hasDefault == b.hasDefault) &&
		sameParameterModifier(a.modifier, b.modifier)
	def sameParameterModifier(a: ParameterModifier, b: ParameterModifier) =
		a == b
	def sameDefinitionType(a: DefinitionType, b: DefinitionType): Boolean =
		a == b
	def sameVariance(a: Variance, b: Variance): Boolean =
		a == b

	def sameTypeParameters(a: Seq[TypeParameter], b: Seq[TypeParameter]): Boolean =
		debug(sameSeq(a, b)(sameTypeParameter), "Different type parameters")
	def sameTypeParameter(a: TypeParameter, b: TypeParameter): Boolean =
		sameTypeParameters(a.typeParameters, b.typeParameters) &&
		debug(sameVariance(a.variance, b.variance), "Different variance") &&
		debug(sameType(a.lowerBound, b.lowerBound), "Different lower bound") &&
		debug(sameType(a.upperBound, b.upperBound), "Different upper bound") &&
		debug(mapSameParameters(a, b), "Different type parameter bindings")

	def mapSameParameters(a: TypeParameter, b: TypeParameter): Boolean =
		mapID(parameterMap, a.id, b.id)

	def sameType(a: Type, b: Type): Boolean =
		(a, b) match
		{
			case (sa: SimpleType, sb: SimpleType) => debug(sameSimpleType(sa, sb), "Different simple type")
			case (aa: Annotated, ab: Annotated) => debug(sameAnnotatedType(aa, ab), "Different annotated type")
			case (sa: Structure, sb: Structure) => debug(sameStructure(sa, sb), "Different structure type")
			case (ea: Existential, eb: Existential) => debug(sameExistentialType(ea, eb), "Different existential type")
			case _ => false
		}

	def sameExistentialType(a: Existential, b: Existential): Boolean =
		sameTypeParameters(a.clause, b.clause) &&
		sameType(a.baseType, b.baseType)
	def sameAnnotatedType(a: Annotated, b: Annotated): Boolean =
		sameSimpleType(a.baseType, b.baseType) &&
		sameAnnotations(a.annotations, b.annotations)
	def sameStructure(a: Structure, b: Structure): Boolean =
		sameSeq(a.parents, b.parents)(sameType) &&
		sameMembers(a.declarations, b.declarations) &&
		sameMembers(a.inherited, b.inherited)

	def sameMembers(a: Seq[Definition], b: Seq[Definition]): Boolean =
		sameDefinitions(a, b)

	def sameSimpleType(a: SimpleType, b: SimpleType): Boolean =
		(a, b) match
		{
			case (pa: Projection, pb: Projection) => debug(sameProjection(pa, pb), "Different projection")
			case (pa: ParameterRef, pb: ParameterRef) => debug(sameParameterRef(pa, pb), "Different parameter ref")
			case (sa: Singleton, sb: Singleton) => debug(sameSingleton(sa, sb), "Different singleton")
			case (_: EmptyType, _: EmptyType) => true
			case (pa: Parameterized, pb: Parameterized) => debug(sameParameterized(pa, pb), "Different parameterized")
			case _ => debug(false, "Different category of simple type")
		}

	def sameParameterized(a: Parameterized, b: Parameterized): Boolean =
		sameSimpleType(a.baseType, b.baseType) &&
		sameSeq(a.typeArguments, b.typeArguments)(sameSimpleType)
	def sameParameterRef(a: ParameterRef, b: ParameterRef): Boolean =
		mapID(referencesMap, a.id, b.id)
	def sameSingleton(a: Singleton, b: Singleton): Boolean =
		samePath(a.path, b.path)
	def sameProjection(a: Projection, b: Projection): Boolean =
		sameSimpleType(a.prefix, b.prefix) &&
		(a.id == b.id)

	def samePath(a: Path, b: Path): Boolean =
		samePathComponents(a.components, b.components)
	def samePathComponents(a: Seq[PathComponent], b: Seq[PathComponent]): Boolean =
		sameSeq(a, b)(samePathComponent)
	def samePathComponent(a: PathComponent, b: PathComponent): Boolean =
		(a, b) match
		{
			case (_: This, _: This) => true
			case (sa: Super, sb: Super) => samePathSuper(sa, sb)
			case (ia: Id, ib: Id) => samePathId(ia, ib)
			case _ => false
		}
	def samePathSuper(a: Super, b: Super): Boolean =
		samePath(a.qualifier, b.qualifier)
	def samePathId(a: Id, b: Id): Boolean =
		a.id == b.id

	// precondition: a.keySet == b.keySet
	protected def zippedEntries[A,B](a: scala.collection.Map[A,B], b: scala.collection.Map[A,B]): Iterable[(B,B)] =
		for( (key, avalue) <- a) yield (avalue, b(key))

	def sameStrings(a: scala.collection.Set[String], b: scala.collection.Set[String]) =
		a == b
	def sameSeq[T](a: Seq[T], b: Seq[T])(eq: (T,T) => Boolean): Boolean =
		sameArray(a.toArray, b.toArray)(eq)
	private def sameArray[T](a: Array[T], b: Array[T])(eq: (T,T) => Boolean): Boolean =
		(a.length == b.length) && a.zip(b).forall(tupled(eq))

	def defaultEquals[T <: AnyRef] = (a: T, b: T) => a == b
}
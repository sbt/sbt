/* sbt -- Simple Build Tool
 * Copyright 2009, 2010, 2011 Mark Harrah
 */
package xsbt.api

import xsbti.api._
import util.MurmurHash
import TagTypeVariables.TypeVars
import HashAPI.Hash

object HashAPI
{
	type Hash = Int
	def apply(a: SourceAPI): Hash =
	{
		/** de Bruijn levels for type parameters in source a and b*/
		val tags = TagTypeVariables(a)
		(new HashAPI(tags, false, true)).hashAPI(a)
	}
}

final class HashAPI(tags: TypeVars, includePrivate: Boolean, includeParamNames: Boolean)
{
	import scala.collection.mutable
	import MurmurHash._

	private[this] val visitedStructures = visitedMap[Structure]
	private[this] val visitedClassLike = visitedMap[ClassLike]
	private[this] def visitedMap[T] = new mutable.HashMap[T, List[Hash]]
	private[this] def visit[T](map: mutable.Map[T, List[Hash]], t: T)(hashF: T => Unit)
	{
		map.put(t, hash :: map.getOrElse(t,Nil)) match {
			case Some(x :: _) => extend(x)
			case _ =>
				hashF(t)
				for(hs <- map(t))
					extend(hs)
				map.put(t, hash :: Nil)
		}
	}

	private[this] final val ValHash = 1
	private[this] final val VarHash = 2
	private[this] final val DefHash = 3
	private[this] final val ClassHash = 4
	private[this] final val TypeDeclHash = 5
	private[this] final val TypeAliasHash = 6

	private[this] final val PublicHash = 30
	private[this] final val ProtectedHash = 31
	private[this] final val PrivateHash = 32	
	private[this] final val UnqualifiedHash = 33
	private[this] final val ThisQualifierHash = 34
	private[this] final val IdQualifierHash = 35

	private[this] final val IdPathHash = 20
	private[this] final val SuperHash = 21
	private[this] final val ThisPathHash = 22

	private[this] final val ValueParamsHash = 40
	private[this] final val ClassPendingHash = 41
	private[this] final val StructurePendingHash = 42

	private[this] final val EmptyTypeHash = 51
	private[this] final val ParameterRefHash = 52
	private[this] final val SingletonHash = 53
	private[this] final val ProjectionHash = 54
	private[this] final val ParameterizedHash = 55
	private[this] final val AnnotatedHash = 56
	private[this] final val PolymorphicHash = 57
	private[this] final val ConstantHash = 58
	private[this] final val ExistentialHash = 59
	private[this] final val StructureHash = 60

	private[this] final val TrueHash = 97
	private[this] final val FalseHash = 98


	private[this] var hash: Hash = startHash(0)
	private[this] var magicA: Hash = startMagicA
	private[this] var magicB: Hash = startMagicB
	
	@inline final def hashString(s: String): Unit = extend(stringHash(s))
	@inline final def hashBoolean(b: Boolean): Unit = extend(if(b) TrueHash else FalseHash)
	@inline final def hashSeq[T](s: Seq[T], hashF: T => Unit)
	{
		extend(s.length)
		s foreach hashF
	}
	final def hashSymmetric[T](ts: TraversableOnce[T], hashF: T => Unit)
	{
		val current = hash
		val mA = magicA
		val mB = magicB
		val (hashes, mAs, mBs) = ts.toList.map { t =>
			hash = startHash(1)
			magicA = startMagicA
			magicB = startMagicB
			hashF(t)
			(finalizeHash(hash), magicA, magicB)
		} unzip3;
		hash = current
		magicA = mA
		magicB = mB
		extend(symmetricHash(hashes, 0xb592f7ae))  // constant from MurmurHash3
	}

	@inline final def extend(a: Hash)
	{
		hash = extendHash(hash, a, magicA, magicB)
		magicA = nextMagicA(magicA)
		magicB = nextMagicB(magicB)
	}
	def hashModifiers(m: Modifiers) = extend(m.raw)

	def hashAPI(s: SourceAPI): Hash =
	{
		hash = startHash(0)
		hashSymmetric(s.packages, hashPackage)
		hashDefinitions(s.definitions, true)
		finalizeHash(hash)
	}

	def hashPackage(p: Package) = hashString(p.name)

	def hashDefinitions(ds: Seq[Definition], topLevel: Boolean): Unit =
	{
		val defs = SameAPI.filterDefinitions(ds, topLevel, includePrivate)
		hashSymmetric(defs, hashDefinition)
	}
	def hashDefinition(d: Definition)
	{
		hashString(d.name)
		hashAnnotations(d.annotations)
		hashModifiers(d.modifiers)
		hashAccess(d.access)
		d match
		{
			case c: ClassLike => hashClass(c)
			case f: FieldLike => hashField(f)
			case d: Def => hashDef(d)
			case t: TypeDeclaration => hashTypeDeclaration(t)
			case t: TypeAlias => hashTypeAlias(t)
		}
	}
	final def hashClass(c: ClassLike): Unit = visit(visitedClassLike, c)(hashClass0)
	def hashClass0(c: ClassLike)
	{
		extend(ClassHash)
		hashParameterizedDefinition(c)
		hashType(c.selfType)
		hashStructure(c.structure)
	}
	def hashField(f: FieldLike)
	{
		f match
		{
			case v: Var => extend(VarHash)
			case v: Val => extend(ValHash)
		}
		hashType(f.tpe)
	}
	def hashDef(d: Def)
	{
		extend(DefHash)
		hashParameterizedDefinition(d)
		hashValueParameters(d.valueParameters)
		hashType(d.returnType)
	}
	def hashAccess(a: Access): Unit =
		a match
		{
			case pub: Public => extend(PublicHash)
			case qual: Qualified => hashQualified(qual)
		}
	def hashQualified(qual: Qualified): Unit =
	{
		qual match
		{
			case p: Protected => extend(ProtectedHash)
			case p: Private => extend(PrivateHash)
		}
		hashQualifier(qual.qualifier)
	}
	def hashQualifier(qual: Qualifier): Unit =
		qual match
		{
			case _: Unqualified => extend(UnqualifiedHash)
			case _: ThisQualifier => extend(ThisQualifierHash)
			case id: IdQualifier =>
				extend(IdQualifierHash)
				hashString(id.value)
		}

	def hashValueParameters(valueParameters: Seq[ParameterList]) = hashSeq(valueParameters, hashValueParameterList)
	def hashValueParameterList(list: ParameterList) =
	{
		extend(ValueParamsHash)
		hashBoolean(list.isImplicit)
		hashSeq(list.parameters, hashValueParameter)
	}
	def hashValueParameter(parameter: MethodParameter) =
	{
		hashString(parameter.name)
		hashType(parameter.tpe)
		extend(parameter.modifier.ordinal)
		hashBoolean(parameter.hasDefault)
	}
		
	def hashParameterizedDefinition[T <: ParameterizedDefinition](d: T)
	{
		hashTypeParameters(d.typeParameters)
	}
	def hashTypeDeclaration(d: TypeDeclaration)
	{
		extend(TypeDeclHash)
		hashParameterizedDefinition(d)
		hashType(d.lowerBound)
		hashType(d.upperBound)
	}
	def hashTypeAlias(d: TypeAlias)
	{
		extend(TypeAliasHash)
		hashParameterizedDefinition(d)
		hashType(d.tpe)
	}
	
	def hashTypeParameters(parameters: Seq[TypeParameter]) = hashSeq(parameters, hashTypeParameter)
	def hashTypeParameter(parameter: TypeParameter)
	{
		extend(parameter.variance.ordinal)
		hashTypeParameters(parameter.typeParameters)
		hashType(parameter.lowerBound)
		hashType(parameter.upperBound)
		hashAnnotations(parameter.annotations)
	}
	def hashAnnotations(annotations: Seq[Annotation]) = hashSeq(annotations, hashAnnotation)
	def hashAnnotation(annotation: Annotation) =
	{
		hashType(annotation.base)
		hashAnnotationArguments(annotation.arguments)
	}
	def hashAnnotationArguments(args: Seq[AnnotationArgument]) = hashSeq(args, hashAnnotationArgument)
	def hashAnnotationArgument(arg: AnnotationArgument)
	{
		hashString(arg.name)
		hashString(arg.value)
	}
	
	def hashTypes(ts: Seq[Type]) = hashSeq(ts, hashType)
	def hashType(t: Type): Unit =
		t match
		{
			case s: Structure => hashStructure(s)
			case e: Existential => hashExistential(e)
			case c: Constant => hashConstant(c)
			case p: Polymorphic => hashPolymorphic(p)
			case a: Annotated => hashAnnotated(a)
			case p: Parameterized => hashParameterized(p)
			case p: Projection => hashProjection(p)
			case _: EmptyType => extend(EmptyTypeHash)
			case s: Singleton => hashSingleton(s)
			case pr: ParameterRef => hashParameterRef(pr)
		}
	
	def hashParameterRef(p: ParameterRef)
	{
		extend(ParameterRefHash)
		tags.get(p.id) match {
			case Some((a,b)) => extend(a); extend(b)
			case None => extend(-1)
		}
	}
	def hashSingleton(s: Singleton)
	{
		extend(SingletonHash)
		hashPath(s.path)
	}
	def hashPath(path: Path) = hashSeq(path.components, hashPathComponent)
	def hashPathComponent(pc: PathComponent) = pc match
	{
		case _: This => extend(ThisPathHash)
		case s: Super => hashSuperPath(s)
		case id: Id => hashIdPath(id)
	}
	def hashSuperPath(s: Super)
	{
		extend(SuperHash)
		hashPath(s.qualifier)
	}
	def hashIdPath(id: Id)
	{
		extend(IdPathHash)
		hashString(id.id)
	}

	def hashConstant(c: Constant) =
	{
		extend(ConstantHash)
		hashString(c.value)
		hashType(c.baseType)
	}
	def hashExistential(e: Existential) =
	{
		extend(ExistentialHash)
		hashParameters(e.clause, e.baseType)
	}
	def hashPolymorphic(p: Polymorphic) =
	{
		extend(PolymorphicHash)
		hashParameters(p.parameters, p.baseType)
	}
	def hashProjection(p: Projection) =
	{
		extend(ProjectionHash)
		hashString(p.id)
		hashType(p.prefix)
	}
	def hashParameterized(p: Parameterized)
	{
		extend(ParameterizedHash)
		hashType(p.baseType)
		hashTypes(p.typeArguments)
	}
	def hashAnnotated(a: Annotated)
	{
		extend(AnnotatedHash)
		hashType(a.baseType)
		hashAnnotations(a.annotations)
	}
	final def hashStructure(structure: Structure) = visit(visitedStructures, structure)(hashStructure0)
	def hashStructure0(structure: Structure)
	{
		extend(StructureHash)
		hashTypes(structure.parents)
		hashDefinitions(structure.declared, false)
		hashDefinitions(structure.inherited, false)
	}
	def hashParameters(parameters: Seq[TypeParameter], base: Type): Unit =
	{
		hashTypeParameters(parameters)
		hashType(base)
	}
}
	

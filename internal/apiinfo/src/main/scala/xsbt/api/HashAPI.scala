/* sbt -- Simple Build Tool
 * Copyright 2009, 2010, 2011 Mark Harrah
 */
package xsbt.api

import scala.util
import xsbti.api._
import util.MurmurHash
import HashAPI.Hash

object HashAPI {
  type Hash = Int
  def apply(a: SourceAPI): Hash =
    (new HashAPI(false, true, true)).hashAPI(a)

  def apply(x: Def): Hash = {
    val hashApi = new HashAPI(false, true, true)
    hashApi.hashDefinition(x)
    hashApi.finalizeHash
  }

  def hashDefinitionsWithExtraHashes(ds: Seq[(Definition, Hash)]): Hash = {
    val hashAPI = new HashAPI(false, true, false)
    hashAPI.hashDefinitionsWithExtraHashes(ds)
    hashAPI.finalizeHash
  }
}

/**
 * Implements hashing of public API.
 *
 * @param includePrivate should private definitions be included in a hash sum
 * @param includeParamNames should parameter names for methods be included in a hash sum
 * @param includeDefinitions when hashing a structure (e.g. of a class) should hashes of definitions (members)
 *   be included in a hash sum. Structure can appear as a type (in structural type) and in that case we
 *   always include definitions in a hash sum.
 */
final class HashAPI(includePrivate: Boolean, includeParamNames: Boolean, includeDefinitions: Boolean) {
  // this constructor variant is for source and binary backwards compatibility with sbt 0.13.0
  def this(includePrivate: Boolean, includeParamNames: Boolean) {
    // in the old logic we used to always include definitions hence
    // includeDefinitions=true
    this(includePrivate, includeParamNames, includeDefinitions = true)
  }

  import scala.collection.mutable
  import MurmurHash.{ extendHash, nextMagicA, nextMagicB, startHash, startMagicA, startMagicB, stringHash, symmetricHash }

  private[this] val visitedStructures = visitedMap[Structure]
  private[this] val visitedClassLike = visitedMap[ClassLike]
  private[this] def visitedMap[T] = new mutable.HashMap[T, List[Hash]]
  private[this] def visit[T](map: mutable.Map[T, List[Hash]], t: T)(hashF: T => Unit): Unit = {
    map.put(t, hash :: map.getOrElse(t, Nil)) match {
      case Some(x :: _) => extend(x)
      case _ =>
        hashF(t)
        for (hs <- map(t))
          extend(hs)
        map.put(t, hash :: Nil)
    }
  }

  private[this] def isTrait(cl: ClassLike) = cl.definitionType == DefinitionType.Trait

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
  @inline final def hashBoolean(b: Boolean): Unit = extend(if (b) TrueHash else FalseHash)
  @inline final def hashSeq[T](s: Seq[T], hashF: T => Unit): Unit = {
    extend(s.length)
    s foreach hashF
  }
  final def hashSymmetric[T](ts: TraversableOnce[T], hashF: T => Unit): Unit = {
    val current = hash
    val mA = magicA
    val mB = magicB
    val (hashes, mAs, mBs) = ts.toList.map { t =>
      hash = startHash(1)
      magicA = startMagicA
      magicB = startMagicB
      hashF(t)
      (finalizeHash, magicA, magicB)
    } unzip3;
    hash = current
    magicA = mA
    magicB = mB
    extend(symmetricHash(hashes, 0xb592f7ae)) // constant from MurmurHash3
  }

  @inline final def extend(a: Hash): Unit = {
    hash = extendHash(hash, a, magicA, magicB)
    magicA = nextMagicA(magicA)
    magicB = nextMagicB(magicB)
  }

  def finalizeHash: Hash = MurmurHash.finalizeHash(hash)

  def hashModifiers(m: Modifiers) = extend(m.raw)

  def hashAPI(s: SourceAPI): Hash =
    {
      hash = startHash(0)
      hashSymmetric(s.packages, hashPackage)
      hashDefinitions(s.definitions, true)
      finalizeHash
    }

  def hashPackage(p: Package) = hashString(p.name)

  def hashDefinitions(ds: Seq[Definition], topLevel: Boolean): Unit =
    {
      val defs = SameAPI.filterDefinitions(ds, topLevel, includePrivate)
      hashSymmetric(defs, hashDefinition)
    }

  /**
   * Hashes a sequence of definitions by combining each definition's own
   * hash with extra one supplied as first element of a pair.
   *
   * It's useful when one wants to influence hash of a definition by some
   * external (to definition) factor (e.g. location of definition).
   *
   * NOTE: This method doesn't perform any filtering of passed definitions.
   */
  def hashDefinitionsWithExtraHashes(ds: Seq[(Definition, Hash)]): Unit =
    {
      def hashDefinitionCombined(d: Definition, extraHash: Hash): Unit = {
        hashDefinition(d)
        extend(extraHash)
      }
      hashSymmetric(ds, (hashDefinitionCombined _).tupled)
    }
  def hashDefinition(d: Definition): Unit = {
    hashString(d.name)
    hashAnnotations(d.annotations)
    hashModifiers(d.modifiers)
    hashAccess(d.access)
    d match {
      case c: ClassLike       => hashClass(c)
      case f: FieldLike       => hashField(f)
      case d: Def             => hashDef(d)
      case t: TypeDeclaration => hashTypeDeclaration(t)
      case t: TypeAlias       => hashTypeAlias(t)
    }
  }
  final def hashClass(c: ClassLike): Unit = visit(visitedClassLike, c)(hashClass0)
  def hashClass0(c: ClassLike): Unit = {
    extend(ClassHash)
    hashParameterizedDefinition(c)
    hashType(c.selfType)
    hashStructure(c.structure, includeDefinitions, isTrait(c))
  }
  def hashField(f: FieldLike): Unit = {
    f match {
      case v: Var => extend(VarHash)
      case v: Val => extend(ValHash)
    }
    hashType(f.tpe)
  }
  def hashDef(d: Def): Unit = {
    extend(DefHash)
    hashParameterizedDefinition(d)
    hashValueParameters(d.valueParameters)
    hashType(d.returnType)
  }
  def hashAccess(a: Access): Unit =
    a match {
      case pub: Public     => extend(PublicHash)
      case qual: Qualified => hashQualified(qual)
    }
  def hashQualified(qual: Qualified): Unit =
    {
      qual match {
        case p: Protected => extend(ProtectedHash)
        case p: Private   => extend(PrivateHash)
      }
      hashQualifier(qual.qualifier)
    }
  def hashQualifier(qual: Qualifier): Unit =
    qual match {
      case _: Unqualified   => extend(UnqualifiedHash)
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

  def hashParameterizedDefinition[T <: ParameterizedDefinition](d: T): Unit = {
    hashTypeParameters(d.typeParameters)
  }
  def hashTypeDeclaration(d: TypeDeclaration): Unit = {
    extend(TypeDeclHash)
    hashParameterizedDefinition(d)
    hashType(d.lowerBound)
    hashType(d.upperBound)
  }
  def hashTypeAlias(d: TypeAlias): Unit = {
    extend(TypeAliasHash)
    hashParameterizedDefinition(d)
    hashType(d.tpe)
  }

  def hashTypeParameters(parameters: Seq[TypeParameter]) = hashSeq(parameters, hashTypeParameter)
  def hashTypeParameter(parameter: TypeParameter): Unit = {
    hashString(parameter.id)
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
  def hashAnnotationArgument(arg: AnnotationArgument): Unit = {
    hashString(arg.name)
    hashString(arg.value)
  }

  def hashTypes(ts: Seq[Type], includeDefinitions: Boolean = true) =
    hashSeq(ts, (t: Type) => hashType(t, includeDefinitions))
  def hashType(t: Type, includeDefinitions: Boolean = true): Unit =
    t match {
      case s: Structure     => hashStructure(s, includeDefinitions, isTrait = false)
      case e: Existential   => hashExistential(e)
      case c: Constant      => hashConstant(c)
      case p: Polymorphic   => hashPolymorphic(p)
      case a: Annotated     => hashAnnotated(a)
      case p: Parameterized => hashParameterized(p)
      case p: Projection    => hashProjection(p)
      case _: EmptyType     => extend(EmptyTypeHash)
      case s: Singleton     => hashSingleton(s)
      case pr: ParameterRef => hashParameterRef(pr)
    }

  def hashParameterRef(p: ParameterRef): Unit = {
    extend(ParameterRefHash)
    hashString(p.id)
  }
  def hashSingleton(s: Singleton): Unit = {
    extend(SingletonHash)
    hashPath(s.path)
  }
  def hashPath(path: Path) = hashSeq(path.components, hashPathComponent)
  def hashPathComponent(pc: PathComponent) = pc match {
    case _: This  => extend(ThisPathHash)
    case s: Super => hashSuperPath(s)
    case id: Id   => hashIdPath(id)
  }
  def hashSuperPath(s: Super): Unit = {
    extend(SuperHash)
    hashPath(s.qualifier)
  }
  def hashIdPath(id: Id): Unit = {
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
  def hashParameterized(p: Parameterized): Unit = {
    extend(ParameterizedHash)
    hashType(p.baseType)
    hashTypes(p.typeArguments)
  }
  def hashAnnotated(a: Annotated): Unit = {
    extend(AnnotatedHash)
    hashType(a.baseType)
    hashAnnotations(a.annotations)
  }
  @deprecated("Use the overload that indicates if the definition is a trait.", "0.14")
  final def hashStructure(structure: Structure, includeDefinitions: Boolean): Unit =
    hashStructure(structure, includeDefinitions, isTrait = false)
  final def hashStructure(structure: Structure, includeDefinitions: Boolean, isTrait: Boolean = false): Unit =
    visit(visitedStructures, structure)(structure => hashStructure0(structure, includeDefinitions, isTrait))
  @deprecated("Use the overload that indicates if the definition is a trait.", "0.14")
  def hashStructure0(structure: Structure, includeDefinitions: Boolean): Unit =
    hashStructure0(structure, includeDefinitions, isTrait = false)
  def hashStructure0(structure: Structure, includeDefinitions: Boolean, isTrait: Boolean = false): Unit = {
    extend(StructureHash)
    hashTypes(structure.parents, includeDefinitions)
    if (includeDefinitions || isTrait) {
      hashDefinitions(structure.declared, isTrait)
      hashDefinitions(structure.inherited, isTrait)
    }
  }
  def hashParameters(parameters: Seq[TypeParameter], base: Type): Unit =
    {
      hashTypeParameters(parameters)
      hashType(base)
    }
}

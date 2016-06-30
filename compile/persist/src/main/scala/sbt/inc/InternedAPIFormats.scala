package sbt
package inc


import scala.collection.mutable

import xsbti.SafeLazy
import xsbti.api._
import sbinary._
import DefaultProtocol._


// Formats for all the classes in xsbti.api.
// Uses intern pools, so cannot be a singleton.
class InternedAPIFormats {
	// Intern pools.
	val typePool = new InternPoolFormats[Type](typeF)
	val structurePool = new InternPoolFormats[Structure](structureF)
	val definitionPool = new InternPoolFormats[Definition](definitionF)

	// Formats that evaluate lazily via the intern pools. These are used to overcome the circularity problem.
	def lazyTypeF = typePool.lazyItemFormat
	def lazyTypesF = typePool.lazyItemsFormat
	def lazyStructureF = structurePool.lazyItemFormat
	def lazyDefinitionsF = definitionPool.lazyItemsFormat

	// A convenient wrapper class, for serialization of the pools.
	class Pools(var aP: InternPool[Type], var bP: InternPool[Structure], var cP: InternPool[Definition])

	def initPools(apis: APIs) = {
		val (a, b, c) = allAPIObjects(apis)
		new Pools(typePool.initPool(a), structurePool.initPool(b), definitionPool.initPool(c))
	}

	def poolsFormat: Format[Pools] =
		asProduct3[Pools, InternPool[Type], InternPool[Structure], InternPool[Definition]]((a, b, c) => new Pools(a, b, c))(x => (x.aP, x.bP, x.cP))(typePool.poolFormat, structurePool.poolFormat, definitionPool.poolFormat)



	// Formats for external use.
	// TODO: Generate these instead of maintaining them by hand? It might be more trouble than its worth to do so.

	// Compilation

	implicit def compilationF: Format[Compilation] =
		asProduct2[Compilation, Long, Array[OutputSetting]]((a, b) => new Compilation(a, b))(x => (x.startTime, x.outputs))(longF, outputSettingsF)

	// Source

	implicit def sourceF: Format[Source] =
		asProduct5[Source, Compilation, Array[Byte], SourceAPI, Int, Boolean]((a, b, c, d, e) => new Source(a, b, c, d, e))(x => (x.compilation, x.hash, x.api, x.apiHash, x.hasMacro))(compilationF, bytesF, sourceAPIF, intF, booleanF)


	// Implementation details. To avoid confusion, all internal formats are private and are passed around explicitly.

	// Basic types

	private[this] def strF = StringFormat
	private[this] def strsF = arrayFormat(strF, manifest[String])
	private[this] def longF = LongFormat
	private[this] def intF = IntFormat
	private[this] def byteF = ByteFormat
	private[this] def bytesF = arrayFormat(byteF, manifest[Byte])
	private[this] def booleanF = BooleanFormat

	// Qualifier

	private[this] def qualifierF: Format[Qualifier] = asUnion[Qualifier](
		wrap[IdQualifier, String](_.value, new IdQualifier(_))(strF),
		wrap[ThisQualifier, Unit](_ => {}, _ => new ThisQualifier()),
		wrap[Unqualified, Unit](_ => {}, _ => new Unqualified())
	)

	// Access

	private[this] def accessF: Format[Access] = asUnion[Access](
	  wrap[Private, Qualifier](_.qualifier, new Private(_))(qualifierF),
		wrap[Protected, Qualifier](_.qualifier, new Protected(_))(qualifierF),
		wrap[Public, Unit](_ => {}, _ => new Public())
	)

	// PathComponent and Path

	 private[this] def pathComponentF: Format[PathComponent] = lazyFormat(asUnion[PathComponent](
		wrap[Id, String](_.id, new Id(_))(strF),
		wrap[This, Unit](_ => {}, _ => new This()),
		wrap[Super, Path](_.qualifier, new Super(_))(pathF)
	))

	private[this] def pathF: Format[Path] = lazyFormat(wrap[Path, Array[PathComponent]](_.components, new Path(_))
		(arrayFormat(pathComponentF, manifest[PathComponent])))

	// AnnotationArgument and Annotation

	private[this] def annotationArgF: Format[AnnotationArgument] =
		asProduct2[AnnotationArgument, String, String]( (a, b) => new AnnotationArgument(a, b))(x => (x.name, x.value))(strF, strF)

	private[this] def annotationArgsF: Format[Array[AnnotationArgument]] = arrayFormat(annotationArgF, manifest[AnnotationArgument])

	private[this] def annotationF: Format[Annotation] =
		asProduct2[Annotation, Type, Array[AnnotationArgument]]((a, b) => new Annotation(a, b))(a => (a.base, a.arguments))(typeF, annotationArgsF)

	private[this] def annotationsF: Format[Array[Annotation]] = arrayFormat(annotationF, manifest[Annotation])


	// Variance and TypeParameter

	private[this] def varianceF: Format[Variance] = wrap[Variance, String](_.name, Variance.valueOf)(strF)

	private[this] def typeParamF: Format[TypeParameter] = lazyFormat(
		asProduct6[TypeParameter, String, Array[Annotation], Array[TypeParameter], Variance, Type, Type]
			((a, b, c, d, e, f) => new TypeParameter(a, b, c, d, e, f))
			(x=> (x.id, x.annotations, x.typeParameters, x.variance, x.lowerBound, x.upperBound))
			(strF, annotationsF, typeParamsF, varianceF, typeF, typeF)
	)

	private[this] def typeParamsF: Format[Array[TypeParameter]] = arrayFormat(typeParamF, manifest[TypeParameter])

	// SimpleType and Type

	private[this] def typeF: Format[Type] = lazyFormat(asUnion(
		// SimpleTypeFormat
		simpleTypeF,
		// Polymorphic
		asProduct2[Polymorphic, Type, Array[TypeParameter]]
			((a, b) => new Polymorphic(a, b))
			(x => (x.baseType, x.parameters))
			(typeF, typeParamsF),
		// Existential
		asProduct2[Existential, Type, Array[TypeParameter]]
			((a, b) => new Existential(a, b))
			(x => (x.baseType, x.clause))
			(typeF, typeParamsF),
		// Constant
	  asProduct2[Constant, Type, String]
			((a, b) => new Constant(a, b))
			(x => (x.baseType, x.value))
			(typeF, strF),
		// Annotated
		asProduct2[Annotated, Type, Array[Annotation]]
			((a, b) => new Annotated(a, b))
			(x => (x.baseType, x.annotations))
			(typeF, annotationsF),
		// Structure
		structureF
	))

	private[this] def simpleTypeF: Format[SimpleType] = lazyFormat(asUnion(
		// EmptyType
		wrap[EmptyType, Unit](_ => {}, _ => new EmptyType()),
		// ParameterRef
		wrap[ParameterRef, String](_.id, new ParameterRef(_))(strF),
		// Parameterized
		asProduct2[Parameterized, SimpleType, Array[Type]]
			((a, b) => new Parameterized(a, b))
			(x => (x.baseType, x.typeArguments))
			(simpleTypeF, arrayFormat[Type](typeF, manifest[Type])),
		// Projection
		asProduct2[Projection, SimpleType, String]
			((a, b) => new Projection(a, b))
			(x => (x.prefix, x.id))
			(simpleTypeF, strF),
		// Singleton
		wrap[Singleton, Path](_.path, new Singleton(_))(pathF)
	))

	// Modifier

	private[this] def rawToModifiers(b: Byte) = {
		def a(bit: Int): Boolean = (b & (1 << bit)) != 0
		new Modifiers(a(0), a(1), a(2), a(3), a(4), a(5), a(6))
	}
	private[this] def modifiersF: Format[Modifiers] = wrap[Modifiers, Byte](_.raw, rawToModifiers)(byteF)

	// ParameterList

	private[this] def parameterModifierF: Format[ParameterModifier] = wrap[ParameterModifier, String](_.name, ParameterModifier.valueOf)(strF)

	private[this] def methodParamF: Format[MethodParameter] = lazyFormat(
		asProduct4[MethodParameter, String, Type, Boolean, ParameterModifier]
			((a, b, c, d) => new MethodParameter(a, b, c, d))
			(x => (x.name, x.tpe, x.hasDefault, x.modifier))
			(strF, typeF, booleanF, parameterModifierF)
	)

	private[this] def methodParamsF: Format[Array[MethodParameter]] = arrayFormat(methodParamF, manifest[MethodParameter])

	private[this] def paramListF: Format[ParameterList] =
		asProduct2[ParameterList, Array[MethodParameter], Boolean]((a, b) => new ParameterList(a, b))(x => (x.parameters, x.isImplicit))(methodParamsF, booleanF)

	private[this] def paramListsF: Format[Array[ParameterList]] = arrayFormat(paramListF, manifest[ParameterList])

	// Structure and Definition

	private[this] def structureF: Format[Structure] = lazyFormat(
		asProduct3[Structure, Lazy[Array[Type]], Lazy[Array[Definition]], Lazy[Array[Definition]]]
			((a, b, c) => new Structure(a, b, c))
			(x => (SafeLazy.strict(x.parents), SafeLazy.strict(x.declared), SafeLazy.strict(x.inherited)))
			(lazyTypesF, lazyDefinitionsF, lazyDefinitionsF)
		)

	private[this] def definitionTypeF: Format[DefinitionType] = wrap[DefinitionType, String](_.name, DefinitionType.valueOf)(strF)

	private[this] def definitionF = asUnion[Definition](
		// Val
		asProduct5[Val, Type, String, Access, Modifiers, Array[Annotation]]
			((a, b, c, d, e) => new Val(a, b, c, d, e))
			(x => (x.tpe, x.name, x.access, x.modifiers, x.annotations))
			(typeF, strF, accessF, modifiersF, annotationsF),
		// Var
		asProduct5[Var, Type, String, Access, Modifiers, Array[Annotation]]
			((a, b, c, d, e) => new Var(a, b, c, d, e))
			(x => (x.tpe, x.name, x.access, x.modifiers, x.annotations))
			(typeF, strF, accessF, modifiersF, annotationsF),
		// TypeDeclaration
		asProduct7[TypeDeclaration, Type, Type, Array[TypeParameter], String, Access, Modifiers, Array[Annotation]]
			((a, b, c, d, e, f, g) => new TypeDeclaration(a, b, c, d, e, f, g))
			(x => (x.lowerBound, x.upperBound, x.typeParameters, x.name, x.access, x.modifiers, x.annotations))
			(typeF, typeF, typeParamsF, strF, accessF, modifiersF, annotationsF),
		// TypeAlias
		asProduct6[TypeAlias, Type, Array[TypeParameter], String, Access, Modifiers, Array[Annotation]]
			((a, b, c, d, e, f) => new TypeAlias(a, b, c, d, e, f))
			(x => (x.tpe, x.typeParameters, x.name, x.access, x.modifiers, x.annotations))
			(typeF, typeParamsF, strF, accessF, modifiersF, annotationsF),
		// Def
		asProduct7[Def, Array[ParameterList], Type, Array[TypeParameter], String, Access, Modifiers, Array[Annotation]]
			((a, b, c, d, e, f, g) => new Def(a, b, c, d, e, f, g))
			(x => (x.valueParameters, x.returnType, x.typeParameters, x.name, x.access, x.modifiers, x.annotations))
			(paramListsF, typeF, typeParamsF, strF, accessF, modifiersF, annotationsF),
		// ClassLike
		asProduct9[ClassLike, DefinitionType, Lazy[Type], Lazy[Structure], Array[String], Array[TypeParameter], String, Access, Modifiers, Array[Annotation]]
			((a, b, c, d, e, f, g, h, i) => new ClassLike(a, b, c, d, e, f, g, h, i))
			(x => (x.definitionType, SafeLazy.strict(x.selfType), SafeLazy.strict(x.structure), x.savedAnnotations, x.typeParameters, x.name, x.access, x.modifiers, x.annotations))
			(definitionTypeF, lazyTypeF, lazyStructureF, strsF, typeParamsF, strF, accessF, modifiersF, annotationsF)
	)

	private[this] def definitionsF: Format[Array[Definition]] = arrayFormat(definitionF, manifest[Definition])

	// Package

	private[this] def packageF: Format[Package] = wrap[Package, String](_.name, new Package(_))(strF)

	private[this] def packagesF: Format[Array[Package]] = arrayFormat(packageF, manifest[Package])

	// OutputSetting

  private[this] def outputSettingF: Format[OutputSetting] =
		asProduct2[OutputSetting, String, String]((a, b) => new OutputSetting(a, b))(x => (x.sourceDirectory, x.outputDirectory))(strF, strF)

	private[this] def outputSettingsF: Format[Array[OutputSetting]] = arrayFormat(outputSettingF, manifest[OutputSetting])

	// SourceAPI

	private[this] def sourceAPIF: Format[SourceAPI] =
		asProduct2[SourceAPI, Array[Package], Array[Definition]]((a, b) => new SourceAPI(a, b))(x => (x.packages, x.definitions))(packagesF, definitionsF)


	// Get all Type, Structure and Definition objects, in dependency post-order, for use when creating intern pools.
	def allAPIObjects(apis: APIs): (Array[Type], Array[Structure], Array[Definition]) = {
		val visitedTypes = mutable.LinkedHashSet.empty[Type]
		val visitedAnnotations = mutable.LinkedHashSet.empty[Annotation]
		val visitedTypeParameters = mutable.LinkedHashSet.empty[TypeParameter]
		val visitedDefinitions = mutable.LinkedHashSet.empty[Definition]

		val orderedTypes = mutable.ArrayBuffer.empty[Type]
		val orderedStructures = mutable.ArrayBuffer.empty[Structure]
		val orderedDefinitions = mutable.ArrayBuffer.empty[Definition]

		apis.internal.values foreach visitSource
		apis.external.values foreach visitSource

	  def visitSource(s: Source) {
	    s.api.definitions foreach visitDefinition
		}

		def visitType(t: Type) {
			if (!visitedTypes.contains(t)) {
				visitedTypes.add(t)
				t match {
					case a: Annotated => visitType(a.baseType); a.annotations foreach visitAnnotation
					case c: Constant => visitType(c.baseType)
					case e: Existential => visitType(e.baseType()); e.clause foreach visitTypeParameter
					case p: Polymorphic => visitType(p.baseType()); p.parameters foreach visitTypeParameter
					case s: SimpleType => {
						s match {
							case p: Parameterized => visitType(p.baseType); p.typeArguments foreach visitType
							case p: Projection => visitType(p.prefix)
							case _ =>
						}
					}
					case s: Structure => {
						s.parents foreach visitType
						s.declared foreach visitDefinition
						s.inherited foreach visitDefinition
						orderedStructures.append(s)
					}
				}
				orderedTypes.append(t)
			}
		}

		def visitAnnotation(a: Annotation) {
			if (!visitedAnnotations.contains(a)) {
				visitedAnnotations.add(a)
				visitType(a.base)
			}
		}

		def visitTypeParameter(tp: TypeParameter) {
			if (!visitedTypeParameters.contains(tp)) {
				visitedTypeParameters.add(tp)
				tp.annotations foreach visitAnnotation
				tp.typeParameters foreach visitTypeParameter
				visitType(tp.lowerBound)
				visitType(tp.upperBound)
			}
		}

		def visitDefinition(d: Definition) {
			if (!visitedDefinitions.contains(d)) {
				visitedDefinitions.add(d)
				d match {
					case f: FieldLike => visitType(f.tpe)
					case p: ParameterizedDefinition => {
						p match {
							case c: ClassLike => visitType(c.selfType); visitType(c.structure)
							case d1: Def => {
								d1.valueParameters foreach { _.parameters foreach { mp => visitType(mp.tpe) } }
								visitType(d1.returnType)
							}
							case t: TypeAlias => visitType(t.tpe)
							case t: TypeDeclaration => visitType(t.lowerBound); visitType(t.upperBound)
						}
						p.typeParameters foreach visitTypeParameter
					}
				}
				d.annotations foreach visitAnnotation
				orderedDefinitions.append(d)
			}
		}

		(orderedTypes.toArray, orderedStructures.toArray, orderedDefinitions.toArray)
	}
}

/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package xsbt.api

	import xsbti.api._
	import scala.collection.mutable

object TagTypeVariables
{
	type TypeVars = collection.Map[Int, (Int, Int)]
	def apply(s: SourceAPI): TypeVars = (new TagTypeVariables).tag(s)
}
import TagTypeVariables.TypeVars
private class TagTypeVariables
{
	private val taggedStructures = new mutable.HashSet[Structure]
	private val taggedClasses = new mutable.HashSet[ClassLike]

	private val tags = new mutable.HashMap[Int, (Int, Int)]
	private var level = 0
	private var index = 0

	def tag(s: SourceAPI): TypeVars =
	{
		s.definitions.foreach(tagDefinition)
		tags
	}
	def tagDefinitions(ds: Seq[Definition]) = ds.foreach(tagDefinition)
	def tagDefinition(d: Definition)
	{
		d match
		{
			case c: ClassLike =>  tagClass(c)
			case f: FieldLike => tagField(f)
			case d: Def => tagDef(d)
			case t: TypeDeclaration => tagTypeDeclaration(t)
			case t: TypeAlias => tagTypeAlias(t)
		}
	}
	def tagClass(c: ClassLike): Unit = if(taggedClasses add c) tagClass0(c)
	def tagClass0(c: ClassLike): Unit =
		tagParameterizedDefinition(c) {
			tagType(c.selfType)
			tagStructure(c.structure)
		}
	def tagField(f: FieldLike)
	{
		tagType(f.tpe)
		tagAnnotations(f.annotations)
	}
	def tagDef(d: Def): Unit =
		tagParameterizedDefinition(d) {
			tagValueParameters(d.valueParameters)
			tagType(d.returnType)
		}
	def tagValueParameters(valueParameters: Seq[ParameterList]) = valueParameters.foreach(tagValueParameterList)
	def tagValueParameterList(list: ParameterList) = list.parameters.foreach(tagValueParameter)
	def tagValueParameter(parameter: MethodParameter) = tagType(parameter.tpe)
		
	def tagParameterizedDefinition[T <: ParameterizedDefinition](d: T)(tagExtra: => Unit)
	{
		tagAnnotations(d.annotations)
		scope {
			tagTypeParameters(d.typeParameters)
			tagExtra
		}
	}
	def tagTypeDeclaration(d: TypeDeclaration): Unit =
		tagParameterizedDefinition(d) {
			tagType(d.lowerBound)
			tagType(d.upperBound)
		}
	def tagTypeAlias(d: TypeAlias): Unit =
		tagParameterizedDefinition(d) {
			tagType(d.tpe)
		}
	
	def tagTypeParameters(parameters: Seq[TypeParameter]) = parameters.foreach(tagTypeParameter)
	def tagTypeParameter(parameter: TypeParameter)
	{
		recordTypeParameter(parameter.id)
		scope {
			tagTypeParameters(parameter.typeParameters)
			tagType(parameter.lowerBound)
			tagType(parameter.upperBound)
		}
	}
	def tagAnnotations(annotations: Seq[Annotation]) = tagTypes(annotations.map(_.base))
	
	def tagTypes(ts: Seq[Type]) = ts.foreach(tagType)
	def tagType(t: Type)
	{
		t match
		{
			case s: Structure => tagStructure(s)
			case e: Existential => tagExistential(e)
			case c: Constant => tagConstant(c)
			case p: Polymorphic => tagPolymorphic(p)
			case a: Annotated => tagAnnotated(a)
			case p: Parameterized => tagParameterized(p)
			case p: Projection => tagProjection(p)
			case _: EmptyType | _: Singleton | _: ParameterRef => ()
		}
	}

	def tagConstant(c: Constant) = tagType(c.baseType)
	def tagExistential(e: Existential) = tagParameters(e.clause, e.baseType)
	def tagPolymorphic(p: Polymorphic) = tagParameters(p.parameters, p.baseType)
	def tagProjection(p: Projection) = tagType(p.prefix)
	def tagParameterized(p: Parameterized)
	{
		tagType(p.baseType)
		tagTypes(p.typeArguments)
	}
	def tagAnnotated(a: Annotated)
	{
		tagType(a.baseType)
		tagAnnotations(a.annotations)
	}
	def tagStructure(structure: Structure): Unit = if(taggedStructures add structure) tagStructure0(structure)
	def tagStructure0(structure: Structure)
	{
		tagTypes(structure.parents)
		tagDefinitions(structure.declared)
		tagDefinitions(structure.inherited)
	}
	def tagParameters(parameters: Seq[TypeParameter], base: Type): Unit =
		scope {
			tagTypeParameters(parameters)
			tagType(base)
		}
	
	def scope(action: => Unit)
	{
		val saveIndex = index
		index = 0
		level += 1
		
		action
		
		level -= 1
		index = saveIndex
	}
	def recordTypeParameter(id: Int)
	{
		tags(id) = (level, index)
		index += 1
	}
}
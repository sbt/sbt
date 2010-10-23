/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package xsbt.api

	import xsbti.api._
	import scala.collection.mutable

class Visit
{
	private[this] val visitedStructures = new mutable.HashSet[Structure]
	private[this] val visitedClassLike = new mutable.HashSet[ClassLike]

	def visit(s: Source): Unit =
		s.definitions foreach visitDefinition

	def visitDefinitions(ds: Seq[Definition]) = ds foreach visitDefinition
	def visitDefinition(d: Definition)
	{
		d match
		{
			case c: ClassLike =>  visitClass(c)
			case f: FieldLike => visitField(f)
			case d: Def => visitDef(d)
			case t: TypeDeclaration => visitTypeDeclaration(t)
			case t: TypeAlias => visitTypeAlias(t)
		}
	}
	final def visitClass(c: ClassLike): Unit = if(visitedClassLike add c) visitClass0(c)
	def visitClass0(c: ClassLike)
	{
		visitParameterizedDefinition(c)
		visitType(c.selfType)
		visitStructure(c.structure)
	}
	def visitField(f: FieldLike)
	{
		visitType(f.tpe)
		visitAnnotations(f.annotations)
	}
	def visitDef(d: Def)
	{
		visitParameterizedDefinition(d)
		visitValueParameters(d.valueParameters)
		visitType(d.returnType)
	}
	def visitValueParameters(valueParameters: Seq[ParameterList]) = valueParameters foreach visitValueParameterList
	def visitValueParameterList(list: ParameterList) = list.parameters foreach visitValueParameter
	def visitValueParameter(parameter: MethodParameter) = visitType(parameter.tpe)
		
	def visitParameterizedDefinition[T <: ParameterizedDefinition](d: T)
	{
		visitAnnotations(d.annotations)
		visitTypeParameters(d.typeParameters)
	}
	def visitTypeDeclaration(d: TypeDeclaration)
	{
		visitParameterizedDefinition(d)
		visitType(d.lowerBound)
		visitType(d.upperBound)
	}
	def visitTypeAlias(d: TypeAlias)
	{
		visitParameterizedDefinition(d)
		visitType(d.tpe)
	}
	
	def visitTypeParameters(parameters: Seq[TypeParameter]) = parameters foreach visitTypeParameter
	def visitTypeParameter(parameter: TypeParameter)
	{
		visitTypeParameters(parameter.typeParameters)
		visitType(parameter.lowerBound)
		visitType(parameter.upperBound)
	}
	def visitAnnotations(annotations: Seq[Annotation]) = annotations foreach visitAnnotation
	def visitAnnotation(annotation: Annotation) = visitType(annotation.base)
	
	def visitTypes(ts: Seq[Type]) = ts.foreach(visitType)
	def visitType(t: Type)
	{
		t match
		{
			case s: Structure => visitStructure(s)
			case e: Existential => visitExistential(e)
			case p: Polymorphic => visitPolymorphic(p)
			case a: Annotated => visitAnnotated(a)
			case p: Parameterized => visitParameterized(p)
			case p: Projection => visitProjection(p)
			case _: EmptyType => visitEmptyType()
			case s: Singleton => visitSingleton(s)
			case pr: ParameterRef => visitParameterRef(pr)
		}
	}
	
	def visitEmptyType() {}
	def visitSingleton(s: Singleton) {}
	def visitParameterRef(p: ParameterRef) {}

	def visitExistential(e: Existential) = visitParameters(e.clause, e.baseType)
	def visitPolymorphic(p: Polymorphic) = visitParameters(p.parameters, p.baseType)
	def visitProjection(p: Projection) = visitType(p.prefix)
	def visitParameterized(p: Parameterized)
	{
		visitType(p.baseType)
		visitTypes(p.typeArguments)
	}
	def visitAnnotated(a: Annotated)
	{
		visitType(a.baseType)
		visitAnnotations(a.annotations)
	}
	final def visitStructure(structure: Structure) = if(visitedStructures add structure) visitStructure0(structure)
	def visitStructure0(structure: Structure)
	{
		visitTypes(structure.parents)
		visitDefinitions(structure.declared)
		visitDefinitions(structure.inherited)
	}
	def visitParameters(parameters: Seq[TypeParameter], base: Type): Unit =
	{
		visitTypeParameters(parameters)
		visitType(base)
	}	
}
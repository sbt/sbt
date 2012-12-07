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

	def visit(s: Source): Unit = visitAPI(s.api)
	def visitAPI(s: SourceAPI): Unit =
	{
		s.packages foreach visitPackage
		s.definitions foreach visitDefinition
	}

	def visitPackage(p: Package)
	{
		visitString(p.name)
	}

	def visitDefinitions(ds: Seq[Definition]) = ds foreach visitDefinition
	def visitDefinition(d: Definition)
	{
		visitString(d.name)
		visitAnnotations(d.annotations)
		visitModifiers(d.modifiers)
		visitAccess(d.access)
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
		f match
		{
			case v: Var => visitVar(v)
			case v: Val => visitVal(v)
		}
	}
	def visitVar(v: Var) {}
	def visitVal(v: Val) {}
	def visitDef(d: Def)
	{
		visitParameterizedDefinition(d)
		visitValueParameters(d.valueParameters)
		visitType(d.returnType)
	}
	def visitAccess(a: Access): Unit =
		a match
		{
			case pub: Public => visitPublic(pub)
			case qual: Qualified => visitQualified(qual)
		}
	def visitQualified(qual: Qualified): Unit =
		qual match
		{
			case p: Protected => visitProtected(p)
			case p: Private => visitPrivate(p)
		}
	def visitQualifier(qual: Qualifier): Unit =
		qual match
		{
			case unq: Unqualified => visitUnqualified(unq)
			case thisq: ThisQualifier => visitThisQualifier(thisq)
			case id: IdQualifier => visitIdQualifier(id)
		}
	def visitIdQualifier(id: IdQualifier)
	{
		visitString(id.value)
	}
	def visitUnqualified(unq: Unqualified) {}
	def visitThisQualifier(thisq: ThisQualifier) {}
	def visitPublic(pub: Public) {}
	def visitPrivate(p: Private) { visitQualifier(p.qualifier) }
	def visitProtected(p: Protected) { visitQualifier(p.qualifier) }
	def visitModifiers(m: Modifiers) {}
	
	def visitValueParameters(valueParameters: Seq[ParameterList]) = valueParameters foreach visitValueParameterList
	def visitValueParameterList(list: ParameterList) = list.parameters foreach visitValueParameter
	def visitValueParameter(parameter: MethodParameter) =
	{
		visitString(parameter.name)
		visitType(parameter.tpe)
	}
		
	def visitParameterizedDefinition[T <: ParameterizedDefinition](d: T)
	{
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
		visitAnnotations(parameter.annotations)
	}
	def visitAnnotations(annotations: Seq[Annotation]) = annotations foreach visitAnnotation
	def visitAnnotation(annotation: Annotation) =
	{
		visitType(annotation.base)
		visitAnnotationArguments(annotation.arguments)
	}
	def visitAnnotationArguments(args: Seq[AnnotationArgument]) = args foreach visitAnnotationArgument
	def visitAnnotationArgument(arg: AnnotationArgument)
	{
		visitString(arg.name)
		visitString(arg.value)
	}
	
	def visitTypes(ts: Seq[Type]) = ts.foreach(visitType)
	def visitType(t: Type)
	{
		t match
		{
			case s: Structure => visitStructure(s)
			case e: Existential => visitExistential(e)
			case c: Constant => visitConstant(c)
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
	def visitParameterRef(p: ParameterRef) {}
	def visitSingleton(s: Singleton) { visitPath(s.path) }
	def visitPath(path: Path) = path.components foreach visitPathComponent
	def visitPathComponent(pc: PathComponent) = pc match
	{
		case t: This => visitThisPath(t)
		case s: Super => visitSuperPath(s)
		case id: Id => visitIdPath(id)
	}
	def visitThisPath(t: This) {}
	def visitSuperPath(s: Super) { visitPath(s.qualifier) }
	def visitIdPath(id: Id) { visitString(id.id) }


	def visitConstant(c: Constant) =
	{
		visitString(c.value)
		visitType(c.baseType)
	}
	def visitExistential(e: Existential) = visitParameters(e.clause, e.baseType)
	def visitPolymorphic(p: Polymorphic) = visitParameters(p.parameters, p.baseType)
	def visitProjection(p: Projection) =
	{
		visitString(p.id)
		visitType(p.prefix)
	}
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
	def visitString(s: String) {}
}
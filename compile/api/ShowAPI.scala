/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package xsbt.api

	import xsbti.api._

trait Show[A]
{
	def show(a: A): String
}

final class ShowLazy[A](delegate: => Show[A]) extends Show[A]
{
	private lazy val s = delegate
	def show(a: A) = s.show(a)
}

	import ShowAPI._

object ShowAPI
{
	def Show[T](implicit s: Show[T]): Show[T] = s
	def show[T](t: T)(implicit s: Show[T]): String = s.show(t)
	
	def bounds(lower: Type, upper: Type)(implicit t: Show[Type]): String =
		">: " + t.show(lower) + " <: " + t.show(upper)

		import ParameterModifier._
	def parameterModifier(base: String, pm: ParameterModifier): String =
		pm match
		{
			case Plain => base
			case Repeated => base + "*"
			case ByName => "=> " + base
		}
		
	def concat[A](list: Seq[A], as: Show[A], sep: String): String = mapSeq(list, as).mkString(sep)
	def commas[A](list: Seq[A], as: Show[A]): String = concat(list, as, ", ")
	def spaced[A](list: Seq[A], as: Show[A]): String = concat(list, as, " ")
	def lines[A](list: Seq[A], as: Show[A]): String = mapSeq(list, as).mkString("\n")
	def mapSeq[A](list: Seq[A], as: Show[A]): Seq[String] = list.map(as.show)
}

trait ShowBase
{
	implicit def showAnnotation(implicit as: Show[AnnotationArgument], t: Show[Type]): Show[Annotation] =
		new Show[Annotation] { def show(a: Annotation) = "@" + t.show(a.base) + (if(a.arguments.isEmpty) "" else "(" + commas(a.arguments, as) + ")") }
		
	implicit def showAnnotationArgument: Show[AnnotationArgument] =
		new Show[AnnotationArgument] { def show(a: AnnotationArgument) = a.name + " = " + a.value }
		
		import Variance._
	implicit def showVariance: Show[Variance] = 
		new Show[Variance] { def show(v: Variance) = v match { case Invariant => ""; case Covariant => "+"; case Contravariant => "-" } }
	
	implicit def showSource(implicit ps: Show[Package], ds: Show[Definition]): Show[Source] =
		new Show[Source] { def show(a: Source) = lines(a.packages, ps) + "\n" + lines(a.definitions, ds) }

	implicit def showPackage: Show[Package] =
		new Show[Package] { def show(pkg: Package) = "package " + pkg.name }

	implicit def showAccess(implicit sq: Show[Qualified]): Show[Access] = 
		new Show[Access]
		{
			def show(a: Access) =
				a match
				{
					case p: Public => ""
					case q: Qualified => sq.show(q)
				}
			}
	implicit def showQualified(implicit sq: Show[Qualifier]): Show[Qualified] =
		new Show[Qualified]
		{
			def show(q: Qualified) =
				((q match
				{
					case p: Protected => "protected"
					case p: Private => "private"
				})
				+ sq.show(q.qualifier) )
		}
	implicit def showQualifier: Show[Qualifier] = 
		new Show[Qualifier]
		{
			def show(q: Qualifier) =
				q match
				{
					case _: Unqualified => ""
					case _: ThisQualifier => "[this]"
					case i: IdQualifier => "[" + i.value + "]"
				}
		}
	implicit def showModifiers: Show[Modifiers] =
		new Show[Modifiers]
		{
			def show(m: Modifiers) =
			{
				val mods =
					(m.isOverride, "override") ::
					(m.isFinal, "final") ::
					(m.isSealed, "sealed") ::
					(m.isImplicit, "implicit") ::
					(m.isAbstract, "abstract") ::
					(m.isLazy, "lazy") ::
					Nil
				mods.filter(_._1).map(_._2).mkString(" ")
			}
		}
		
	implicit def showDefinitionType: Show[DefinitionType] =
		new Show[DefinitionType] {
			import DefinitionType._
			def show(dt: DefinitionType) =
				dt match
				{
					case Trait => "trait"
					case ClassDef => "class"
					case Module => "object"
					case PackageModule => "package object"
				}
		}
}
trait ShowDefinitions
{
	implicit def showVal(implicit acs: Show[Access], ms: Show[Modifiers], ans: Show[Annotation], t: Show[Type]): Show[Val] =
		new Show[Val] { def show(v: Val) = definitionBase(v, "val")(acs, ms, ans) + ": " + t.show(v.tpe) }
		
	implicit def showVar(implicit acs: Show[Access], ms: Show[Modifiers], ans: Show[Annotation], t: Show[Type]): Show[Var] =
		new Show[Var] { def show(v: Var) = definitionBase(v, "var")(acs, ms, ans) + ": " + t.show(v.tpe) }
		
	implicit def showDef(implicit acs: Show[Access], ms: Show[Modifiers], ans: Show[Annotation], tp: Show[Seq[TypeParameter]], vp: Show[Seq[ParameterList]], t: Show[Type]): Show[Def] =
		new Show[Def] { def show(d: Def) = parameterizedDef(d, "def")(acs, ms, ans, tp) + vp.show(d.valueParameters) + ": " + t.show(d.returnType) }
		
	implicit def showClassLike(implicit acs: Show[Access], ms: Show[Modifiers], ans: Show[Annotation], tp: Show[Seq[TypeParameter]], dt: Show[DefinitionType], s: Show[Structure], t: Show[Type]): Show[ClassLike] =
		new Show[ClassLike] { def show(cl: ClassLike) = parameterizedDef(cl, dt.show(cl.definitionType))(acs, ms, ans, tp) + " requires " + t.show(cl.selfType) + " extends " + s.show(cl.structure) }
		
	implicit def showTypeAlias(implicit acs: Show[Access], ms: Show[Modifiers], ans: Show[Annotation], tp: Show[Seq[TypeParameter]], t: Show[Type]): Show[TypeAlias] =
		new Show[TypeAlias] { def show(ta: TypeAlias) = parameterizedDef(ta, "type")(acs, ms, ans, tp) + " = " + t.show(ta.tpe) }
		
	implicit def showTypeDeclaration(implicit acs: Show[Access], ms: Show[Modifiers], ans: Show[Annotation], tp: Show[Seq[TypeParameter]], t: Show[Type]): Show[TypeDeclaration] =
		new Show[TypeDeclaration] { def show(td: TypeDeclaration) = parameterizedDef(td, "type")(acs, ms, ans, tp) + bounds(td.lowerBound, td.upperBound) }
	def showClassLikeSimple(implicit acs: Show[Access], ms: Show[Modifiers], ans: Show[Annotation], tp: Show[Seq[TypeParameter]], dt: Show[DefinitionType]): Show[ClassLike] =
		new Show[ClassLike] { def show(cl: ClassLike) = parameterizedDef(cl, dt.show(cl.definitionType))(acs, ms, ans, tp) }

	def parameterizedDef(d: ParameterizedDefinition, label: String)(implicit acs: Show[Access], ms: Show[Modifiers], ans: Show[Annotation], tp: Show[Seq[TypeParameter]]): String =
		definitionBase(d, label)(acs, ms, ans) + tp.show(d.typeParameters)
	def definitionBase(d: Definition, label: String)(implicit acs: Show[Access], ms: Show[Modifiers], ans: Show[Annotation]): String =
		space(spaced(d.annotations, ans)) + space(acs.show(d.access)) + space(ms.show(d.modifiers)) + space(label) + d.name
	def space(s: String) = if(s.isEmpty) s else s + " "
}
trait ShowDefinition
{
	implicit def showDefinition(implicit vl: Show[Val], vr: Show[Var], ds: Show[Def], cl: Show[ClassLike], ta: Show[TypeAlias], td: Show[TypeDeclaration]): Show[Definition] =
		new Show[Definition]
		{
			def show(d: Definition) = 
				d match
				{
					case v: Val => vl.show(v)
					case v: Var => vr.show(v)
					case d: Def => ds.show(d)
					case c: ClassLike => cl.show(c)
					case t: TypeAlias => ta.show(t)
					case t: TypeDeclaration => td.show(t)
				}
			}
}
trait ShowType
{
	implicit def showType(implicit s: Show[SimpleType], a: Show[Annotated], st: Show[Structure], e: Show[Existential], po: Show[Polymorphic]): Show[Type] =
		new Show[Type]
		{
			def show(t: Type) =
				t match
				{
					case q: SimpleType => s.show(q)
					case q: Annotated => a.show(q)
					case q: Structure => st.show(q)
					case q: Existential => e.show(q)
					case q: Polymorphic => po.show(q)
				}
		}
	
	implicit def showSimpleType(implicit pr: Show[Projection], pa: Show[ParameterRef], si: Show[Singleton], et: Show[EmptyType], p: Show[Parameterized]): Show[SimpleType] =
		new Show[SimpleType] {
			def show(t: SimpleType) = 
				t match
				{
					case q: Projection => pr.show(q)
					case q: ParameterRef => pa.show(q)
					case q: Singleton => si.show(q)
					case q: EmptyType =>  et.show(q)
					case q: Parameterized => p.show(q)
				}
		}
}
trait ShowBasicTypes
{
	implicit def showSingleton(implicit p: Show[Path]): Show[Singleton] =
		new Show[Singleton] { def show(s: Singleton) = p.show(s.path) }
	implicit def showEmptyType: Show[EmptyType] =
		new Show[EmptyType] { def show(e: EmptyType) = "<empty>" }
	implicit def showParameterRef: Show[ParameterRef] =
		new Show[ParameterRef] { def show(p: ParameterRef) = "<" + p.id + ">" }
}
trait ShowTypes
{
	implicit def showStructure(implicit t: Show[Type], d: Show[Definition]): Show[Structure] =
		new Show[Structure] {
			def show(s: Structure) =
				// don't show inherited to avoid dealing with cycles
				concat(s.parents, t, " with ") + "\n{\n" + lines(s.declared, d) + "\n}"
		}
	implicit def showAnnotated(implicit as: Show[Annotation], t: Show[SimpleType]): Show[Annotated] = 
		new Show[Annotated] { def show(a: Annotated) = spaced(a.annotations, as) + " " + t.show(a.baseType) }
	implicit def showProjection(implicit t: Show[SimpleType]): Show[Projection] =
		new Show[Projection] { def show(p: Projection) = t.show(p.prefix) + "#" + p.id }
	implicit def showParameterized(implicit t: Show[Type]): Show[Parameterized] =
		new Show[Parameterized] { def show(p: Parameterized) = t.show(p.baseType) + mapSeq(p.typeArguments, t).mkString("[", ", ", "]") }
	implicit def showExistential(implicit t: Show[Type], tp: Show[TypeParameter]): Show[Existential] =
		new Show[Existential] {
			def show(e: Existential) =
				t.show(e.baseType) + e.clause.map(t => "type " + tp.show(t)).mkString(" forSome { ", "; ", "}")
		}
	implicit def showPolymorphic(implicit t: Show[Type], tps: Show[Seq[TypeParameter]]): Show[Polymorphic] =
		new Show[Polymorphic] { def show(p: Polymorphic) = t.show(p.baseType) + tps.show(p.parameters) }
		
}

trait ShowPath
{
	implicit def showPath(implicit pc: Show[PathComponent]): Show[Path] =
		new Show[Path] { def show(p: Path) = mapSeq(p.components, pc).mkString(".") }
	
	implicit def showPathComponent(implicit sp: Show[Path]): Show[PathComponent] =
		new Show[PathComponent] {
			def show(p: PathComponent) =
				p match
				{
					case s: Super => "super[" + sp.show(s.qualifier) + "]"
					case _: This => "this"
					case i: Id => i.id
				}
		}
}

trait ShowValueParameters
{
	implicit def showParameterLists(implicit pl: Show[ParameterList]): Show[Seq[ParameterList]] =
		new Show[Seq[ParameterList]] { def show(p: Seq[ParameterList]) = concat(p,pl, "") }
	implicit def showParameterList(implicit mp: Show[MethodParameter]): Show[ParameterList] =
		new Show[ParameterList] { def show(pl: ParameterList) = "(" + (if(pl.isImplicit) "implicit " else "") + commas(pl.parameters, mp) + ")" }
		
	implicit def showMethodParameter(implicit t: Show[Type]): Show[MethodParameter] =
		new Show[MethodParameter] {
			def show(mp: MethodParameter) =
				mp.name + ": " + parameterModifier(t.show(mp.tpe), mp.modifier) + (if(mp.hasDefault) "= ..." else "")
		}
}
trait ShowTypeParameters
{
	implicit def showTypeParameters(implicit as: Show[TypeParameter]): Show[Seq[TypeParameter]] =
		new Show[Seq[TypeParameter]] { def show(tps: Seq[TypeParameter]) = if(tps.isEmpty) "" else mapSeq(tps, as).mkString("[", ",", "]") }
	implicit def showTypeParameter(implicit as: Show[Annotation], tp: Show[Seq[TypeParameter]], t: Show[Type], v: Show[Variance]): Show[TypeParameter] =
		new Show[TypeParameter] {
			def show(tps: TypeParameter) =
				spaced(tps.annotations, as) + " " + v.show(tps.variance) + tps.id + tp.show(tps.typeParameters) + " " + bounds(tps.lowerBound, tps.upperBound)
		}
}

// this class is a hack to resolve some diverging implicit errors.
// I'm pretty sure the cause is the Show[Seq[T]] dominating Show[X] issue.
//  It could probably be reduced a bit if that is the case (below was trial and error)
object DefaultShowAPI extends ShowBase with ShowBasicTypes with ShowValueParameters
{
	def apply(d: Definition) = ShowAPI.show(d)
	def apply(d: Type) = ShowAPI.show(d)

	implicit lazy val showVal: Show[Val] = Cyclic.showVal
	implicit lazy val showVar: Show[Var] = Cyclic.showVar
	implicit lazy val showClassLike: Show[ClassLike] = Cyclic.showClassLike
	implicit lazy val showTypeDeclaration: Show[TypeDeclaration] = Cyclic.showTypeDeclaration
	implicit lazy val showTypeAlias: Show[TypeAlias] = Cyclic.showTypeAlias
	implicit lazy val showDef: Show[Def] = Cyclic.showDef
	
	implicit lazy val showProj: Show[Projection] = Cyclic.showProjection
	implicit lazy val showPoly: Show[Polymorphic] = Cyclic.showPolymorphic
	
	implicit lazy val showSimple: Show[SimpleType] = new ShowLazy(Cyclic.showSimpleType)
	implicit lazy val showAnnotated: Show[Annotated] = Cyclic.showAnnotated
	implicit lazy val showExistential: Show[Existential] = Cyclic.showExistential
	implicit lazy val showParameterized: Show[Parameterized] = Cyclic.showParameterized
	
	implicit lazy val showTypeParameters: Show[Seq[TypeParameter]] = new ShowLazy(Cyclic.showTypeParameters)
	implicit lazy val showTypeParameter: Show[TypeParameter] = Cyclic.showTypeParameter
	
	implicit lazy val showDefinition: Show[Definition] = new ShowLazy(Cyclic.showDefinition)
	implicit lazy val showType: Show[Type] = new ShowLazy(Cyclic.showType)
	implicit lazy val showStructure: Show[Structure] = new ShowLazy(Cyclic.showStructure)
	
	implicit lazy val showPath: Show[Path] = new ShowLazy(Cyclic.showPath)
	implicit lazy val showPathComponent: Show[PathComponent] = Cyclic.showPathComponent

	private object Cyclic extends  ShowTypes with ShowType with ShowPath with ShowDefinition with ShowDefinitions with ShowTypeParameters
}
/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package xsbt.api

	import Predef.{implicitly => ??, _}
	import sbt.Using
	import xsbti.api._
	import sbinary._
	import DefaultProtocol._
	import java.io.File
	import scala.collection.mutable

trait FormatExtra
{
	def p2[A,B,R](unapply: R => (A,B))( apply: (A,B) => R)(implicit fa: Format[A], fb: Format[B]): Format[R] =
		asProduct2(apply)(unapply)(fa, fb)
	def p3[A,B,C,R](unapply: R => (A,B,C))( apply: (A,B,C) => R)(implicit fa: Format[A], fb: Format[B], fc: Format[C]): Format[R] =
		asProduct3(apply)(unapply)(fa, fb, fc)
	def p4[A,B,C,D,R](unapply: R => (A,B,C,D))( apply: (A,B,C,D) => R)(implicit fa: Format[A], fb: Format[B], fc: Format[C], fd: Format[D]): Format[R] =
		asProduct4(apply)(unapply)(fa, fb, fc, fd)
	def p5[A,B,C,D,E,R](unapply: R => (A,B,C,D,E))( apply: (A,B,C,D,E) => R)(implicit fa: Format[A], fb: Format[B], fc: Format[C], fd: Format[D], fe: Format[E]): Format[R] =
		asProduct5(apply)(unapply)(fa, fb, fc, fd, fe)
	def p6[A,B,C,D,E,F,R](unapply: R => (A,B,C,D,E,F))( apply: (A,B,C,D,E,F) => R)(implicit fa: Format[A], fb: Format[B], fc: Format[C], fd: Format[D], fe: Format[E], ff: Format[F]): Format[R] =
		asProduct6(apply)(unapply)(fa, fb, fc, fd, fe, ff)
	def p7[A,B,C,D,E,F,G,R](unapply: R => (A,B,C,D,E,F,G))( apply: (A,B,C,D,E,F,G) => R)(implicit fa: Format[A], fb: Format[B], fc: Format[C], fd: Format[D], fe: Format[E], ff: Format[F], fg: Format[G]): Format[R] =
		asProduct7(apply)(unapply)(fa, fb, fc, fd, fe, ff, fg)
	def p8[A,B,C,D,E,F,G,H,R](unapply: R => (A,B,C,D,E,F,G,H))( apply: (A,B,C,D,E,F,G,H) => R)(implicit fa: Format[A], fb: Format[B], fc: Format[C], fd: Format[D], fe: Format[E], ff: Format[F], fg: Format[G], fh: Format[H]): Format[R] =
		asProduct8(apply)(unapply)(fa, fb, fc, fd, fe, ff, fg, fh)
}
trait APIFormats extends FormatExtra
{
	implicit def formatStructure(implicit fi: Format[Int], references: References): Format[Structure] =
		wrap(references.id, references.structure)
	implicit def formatClassLike(implicit fi: Format[Int], references: References): Format[ClassLike] =
		wrap(references.id, references.classLike)

	// cyclic with formatSuper, so it is not implicit by default
	def formatPath(implicit pc: Format[Array[PathComponent]]): Format[Path] =
		wrap[Path, Array[PathComponent]]( _.components, x => new Path(x))(pc)
	
	implicit def formatComponent(implicit t: Format[This], s: Format[Super], i: Format[Id]): Format[PathComponent] =
		asUnion(t, s, i)
	def formatSuper(implicit p: Format[Path]): Format[Super] =
		wrap[Super, Path](_.qualifier, q => new Super(q))(p)
	implicit def formatId(implicit s: Format[String]): Format[Id] =
		wrap[Id, String](_.id, i => new Id(i))(s)
	implicit val formatThis: Format[This] = asSingleton(new This)

	implicit def formatSource(implicit pa: Format[Array[Package]], da: Format[Array[Definition]]): Format[SourceAPI] =
		p2( (s: SourceAPI) => (s.packages, s.definitions))( (p, d) => new SourceAPI(p, d) )(pa, da)

	implicit def formatAnnotated(implicit t: Format[SimpleType], as: Format[Array[Annotation]]): Format[Annotated] =
		p2( (a: Annotated) => (a.baseType,a.annotations))(new Annotated(_,_))(t,as)

	implicit def formatPolymorphic(implicit t: Format[Type], tps: Format[Array[TypeParameter]]): Format[Polymorphic] =
		p2( (p: Polymorphic) => (p.baseType, p.parameters) )( new Polymorphic(_,_) )(t, tps)

	implicit def formatConstant(implicit t: Format[Type], fs: Format[String]): Format[Constant] =
		p2( (c: Constant) => (c.baseType, c.value) )( new Constant(_,_) )(t,fs)

	implicit def formatExistential(implicit t: Format[Type], tps: Format[Array[TypeParameter]]): Format[Existential] =
		p2( (e: Existential) => (e.baseType, e.clause) )( new Existential(_,_) )(t,tps)

	implicit def formatParameterRef(implicit i: Format[String]): Format[ParameterRef] =
		wrap[ParameterRef, String](_.id, new ParameterRef(_))(i)

	// cyclic with many formats
	def formatType(implicit s: Format[SimpleType], a: Format[Annotated], st: Format[Structure], e: Format[Existential], po: Format[Polymorphic]): Format[Type] =
		asUnion(s, a, st, e, po)

	implicit def formatDef(implicit acs: Format[Access], ms: Format[Modifiers], ans: Format[Array[Annotation]], tp: Format[Array[TypeParameter]], vp: Format[Array[ParameterList]], t: Format[Type], fs: Format[String]): Format[Def] =
		p7( (d: Def) => (d.valueParameters, d.returnType, d.typeParameters, d.name, d.access, d.modifiers, d.annotations))( new Def(_,_,_,_,_,_,_) )(vp, t, tp, fs, acs, ms, ans)
		
	implicit def formatVal(implicit acs: Format[Access], ms: Format[Modifiers], ans: Format[Array[Annotation]], t: Format[Type], ts: Format[String]): Format[Val] =
		fieldLike( new Val(_,_,_,_,_) )(acs, ms, ans, ts, t)
		
	implicit def formatVar(implicit acs: Format[Access], ms: Format[Modifiers], ans: Format[Array[Annotation]], t: Format[Type], ts: Format[String]): Format[Var] =
		fieldLike( new Var(_,_,_,_,_) )(acs, ms, ans, ts, t)

	def fieldLike[F <: FieldLike](construct: (Type, String, Access, Modifiers, Array[Annotation]) => F)(implicit acs: Format[Access], ms: Format[Modifiers], ans: Format[Array[Annotation]], nme: Format[String], tpe: Format[Type]): Format[F] =
		asProduct5(construct)( d => (d.tpe, d.name, d.access, d.modifiers, d.annotations))(tpe, nme, acs, ms, ans)

	def formatDefinition(implicit vl: Format[Val], vr: Format[Var], ds: Format[Def], cl: Format[ClassLike], ta: Format[TypeAlias], td: Format[TypeDeclaration]): Format[Definition] =
		asUnion(vl, vr, ds, cl, ta, td)

	def formatSimpleType(implicit pr: Format[Projection], pa: Format[ParameterRef], si: Format[Singleton], et: Format[EmptyType], p: Format[Parameterized]): Format[SimpleType] =
		asUnion(pr, pa, si, et, p)

	implicit def formatEmptyType: Format[EmptyType] = asSingleton(new EmptyType)
	implicit def formatSingleton(implicit p: Format[Path]): Format[Singleton] = wrap[Singleton, Path](_.path, new Singleton(_))

	def formatProjection(implicit t: Format[SimpleType], s: Format[String]): Format[Projection] =
		p2( (p: Projection) => (p.prefix, p.id))(new Projection(_,_))(t, s)

	def formatParameterized(implicit t: Format[SimpleType], tps: Format[Array[Type]]): Format[Parameterized] =
		p2( (p: Parameterized) => (p.baseType, p.typeArguments))(new Parameterized(_,_))(t, tps)

	implicit def formatTypeAlias(implicit acs: Format[Access], ms: Format[Modifiers], ans: Format[Array[Annotation]], tps: Format[Array[TypeParameter]], t: Format[Type], n: Format[String]): Format[TypeAlias] =
		p6( (ta: TypeAlias) => (ta.tpe, ta.typeParameters, ta.name, ta.access, ta.modifiers, ta.annotations))( new TypeAlias(_,_,_,_,_,_) )(t, tps, n, acs, ms, ans)
		
	implicit def formatTypeDeclaration(implicit acs: Format[Access], ms: Format[Modifiers], ans: Format[Array[Annotation]], tps: Format[Array[TypeParameter]], t: Format[Type], n: Format[String]): Format[TypeDeclaration] =
		p7( (td: TypeDeclaration) => (td.lowerBound, td.upperBound, td.typeParameters, td.name, td.access, td.modifiers, td.annotations))( new TypeDeclaration(_,_,_,_,_,_,_))(t,t,tps,n,acs,ms,ans)

	// cyclic with SimpleType
	def formatAnnotation(implicit t: Format[Type], af: Format[Array[AnnotationArgument]]): Format[Annotation] =
		p2( (a: Annotation) => (a.base, a.arguments) )( (a,b) => new Annotation(a,b) )(t, af)
		
	implicit def formatAnnotationArgument(implicit sf: Format[String]): Format[AnnotationArgument] =
		p2( (aa: AnnotationArgument) => (aa.name, aa.value))( (a,b) => new AnnotationArgument(a,b))(sf, sf)


	implicit def formatVariance(implicit b: Format[Byte]): Format[Variance] = 
		wrap[Variance, Byte]( v => v.ordinal.toByte, b => Variance.values.apply(b.toInt) )(b)

	implicit def formatDefinitionType(implicit b: Format[Byte]): Format[DefinitionType] =
		wrap[DefinitionType, Byte]( dt => dt.ordinal.toByte, b => DefinitionType.values.apply(b.toInt) )(b)

	implicit def formatPackage(implicit fs: Format[String]): Format[Package] =
		wrap[Package, String]( _.name, n => new Package(n) )(fs)

	implicit def formatAccess(implicit fs: Format[String]): Format[Access] =
		new Format[Access]
		{
			val unqualified = new Unqualified
			val ths = new ThisQualifier

			val public = new Public
			val privateUn = new Private(unqualified)
			val protectedUn = new Protected(unqualified)
			val privateThis = new Private(ths)
			val protectedThis = new Protected(ths)

			def reads(in: Input): Access =
			{
				def qualifier() = new IdQualifier(fs.reads(in))
				import AccessIDs._
				AccessIDs(in.readByte) match
				{
					case PublicID => public
					case PrivateID => privateUn
					case ProtectedID => protectedUn
					case PrivateThisID => privateThis
					case ProtectedThisID => protectedThis
					case QPrivateID => new Private(qualifier())
					case QProtectedID => new Protected(qualifier())
				}
			}
			def writes(out: Output, a: Access)
			{
					import AccessIDs._
				def w(id: AccessIDs.Value) = out.writeByte(id.id.toByte)
				def qualified(un: Value, ths: Value, qual: Value, qualifier: Qualifier): Unit =
					qualifier match
					{
						case _: Unqualified => w(un)
						case _: ThisQualifier => w(ths)
						case i: IdQualifier => w(qual); fs.writes(out, i.value)
					}
				a match
				{
					case _: Public => w(PublicID)
					case q: Qualified => q match {
						case p: Private => qualified(PrivateID, PrivateThisID, QPrivateID, p.qualifier)
						case p: Protected => qualified(ProtectedID, ProtectedThisID, QProtectedID, p.qualifier)
					}
				}
			}
		}

		import APIUtil._
	implicit def formatModifiers(implicit bf: Format[Byte]): Format[Modifiers] =
		wrap[Modifiers, Byte]( modifiersToByte, byteToModifiers )

	def formatTypeParameter(tps: Format[TypeParameter] => Format[Array[TypeParameter]])(implicit as: Format[Array[Annotation]], t: Format[Type], v: Format[Variance], i: Format[String]): Format[TypeParameter] =
	{
		lazy val ltps: Format[Array[TypeParameter]] = lazyFormat( tps(ltp) )
		lazy val ltp = p6( (tp: TypeParameter) => (tp.id, tp.annotations, tp.typeParameters, tp.variance, tp.lowerBound, tp.upperBound))(new TypeParameter(_,_,_,_,_,_))(i, as, ltps, v, t, t)
		ltp
	}

	implicit def formatParameterList(implicit mp: Format[Array[MethodParameter]], bf: Format[Boolean]): Format[ParameterList] =
		p2( (pl: ParameterList) => (pl.parameters, pl.isImplicit) )( (ps, impl) => new ParameterList(ps, impl) )
		
	implicit def formatMethodParameter(implicit s: Format[String], b: Format[Boolean], m: Format[ParameterModifier], t: Format[Type]): Format[MethodParameter] =
		p4( (mp: MethodParameter) => (mp.name, mp.tpe, mp.hasDefault, mp.modifier) )( new MethodParameter(_,_,_,_) )(s,t,b,m)

	implicit def formatParameterModifier(implicit bf: Format[Byte]): Format[ParameterModifier] =
		wrap[ParameterModifier, Byte]( dt => dt.ordinal.toByte, b => ParameterModifier.values.apply(b.toInt) )(bf)
}
private object AccessIDs extends Enumeration
{
	val PublicID, PrivateID, ProtectedID, QPrivateID, QProtectedID, PrivateThisID, ProtectedThisID = Value
}

// construct a concrete Format[Source], handling cycles
//  ?? (Predef.implicitly) is used for implicits the compiler should infer
class DefaultAPIFormats(implicit val references: References) extends APIFormats
{
		import sbinary.DefaultProtocol._

	implicit lazy val tpf: Format[TypeParameter] = formatTypeParameter(array)

	// SimpleType is cyclic with Projection and Parameterized
	implicit lazy val stf: Format[SimpleType] = lazyFormat(formatSimpleType(projf, ??, ??, ??, paramf))
	// Type is cyclic with a lot, including SimpleType and TypeParameter
	implicit lazy val tf: Format[Type] = lazyFormat( formatType(stf, ??, ??, ??, ??) )

	implicit lazy val df: Format[Definition] = lazyFormat( formatDefinition )

	// Projection, Annotation, and Parameterized are cyclic with SimpleType
	//  Parameterized and Annotation are also cyclic with Type
	implicit lazy val projf: Format[Projection] = formatProjection(stf, ??)
	implicit lazy val af: Format[Annotation] = formatAnnotation(tf, ??)
	implicit lazy val paramf: Format[Parameterized] = formatParameterized(stf, array(tf))

	// Super and Path are cyclic
	implicit lazy val sf: Format[Super] = lazyFormat(formatSuper(pathf))
	implicit lazy val pathf: Format[Path] = formatPath

	implicit val srcFormat: Format[SourceAPI] = formatSource(??, array(df))

	private[this] def array[T](format: Format[T])(implicit mf: Manifest[T]): Format[Array[T]] = arrayFormat(format, mf)
}
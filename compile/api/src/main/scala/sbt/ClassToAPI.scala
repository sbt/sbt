package sbt

import java.lang.reflect.{ Array => _, _ }
import java.lang.annotation.Annotation
import annotation.tailrec
import sbt.classfile.ClassFile
import xsbti.api
import xsbti.SafeLazy
import SafeLazy.strict
import collection.mutable

object ClassToAPI {
  def apply(c: Seq[Class[_]]): api.SourceAPI = process(c)._1

  // (api, public inherited classes)
  def process(c: Seq[Class[_]]): (api.SourceAPI, Set[Class[_]]) =
    {
      val pkgs = packages(c).map(p => new api.Package(p))
      val cmap = emptyClassMap
      val defs = c.filter(isTopLevel).flatMap(toDefinitions(cmap))
      val source = new api.SourceAPI(pkgs.toArray, defs.toArray)
      cmap.lz.foreach(_.get()) // force thunks to ensure all inherited dependencies are recorded
      val inDeps = cmap.inherited.toSet
      cmap.clear()
      (source, inDeps)
    }

  // Avoiding implicit allocation.
  private def arrayMap[T <: AnyRef, U <: AnyRef: ClassManifest](xs: Array[T])(f: T => U): Array[U] = {
    val len = xs.length
    var i = 0
    val res = new Array[U](len)
    while (i < len) {
      res(i) = f(xs(i))
      i += 1
    }
    res
  }

  def packages(c: Seq[Class[_]]): Set[String] =
    c.flatMap(packageName).toSet

  def isTopLevel(c: Class[_]): Boolean =
    c.getEnclosingClass eq null

  final class ClassMap private[sbt] (private[sbt] val memo: mutable.Map[String, Seq[api.ClassLike]], private[sbt] val inherited: mutable.Set[Class[_]], private[sbt] val lz: mutable.Buffer[xsbti.api.Lazy[_]]) {
    def clear() { memo.clear(); inherited.clear(); lz.clear() }
  }
  def emptyClassMap: ClassMap = new ClassMap(new mutable.HashMap, new mutable.HashSet, new mutable.ListBuffer)

  def toDefinitions(cmap: ClassMap)(c: Class[_]): Seq[api.ClassLike] =
    cmap.memo.getOrElseUpdate(c.getName, toDefinitions0(c, cmap))
  def toDefinitions0(c: Class[_], cmap: ClassMap): Seq[api.ClassLike] =
    {
      import api.DefinitionType.{ ClassDef, Module, Trait }
      val enclPkg = packageName(c)
      val mods = modifiers(c.getModifiers)
      val acc = access(c.getModifiers, enclPkg)
      val annots = annotations(c.getAnnotations)
      val name = c.getName
      val tpe = if (Modifier.isInterface(c.getModifiers)) Trait else ClassDef
      lazy val (static, instance) = structure(c, enclPkg, cmap)
      val cls = new api.ClassLike(tpe, strict(Empty), lzy(instance, cmap), emptyStringArray, typeParameters(typeParameterTypes(c)), name, acc, mods, annots)
      val stat = new api.ClassLike(Module, strict(Empty), lzy(static, cmap), emptyStringArray, emptyTypeParameterArray, name, acc, mods, annots)
      val defs = cls :: stat :: Nil
      cmap.memo(c.getName) = defs
      defs
    }

  /** Returns the (static structure, instance structure, inherited classes) for `c`. */
  def structure(c: Class[_], enclPkg: Option[String], cmap: ClassMap): (api.Structure, api.Structure) = {
    lazy val cf = classFileForClass(c)
    val methods = mergeMap(c, c.getDeclaredMethods, c.getMethods, methodToDef(enclPkg))
    val fields = mergeMap(c, c.getDeclaredFields, c.getFields, fieldToDef(c, cf, enclPkg))
    val constructors = mergeMap(c, c.getDeclaredConstructors, c.getConstructors, constructorToDef(enclPkg))
    val classes = merge[Class[_]](c, c.getDeclaredClasses, c.getClasses, toDefinitions(cmap), (_: Seq[Class[_]]).partition(isStatic), _.getEnclosingClass != c)
    val all = methods ++ fields ++ constructors ++ classes
    val parentJavaTypes = allSuperTypes(c)
    if (!Modifier.isPrivate(c.getModifiers))
      cmap.inherited ++= parentJavaTypes.collect { case c: Class[_] => c }
    val parentTypes = types(parentJavaTypes)
    val instanceStructure = new api.Structure(lzyS(parentTypes.toArray), lzyS(all.declared.toArray), lzyS(all.inherited.toArray))
    val staticStructure = new api.Structure(lzyEmptyTpeArray, lzyS(all.staticDeclared.toArray), lzyS(all.staticInherited.toArray))
    (staticStructure, instanceStructure)
  }

  /** TODO: over time, ClassToAPI should switch the majority of access to the classfile parser */
  private[this] def classFileForClass(c: Class[_]): ClassFile = {
    val file = new java.io.File(IO.classLocationFile(c), s"${c.getName.replace('.', '/')}.class")
    classfile.Parser.apply(file)
  }

  private[this] def lzyS[T <: AnyRef](t: T): xsbti.api.Lazy[T] = lzy(t)
  def lzy[T <: AnyRef](t: => T): xsbti.api.Lazy[T] = xsbti.SafeLazy(t)
  private[this] def lzy[T <: AnyRef](t: => T, cmap: ClassMap): xsbti.api.Lazy[T] = {
    val s = lzy(t)
    cmap.lz += s
    s
  }

  private val emptyStringArray = new Array[String](0)
  private val emptyTypeArray = new Array[xsbti.api.Type](0)
  private val emptyAnnotationArray = new Array[xsbti.api.Annotation](0)
  private val emptyTypeParameterArray = new Array[xsbti.api.TypeParameter](0)
  private val emptySimpleTypeArray = new Array[xsbti.api.SimpleType](0)
  private val lzyEmptyTpeArray = lzyS(emptyTypeArray)
  private val lzyEmptyDefArray = lzyS(new Array[xsbti.api.Definition](0))

  private def allSuperTypes(t: Type): Seq[Type] =
    {
      @tailrec def accumulate(t: Type, accum: Seq[Type] = Seq.empty): Seq[Type] = t match {
        case c: Class[_] =>
          val (parent, interfaces) = (c.getGenericSuperclass, c.getGenericInterfaces)
          accumulate(parent, (accum :+ parent) ++ flattenAll(interfaces))
        case p: ParameterizedType =>
          accumulate(p.getRawType, accum)
        case _ =>
          accum
      }
      @tailrec def flattenAll(interfaces: Seq[Type], accum: Seq[Type] = Seq.empty): Seq[Type] =
        {
          if (interfaces.nonEmpty) {
            val raw = interfaces map { case p: ParameterizedType => p.getRawType; case i => i }
            val children = raw flatMap { case i: Class[_] => i.getGenericInterfaces; case _ => Seq.empty }
            flattenAll(children, accum ++ interfaces ++ children)
          } else
            accum
        }
      accumulate(t).filterNot(_ == null).distinct
    }

  @deprecated("No longer used", "0.13.0")
  def parents(c: Class[_]): Seq[api.Type] = types(allSuperTypes(c))
  def types(ts: Seq[Type]): Array[api.Type] = ts filter (_ ne null) map reference toArray;
  def upperBounds(ts: Array[Type]): api.Type =
    new api.Structure(lzy(types(ts)), lzyEmptyDefArray, lzyEmptyDefArray)

  @deprecated("Use fieldToDef[4] instead", "0.13.9")
  def fieldToDef(enclPkg: Option[String])(f: Field): api.FieldLike = {
    val c = f.getDeclaringClass()
    fieldToDef(c, classFileForClass(c), enclPkg)(f)
  }

  def fieldToDef(c: Class[_], cf: => ClassFile, enclPkg: Option[String])(f: Field): api.FieldLike =
    {
      val name = f.getName
      val accs = access(f.getModifiers, enclPkg)
      val mods = modifiers(f.getModifiers)
      val annots = annotations(f.getDeclaredAnnotations)
      val fieldTpe = reference(returnType(f))
      // generate a more specific type for constant fields
      val specificTpe: Option[api.Type] =
        if (mods.isFinal) {
          try {
            cf.constantValue(name).map(singletonForConstantField(c, f, _))
          } catch {
            case e: Throwable =>
              throw new IllegalStateException(
                s"Failed to parse class $c: this may mean your classfiles are corrupted. Please clean and try again.",
                e
              )
          }
        } else {
          None
        }
      val tpe = specificTpe.getOrElse(fieldTpe)
      if (mods.isFinal) {
        new api.Val(tpe, name, accs, mods, annots)
      } else {
        new api.Var(tpe, name, accs, mods, annots)
      }
    }

  /**
   * Creates a Singleton type that includes both the type and ConstantValue for the given Field.
   *
   * Since java compilers are allowed to inline constant (static final primitive) fields in
   * downstream classfiles, we generate a type that will cause APIs to match only when both
   * the type and value of the field match. We include the classname mostly for readability.
   *
   * Because this type is purely synthetic, it's fine that the name might contain filename-
   * banned characters.
   */
  private def singletonForConstantField(c: Class[_], field: Field, constantValue: AnyRef) =
    new api.Singleton(
      pathFromStrings(
        c.getName.split("\\.").toSeq :+ (field.getName + "$" + returnType(field) + "$" + constantValue)
      )
    )

  def methodToDef(enclPkg: Option[String])(m: Method): api.Def =
    defLike(m.getName, m.getModifiers, m.getDeclaredAnnotations, typeParameterTypes(m), m.getParameterAnnotations, parameterTypes(m), Option(returnType(m)), exceptionTypes(m), m.isVarArgs, enclPkg)

  def constructorToDef(enclPkg: Option[String])(c: Constructor[_]): api.Def =
    defLike("<init>", c.getModifiers, c.getDeclaredAnnotations, typeParameterTypes(c), c.getParameterAnnotations, parameterTypes(c), None, exceptionTypes(c), c.isVarArgs, enclPkg)

  def defLike[T <: GenericDeclaration](name: String, mods: Int, annots: Array[Annotation], tps: Array[TypeVariable[T]], paramAnnots: Array[Array[Annotation]], paramTypes: Array[Type], retType: Option[Type], exceptions: Array[Type], varArgs: Boolean, enclPkg: Option[String]): api.Def =
    {
      val varArgPosition = if (varArgs) paramTypes.length - 1 else -1
      val isVarArg = List.tabulate(paramTypes.length)(_ == varArgPosition)
      val pa = (paramAnnots, paramTypes, isVarArg).zipped map { case (a, p, v) => parameter(a, p, v) }
      val params = new api.ParameterList(pa, false)
      val ret = retType match { case Some(rt) => reference(rt); case None => Empty }
      new api.Def(Array(params), ret, typeParameters(tps), name, access(mods, enclPkg), modifiers(mods), annotations(annots) ++ exceptionAnnotations(exceptions))
    }

  def exceptionAnnotations(exceptions: Array[Type]): Array[api.Annotation] =
    if (exceptions.length == 0) emptyAnnotationArray
    else arrayMap(exceptions)(t => new api.Annotation(Throws, Array(new api.AnnotationArgument("value", t.toString))))

  def parameter(annots: Array[Annotation], parameter: Type, varArgs: Boolean): api.MethodParameter =
    new api.MethodParameter("", annotated(reference(parameter), annots), false, if (varArgs) api.ParameterModifier.Repeated else api.ParameterModifier.Plain)

  def annotated(t: api.SimpleType, annots: Array[Annotation]): api.Type = (
    if (annots.length == 0) t
    else new api.Annotated(t, annotations(annots))
  )

  case class Defs(declared: Seq[api.Definition], inherited: Seq[api.Definition], staticDeclared: Seq[api.Definition], staticInherited: Seq[api.Definition]) {
    def ++(o: Defs) = Defs(declared ++ o.declared, inherited ++ o.inherited, staticDeclared ++ o.staticDeclared, staticInherited ++ o.staticInherited)
  }
  def mergeMap[T <: Member](of: Class[_], self: Seq[T], public: Seq[T], f: T => api.Definition): Defs =
    merge[T](of, self, public, x => f(x) :: Nil, splitStatic _, _.getDeclaringClass != of)

  def merge[T](of: Class[_], self: Seq[T], public: Seq[T], f: T => Seq[api.Definition], splitStatic: Seq[T] => (Seq[T], Seq[T]), isInherited: T => Boolean): Defs =
    {
      val (selfStatic, selfInstance) = splitStatic(self)
      val (inheritedStatic, inheritedInstance) = splitStatic(public filter isInherited)
      Defs(selfInstance flatMap f, inheritedInstance flatMap f, selfStatic flatMap f, inheritedStatic flatMap f)
    }

  def splitStatic[T <: Member](defs: Seq[T]): (Seq[T], Seq[T]) =
    defs partition isStatic

  def isStatic(c: Class[_]): Boolean = Modifier.isStatic(c.getModifiers)
  def isStatic(a: Member): Boolean = Modifier.isStatic(a.getModifiers)

  def typeParameters[T <: GenericDeclaration](tps: Array[TypeVariable[T]]): Array[api.TypeParameter] =
    if (tps.length == 0) emptyTypeParameterArray
    else arrayMap(tps)(typeParameter)

  def typeParameter[T <: GenericDeclaration](tp: TypeVariable[T]): api.TypeParameter =
    new api.TypeParameter(typeVariable(tp), emptyAnnotationArray, emptyTypeParameterArray, api.Variance.Invariant, NothingRef, upperBounds(tp.getBounds))

  // needs to be stable across compilations
  def typeVariable[T <: GenericDeclaration](tv: TypeVariable[T]): String =
    name(tv.getGenericDeclaration) + " " + tv.getName

  def reduceHash(in: Array[Byte]): Int =
    (0 /: in)((acc, b) => (acc * 43) ^ b)

  def name(gd: GenericDeclaration): String =
    gd match {
      case c: Class[_]       => c.getName
      case m: Method         => m.getName
      case c: Constructor[_] => c.getName
    }

  def modifiers(i: Int): api.Modifiers =
    {
      import Modifier.{ isAbstract, isFinal }
      new api.Modifiers(isAbstract(i), false, isFinal(i), false, false, false, false)
    }
  def access(i: Int, pkg: Option[String]): api.Access =
    {
      import Modifier.{ isPublic, isPrivate, isProtected }
      if (isPublic(i)) Public else if (isPrivate(i)) Private else if (isProtected(i)) Protected else packagePrivate(pkg)
    }

  def annotations(a: Array[Annotation]): Array[api.Annotation] = if (a.length == 0) emptyAnnotationArray else arrayMap(a)(annotation)
  def annotation(a: Annotation): api.Annotation =
    new api.Annotation(reference(a.annotationType), Array(javaAnnotation(a.toString)))

  // full information not available from reflection
  def javaAnnotation(s: String): api.AnnotationArgument =
    new api.AnnotationArgument("toString", s)

  def array(tpe: api.Type): api.SimpleType = new api.Parameterized(ArrayRef, Array(tpe))
  def reference(c: Class[_]): api.SimpleType =
    if (c.isArray) array(reference(c.getComponentType)) else if (c.isPrimitive) primitive(c.getName) else reference(c.getName)

  // does not handle primitives
  def reference(s: String): api.SimpleType =
    {
      val (pkg, cls) = packageAndName(s)
      pkg match {
        // translate all primitives?
        case None => new api.Projection(Empty, cls)
        case Some(p) =>
          new api.Projection(new api.Singleton(pathFromString(p)), cls)
      }
    }
  def referenceP(t: ParameterizedType): api.Parameterized =
    {
      val targs = t.getActualTypeArguments
      val args = if (targs.length == 0) emptyTypeArray else arrayMap(targs)(t => reference(t): api.Type)
      val base = reference(t.getRawType)
      new api.Parameterized(base, args.toArray[api.Type])
    }
  def reference(t: Type): api.SimpleType =
    t match {
      case w: WildcardType       => reference("_")
      case tv: TypeVariable[_]   => new api.ParameterRef(typeVariable(tv))
      case pt: ParameterizedType => referenceP(pt)
      case gat: GenericArrayType => array(reference(gat.getGenericComponentType))
      case c: Class[_]           => reference(c)
    }

  def pathFromString(s: String): api.Path =
    pathFromStrings(s.split("\\."))
  def pathFromStrings(ss: Seq[String]): api.Path =
    new api.Path((ss.map(new api.Id(_)) :+ ThisRef).toArray)
  def packageName(c: Class[_]) = packageAndName(c)._1
  def packageAndName(c: Class[_]): (Option[String], String) =
    packageAndName(c.getName)
  def packageAndName(name: String): (Option[String], String) =
    {
      val lastDot = name.lastIndexOf('.')
      if (lastDot >= 0)
        (Some(name.substring(0, lastDot)), name.substring(lastDot + 1))
      else
        (None, name)
    }

  val Empty = new api.EmptyType
  val ThisRef = new api.This

  val Public = new api.Public
  val Unqualified = new api.Unqualified
  val Private = new api.Private(Unqualified)
  val Protected = new api.Protected(Unqualified)
  def packagePrivate(pkg: Option[String]): api.Access = new api.Private(new api.IdQualifier(pkg getOrElse ""))

  val ArrayRef = reference("scala.Array")
  val Throws = reference("scala.throws")
  val NothingRef = reference("scala.Nothing")

  private[this] def PrimitiveNames = Seq("boolean", "byte", "char", "short", "int", "long", "float", "double")
  private[this] def PrimitiveMap = PrimitiveNames.map(j => (j, j.capitalize)) :+ ("void" -> "Unit")
  private[this] val PrimitiveRefs = PrimitiveMap.map { case (n, sn) => (n, reference("scala." + sn)) }.toMap
  def primitive(name: String): api.SimpleType = PrimitiveRefs(name)

  // Workarounds for https://github.com/sbt/sbt/issues/1035
  //   these catch the GenericSignatureFormatError and return the erased type

  private[this] def returnType(f: Field): Type = try f.getGenericType catch {
    case _: GenericSignatureFormatError => f.getType
  }
  private[this] def parameterTypes(c: Constructor[_]): Array[Type] = try c.getGenericParameterTypes catch {
    case _: GenericSignatureFormatError => convert(c.getParameterTypes)
  }
  private[this] def exceptionTypes(c: Constructor[_]): Array[Type] = try c.getGenericExceptionTypes catch {
    case _: GenericSignatureFormatError => convert(c.getExceptionTypes)
  }
  private[this] def parameterTypes(m: Method): Array[Type] = try m.getGenericParameterTypes catch {
    case _: GenericSignatureFormatError => convert(m.getParameterTypes)
  }
  private[this] def returnType(m: Method): Type = try m.getGenericReturnType catch {
    case _: GenericSignatureFormatError => m.getReturnType
  }
  private[this] def exceptionTypes(m: Method): Array[Type] = try m.getGenericExceptionTypes catch {
    case _: GenericSignatureFormatError => convert(m.getExceptionTypes)
  }

  private[this] def typeParameterTypes[T](m: Constructor[T]): Array[TypeVariable[Constructor[T]]] = try m.getTypeParameters catch {
    case _: GenericSignatureFormatError => new Array(0)
  }
  private[this] def typeParameterTypes[T](m: Class[T]): Array[TypeVariable[Class[T]]] = try m.getTypeParameters catch {
    case _: GenericSignatureFormatError => new Array(0)
  }
  private[this] def typeParameterTypes(m: Method): Array[TypeVariable[Method]] = try m.getTypeParameters catch {
    case _: GenericSignatureFormatError => new Array(0)
  }
  private[this] def superclassType(c: Class[_]): Type = try c.getGenericSuperclass catch {
    case _: GenericSignatureFormatError => c.getSuperclass
  }
  private[this] def interfaces(c: Class[_]): Array[Type] = try c.getGenericInterfaces catch {
    case _: GenericSignatureFormatError => convert(c.getInterfaces)
  }

  private[this] def convert(classes: Array[Class[_]]): Array[Type] =
    classes.asInstanceOf[Array[Type]] // ok: treat Arrays as read-only
}

package xsbt

import java.io.File
import java.util.{ Arrays, Comparator }
import scala.tools.nsc.{ io, plugins, symtab, Global, Phase }
import io.{ AbstractFile, PlainFile, ZipArchive }
import plugins.{ Plugin, PluginComponent }
import symtab.Flags
import scala.collection.mutable.{ HashMap, HashSet, ListBuffer }
import xsbti.api.{ ClassLike, DefinitionType, PathComponent, SimpleType }

/**
 * Extracts full (including private members) API representation out of Symbols and Types.
 *
 * Each compilation unit should be processed by a fresh instance of this class.
 *
 * This class depends on instance of CallbackGlobal instead of regular Global because
 * it has a call to `addInheritedDependencies` method defined in CallbackGlobal. In the future
 * we should refactor this code so inherited dependencies are just accumulated in a buffer and
 * exposed to a client that can pass them to an instance of CallbackGlobal it holds.
 *
 * NOTE: This class extract *full* API representation. In most of other places in the incremental compiler,
 * only non-private (accessible from other compilation units) members are relevant. Other parts of the
 * incremental compiler filter out private definitions before processing API structures. Check SameAPI for
 * an example.
 *
 */
class ExtractAPI[GlobalType <: CallbackGlobal](val global: GlobalType,
    // Tracks the source file associated with the CompilationUnit currently being processed by the API phase.
    // This is used when recording inheritance dependencies.
    sourceFile: File) extends Compat {

  import global._

  private def error(msg: String) = throw new RuntimeException(msg)

  // this cache reduces duplicate work both here and when persisting
  //   caches on other structures had minimal effect on time and cache size
  //   (tried: Definition, Modifier, Path, Id, String)
  private[this] val typeCache = new HashMap[(Symbol, Type), xsbti.api.Type]
  // these caches are necessary for correctness
  private[this] val structureCache = new HashMap[Symbol, xsbti.api.Structure]
  private[this] val classLikeCache = new HashMap[(Symbol, Symbol), xsbti.api.ClassLike]
  private[this] val pending = new HashSet[xsbti.api.Lazy[_]]

  private[this] val emptyStringArray = new Array[String](0)

  /**
   * Implements a work-around for https://github.com/sbt/sbt/issues/823
   *
   * The strategy is to rename all type variables bound by existential type to stable
   * names by assigning to each type variable a De Bruijn-like index. As a result, each
   * type variable gets name of this shape:
   *
   *   "existential_${nestingLevel}_${i}"
   *
   * where `nestingLevel` indicates nesting level of existential types and `i` variable
   * indicates position of type variable in given existential type.
   *
   * For example, let's assume we have the following classes declared:
   *
   *   class A[T]; class B[T,U]
   *
   * and we have type A[_] that is expanded by Scala compiler into
   *
   *   A[_$1] forSome { type _$1 }
   *
   * After applying our renaming strategy we get
   *
   *   A[existential_0_0] forSome { type existential_0_0 }
   *
   * Let's consider a bit more complicated example which shows how our strategy deals with
   * nested existential types:
   *
   *   A[_ <: B[_, _]]
   *
   * which gets expanded into:
   *
   *   A[_$1] forSome {
   *     type _$1 <: B[_$2, _$3] forSome { type _$2; type _$3 }
   *   }
   *
   * After applying our renaming strategy we get
   *
   *   A[existential_0_0] forSome {
   *     type existential_0_0 <: B[existential_1_0, existential_1_1] forSome {
   *       type existential_1_0; type existential_1_1
   *     }
   *   }
   *
   * Note how the first index (nesting level) is bumped for both existential types.
   *
   * This way, all names of existential type variables depend only on the structure of
   * existential types and are kept stable.
   *
   * Both examples presented above used placeholder syntax for existential types but our
   * strategy is applied uniformly to all existential types no matter if they are written
   * using placeholder syntax or explicitly.
   */
  private[this] object existentialRenamings {
    private var nestingLevel: Int = 0
    import scala.collection.mutable.Map
    private var renameTo: Map[Symbol, String] = Map.empty

    def leaveExistentialTypeVariables(typeVariables: Seq[Symbol]): Unit = {
      nestingLevel -= 1
      assert(nestingLevel >= 0)
      typeVariables.foreach(renameTo.remove)
    }
    def enterExistentialTypeVariables(typeVariables: Seq[Symbol]): Unit = {
      nestingLevel += 1
      typeVariables.zipWithIndex foreach {
        case (tv, i) =>
          val newName = "existential_" + nestingLevel + "_" + i
          renameTo(tv) = newName
      }
    }
    def renaming(symbol: Symbol): Option[String] = renameTo.get(symbol)
  }

  // call back to the xsbti.SafeLazy class in main sbt code to construct a SafeLazy instance
  //   we pass a thunk, whose class is loaded by the interface class loader (this class's loader)
  //   SafeLazy ensures that once the value is forced, the thunk is nulled out and so
  //   references to the thunk's classes are not retained.  Specifically, it allows the interface classes
  //   (those in this subproject) to be garbage collected after compilation.
  private[this] val safeLazy = Class.forName("xsbti.SafeLazy").getMethod("apply", classOf[xsbti.F0[_]])
  private def lzy[S <: AnyRef](s: => S): xsbti.api.Lazy[S] =
    {
      val z = safeLazy.invoke(null, Message(s)).asInstanceOf[xsbti.api.Lazy[S]]
      pending += z
      z
    }

  /**
   * Force all lazy structures.  This is necessary so that we see the symbols/types at this phase and
   * so that we don't hold on to compiler objects and classes
   */
  def forceStructures(): Unit =
    if (pending.isEmpty)
      structureCache.clear()
    else {
      val toProcess = pending.toList
      pending.clear()
      toProcess foreach { _.get() }
      forceStructures()
    }

  private def thisPath(sym: Symbol) = path(pathComponents(sym, Constants.thisPath :: Nil))
  private def path(components: List[PathComponent]) = new xsbti.api.Path(components.toArray[PathComponent])
  private def pathComponents(sym: Symbol, postfix: List[PathComponent]): List[PathComponent] =
    {
      if (sym == NoSymbol || sym.isRoot || sym.isEmptyPackageClass || sym.isRootPackage) postfix
      else pathComponents(sym.owner, new xsbti.api.Id(simpleName(sym)) :: postfix)
    }
  private def simpleType(in: Symbol, t: Type): SimpleType =
    processType(in, t) match {
      case s: SimpleType => s
      case x             => log("Not a simple type:\n\tType: " + t + " (" + t.getClass + ")\n\tTransformed: " + x.getClass); Constants.emptyType
    }
  private def types(in: Symbol, t: List[Type]): Array[xsbti.api.Type] = t.toArray[Type].map(processType(in, _))
  private def projectionType(in: Symbol, pre: Type, sym: Symbol) =
    {
      if (pre == NoPrefix) {
        if (sym.isLocalClass || sym.isRoot || sym.isRootPackage) Constants.emptyType
        else if (sym.isTypeParameterOrSkolem || sym.isExistentiallyBound) reference(sym)
        else {
          // this appears to come from an existential type in an inherited member- not sure why isExistential is false here
          /*println("Warning: Unknown prefixless type: " + sym + " in " + sym.owner + " in " + sym.enclClass)
				println("\tFlags: " + sym.flags + ", istype: " + sym.isType + ", absT: " + sym.isAbstractType + ", alias: " + sym.isAliasType + ", nonclass: " + isNonClassType(sym))*/
          reference(sym)
        }
      } else if (sym.isRoot || sym.isRootPackage) Constants.emptyType
      else new xsbti.api.Projection(simpleType(in, pre), simpleName(sym))
    }
  private def reference(sym: Symbol): xsbti.api.ParameterRef = new xsbti.api.ParameterRef(tparamID(sym))

  // The compiler only pickles static annotations, so only include these in the API.
  // This way, the API is not sensitive to whether we compiled from source or loaded from classfile.
  // (When looking at the sources we see all annotations, but when loading from classes we only see the pickled (static) ones.)
  private def mkAnnotations(in: Symbol, as: List[AnnotationInfo]): Array[xsbti.api.Annotation] =
    staticAnnotations(as).toArray.map { a =>
      new xsbti.api.Annotation(processType(in, a.atp),
        if (a.assocs.isEmpty) Array(new xsbti.api.AnnotationArgument("", a.args.mkString("(", ",", ")"))) // what else to do with a Tree?
        else a.assocs.map { case (name, value) => new xsbti.api.AnnotationArgument(name.toString, value.toString) }.toArray[xsbti.api.AnnotationArgument]
      )
    }

  private def annotations(in: Symbol, s: Symbol): Array[xsbti.api.Annotation] =
    atPhase(currentRun.typerPhase) {
      val base = if (s.hasFlag(Flags.ACCESSOR)) s.accessed else NoSymbol
      val b = if (base == NoSymbol) s else base
      // annotations from bean methods are not handled because:
      //  a) they are recorded as normal source methods anyway
      //  b) there is no way to distinguish them from user-defined methods
      val associated = List(b, b.getter(b.enclClass), b.setter(b.enclClass)).filter(_ != NoSymbol)
      associated.flatMap(ss => mkAnnotations(in, ss.annotations)).distinct.toArray
    }

  private def viewer(s: Symbol) = (if (s.isModule) s.moduleClass else s).thisType
  private def printMember(label: String, in: Symbol, t: Type) = println(label + " in " + in + " : " + t + " (debug: " + debugString(t) + " )")

  private def defDef(in: Symbol, s: Symbol): List[xsbti.api.Def] =
    {
      import MirrorHelper._

      val hasValueClassAsParameter: Boolean = {
        import MirrorHelper._
        s.asMethod.paramss.flatten map (_.info) exists (t => isDerivedValueClass(t.typeSymbol))
      }

      def hasValueClassAsReturnType(tpe: Type): Boolean = tpe match {
        case PolyType(_, base) => hasValueClassAsReturnType(base)
        case MethodType(_, resultType) => hasValueClassAsReturnType(resultType)
        case Nullary(resultType) => hasValueClassAsReturnType(resultType)
        case resultType => isDerivedValueClass(resultType.typeSymbol)
      }

      val inspectPostErasure = hasValueClassAsParameter || hasValueClassAsReturnType(viewer(in).memberInfo(s))

      def build(t: Type, typeParams: Array[xsbti.api.TypeParameter], valueParameters: List[xsbti.api.ParameterList]): List[xsbti.api.Def] =
        {
          def parameterList(syms: List[Symbol], erase: Boolean = false): xsbti.api.ParameterList =
            {
              val isImplicitList = syms match { case head :: _ => isImplicit(head); case _ => false }
              new xsbti.api.ParameterList(syms.map(parameterS(erase)).toArray, isImplicitList)
            }
          t match {
            case PolyType(typeParams0, base) =>
              assert(typeParams.isEmpty)
              assert(valueParameters.isEmpty)
              build(base, typeParameters(in, typeParams0), Nil)
            case mType @ MethodType(params, resultType) =>
              // The types of a method's parameters change between phases: For instance, if a
              // parameter is a subtype of AnyVal, then it won't have the same type before and after
              // erasure. Therefore we record the type of parameters before AND after erasure to
              // make sure that we don't miss some API changes.
              //   class A(val x: Int) extends AnyVal
              //   def foo(a: A): Int = A.x <- has type (LA)I before erasure
              //                            <- has type (I)I after erasure
              // If we change A from value class to normal class, we need to recompile all clients
              // of def foo.
              val beforeErasure =
                build(resultType, typeParams, parameterList(params) :: valueParameters)
              val afterErasure  =
                if (inspectPostErasure)
                  build(resultType, typeParams, parameterList(mType.params, erase = true) :: valueParameters)
                else
                  Nil

              beforeErasure ++ afterErasure
            case Nullary(resultType) => // 2.9 and later
              build(resultType, typeParams, valueParameters)
            case returnType =>
              def makeDef(retTpe: xsbti.api.Type): xsbti.api.Def =
                new xsbti.api.Def(
                  valueParameters.reverse.toArray,
                  retTpe,
                  typeParams,
                  simpleName(s),
                  getAccess(s),
                  getModifiers(s),
                  annotations(in, s))

              // The return type of a method may change before and after erasure. Consider the
              // following method:
              //   class A(val x: Int) extends AnyVal
              //   def foo(x: Int): A = new A(x) <- has type (I)LA before erasure
              //                                 <- has type (I)I after erasure
              // If we change A from value class to normal class, we need to recompile all clients
              // of def foo.
              val beforeErasure = makeDef(processType(in, dropConst(returnType)))
              val afterErasure =
                if (inspectPostErasure) {
                  val erasedReturn = dropConst(global.transformedType(viewer(in).memberInfo(s))) map {
                    case MethodType(_, r) => r
                    case other            => other
                  }
                  List(makeDef(processType(in, erasedReturn)))
                } else Nil

              beforeErasure :: afterErasure
          }
        }
      def parameterS(erase: Boolean)(s: Symbol): xsbti.api.MethodParameter = {
        val tp = if (erase) global.transformedType(s.info) else s.info
        makeParameter(simpleName(s), tp, tp.typeSymbol, s)
      }

      // paramSym is only for 2.8 and is to determine if the parameter has a default
      def makeParameter(name: String, tpe: Type, ts: Symbol, paramSym: Symbol): xsbti.api.MethodParameter =
        {
          import xsbti.api.ParameterModifier._
          val (t, special) =
            if (ts == definitions.RepeatedParamClass) // || s == definitions.JavaRepeatedParamClass)
              (tpe.typeArgs(0), Repeated)
            else if (ts == definitions.ByNameParamClass)
              (tpe.typeArgs(0), ByName)
            else
              (tpe, Plain)
          new xsbti.api.MethodParameter(name, processType(in, t), hasDefault(paramSym), special)
        }
      val t = viewer(in).memberInfo(s)
      build(t, Array(), Nil)
    }
  private def hasDefault(s: Symbol) = s != NoSymbol && s.hasFlag(Flags.DEFAULTPARAM)
  private def fieldDef[T](in: Symbol, s: Symbol, keepConst: Boolean, create: (xsbti.api.Type, String, xsbti.api.Access, xsbti.api.Modifiers, Array[xsbti.api.Annotation]) => T): T =
    {
      val t = dropNullary(viewer(in).memberType(s))
      val t2 = if (keepConst) t else dropConst(t)
      create(processType(in, t2), simpleName(s), getAccess(s), getModifiers(s), annotations(in, s))
    }
  private def dropConst(t: Type): Type = t match {
    case ConstantType(constant) => constant.tpe
    case _                      => t
  }
  private def dropNullary(t: Type): Type = t match {
    case Nullary(un) => un
    case _           => t
  }

  private def typeDef(in: Symbol, s: Symbol): xsbti.api.TypeMember =
    {
      val (typeParams, tpe) =
        viewer(in).memberInfo(s) match {
          case PolyType(typeParams0, base) => (typeParameters(in, typeParams0), base)
          case t                           => (Array[xsbti.api.TypeParameter](), t)
        }
      val name = simpleName(s)
      val access = getAccess(s)
      val modifiers = getModifiers(s)
      val as = annotations(in, s)

      if (s.isAliasType)
        new xsbti.api.TypeAlias(processType(in, tpe), typeParams, name, access, modifiers, as)
      else if (s.isAbstractType) {
        val bounds = tpe.bounds
        new xsbti.api.TypeDeclaration(processType(in, bounds.lo), processType(in, bounds.hi), typeParams, name, access, modifiers, as)
      } else
        error("Unknown type member" + s)
    }

  private def structure(info: Type, s: Symbol): xsbti.api.Structure = structureCache.getOrElseUpdate(s, mkStructure(info, s))
  private def structureWithInherited(info: Type, s: Symbol): xsbti.api.Structure = structureCache.getOrElseUpdate(s, mkStructureWithInherited(info, s))

  private def removeConstructors(ds: List[Symbol]): List[Symbol] = ds filter { !_.isConstructor }

  /**
   * Create structure as-is, without embedding ancestors
   *
   * (for refinement types, and ClassInfoTypes encountered outside of a definition???).
   */
  private def mkStructure(info: Type, s: Symbol): xsbti.api.Structure = {
    // We're not interested in the full linearization, so we can just use `parents`,
    // which side steps issues with baseType when f-bounded existential types and refined types mix 
    // (and we get cyclic types which cause a stack overflow in showAPI).
    //
    // The old algorithm's semantics for inherited dependencies include all types occurring as a parent anywhere in a type,
    // so that, in `class C { def foo: A  }; class A extends B`, C is considered to have an "inherited dependency" on `A` and `B`!!!
    val parentTypes = if (global.callback.nameHashing()) info.parents else linearizedAncestorTypes(info)
    val decls = info.decls.toList
    val declsNoModuleCtor = if (s.isModuleClass) removeConstructors(decls) else decls
    mkStructure(s, parentTypes, declsNoModuleCtor, Nil)
  }

  /**
   * Track all ancestors and inherited members for a class's API.
   *
   * A class's hash does not include hashes for its parent classes -- only the symbolic names --
   * so we must ensure changes propagate somehow.
   *
   * TODO: can we include hashes for parent classes instead? This seems a bit messy.
   */
  private def mkStructureWithInherited(info: Type, s: Symbol): xsbti.api.Structure = {
    val ancestorTypes = linearizedAncestorTypes(info)
    val decls = info.decls.toList
    val declsNoModuleCtor = if (s.isModuleClass) removeConstructors(decls) else decls
    val declSet = decls.toSet
    val inherited = info.nonPrivateMembers.toList.filterNot(declSet) // private members are not inherited
    mkStructure(s, ancestorTypes, declsNoModuleCtor, inherited)
  }

  // Note that the ordering of classes in `baseClasses` is important.
  // It would be easier to just say `baseTypeSeq.toList.tail`,
  // but that does not take linearization into account.
  def linearizedAncestorTypes(info: Type): List[Type] = info.baseClasses.tail.map(info.baseType)

  // If true, this template is publicly visible and should be processed as a public inheritance dependency.
  // Local classes and local refinements will never be traversed by the api phase, so we don't need to check for that.
  private[this] def isPublicStructure(s: Symbol): Boolean =
    s.isStructuralRefinement ||
      // do not consider templates that are private[this] or private
      !(s.isPrivate && (s.privateWithin == NoSymbol || s.isLocal))

  private def mkStructure(s: Symbol, bases: List[Type], declared: List[Symbol], inherited: List[Symbol]): xsbti.api.Structure = {
    if (isPublicStructure(s))
      addInheritedDependencies(sourceFile, bases.map(_.dealias.typeSymbol))
    new xsbti.api.Structure(lzy(types(s, bases)), lzy(processDefinitions(s, declared)), lzy(processDefinitions(s, inherited)))
  }
  private def processDefinitions(in: Symbol, defs: List[Symbol]): Array[xsbti.api.Definition] =
    sort(defs.toArray).flatMap((d: Symbol) => definition(in, d))
  private[this] def sort(defs: Array[Symbol]): Array[Symbol] = {
    Arrays.sort(defs, sortClasses)
    defs
  }

  private def definition(in: Symbol, sym: Symbol): List[xsbti.api.Definition] =
    {
      def mkVar = List(fieldDef(in, sym, false, new xsbti.api.Var(_, _, _, _, _)))
      def mkVal = List(fieldDef(in, sym, true, new xsbti.api.Val(_, _, _, _, _)))
      if (isClass(sym))
        if (ignoreClass(sym)) Nil else List(classLike(in, sym))
      else if (sym.isNonClassType)
        List(typeDef(in, sym))
      else if (sym.isVariable)
        if (isSourceField(sym)) mkVar else Nil
      else if (sym.isStable)
        if (isSourceField(sym)) mkVal else Nil
      else if (sym.isSourceMethod && !sym.isSetter)
        if (sym.isGetter) mkVar else defDef(in, sym)
      else
        Nil
    }
  private def ignoreClass(sym: Symbol): Boolean =
    sym.isLocalClass || sym.isAnonymousClass || sym.fullName.endsWith(LocalChild.toString)

  // This filters private[this] vals/vars that were not in the original source.
  //  The getter will be used for processing instead.
  private def isSourceField(sym: Symbol): Boolean =
    {
      val getter = sym.getter(sym.enclClass)
      // the check `getter eq sym` is a precaution against infinite recursion
      // `isParamAccessor` does not exist in all supported versions of Scala, so the flag check is done directly
      (getter == NoSymbol && !sym.hasFlag(Flags.PARAMACCESSOR)) || (getter eq sym)
    }
  private def getModifiers(s: Symbol): xsbti.api.Modifiers =
    {
      import Flags._
      val absOver = s.hasFlag(ABSOVERRIDE)
      val abs = s.hasFlag(ABSTRACT) || s.hasFlag(DEFERRED) || absOver
      val over = s.hasFlag(OVERRIDE) || absOver
      new xsbti.api.Modifiers(abs, over, s.isFinal, s.hasFlag(SEALED), isImplicit(s), s.hasFlag(LAZY), hasMacro(s), s.hasFlag(SUPERACCESSOR))
    }

  private def isImplicit(s: Symbol) = s.hasFlag(Flags.IMPLICIT)
  private def getAccess(c: Symbol): xsbti.api.Access =
    {
      if (c.isPublic) Constants.public
      else if (c.isPrivateLocal) Constants.privateLocal
      else if (c.isProtectedLocal) Constants.protectedLocal
      else {
        val within = c.privateWithin
        val qualifier = if (within == NoSymbol) Constants.unqualified else new xsbti.api.IdQualifier(within.fullName)
        if (c.hasFlag(Flags.PROTECTED)) new xsbti.api.Protected(qualifier)
        else new xsbti.api.Private(qualifier)
      }
    }

  /**
   * Replace all types that directly refer to the `forbidden` symbol by `NoType`.
   * (a specialized version of substThisAndSym)
   */
  class SuppressSymbolRef(forbidden: Symbol) extends TypeMap {
    def apply(tp: Type) =
      if (tp.typeSymbolDirect == forbidden) NoType
      else mapOver(tp)
  }

  private def processType(in: Symbol, t: Type): xsbti.api.Type = typeCache.getOrElseUpdate((in, t), makeType(in, t))
  private def makeType(in: Symbol, t: Type): xsbti.api.Type =
    {

      val dealiased = t match {
        case TypeRef(_, sym, _) if sym.isAliasType => t.dealias
        case _                                     => t
      }

      dealiased match {
        case NoPrefix               => Constants.emptyType
        case ThisType(sym)          => new xsbti.api.Singleton(thisPath(sym))
        case SingleType(pre, sym)   => projectionType(in, pre, sym)
        case ConstantType(constant) => new xsbti.api.Constant(processType(in, constant.tpe), constant.stringValue)

        /* explaining the special-casing of references to refinement classes (https://support.typesafe.com/tickets/1882)
			 *
			 * goal: a representation of type references to refinement classes that's stable across compilation runs
			 *       (and thus insensitive to typing from source or unpickling from bytecode)
			 *
			 * problem: the current representation, which corresponds to the owner chain of the refinement:
			 *   1. is affected by pickling, so typing from source or using unpickled symbols give different results (because the unpickler "localizes" owners -- this could be fixed in the compiler)
			 *   2. can't distinguish multiple refinements in the same owner (this is a limitation of SBT's internal representation and cannot be fixed in the compiler)
			 *
			 * potential solutions:
			 *   - simply drop the reference: won't work as collapsing all refinement types will cause recompilation to be skipped when a refinement is changed to another refinement
			 *   - represent the symbol in the api: can't think of a stable way of referring to an anonymous symbol whose owner changes when pickled
			 *   + expand the reference to the corresponding refinement type: doing that recursively may not terminate, but we can deal with that by approximating recursive references
			 *     (all we care about is being sound for recompilation: recompile iff a dependency changes, and this will happen as long as we have one unrolling of the reference to the refinement)
			 */
        case TypeRef(pre, sym, Nil) if sym.isRefinementClass =>
          // Since we only care about detecting changes reliably, we unroll a reference to a refinement class once.
          // Recursive references are simply replaced by NoType -- changes to the type will be seen in the first unrolling.
          // The API need not be type correct, so this truncation is acceptable. Most of all, the API should be compact.
          val unrolling = pre.memberInfo(sym) // this is a refinement type

          // in case there are recursive references, suppress them -- does this ever happen?
          // we don't have a test case for this, so warn and hope we'll get a contribution for it :-)
          val withoutRecursiveRefs = new SuppressSymbolRef(sym).mapOver(unrolling)
          if (unrolling ne withoutRecursiveRefs)
            reporter.warning(sym.pos, "sbt-api: approximated refinement ref" + t + " (== " + unrolling + ") to " + withoutRecursiveRefs + "\nThis is currently untested, please report the code you were compiling.")

          structure(withoutRecursiveRefs, sym)
        case tr @ TypeRef(pre, sym, args) =>
          val base = projectionType(in, pre, sym)
          if (args.isEmpty)
            if (isRawType(tr))
              processType(in, rawToExistential(tr))
            else
              base
          else
            new xsbti.api.Parameterized(base, types(in, args))
        case SuperType(thistpe: Type, supertpe: Type) =>
          warning("sbt-api: Super type (not implemented): this=" + thistpe + ", super=" + supertpe); Constants.emptyType
        case at: AnnotatedType =>
          at.annotations match {
            case Nil    => processType(in, at.underlying)
            case annots => new xsbti.api.Annotated(processType(in, at.underlying), mkAnnotations(in, annots))
          }
        case rt: CompoundType                 => structure(rt, rt.typeSymbol)
        case t: ExistentialType               => makeExistentialType(in, t)
        case NoType                           => Constants.emptyType // this can happen when there is an error that will be reported by a later phase
        case PolyType(typeParams, resultType) => new xsbti.api.Polymorphic(processType(in, resultType), typeParameters(in, typeParams))
        case Nullary(resultType) =>
          warning("sbt-api: Unexpected nullary method type " + in + " in " + in.owner); Constants.emptyType
        case _ => warning("sbt-api: Unhandled type " + t.getClass + " : " + t); Constants.emptyType
      }
    }
  private def makeExistentialType(in: Symbol, t: ExistentialType): xsbti.api.Existential = {
    val ExistentialType(typeVariables, qualified) = t
    existentialRenamings.enterExistentialTypeVariables(typeVariables)
    try {
      val typeVariablesConverted = typeParameters(in, typeVariables)
      val qualifiedConverted = processType(in, qualified)
      new xsbti.api.Existential(qualifiedConverted, typeVariablesConverted)
    } finally {
      existentialRenamings.leaveExistentialTypeVariables(typeVariables)
    }
  }
  private def typeParameters(in: Symbol, s: Symbol): Array[xsbti.api.TypeParameter] = typeParameters(in, s.typeParams)
  private def typeParameters(in: Symbol, s: List[Symbol]): Array[xsbti.api.TypeParameter] = s.map(typeParameter(in, _)).toArray[xsbti.api.TypeParameter]
  private def typeParameter(in: Symbol, s: Symbol): xsbti.api.TypeParameter =
    {
      val varianceInt = s.variance
      import xsbti.api.Variance._
      val annots = annotations(in, s)
      val variance = if (varianceInt < 0) Contravariant else if (varianceInt > 0) Covariant else Invariant
      viewer(in).memberInfo(s) match {
        case TypeBounds(low, high)      => new xsbti.api.TypeParameter(tparamID(s), annots, typeParameters(in, s), variance, processType(in, low), processType(in, high))
        case PolyType(typeParams, base) => new xsbti.api.TypeParameter(tparamID(s), annots, typeParameters(in, typeParams), variance, processType(in, base.bounds.lo), processType(in, base.bounds.hi))
        case x                          => error("Unknown type parameter info: " + x.getClass)
      }
    }
  private def tparamID(s: Symbol): String =
    existentialRenamings.renaming(s) match {
      case Some(rename) =>
        // can't use debuglog because it doesn't exist in Scala 2.9.x
        if (settings.debug.value)
          log("Renaming existential type variable " + s.fullName + " to " + rename)
        rename
      case None =>
        s.fullName
    }


  /* Representation for the self type of a class symbol `s`, or `emptyType` for an *unascribed* self variable (or no self variable at all).
     Only the self variable's explicitly ascribed type is relevant for incremental compilation. */
  private def selfType(in: Symbol, s: Symbol): xsbti.api.Type =
    // `sym.typeOfThis` is implemented as `sym.thisSym.info`, which ensures the *self* symbol is initialized (the type completer is run).
    // We can safely avoid running the type completer for `thisSym` for *class* symbols where `thisSym == this`,
    // as that invariant is established on completing the class symbol (`mkClassLike` calls `s.initialize` before calling us).
    // Technically, we could even ignore a self type that's a supertype of the class's type,
    // as it does not contribute any information relevant outside of the class definition.
    if ((s.thisSym eq s) || (s.thisSym.tpeHK == s.tpeHK)) Constants.emptyType else processType(in, s.typeOfThis)

  def classLike(in: Symbol, c: Symbol): ClassLike = classLikeCache.getOrElseUpdate((in, c), mkClassLike(in, c))
  private def mkClassLike(in: Symbol, c: Symbol): ClassLike = {
    // Normalize to a class symbol, and initialize it.
    // (An object -- aka module -- also has a term symbol,
    //  but it's the module class that holds the info about its structure.)
    val sym = (if (c.isModule) c.moduleClass else c).initialize
    val defType =
      if (sym.isTrait) DefinitionType.Trait
      else if (sym.isModuleClass) {
        if (sym.isPackageClass) DefinitionType.PackageModule
        else DefinitionType.Module
      } else DefinitionType.ClassDef

    new xsbti.api.ClassLike(
      defType, lzy(selfType(in, sym)), lzy(structureWithInherited(viewer(in).memberInfo(sym), sym)), emptyStringArray, typeParameters(in, sym), // look at class symbol
      c.fullName, getAccess(c), getModifiers(c), annotations(in, c)) // use original symbol (which is a term symbol when `c.isModule`) for `name` and other non-classy stuff
  }

  // TODO: could we restrict ourselves to classes, ignoring the term symbol for modules,
  // since everything we need to track about a module is in the module's class (`moduleSym.moduleClass`)?
  private[this] def isClass(s: Symbol) = s.isClass || s.isModule
  // necessary to ensure a stable ordering of classes in the definitions list:
  //  modules and classes come first and are sorted by name
  // all other definitions come later and are not sorted
  private[this] val sortClasses = new Comparator[Symbol] {
    def compare(a: Symbol, b: Symbol) = {
      val aIsClass = isClass(a)
      val bIsClass = isClass(b)
      if (aIsClass == bIsClass)
        if (aIsClass)
          if (a.isModule == b.isModule)
            a.fullName.compareTo(b.fullName)
          else if (a.isModule)
            -1
          else
            1
        else
          0 // substantial performance hit if fullNames are compared here
      else if (aIsClass)
        -1
      else
        1
    }
  }
  private object Constants {
    val local = new xsbti.api.ThisQualifier
    val public = new xsbti.api.Public
    val privateLocal = new xsbti.api.Private(local)
    val protectedLocal = new xsbti.api.Protected(local)
    val unqualified = new xsbti.api.Unqualified
    val emptyPath = new xsbti.api.Path(Array())
    val thisPath = new xsbti.api.This
    val emptyType = new xsbti.api.EmptyType
  }

  private def simpleName(s: Symbol): String =
    {
      val n = s.originalName
      val n2 = if (n.toString == "<init>") n else n.decode
      n2.toString.trim
    }

  private def staticAnnotations(annotations: List[AnnotationInfo]): List[AnnotationInfo] = {
    // compat stub for 2.8/2.9
    class IsStatic(ann: AnnotationInfo) { def isStatic: Boolean = ann.atp.typeSymbol isNonBottomSubClass definitions.StaticAnnotationClass }
    implicit def compat(ann: AnnotationInfo): IsStatic = new IsStatic(ann)
    annotations.filter(_.isStatic)
  }
}

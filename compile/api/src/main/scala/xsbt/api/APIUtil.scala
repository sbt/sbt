package xsbt.api

import xsbti.SafeLazy
import xsbti.api._
import scala.collection.mutable.HashSet

object APIUtil {
  val modifiersToByte = (m: Modifiers) => {
    import m._
    def x(b: Boolean, bit: Int) = if (b) 1 << bit else 0
    (x(isAbstract, 0) | x(isOverride, 1) | x(isFinal, 2) | x(isSealed, 3) | x(isImplicit, 4) | x(isLazy, 5) | x(isMacro, 6)).toByte
  }
  val byteToModifiers = (b: Byte) => {
    def x(bit: Int) = (b & (1 << bit)) != 0
    new Modifiers(x(0), x(1), x(2), x(3), x(4), x(5), x(6), x(7))
  }

  def isScalaSourceName(name: String): Boolean = name.endsWith(".scala")

  def hasPackageObject(s: SourceAPI): Boolean =
    {
      val check = new HasPackageObject
      check.visitAPI(s)
      check.hasPackageObject
    }

  private[this] class HasPackageObject extends Visit {
    private var _hasPackageObject = false

    def hasPackageObject = _hasPackageObject

    private def isPackageObject(c: ClassLike): Boolean = {
      import xsbti.api.DefinitionType.{ Module, PackageModule }
      c.definitionType == PackageModule || (c.definitionType == Module && c.name.endsWith(".package"))
    }

    override def visitClass0(c: ClassLike): Unit = {
      _hasPackageObject ||= isPackageObject(c)
    }
  }

  def hasMacro(s: SourceAPI): Boolean =
    {
      val check = new HasMacro
      check.visitAPI(s)
      check.hasMacro
    }

  private[this] class HasMacro extends Visit {
    private var _hasMacro = false

    def hasMacro = _hasMacro

    // Don't visit inherited definitions since we consider that a class
    // that inherits a macro does not have a macro.
    override def visitStructure0(structure: Structure): Unit = {
      visitTypes(structure.parents)
      visitDefinitions(structure.declared)
    }

    override def visitModifiers(m: Modifiers): Unit = {
      _hasMacro ||= m.isMacro
      super.visitModifiers(m)
    }
  }

  def minimize(api: SourceAPI): SourceAPI =
    new SourceAPI(api.packages, minimizeDefinitions(api.definitions))
  def minimizeDefinitions(ds: Array[Definition]): Array[Definition] =
    ds flatMap minimizeDefinition
  def minimizeDefinition(d: Definition): Array[Definition] =
    d match {
      case c: ClassLike => Array(minimizeClass(c))
      case _            => Array()
    }
  def minimizeClass(c: ClassLike): ClassLike =
    {
      val savedAnnotations = Discovery.defAnnotations(c.structure, (_: Any) => true).toArray[String]
      val struct = minimizeStructure(c.structure, c.definitionType == DefinitionType.Module)
      new ClassLike(c.definitionType, lzy(emptyType), lzy(struct), savedAnnotations, c.typeParameters, c.name, c.access, c.modifiers, c.annotations)
    }

  def minimizeStructure(s: Structure, isModule: Boolean): Structure =
    new Structure(lzy(s.parents), filterDefinitions(s.declared, isModule), filterDefinitions(s.inherited, isModule))
  def filterDefinitions(ds: Array[Definition], isModule: Boolean): Lazy[Array[Definition]] =
    lzy(if (isModule) ds filter Discovery.isMainMethod else Array())

  def isNonPrivate(d: Definition): Boolean = isNonPrivate(d.access)
  /** Returns false if the `access` is `Private` and qualified, true otherwise.*/
  def isNonPrivate(access: Access): Boolean =
    access match {
      case p: Private if !p.qualifier.isInstanceOf[IdQualifier] => false
      case _ => true
    }

  private[this] def lzy[T <: AnyRef](t: T): Lazy[T] = SafeLazy.strict(t)

  private[this] val emptyType = new EmptyType
}

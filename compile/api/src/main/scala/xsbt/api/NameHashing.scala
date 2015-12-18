package xsbt.api

import xsbti.api.SourceAPI
import xsbti.api.Definition
import xsbti.api.DefinitionType
import xsbti.api.ClassLike
import xsbti.api._internalOnly_NameHash
import xsbti.api._internalOnly_NameHashes
import xsbti.api.DefinitionType.ClassDef
import xsbti.api.DefinitionType.Module
import xsbti.api.DefinitionType.PackageModule
import xsbti.api.DefinitionType.Trait

/**
 * A class that computes hashes for each group of definitions grouped by a simple name.
 *
 * See `nameHashes` method for details.
 */
class NameHashing {

  import NameHashing._

  /**
   * This method takes an API representation and extracts a flat collection of all
   * definitions contained in that API representation. Then it groups definition
   * by a simple name. Lastly, it computes a hash sum of all definitions in a single
   * group.
   *
   * NOTE: The hashing sum used for hashing a group of definition is insensitive
   * to order of definitions.
   */
  def nameHashes(source: SourceAPI): _internalOnly_NameHashes = {
    val apiPublicDefs = publicDefs(source)
    val (regularDefs, implicitDefs) = apiPublicDefs.partition(locDef => !locDef.definition.modifiers.isImplicit)
    val regularNameHashes = nameHashesForLocatedDefinitions(regularDefs)
    val implicitNameHashes = nameHashesForLocatedDefinitions(implicitDefs)
    new _internalOnly_NameHashes(regularNameHashes.toArray, implicitNameHashes.toArray)
  }

  private def nameHashesForLocatedDefinitions(locatedDefs: Iterable[LocatedDefinition]): Iterable[_internalOnly_NameHash] = {
    val groupedBySimpleName = locatedDefs.groupBy(locatedDef => localName(locatedDef.definition.name))
    val hashes = groupedBySimpleName.mapValues(hashLocatedDefinitions)
    hashes.toIterable.map({ case (name: String, hash: Int) => new _internalOnly_NameHash(name, hash) })
  }

  private def hashLocatedDefinitions(locatedDefs: Iterable[LocatedDefinition]): Int = {
    val defsWithExtraHashes = locatedDefs.toSeq.map(ld => ld.definition -> ld.location.hashCode)
    xsbt.api.HashAPI.hashDefinitionsWithExtraHashes(defsWithExtraHashes)
  }

  /**
   * A visitor that visits given API object and extracts all nested public
   * definitions it finds. The extracted definitions have Location attached
   * to them which identifies API object's location.
   *
   * The returned location is basically a path to a definition that contains
   * the located definition. For example, if we have:
   *
   * object Foo {
   *   class Bar { def abc: Int }
   * }
   *
   * then location of `abc` is Seq((TermName, Foo), (TypeName, Bar))
   */
  private class ExtractPublicDefinitions extends Visit {
    val locatedDefs = scala.collection.mutable.Buffer[LocatedDefinition]()
    private var currentLocation: Location = Location()
    override def visitAPI(s: SourceAPI): Unit = {
      s.packages foreach visitPackage
      s.definitions foreach {
        case topLevelDef: ClassLike =>
          val packageName = {
            val fullName = topLevelDef.name()
            val lastDotIndex = fullName.lastIndexOf('.')
            if (lastDotIndex <= 0) "" else fullName.substring(0, lastDotIndex - 1)
          }
          currentLocation = packageAsLocation(packageName)
          visitDefinition(topLevelDef)
      }
    }
    // if the definition is private, we do not visit because we do
    // not want to include any private members or its children
    override def visitDefinition(d: Definition): Unit = if (APIUtil.isNonPrivate(d)) {
      val locatedDef = LocatedDefinition(currentLocation, d)
      locatedDefs += locatedDef
      d match {
        case cl: xsbti.api.ClassLike =>
          val savedLocation = currentLocation
          currentLocation = classLikeAsLocation(currentLocation, cl)
          super.visitDefinition(d)
          currentLocation = savedLocation
        case _ =>
          super.visitDefinition(d)
      }
    }
  }

  private def publicDefs(source: SourceAPI): Iterable[LocatedDefinition] = {
    val visitor = new ExtractPublicDefinitions
    visitor.visitAPI(source)
    visitor.locatedDefs
  }

  private def localName(name: String): String = {
    // when there's no dot in name `lastIndexOf` returns -1 so we handle
    // that case properly
    val index = name.lastIndexOf('.') + 1
    name.substring(index)
  }

  private def packageAsLocation(pkg: String): Location = if (pkg != "") {
    val selectors = pkg.split('.').map(name => Selector(name, TermName)).toSeq
    Location(selectors: _*)
  } else Location.Empty

  private def classLikeAsLocation(prefix: Location, cl: ClassLike): Location = {
    val selector = {
      val clNameType = NameType(cl.definitionType)
      Selector(localName(cl.name), clNameType)
    }
    Location((prefix.selectors :+ selector): _*)
  }
}

object NameHashing {
  private case class LocatedDefinition(location: Location, definition: Definition)
  /**
   * Location is expressed as sequence of annotated names. The annotation denotes
   * a type of a name, i.e. whether it's a term name or type name.
   *
   * Using Scala compiler terminology, location is defined as a sequence of member
   * selections that uniquely identify a given Symbol.
   */
  private case class Location(selectors: Selector*)
  private object Location {
    val Empty = Location(Seq.empty: _*)
  }
  private case class Selector(name: String, nameType: NameType)
  private sealed trait NameType
  private object NameType {
    import DefinitionType._
    def apply(dt: DefinitionType): NameType = dt match {
      case Trait | ClassDef       => TypeName
      case Module | PackageModule => TermName
    }
  }
  private case object TermName extends NameType
  private case object TypeName extends NameType
}

package sbt.internal.librarymanagement
package mavenint

import scala.collection.immutable.ArraySeq
import java.util.Properties
import java.util.regex.Pattern

import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.util.extendable.ExtendableItem

/**
 * This class contains all the logic for dealing with the extra attributes in pom files relating to extra attributes
 * on dependency declarations.
 *
 * Specifically, if we have a dependency on an sbt plugin, there are two properties that need to propogate:
 * - `sbtVersion`
 * - `scalaVersion`
 *
 * These need to exist on the *dependency declaration*.  Maven/Aether has no way to inject these into
 * the <dependency> section of pom files, so we use Ivy's Extra attribute hackery to inject a lookup table
 * of extra attributes by dependency id into POM files and later we read these back.
 */
object PomExtraDependencyAttributes {

  val ExtraAttributesKey = "extraDependencyAttributes"
  val SbtVersionKey = "sbtVersion"
  val ScalaVersionKey = "scalaVersion"

  /**
   * Reads the extra dependency attributes out of a maven property.
   * @param props  The properties from an Aether resolution.
   * @return
   *         A map of module id to extra dependency attributes associated with dependencies on that module.
   */
  def readFromAether(
      props: java.util.Map[String, AnyRef]
  ): Map[ModuleRevisionId, Map[String, String]] = {
    import scala.jdk.CollectionConverters._
    (props.asScala get ExtraAttributesKey) match {
      case None => Map.empty
      case Some(str) =>
        def processDep(m: ModuleRevisionId) = (simplify(m), filterCustomExtra(m, include = true))
        (for {
          (id, props) <- readDependencyExtra(str.toString).map(processDep)
        } yield id -> props).toMap
    }
  }

  /**
   * Mutates the to collection with the extra depdendency attributes from the incoming pom properties list.
   *
   * @param from The properties directly off a maven POM file
   * @param to  The aaether properties where we can write whatever we want.
   *
   * TODO - maybe we can just parse this directly here.  Note the `readFromAether` method uses
   *        whatever we set here.
   */
  def transferDependencyExtraAttributes(
      from: Properties,
      to: java.util.Map[String, AnyRef]
  ): Unit =
    Option(from.getProperty(ExtraAttributesKey, null)) foreach (to.put(ExtraAttributesKey, _))

  /**
   * Reads the extra dependency information out of Ivy's notion of POM properties and returns
   * the map of ID -> Extra Properties.
   */
  def getDependencyExtra(m: Map[String, String]): Map[ModuleRevisionId, Map[String, String]] =
    (m get ExtraAttributesKey) match {
      case None => Map.empty
      case Some(str) =>
        def processDep(m: ModuleRevisionId) = (simplify(m), filterCustomExtra(m, include = true))
        readDependencyExtra(str).map(processDep).toMap
    }

  def qualifiedExtra(item: ExtendableItem): Map[String, String] = {
    import scala.jdk.CollectionConverters._
    item.getQualifiedExtraAttributes.asInstanceOf[java.util.Map[String, String]].asScala.toMap
  }
  def filterCustomExtra(item: ExtendableItem, include: Boolean): Map[String, String] =
    qualifiedExtra(item).view.filterKeys { k => qualifiedIsExtra(k) == include }.toMap

  def qualifiedIsExtra(k: String): Boolean =
    k.endsWith(ScalaVersionKey) || k.endsWith(SbtVersionKey)

  // Reduces the id to exclude custom extra attributes
  // This makes the id suitable as a key to associate a dependency parsed from a <dependency> element
  //  with the extra attributes from the <properties> section
  def simplify(id: ModuleRevisionId): ModuleRevisionId = {
    import scala.jdk.CollectionConverters._
    ModuleRevisionId.newInstance(
      id.getOrganisation,
      id.getName,
      id.getBranch,
      id.getRevision,
      filterCustomExtra(id, include = false).asJava
    )
  }

  /** parses the sequence of dependencies with extra attribute information, with one dependency per line */
  def readDependencyExtra(s: String): Seq[ModuleRevisionId] = ArraySeq.unsafeWrapArray(
    LinesP.split(s).map(_.trim).filter(!_.isEmpty).map(ModuleRevisionId.decode)
  )

  private[this] val LinesP = Pattern.compile("(?m)^")

  /**
   * Creates the "extra" property values for DependencyDescriptors that can be written into a maven pom
   * so  we don't lose the information.
   * @param s
   * @return
   */
  def writeDependencyExtra(s: Seq[DependencyDescriptor]): Seq[String] =
    s.flatMap { dd =>
      val revId = dd.getDependencyRevisionId
      val filteredExtra = filterCustomExtra(revId, include = true)
      if (filteredExtra.isEmpty)
        Nil
      else {
        import scala.collection.JavaConverters._
        val revId0 = ModuleRevisionId.newInstance(
          revId.getOrganisation,
          revId.getName,
          revId.getBranch,
          revId.getRevision,
          filteredExtra.asJava
        )
        revId0.encodeToString :: Nil
      }
    }

}

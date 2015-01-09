package org.apache.maven.repository.internal

import java.util.Properties

import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.util.extendable.ExtendableItem
import sbt.CustomPomParser

object PomExtraDependencyAttributes {
  // TODO - Move custom pom parser extra attribtues code into this class, rather than relying on
  // custom pom parser (so we can deprecate it completely).
  import CustomPomParser.{ ExtraAttributesKey, simplify, filterCustomExtra, readDependencyExtra }

  /**
   * Reads the extra dependency attributes out of a maven property.
   * @param props  The properties from an Aether resolution.
   * @return
   *         A map of module id to extra dependency attributes associated with dependencies on that module.
   */
  def readFromAether(props: java.util.Map[String, AnyRef]): Map[ModuleRevisionId, Map[String, String]] = {
    import collection.JavaConverters._
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
  def transferDependencyExtraAttributes(from: Properties, to: java.util.Map[String, AnyRef]): Unit = {
    Option(from.getProperty(ExtraAttributesKey, null)) match {
      case Some(str) => to.put(ExtraAttributesKey, str)
      case None      =>
    }
  }
}

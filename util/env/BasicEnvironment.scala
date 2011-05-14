/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah, David MacIver
 */
package sbt

import scala.reflect.Manifest
import java.io.File
import scala.collection.Map

trait BasicEnvironment extends Environment
{
	protected def log: Logger
	/** The location of the properties file that backs the user-defined properties. */
	def envBackingPath: File
	/** The environment from which user-defined properties inherit (if enabled). */
	protected def parentEnvironment: Option[BasicEnvironment] = None
	/** The identifier used in messages to refer to this environment. */
	def environmentLabel = envBackingPath.getAbsolutePath

	private[this] var isModified = false
	private[sbt] def setEnvironmentModified(modified: Boolean) { synchronized { isModified = modified } }
	private[this] def isEnvironmentModified = synchronized { isModified }


	implicit val IntFormat: Format[Int] = new SimpleFormat[Int] { def fromString(s: String) = java.lang.Integer.parseInt(s) }
	implicit val LongFormat: Format[Long] = new SimpleFormat[Long] { def fromString(s: String) = java.lang.Long.parseLong(s) }
	implicit val DoubleFormat: Format[Double] = new SimpleFormat[Double] { def fromString(s: String) = java.lang.Double.parseDouble(s) }
	implicit val BooleanFormat: Format[Boolean] = new SimpleFormat[Boolean] { def fromString(s: String) = java.lang.Boolean.valueOf(s).booleanValue }
	implicit val StringFormat: Format[String] = Format.string
	val NonEmptyStringFormat: Format[String] = new SimpleFormat[String]
	{
		def fromString(s: String) =
		{
			val trimmed = s.trim
			if(trimmed.isEmpty)
				error("The empty string is not allowed.")
			trimmed
		}
	}
	implicit val VersionFormat: Format[Version] =
		new SimpleFormat[Version]
		{
			def fromString(s: String) = Version.fromString(s).fold(msg => error(msg), x => x)
		}
	implicit val FileFormat = Format.file


	/** Implementation of 'Property' for user-defined properties. */
	private[sbt] class UserProperty[T](lazyDefaultValue: => Option[T], format: Format[T], inheritEnabled: Boolean,
		inheritFirst: Boolean, private[BasicEnvironment] val manifest: Manifest[T]) extends Property[T]
	{
		/** The name of this property is used for persistence in the properties file and as an identifier in messages.*/
		lazy val name = propertyMap.find( p => p._2 eq this ).map(_._1)
		/** Gets the name of this property or an alternative if the name is not available.*/
		private def nameString = name.getOrElse("<unnamed>")
		/** The lazily evaluated default value for this property.*/
		private lazy val defaultValue = lazyDefaultValue
		/** The explicitly set value for this property.*/
		private[BasicEnvironment] var explicitValue =
		{
			def initialValue = for(n <- name; stringValue <- initialValues.get(n)) yield format.fromString(stringValue)
			new LazyVar[Option[T]](initialValue) // ensure propertyMap is initialized before a read occurs
		}
		def update(v: T): Unit = synchronized { explicitValue() = Some(v); setEnvironmentModified(true) }
		def resolve: PropertyResolution[T] =
			synchronized
			{
				if(inheritFirst) resolveInheritFirst
				else resolveDefaultFirst
			}
		private def resolveInheritFirst =
			explicitValue() match
			{
				case Some(v) => DefinedValue(v, false, false)
				case None =>
					val inherited = inheritedValue
					 // note that the following means the default value will not be used if an exception occurs inheriting
					inherited orElse getDefault(inherited)
			}
		private def resolveDefaultFirst =
			explicitValue() match
			{
				case Some(v) => DefinedValue(v, false, false)
				case None => getDefault(inheritedValue)
			}
		private def getDefault(orElse: => PropertyResolution[T]): PropertyResolution[T] =
			try
			{
				defaultValue match
				{
					case Some(v) => DefinedValue(v, false, true)
					case None => orElse
				}
			} catch { case e: Exception =>
				ResolutionException("Error while evaluating default value for property", Some(e))
			}

		private def inheritedValue: PropertyResolution[T] =
		{
			val propOption = if(inheritEnabled) parentProperty else None
			propOption match
			{
				case Some(prop) => tryToInherit(prop)
				case None => UndefinedValue(nameString, environmentLabel)
			}
		}
		private def parentProperty = for(parent <- parentEnvironment; n <- name; prop <- parent.propertyMap.get(n)) yield prop

		private def tryToInherit[R](prop: BasicEnvironment#UserProperty[R]): PropertyResolution[T] =
		{
			if(prop.manifest <:< manifest)
				markInherited(prop.resolve.asInstanceOf[PropertyResolution[T]])
			else
				ResolutionException("Could not inherit property '" + nameString + "' from '" + environmentLabel + "':\n" +
					"\t Property had type " + prop.manifest + ", expected type " + manifest, None)
		}
		private def markInherited(result: PropertyResolution[T]) =
			result match
			{
				case DefinedValue(v, isInherited, isDefault) => DefinedValue(v, true, isDefault)
				case x => x
			}

		override def toString = nameString + "=" + resolve

		/** Gets the explicitly set value converted to a 'String'.*/
		private[sbt] def getStringValue: Option[String] = explicitValue().map(format.toString)
		/** Explicitly sets the value for this property by converting the given string value.*/
		private[sbt] def setStringValue(s: String) { update(format.fromString(s)) }
	}
	/** Implementation of 'Property' for system properties (i.e. System.getProperty/setProperty) */
	private class SystemProperty[T](val name: String, lazyDefaultValue: => Option[T], val format: Format[T]) extends Property[T]
	{
		def resolve =
		{
			val rawValue = System.getProperty(name)
			if(rawValue == null)
				notFound
			else
				try
					DefinedValue(format.fromString(rawValue), false, false)
				catch {
					case e: Exception => ResolutionException("Error parsing system property '" + name + "': " + e.toString, Some(e))
				}
		}
		/** Handles resolution when the property has no explicit value.  If there is a default value, that is returned,
		* otherwise, UndefinedValue is returned.*/
		private def notFound =
		{
			defaultValue match
			{
				case Some(dv) =>
				{
					log.debug("System property '" + name + "' does not exist, using provided default.")
					DefinedValue(dv, false, true)
				}
				case None => UndefinedValue(name, environmentLabel)
			}
		}
		protected lazy val defaultValue = lazyDefaultValue
		def update(t: T)
		{
			try System.setProperty(name, format.toString(t))
			catch {
				case e: Exception =>
					log.trace(e)
					log.warn("Error setting system property '" + name + "': " + e.toString)
			}
		}
		override def toString = name + "=" + resolve
	}

	def system[T](propertyName: String)(implicit format: Format[T]): Property[T] =
		new SystemProperty[T](propertyName, None, format)
	def systemOptional[T](propertyName: String, defaultValue: => T)(implicit format: Format[T]): Property[T] =
		new SystemProperty[T](propertyName, Some(defaultValue), format)

	def property[T](implicit manifest: Manifest[T], format: Format[T]): Property[T] =
		new UserProperty[T](None, format, true, false, manifest)
	def propertyLocal[T](implicit manifest: Manifest[T], format: Format[T]): Property[T] =
		new UserProperty[T](None, format, false, false, manifest)
	def propertyOptional[T](defaultValue: => T)(implicit manifest: Manifest[T], format: Format[T]): Property[T] =
		propertyOptional(defaultValue, false)(manifest, format)
	def propertyOptional[T](defaultValue: => T, inheritFirst: Boolean)(implicit manifest: Manifest[T], format: Format[T]): Property[T] =
		new UserProperty[T](Some(defaultValue), format, true, inheritFirst, manifest)

	private type AnyUserProperty = UserProperty[_]
	/** Maps property name to property.  The map is constructed by reflecting vals defined on this object,
	* so it should not be referenced during initialization or else subclass properties will be missed.**/
	private lazy val propertyMap: Map[String, AnyUserProperty] =
	{
		log.debug("Discovering properties")
		val propertyMap = new scala.collection.mutable.HashMap[String, AnyUserProperty]
		// AnyProperty is required because the return type of the property*[T] methods is Property[T]
		// and so the vals we are looking for have type Property[T] and not UserProperty[T]
		// We then only keep instances of UserProperty
		val vals = Environment.reflectiveMappings(this, classOf[Property[_]])
		for( (name, property: AnyUserProperty) <- vals)
			propertyMap(name) = property
		propertyMap //.readOnly (not currently in 2.8)
	}
	private val initialValues: Map[String, String] = MapIO.readStrings(environmentLabel, envBackingPath)

	def propertyNames: Iterable[String] = propertyMap.keys.toList
	def getPropertyNamed(name: String): Option[UserProperty[_]] = propertyMap.get(name)
	def propertyNamed(name: String): UserProperty[_] = propertyMap(name)
	def saveEnvironment()
	{
		if(isEnvironmentModified)
		{
			val properties = new java.util.Properties
			for( (name, variable) <- propertyMap; stringValue <- variable.getStringValue)
				properties.setProperty(name, stringValue)
			IO.write(properties, "Project properties", envBackingPath)
			setEnvironmentModified(false)
		}
	}
	private[sbt] def uninitializedProperties: Iterable[(String, Property[_])] = propertyMap.filter(_._2.get.isEmpty)
}
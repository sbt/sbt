/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
 */
package sbt

import scala.reflect.Manifest

trait Environment
{
	abstract class Property[T]
	{
		/** Explicitly sets the value of this property to 'v'.*/
		def update(v: T): Unit
		/** Returns the current value of this property or throws an exception if the value could not be obtained.*/
		def value: T = resolve.value
		/** Returns the current value of this property in an 'Option'.  'None' is used to indicate that the
		* value could not obtained.*/
		def get: Option[T] = resolve.toOption
		/** Returns full information about this property's current value. */
		def resolve: PropertyResolution[T]

		def foreach(f: T => Unit): Unit = resolve.foreach(f)
	}

	/** Creates a system property with the given name and no default value.*/
	def system[T](propName: String)(implicit format: Format[T]): Property[T]
	/** Creates a system property with the given name and the given default value to use if no value is explicitly specified.*/
	def systemOptional[T](propName: String, defaultValue: => T)(implicit format: Format[T]): Property[T]
	/** Creates a user-defined property that has no default value.  The property will try to inherit its value
	* from a parent environment (if one exists) if its value is not explicitly specified.  An explicitly specified
	* value will persist between builds if the object returned by this method is assigned to a 'val' in this
	* 'Environment'.*/
	def property[T](implicit manifest: Manifest[T], format: Format[T]): Property[T]
	/** Creates a user-defined property that has no default value.  The property will try to inherit its value
	* from a parent environment (if one exists) if its value is not explicitly specified.  An explicitly specified
	* value will persist between builds if the object returned by this method is assigned to a 'val' in this
	* 'Environment'.  The given 'format' is used to convert an instance of 'T' to and from the 'String' representation
	* used for persistence.*/
	def propertyF[T](format: Format[T])(implicit manifest: Manifest[T]): Property[T] = property(manifest, format)
	/** Creates a user-defined property with no default value and no value inheritance from a parent environment.
	* Its value will persist between builds if the returned object is assigned to a 'val' in this 'Environment'.*/
	def propertyLocal[T](implicit manifest: Manifest[T], format: Format[T]): Property[T]
	/** Creates a user-defined property with no default value and no value inheritance from a parent environment.
	* The property's value will persist between builds if the object returned by this method is assigned to a
	* 'val' in this 'Environment'.  The given 'format' is used to convert an instance of 'T' to and from the
	* 'String' representation used for persistence.*/
	def propertyLocalF[T](format: Format[T])(implicit manifest: Manifest[T]): Property[T] = propertyLocal(manifest, format)
	/** Creates a user-defined property that uses the given default value if no value is explicitly specified for this property.  The property's value will persist between builds
	* if the object returned by this method is assigned to a 'val' in this 'Environment'.*/
	def propertyOptional[T](defaultValue: => T)(implicit manifest: Manifest[T], format: Format[T]): Property[T]
	/** Creates a user-defined property with no value inheritance from a parent environment but with the given default
	* value if no value is explicitly specified for this property.  The property's value will persist between builds
	* if the object returned by this method is assigned to a 'val' in this 'Environment'.  The given 'format' is used
	* to convert an instance of 'T' to and from the 'String' representation used for persistence.*/
	def propertyOptionalF[T](defaultValue: => T, format: Format[T])(implicit manifest: Manifest[T]): Property[T] =
		propertyOptional(defaultValue)(manifest, format)
}

private object Environment
{
	def reflectiveMappings[T](obj: AnyRef, clazz: Class[T]): Map[String, T] =
	{
		var mappings = Map[String, T]()
		for ((name, value) <- ReflectUtilities.allValsC(obj, clazz))
			mappings = mappings.updated(ReflectUtilities.transformCamelCase(name, '.'), value)
		mappings
	}
}

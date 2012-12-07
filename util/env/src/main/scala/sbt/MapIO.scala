/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */

package sbt

import java.util.Properties
import java.io.File
import scala.collection.mutable.{HashMap, HashSet, Map, Set}

object MapIO
{
	def write[Key, Value](map: Map[Key, Value], label: String, to: File)(implicit keyFormat: Format[Key], valueFormat: Format[Value])
	{
		val properties = new Properties
		map foreach { pair => properties.setProperty(keyFormat.toString(pair._1), valueFormat.toString(pair._2)) }
		IO.write(properties, label, to)
	}
	def read[Key, Value](map: Map[Key, Value], from: File)(implicit keyFormat: Format[Key], valueFormat: Format[Value])
	{
		map.clear
		val properties = new Properties
		IO.load(properties, from)
		
		import collection.JavaConversions._
		for(n <- properties.propertyNames)
		{
			val name = n.toString // convert _ to String
			map.put( keyFormat.fromString(name), valueFormat.fromString(properties.getProperty(name)))
		}
	}
	def readStrings(label: String, envBackingPath: File): scala.collection.immutable.Map[String, String] =
	{
		val map = new HashMap[String, String]
		read(map, envBackingPath)
		map.toMap
	}
	def all[Key, Value](map: Map[Key, Set[Value]]): Iterable[Value] =
		map.values.toList.flatMap(set => set.toList)
	
	def add[Key, Value](key: Key, value: Value, map: Map[Key, Set[Value]]): Unit =
		map.getOrElseUpdate(key, new HashSet[Value]) + value
}
/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */

package sbt.impl

import java.util.Properties
import java.io.{File, FileInputStream, FileOutputStream, InputStream, OutputStream}
import scala.collection.mutable.{HashMap, HashSet, ListBuffer, Map, Set}

private[sbt] object PropertiesUtilities
{
	def write(properties: Properties, label: String, to: Path, log: Logger) =
		FileUtilities.writeStream(to.asFile, log)(output => { properties.store(output, label); None })
	def load(properties: Properties, from: Path, log: Logger): Option[String] =
	{
		val file = from.asFile
		if(file.exists)
			FileUtilities.readStream(file, log)( input => { properties.load(input); None })
		else
			None
	}
	def propertyNames(properties: Properties): Iterable[String] =
		wrap.Wrappers.toList(properties.propertyNames).map(_.toString)
}

private[sbt] object MapUtilities
{
	def write[Key, Value](map: Map[Key, Value], label: String, to: Path, log: Logger)(implicit keyFormat: Format[Key], valueFormat: Format[Value]): Option[String] =
	{
		val properties = new Properties
		map foreach { pair => properties.setProperty(keyFormat.toString(pair._1), valueFormat.toString(pair._2)) }
		PropertiesUtilities.write(properties, label, to, log)
	}
	def read[Key, Value](map: Map[Key, Value], from: Path, log: Logger)(implicit keyFormat: Format[Key], valueFormat: Format[Value]): Option[String] =
	{
		map.clear
		val properties = new Properties
		PropertiesUtilities.load(properties, from, log) orElse
		{
			for(name <- PropertiesUtilities.propertyNames(properties))
				map.put( keyFormat.fromString(name), valueFormat.fromString(properties.getProperty(name)))
			None
		}
	}
	def all[Key, Value](map: Map[Key, Set[Value]]): Iterable[Value] =
		map.values.toList.flatMap(set => set.toList)
	
	def readOnlyIterable[Key, Value](i: Map[Key, Set[Value]]): Iterable[(Key, scala.collection.Set[Value])] =
		for( (key, set) <- i.elements.toList) yield (key, wrap.Wrappers.readOnly(set))//.readOnly)
		
	def mark[Key, Value](source: Key, map: Map[Key, Set[Value]])
	{
		if(!map.contains(source))
			map.put(source, new HashSet[Value])
	}
	def add[Key, Value](key: Key, value: Value, map: Map[Key, Set[Value]]): Unit =
		map.getOrElseUpdate(key, new HashSet[Value]) + value
}
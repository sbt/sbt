/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package xsbt.boot

import Pre._
import java.io.{ File, FileInputStream, FileOutputStream }
import java.util.{ Locale, Properties }
import scala.collection.immutable.List

object Initialize {
  lazy val selectCreate = (_: AppProperty).create
  lazy val selectQuick = (_: AppProperty).quick
  lazy val selectFill = (_: AppProperty).fill
  def create(file: File, promptCreate: String, enableQuick: Boolean, spec: List[AppProperty]) {
    readLine(promptCreate + " (y/N" + (if (enableQuick) "/s" else "") + ") ") match {
      case None => declined("")
      case Some(line) =>
        line.toLowerCase(Locale.ENGLISH) match {
          case "y" | "yes"     => process(file, spec, selectCreate)
          case "s"             => process(file, spec, selectQuick)
          case "n" | "no" | "" => declined("")
          case x =>
            System.out.println("  '" + x + "' not understood.")
            create(file, promptCreate, enableQuick, spec)
        }
    }
  }
  def fill(file: File, spec: List[AppProperty]): Unit = process(file, spec, selectFill)
  def process(file: File, appProperties: List[AppProperty], select: AppProperty => Option[PropertyInit]) {
    val properties = readProperties(file)
    val uninitialized =
      for (property <- appProperties; init <- select(property) if properties.getProperty(property.name) == null) yield initialize(properties, property.name, init)
    if (!uninitialized.isEmpty) writeProperties(properties, file, "")
  }
  def initialize(properties: Properties, name: String, init: PropertyInit) {
    init match {
      case set: SetProperty => properties.setProperty(name, set.value)
      case prompt: PromptProperty =>
        def noValue = declined("No value provided for " + prompt.label)
        readLine(prompt.label + prompt.default.toList.map(" [" + _ + "]").mkString + ": ") match {
          case None => noValue
          case Some(line) =>
            val value = if (isEmpty(line)) orElse(prompt.default, noValue) else line
            properties.setProperty(name, value)
        }
    }
  }
}

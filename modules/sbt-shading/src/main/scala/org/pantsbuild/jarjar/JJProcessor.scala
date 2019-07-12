package org.pantsbuild.jarjar

// from https://github.com/sbt/sbt-assembly/blob/17786404117889e5a8225c97b9b7639160fb91e8/src/main/scala/org/pantsbuild/jarjar/JJProcessor.scala

import org.pantsbuild.jarjar.util.{EntryStruct, JarProcessor}

import scala.collection.JavaConverters._

class JJProcessor(val proc: JarProcessor) {

  def process(entry: EntryStruct): Boolean = proc.process(entry)

  def getExcludes(): Set[String] = {
    val field = proc.getClass().getDeclaredField("kp")
    field.setAccessible(true)
    val keepProcessor = field.get(proc)

    if (keepProcessor == null) Set()
    else {
      val method = proc.getClass().getDeclaredMethod("getExcludes")
      method.setAccessible(true)
      method.invoke(proc).asInstanceOf[java.util.Set[String]].asScala.toSet
    }
  }

}

object JJProcessor {

  def apply(patterns: Seq[PatternElement], verbose: Boolean, skipManifest: Boolean): JJProcessor =
    new JJProcessor(new MainProcessor(patterns.asJava, verbose, skipManifest))

}

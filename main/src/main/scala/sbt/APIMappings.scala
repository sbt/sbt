package sbt

import java.io.File
import java.net.{ MalformedURLException, URL }

import sbt.mavenint.SbtPomExtraProperties

private[sbt] object APIMappings {
  def extractScala(cp: Seq[Attributed[File]], log: Logger): Seq[(File, URL)] =
    cp.flatMap(entry => extractScalaFromEntry(entry, log))
  def extractJava(cp: Seq[Attributed[File]], log: Logger): Seq[(File, URL)] =
    cp.flatMap(entry => extractJavaFromEntry(entry, log))

  def extractScalaFromEntry(entry: Attributed[File], log: Logger): Option[(File, URL)] =
    entry.get(Keys.scalaEntryApiURL) match {
      case Some(u) => Some((entry.data, u))
      case None    => entry.get(Keys.moduleID.key).flatMap { mid =>
        extractFromID(entry.data, mid, SbtPomExtraProperties.POM_SCALA_API_KEY, log) match {
          // Try legacy key as fallback for backward compatibility
          case None => extractFromID(entry.data, mid, SbtPomExtraProperties.POM_API_KEY, log)
          case x    => x
        }
      }
    }

  def extractJavaFromEntry(entry: Attributed[File], log: Logger): Option[(File, URL)] =
    entry.get(Keys.javaEntryApiURL) match {
      case Some(u) => Some((entry.data, u))
      case None    => entry.get(Keys.moduleID.key).flatMap { mid =>
        extractFromID(entry.data, mid, SbtPomExtraProperties.POM_JAVA_API_KEY, log)
      }
    }

  private[this] def extractFromID(entry: File, mid: ModuleID, propKey: String, log: Logger): Option[(File, URL)] =
    for {
      urlString <- mid.extraAttributes.get(propKey)
      u <- parseURL(urlString, entry, log)
    } yield (entry, u)

  private[this] def parseURL(s: String, forEntry: File, log: Logger): Option[URL] =
    try Some(new URL(s)) catch {
      case e: MalformedURLException =>
        log.warn(s"Invalid API base URL '$s' for classpath entry '$forEntry': ${e.toString}")
        None
    }

  def store[T](attr: Attributed[T], scalaEntry: Option[URL], javaEntry: Option[URL]): Attributed[T] =
    storeURL(storeURL(attr, Keys.scalaEntryApiURL, scalaEntry), Keys.javaEntryApiURL, javaEntry)

  private[this] def storeURL[T](attr: Attributed[T], key: AttributeKey[URL], entry: Option[URL]): Attributed[T] = entry match {
    case None    => attr
    case Some(u) => attr.put(key, u)
  }
}

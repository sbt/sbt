/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.File
import sbt.internal.util.AttributeKey
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.librarymanagement.ModuleID

object BasicKeys {
  val historyPath = AttributeKey[Option[File]](
    "history",
    "The location where command line history is persisted.",
    40)
  val shellPrompt = AttributeKey[State => String](
    "shell-prompt",
    "The function that constructs the command prompt from the current build state.",
    10000)
  val watch = AttributeKey[Watched]("watch", "Continuous execution configuration.", 1000)
  val serverPort =
    AttributeKey[Int]("server-port", "The port number used by server command.", 10000)

  val serverHost =
    AttributeKey[String]("serverHost", "The host used by server command.", 10000)

  val serverAuthentication =
    AttributeKey[Set[ServerAuthentication]]("serverAuthentication",
                                            "Method of authenticating server command.",
                                            10000)

  private[sbt] val interactive = AttributeKey[Boolean](
    "interactive",
    "True if commands are currently being entered from an interactive environment.",
    10)
  private[sbt] val classLoaderCache = AttributeKey[ClassLoaderCache](
    "class-loader-cache",
    "Caches class loaders based on the classpath entries and last modified times.",
    10)
  private[sbt] val OnFailureStack = AttributeKey[List[Option[Exec]]](
    "on-failure-stack",
    "Stack that remembers on-failure handlers.",
    10)
  private[sbt] val explicitGlobalLogLevels = AttributeKey[Boolean](
    "explicit-global-log-levels",
    "True if the global logging levels were explicitly set by the user.",
    10)
  private[sbt] val templateResolverInfos = AttributeKey[Seq[TemplateResolverInfo]](
    "templateResolverInfos",
    "List of template resolver infos.",
    1000)
}

case class TemplateResolverInfo(module: ModuleID, implementationClass: String)

/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.nio.file.Path
import sbt.internal.inc.classpath.{ ClassLoaderCache as IncClassLoaderCache }
import sbt.internal.classpath.ClassLoaderCache
import sbt.internal.server.ServerHandler
import sbt.internal.util.AttributeKey
import sbt.librarymanagement.ModuleID
import sbt.util.{ ActionCacheStore, Level }
import scala.annotation.nowarn
import scala.concurrent.duration.FiniteDuration
import xsbti.{ FileConverter, VirtualFile }

object BasicKeys {
  val historyPath = AttributeKey[Option[File]](
    "history",
    "The location where command line history is persisted.",
    40
  )

  val extraMetaSbtFiles = AttributeKey[Seq[VirtualFile]](
    "extraMetaSbtFile",
    "Additional plugin.sbt files.",
    10000
  )

  val shellPrompt = AttributeKey[State => String](
    "shell-prompt",
    "The function that constructs the command prompt from the current build state.",
    10000
  )
  val colorShellPrompt = AttributeKey[(Boolean, State) => String](
    "color-shell-prompt",
    "The function that constructs the command prompt from the current build state for a given terminal.",
    10000
  )
  @nowarn val watch =
    AttributeKey[Watched]("watched", "Continuous execution configuration.", 1000)
  val serverPort =
    AttributeKey[Int]("server-port", "The port number used by server command.", 10000)

  val serverHost =
    AttributeKey[String]("serverHost", "The host used by server command.", 10000)

  val serverAuthentication =
    AttributeKey[Set[ServerAuthentication]](
      "serverAuthentication",
      "Method of authenticating server command.",
      10000
    )

  val serverConnectionType =
    AttributeKey[ConnectionType](
      "serverConnectionType",
      "The wire protocol for the server command.",
      10000
    )

  val fullServerHandlers =
    AttributeKey[Seq[ServerHandler]](
      "fullServerHandlers",
      "Combines default server handlers and user-defined handlers.",
      10000
    )

  val autoStartServer =
    AttributeKey[Boolean](
      "autoStartServer",
      "If true, the sbt server will startup automatically during interactive sessions.",
      10000
    )

  val windowsServerSecurityLevel =
    AttributeKey[Int](
      "windowsServerSecurityLevel",
      "Configures the security level of the named pipe. Values: 0 - No security; 1 - Logon user only; 2 - Process owner only",
      10000
    )
  val serverUseJni =
    AttributeKey[Boolean](
      "serverUseJni",
      "Toggles whether to use the jna or jni implementation in ipcsocket.",
      10000
    )

  val serverIdleTimeout =
    AttributeKey[Option[FiniteDuration]](
      "serverIdleTimeout",
      "If set to a defined value, sbt server will exit if it goes at least the specified duration without receiving any commands.",
      10000
    )

  val bspEnabled =
    AttributeKey[Boolean](
      "bspEnabled",
      "Enable/Disable BSP for this build, project or configuration",
      10000
    )

  val cacheStores =
    AttributeKey[Seq[ActionCacheStore]](
      "cacheStores",
      "Cache backends",
      10000
    )

  val rootOutputDirectory =
    AttributeKey[Path](
      "rootOutputDirectory",
      "Build-wide output directory",
      10000
    )

  val fileConverter = AttributeKey[FileConverter](
    "fileConverter",
    "The file converter used to convert between Path and VirtualFile",
    10000
  )

  // Unlike other BasicKeys, this is not used directly as a setting key,
  // and severLog / logLevel is used instead.
  private[sbt] val serverLogLevel =
    AttributeKey[Level.Value]("serverLogLevel", "The log level for the server.", 10000)

  private[sbt] val logLevel =
    AttributeKey[Level.Value]("logLevel", "The amount of logging sent to the screen.", 10)

  private[sbt] val interactive = AttributeKey[Boolean](
    "interactive",
    "True if commands are currently being entered from an interactive environment.",
    10
  )
  private[sbt] val classLoaderCache = AttributeKey[IncClassLoaderCache](
    "class-loader-cache",
    "Caches class loaders based on the classpath entries and last modified times.",
    10
  )
  private[sbt] val extendedClassLoaderCache = AttributeKey[ClassLoaderCache](
    "extended-class-loader-cache",
    "Caches class loaders based on the classpath entries and last modified times.",
    10
  )
  private[sbt] val OnFailureStack = AttributeKey[List[Option[Exec]]](
    "on-failure-stack",
    "Stack that remembers on-failure handlers.",
    10
  )
  private[sbt] val explicitGlobalLogLevels = AttributeKey[Boolean](
    "explicit-global-log-levels",
    "True if the global logging levels were explicitly set by the user.",
    10
  )
  private[sbt] val templateResolverInfos = AttributeKey[Seq[TemplateResolverInfo]](
    "templateResolverInfos",
    "List of template resolver infos.",
    1000
  )
  private[sbt] val detachStdio = AttributeKey[Boolean](
    "detach-stdio",
    "Toggles whether or not to close system in, out and error when the server starts.",
    1000
  )
}

case class TemplateResolverInfo(module: ModuleID, implementationClass: String)

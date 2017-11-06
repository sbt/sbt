/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package lsp

import sbt.internal.langserver.ErrorCodes
import scala.util.matching.Regex.MatchIterator
import scala.annotation.tailrec

object Definition {
  import java.net.URI
  import Keys._
  import sbt.internal.inc.Analysis
  import sbt.internal.inc.JavaInterfaceUtil._
  val AnalysesKey = "lsp.definition.analyses.key"

  import sjsonnew.JsonFormat
  def send[A: JsonFormat](universe: State)(params: A): Unit = {
    for {
      command <- universe.currentCommand
      source <- command.source
      origChannelName = source.channelName
      channel <- StandardMain.exchange.channels.collectFirst {
        case c if c.name == origChannelName => c
      }
    } yield {
      channel.publishEvent(params, command.execId)
    }
  }

  object textProcessor {
    private val isIdentifier = {
      import scala.tools.reflect.{ ToolBox, ToolBoxError }
      lazy val tb =
        scala.reflect.runtime.universe
          .runtimeMirror(this.getClass.getClassLoader)
          .mkToolBox()
      import tb._
      lazy val check = parse _ andThen compile _
      (identifier: String) =>
        try {
          check(s"val $identifier = 0; val ${identifier}${identifier} = $identifier")
          true
        } catch {
          case _: ToolBoxError => false
        }
    }

    def identifier(line: String, point: Int): Option[String] = {
      val potentials = for {
        from <- 0 to point
        to <- point + 1 to line.length
        fragment = line.slice(from, to).trim if isIdentifier(fragment)
      } yield fragment
      potentials.toSeq match {
        case Nil        => None
        case potentials => Some(potentials.maxBy(_.length))
      }
    }

    private def asClassObjectIdentifier(sym: String) =
      Seq(s".$sym", s".$sym$$", s"$$$sym", s"$$$sym$$")
    def potentialClsOrTraitOrObj(sym: String): PartialFunction[String, String] = {
      case potentialClassOrTraitOrObject
          if asClassObjectIdentifier(sym).exists(potentialClassOrTraitOrObject.endsWith) ||
            sym == potentialClassOrTraitOrObject ||
            s"$sym$$" == potentialClassOrTraitOrObject =>
        potentialClassOrTraitOrObject
    }

    @tailrec
    private def fold(z: Seq[(String, Int)])(it: MatchIterator): Seq[(String, Int)] = {
      if (!it.hasNext) z
      else fold(z :+ (it.next() -> it.start))(it)
    }

    private[lsp] def classTraitObjectInLine(sym: String)(line: String): Seq[(String, Int)] = {
      val potentials =
        Seq(s"object +$sym".r, s"trait +$sym *\\[?".r, s"class +$sym *\\[?".r)
      potentials
        .flatMap { reg =>
          fold(Seq.empty)(reg.findAllIn(line))
        }
        .collect {
          case (name, pos) =>
            (if (name.endsWith("[")) name.init.trim else name.trim) -> pos
        }
    }

    import java.io.File
    def markPosition(file: File, sym: String): Seq[(File, Int, Int, Int)] = {
      import java.nio.file._
      import scala.collection.JavaConverters._
      val findInLine = classTraitObjectInLine(sym)(_)
      Files
        .lines(file.toPath)
        .iterator
        .asScala
        .zipWithIndex
        .flatMap {
          case (line, lineNumber) =>
            findInLine(line)
              .collect {
                case (sym, from) =>
                  (file, lineNumber, from, from + sym.length)
              }
        }
        .toSeq
        .distinct
    }
  }

  import sbt.internal.langserver.TextDocumentPositionParams
  private def getDefinition(rawDefinition: Option[String]): Option[TextDocumentPositionParams] =
    rawDefinition.flatMap { rawDefinition =>
      import sbt.internal.langserver.codec.JsonProtocol._
      import sjsonnew.support.scalajson.unsafe.{ Parser => JsonParser, Converter }
      JsonParser
        .parseFromString(rawDefinition)
        .flatMap { jsonDefinition =>
          Converter.fromJson[TextDocumentPositionParams](jsonDefinition)
        }
        .toOption
    }

  import scalacache.Cache
  import scala.concurrent.Future
  private[sbt] def updateCache(cache: Cache[Any])(cacheFile: String,
                                                  useBinary: Boolean): Future[Any] = {
    import scalacache.modes.scalaFuture._
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.duration.Duration.Inf
    cache.get(AnalysesKey).flatMap {
      case None =>
        cache.put(AnalysesKey)(Set(cacheFile -> useBinary), Option(Inf))
      case Some(set) =>
        cache.put(AnalysesKey)(set.asInstanceOf[Set[(String, Boolean)]].filterNot {
          case (file, bin) => file == cacheFile
        } + (cacheFile -> useBinary), Option(Inf))
      case _ => Future.successful(())
    }
  }

  lazy val lspCollectAnalyses =
    Def.taskKey[Unit]("language server protocol task to collect analyses locations")
  def lspCollectAnalysesTask = Def.task {
    val cacheFile = compileIncSetup.value.cacheFile.getAbsolutePath
    val useBinary = enableBinaryCompileAnalysis.value
    val s = state.value
    s.log.debug(s"analysis location ${(cacheFile -> useBinary)}")
    updateCache(StandardMain.cache)(cacheFile, useBinary)
  }

  private[sbt] def getAnalyses(universe: State): Seq[Analysis] = {
    val root = Project.extract(universe)
    import sbt.librarymanagement.Configurations.Compile
    import sbt.internal.Aggregation
    val skey = (Keys.previousCompile in Compile).scopedKey
    val tasks = root.structure.data.scopes
      .map { scope =>
        root.structure.data.get(scope, skey.key)
      }
      .collect {
        case Some(task) => Aggregation.KeyValue(skey, task)
      }
      .toSeq
    import sbt.std.Transform.DummyTaskMap
    val complete =
      Aggregation.timedRun(universe.copy(remainingCommands = Nil), tasks, DummyTaskMap(Nil))
    complete.results.toEither.toOption.map { results =>
      results.map(_.value.analysis.toOption).collect {
        case Some(analysis: Analysis) =>
          analysis
      }
    }
  }.getOrElse(Seq.empty)

  lazy val lspDefinition = Def.inputKey[Unit]("language server protocol definition request task")
  def lspDefinitionTask = Def.inputTask {
    val LspDefinitionLogHead = "lsp-definition"
    lazy val universe = state.value
    val rawDefinition = {
      import Def._
      spaceDelimited("<lsp-definition>").parsed
    }
    universe.log.debug(s"$LspDefinitionLogHead raw request: $rawDefinition")
    val definition = getDefinition(rawDefinition.headOption)
    lazy val analyses = getAnalyses(universe)

    definition
      .map { definition =>
        val srcs = sources.value
        srcs
          .collectFirst {
            case file if definition.textDocument.uri.endsWith(file.getAbsolutePath) =>
              new URI(definition.textDocument.uri)
          }
          .flatMap { uri =>
            import java.nio.file._
            Files
              .lines(Paths.get(uri))
              .skip(definition.position.line)
              .findFirst
              .toOption
          }
          .flatMap { line =>
            universe.log.debug(s"$LspDefinitionLogHead found line: $line")
            textProcessor
              .identifier(line, definition.position.character.toInt)
              .map { sym =>
                val selectPotentials =
                  textProcessor.potentialClsOrTraitOrObj(sym)
                universe.log.debug(s"symbol $sym")
                val locations = analyses.flatMap { analysis =>
                  val classes =
                    (analysis.apis.allInternalClasses ++ analysis.apis.allExternals).collect {
                      selectPotentials
                    }
                  universe.log.debug(s"$LspDefinitionLogHead potentials: $classes")
                  classes
                    .flatMap { className =>
                      universe.log.debug(
                        s"$LspDefinitionLogHead classes ${analysis.relations.classes}")
                      analysis.relations.definesClass(className) ++ analysis.relations
                        .libraryDefinesClass(className)
                    }
                    .flatMap { classFile =>
                      textProcessor.markPosition(classFile, sym).collect {
                        case (file, line, from, to) =>
                          import sbt.internal.langserver.{ Location, Position, Range }
                          Location(file.toURI.toURL.toString,
                                   Range(Position(line, from), Position(line, to)))
                      }
                    }
                }
                import sbt.internal.langserver.codec.JsonProtocol._
                send(universe)(locations.toArray)
              }
          }
      }
      .orElse {
        import sbt.internal.protocol.JsonRpcResponseError
        import sbt.internal.protocol.codec.JsonRPCProtocol._
        universe.log.warn(s"Incorrect definition request ${rawDefinition.headOption}")
        send(universe)(
          JsonRpcResponseError(ErrorCodes.ParseError, "Incorrect definition request", None))
        None
      }
  }
}

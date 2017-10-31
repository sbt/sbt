/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package lsp

import sbt.internal.langserver.ErrorCodes

object Definition {
  import java.net.URI
  import Keys._
  import sbt.internal.inc.Analysis
  import sbt.internal.inc.JavaInterfaceUtil._

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
      val tb =
        scala.reflect.runtime.universe
          .runtimeMirror(this.getClass.getClassLoader)
          .mkToolBox()
      (identifier: String) =>
        try {
          tb.parse(s"val $identifier = 0")
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

    def asClassObjectIdentifier(sym: String) = Seq(s".$sym", s".$sym$$", s"$$$sym", s"$$$sym$$")

    import java.io.File
    def markPosition(file: File, sym: String): Seq[(File, Int, Int, Int)] = {
      val potentials =
        Seq(s"object $sym", s"trait $sym ", s"trait $sym[", s"class $sym ", s"class $sym[")
      import java.nio.file._
      import scala.collection.JavaConverters._
      Files
        .lines(file.toPath)
        .iterator
        .asScala
        .zipWithIndex
        .collect {
          case (line, lineNumber) if potentials.exists(line.contains) =>
            val from = line.indexOf(sym)
            (file, lineNumber, from, from + sym.length)
        }
        .toSeq
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

  private def getAnalyses(universe: State): Seq[Analysis] = {
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

  private def potentialClsOrTraitOrObj(potentials: Seq[String]): PartialFunction[String, String] = {
    case potentialClassOrTraitOrObject
        if potentials.exists(potentialClassOrTraitOrObject.endsWith) =>
      potentialClassOrTraitOrObject
  }

  lazy val lspDefinition = Def.inputKey[Unit]("language server protocol definition request task")
  def lspDefinitionTask = Def.inputTask {
    val LspDefinitionLogHead = "lsp-definition"
    val universe = state.value
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
                  potentialClsOrTraitOrObj(textProcessor.asClassObjectIdentifier(sym))
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

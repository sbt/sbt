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

    private def find(fragment: String, left: Int, right: Int, dir: Boolean): Option[String] = {
      if (left == -1 && !dir) find(fragment, 0, right, true)
      else if (right > fragment.length && dir)
        Some(fragment.slice(left, fragment.length).trim)
      else if (!isIdentifier(fragment.slice(left, right)) && !dir)
        find(fragment, left + 1, right, true)
      else if (!isIdentifier(fragment.slice(left, right)) && dir)
        Some(fragment.slice(left, right - 1).trim)
      else
        find(fragment, if (dir) left else left - 1, if (dir) right + 1 else right, dir)
    }

    private def findCorrectIdentifier(fragment: String, point: Int) = Option {
      if (isIdentifier(fragment.slice(point, point + 1))) (point, point + 1)
      else if (isIdentifier(fragment.slice(point - 1, point + 1))) (point - 1, point + 1)
      else if (isIdentifier(fragment.slice(point, point + 2))) (point, point + 2)
      else null
    }

    def identifier(line: String, character: Int): Option[String] =
      findCorrectIdentifier(line, character)
        .flatMap {
          case (left, right) => find(line, left, right, false)
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

  lazy val lspDefinition = Def.inputKey[Unit]("language server protocol definition request task")
  def lspDefinitionTask = Def.inputTask {
    val LspDefinitionLogHead = "lsp-definition"
    val universe = state.value
    val rawDefinition = {
      import Def._
      spaceDelimited("<lsp-definition>").parsed
    }
    universe.log.debug(s"$LspDefinitionLogHead raw request: $rawDefinition")
    val definitionOpt = rawDefinition.headOption
      .flatMap { rawDefinition =>
        import sbt.internal.langserver.TextDocumentPositionParams
        import sbt.internal.langserver.codec.JsonProtocol._
        import sjsonnew.support.scalajson.unsafe.{ Parser => JsonParser, Converter }
        JsonParser
          .parseFromString(rawDefinition)
          .flatMap { jsonDefinition =>
            Converter.fromJson[TextDocumentPositionParams](jsonDefinition)
          }
          .toOption
      }
      .orElse {
        import sbt.internal.protocol.JsonRpcResponseError
        import sbt.internal.protocol.codec.JsonRPCProtocol._
        universe.log.warn(s"Incorrect definition request ${rawDefinition.headOption}")
        send(universe)(
          JsonRpcResponseError(ErrorCodes.ParseError, "Incorrect definition request", None))
        None
      }
    lazy val analysisOpt = {
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

    definitionOpt.map { definition =>
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
              val potentialIdentifiers = textProcessor.asClassObjectIdentifier(sym)
              val locations = analysisOpt.flatMap { analysis =>
                val internalPotentials = analysis.apis.allInternalClasses.collect {
                  case n if potentialIdentifiers.exists(n.endsWith) => n
                }
                val externalPotentials = analysis.apis.allExternals.collect {
                  case n if potentialIdentifiers.exists(n.endsWith) => n
                }
                val potentialCls = (internalPotentials ++ externalPotentials)
                universe.log.debug(s"$LspDefinitionLogHead potentials: $potentialCls")
                potentialCls
                  .flatMap { cname =>
                    universe.log.debug(
                      s"$LspDefinitionLogHead classes ${analysis.relations.classes}")
                    analysis.relations.definesClass(cname) ++ analysis.relations
                      .libraryDefinesClass(cname)
                  }
                  .flatMap { file =>
                    textProcessor.markPosition(file, sym).collect {
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
  }
}

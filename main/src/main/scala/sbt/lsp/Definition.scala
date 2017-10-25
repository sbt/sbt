package sbt
package lsp

object Definition {
  import java.net.URI
  import sbt.Defaults._
  import sbt.Keys._
  import sbt.internal.inc.Analysis
  import sbt.internal.inc.JavaInterfaceUtil._

  lazy val lspDefinition = Def.inputKey[Unit]("language server protocol definition request task")
  def lspDefinitionTask = Def.inputTask {
    val LspDefinitionLogHead = "lsp-definition"
    val universe = state.value
    import sbt.internal.langserver.TextDocumentPositionParams
    import sbt.internal.langserver.codec.JsonProtocol._
    import sjsonnew.support.scalajson.unsafe.{ Parser => JsonParser, Converter }
    val rawDefinition = {
      import Def._
      spaceDelimited("<lsp-definition>").parsed
    }
    universe.log.debug(s"$LspDefinitionLogHead raw request: $rawDefinition")
    val definition =
      Converter.fromJsonUnsafe[TextDocumentPositionParams](
        JsonParser.parseUnsafe(rawDefinition.head))
    val analysis = previousCompile.value.analysis.toOption
      .map {
        case a: Analysis => a
      }
      .getOrElse {
        compile.value match {
          case a: Analysis => a
        }
      }
    val srcs = sources.value
    srcs
      .collectFirst {
        case file if definition.textDocument.uri.endsWith(file.getAbsolutePath) =>
          new URI(definition.textDocument.uri)
      }
      .map { uri =>
        import java.nio.file._
        Files
          .lines(Paths.get(uri))
          .skip(definition.position.line)
          .findFirst
          .toOption
          .map { line =>
            universe.log.debug(s"$LspDefinitionLogHead found line: $line")
            val isIdentifier = {
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
            def find(fragment: String, left: Int, right: Int, dir: Boolean): Option[String] = {
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
            def findCorrectIdentifier(fragment: String, point: Int) = Option {
              if (isIdentifier(fragment.slice(point, point + 1))) (point, point + 1)
              else if (isIdentifier(fragment.slice(point - 1, point + 1))) (point - 1, point + 1)
              else if (isIdentifier(fragment.slice(point, point + 2))) (point, point + 2)
              else null
            }
            findCorrectIdentifier(line, definition.position.character.toInt)
              .flatMap {
                case (left, right) => find(line, left, right, false)
              }
              .map { sym =>
                val internalPotentials = analysis.apis.allInternalClasses.collect {
                  case n if n.endsWith(sym) => n
                }
                val externalPotentials = analysis.apis.allExternals.collect {
                  case n if n.endsWith(sym) => n
                }
                val potentialCls = (internalPotentials ++ externalPotentials)
                universe.log.debug(s"$LspDefinitionLogHead potentials: $potentialCls")
                val potentialSrcs = potentialCls
                  .flatMap { cname =>
                    analysis.relations.definesClass(cname)
                  }
                  .map { file =>
                    file.toURI
                  }
                send(potentialSrcs.map { uri =>
                  import sbt.internal.langserver.{ Location, Position, Range }
                  Location(uri.toURL.toString, Range(Position(0, 0), Position(0, 1)))
                }.toArray)
              }
              .orElse {
                send(Array.empty)
                None
              }
          }
      }
    import sbt.internal.langserver.{ Location }
    def send(params: Array[Location]): Unit = {
      universe.log.debug(s"$LspDefinitionLogHead locations: ${params.toSeq}")
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
  }
}

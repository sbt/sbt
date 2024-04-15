/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import java.net.URI
import java.nio.file._

import scala.annotation.{ nowarn, tailrec }
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.NameTransformer
import scala.tools.reflect.{ ToolBox, ToolBoxError }
import scala.util.matching.Regex

import sjsonnew.JsonFormat
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }

import sbt.internal.inc.Analysis
import sbt.internal.inc.JavaInterfaceUtil._
import sbt.internal.protocol.JsonRpcResponseError
import sbt.internal.protocol.codec.JsonRPCProtocol
import sbt.internal.langserver
import sbt.internal.langserver.{ ErrorCodes, Location, Position, Range, TextDocumentPositionParams }
import sbt.util.Logger
import sbt.Keys._
import xsbti.{ FileConverter, VirtualFileRef }
import com.github.benmanes.caffeine.cache.Cache
import scala.concurrent.Promise
import com.github.benmanes.caffeine.cache.Caffeine

private[sbt] object Definition {
  def send[A: JsonFormat](source: CommandSource, execId: String)(params: A): Unit = {
    for {
      channel <- StandardMain.exchange.channels.collectFirst {
        case c: NetworkChannel if c.name == source.channelName => c
      }
    } {
      channel.respond(params, Option(execId))
    }
  }

  object textProcessor {
    private val isIdentifier = {
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

    private def findInBackticks(line: String, point: Int): Option[String] = {
      val (even, odd) = line.zipWithIndex
        .collect { case (char, backtickIndex) if char == '`' => backtickIndex }
        .zipWithIndex
        .partition { case (_, index) => index % 2 == 0 }

      even
        .collect { case (backtickIndex, _) => backtickIndex }
        .zip {
          odd.collect { case (backtickIndex, _) => backtickIndex + 1 }
        }
        .collectFirst {
          case (from, to) if from <= point && point < to => line.slice(from, to)
        }
    }

    def identifier(line: String, point: Int): Option[String] = findInBackticks(line, point).orElse {
      val whiteSpaceReg = "(\\s|\\.)+".r

      val (zero, end) = fold(Seq.empty)(whiteSpaceReg.findAllIn(line))
        .collect {
          case (white, ind) => (ind, ind + white.length)
        }
        .fold((0, line.length)) {
          case ((left, right), (from, to)) =>
            val zero = if (to > left && to <= point) to else left
            val end = if (from < right && from >= point) from else right
            (zero, end)
        }

      val ranges = for {
        from <- zero to point
        to <- point to end
      } yield (from -> to)

      ranges
        .sortBy { case (from, to) => -(to - from) }
        .foldLeft(List.empty[String]) {
          case (z, (from, to)) =>
            val fragment = line.slice(from, to).trim
            if (isIdentifier(fragment))
              z match {
                case Nil if fragment.nonEmpty              => fragment :: z
                case h :: _ if h.length < fragment.length  => fragment :: Nil
                case h :: _ if h.length == fragment.length => fragment :: z
                case _                                     => z
              }
            else z
        }
        .headOption
    }

    private def asClassObjectIdentifier(sym: String) =
      Seq(s".$sym", s".$sym$$", s"$$$sym", s"$$$sym$$")

    def potentialClsOrTraitOrObj(sym: String): PartialFunction[String, String] = {
      val encodedSym = NameTransformer.encode(sym.toSeq match {
        case '`' +: body :+ '`' => body.mkString
        case noBackticked       => noBackticked.mkString
      })
      val action: PartialFunction[String, String] = {
        case potentialClassOrTraitOrObject
            if asClassObjectIdentifier(encodedSym).exists(potentialClassOrTraitOrObject.endsWith) ||
              encodedSym == potentialClassOrTraitOrObject ||
              s"$encodedSym$$" == potentialClassOrTraitOrObject =>
          potentialClassOrTraitOrObject
      }
      action
    }

    @tailrec
    private def fold(z: Seq[(String, Int)])(it: Regex.MatchIterator): Seq[(String, Int)] = {
      if (!it.hasNext) z
      else fold(z :+ (it.next() -> it.start))(it)
    }

    def classTraitObjectInLine(sym: String)(line: String): Seq[(String, Int)] = {
      val potentials = Seq(
        s"object\\s+${Regex quote sym}".r,
        s"trait\\s+${Regex quote sym} *\\[?".r,
        s"class\\s+${Regex quote sym} *\\[?".r,
      )
      potentials
        .flatMap { reg =>
          fold(Seq.empty)(reg.findAllIn(line))
        }
        .collect {
          case (name, pos) =>
            (if (name.endsWith("[")) name.init.trim else name.trim) -> pos
        }
    }

    def markPosition(file: Path, sym: String): Seq[(URI, Long, Long, Long)] = {
      val findInLine = classTraitObjectInLine(sym)(_)
      Files
        .lines(file)
        .iterator
        .asScala
        .zipWithIndex
        .flatMap {
          case (line, lineNumber) =>
            findInLine(line)
              .collect {
                case (sym, from) =>
                  (file.toUri, lineNumber.toLong, from.toLong, from.toLong + sym.length)
              }
        }
        .toSeq
        .distinct
    }
  }

  private def getDefinition(jsonDefinition: JValue): Option[TextDocumentPositionParams] = {
    import langserver.codec.JsonProtocol._
    Converter.fromJson[TextDocumentPositionParams](jsonDefinition).toOption
  }

  private[this] val AnalysesKey = "lsp.definition.analyses.key"
  private[server] type Analyses = Set[((String, Boolean, Boolean), Option[Analysis])]

  private def storeAnalysis(
      cacheFile: Path,
      useBinary: Boolean,
      useConsistent: Boolean,
  ): Option[Analysis] =
    AnalysisUtil
      .staticCachedStore(
        analysisFile = cacheFile,
        useTextAnalysis = !useBinary,
        useConsistent = useConsistent,
      )
      .get
      .toOption
      .map { _.getAnalysis }
      .collect { case a: Analysis => a }

  private[sbt] def updateCache(
      cache: Cache[String, Analyses]
  )(cacheFile: String, useBinary: Boolean, useConsistent: Boolean): Any = {
    cache.get(AnalysesKey, k => Set((cacheFile, useBinary, useConsistent) -> None)) match {
      case null => new AnyRef
      case set =>
        val newSet = set
          .filterNot { case ((file, _, _), _) => file == cacheFile }
          .+((cacheFile, useBinary, useConsistent) -> None)
        cache.put(AnalysesKey, newSet)
    }
  }
  private[sbt] object AnalysesAccess {
    private[sbt] lazy val cache: Cache[String, Analyses] = Caffeine.newBuilder.build()
    ShutdownHooks.add(() => {
      cache.invalidateAll()
      cache.cleanUp()
    })
    private[sbt] def getFrom(cache: Cache[String, Analyses]): Option[Analyses] = {
      cache.getIfPresent(AnalysesKey) match {
        case null => None
        case a    => Some(a)
      }
    }
  }

  def collectAnalysesTask = Def.task {
    val cacheFile: String = compileIncSetup.value.cacheFile.getAbsolutePath
    val s = state.value
    s.log.debug(s"analysis location ${cacheFile}")
    updateCache(AnalysesAccess.cache)(
      cacheFile = cacheFile,
      useBinary = enableBinaryCompileAnalysis.value,
      useConsistent = enableConsistentCompileAnalysis.value,
    )
  }

  private[sbt] def getAnalyses: Future[Seq[Analysis]] = {
    val result = Promise[Seq[Analysis]]

    new Thread("sbt-get-analysis-thread") {
      setDaemon(true)
      start()
      override def run(): Unit =
        try {
          AnalysesAccess.cache.getIfPresent(AnalysesKey) match {
            case null => result.success(Nil)
            case caches =>
              val (working, uninitialized) = caches.partition {
                case (_, Some(_)) => true
                case (_, None)    => false
              }
              val addToCache = uninitialized.collect {
                case (title @ (file, useBinary, useConsistent), _)
                    if Files.exists(Paths.get(file)) =>
                  (title, storeAnalysis(Paths.get(file), !useBinary, useConsistent))
              }
              val validCaches = working ++ addToCache
              if (addToCache.nonEmpty) {
                AnalysesAccess.cache.put(AnalysesKey, validCaches)
              }
              result.success(validCaches.toSeq.collect {
                case (_, Some(analysis)) =>
                  analysis
              })
          }
        } catch { case scala.util.control.NonFatal(e) => result.failure(e) }
    }
    result.future
  }

  @nowarn
  def lspDefinition(
      jsonDefinition: JValue,
      requestId: String,
      commandSource: CommandSource,
      converter: FileConverter,
      log: Logger,
  )(implicit ec: ExecutionContext): Future[Unit] = Future {
    val LspDefinitionLogHead = "lsp-definition"
    val jsonDefinitionString = CompactPrinter(jsonDefinition)
    log.debug(s"$LspDefinitionLogHead json request: $jsonDefinitionString")
    lazy val analyses = getAnalyses
    getDefinition(jsonDefinition)
      .flatMap { definition =>
        val uri = URI.create(definition.textDocument.uri)
        Files
          .lines(Paths.get(uri))
          .skip(definition.position.line)
          .findFirst
          .toOption
          .flatMap { line =>
            log.debug(s"$LspDefinitionLogHead found line: $line")
            textProcessor.identifier(line, definition.position.character.toInt)
          }
      } match {
      case Some(sym) =>
        log.debug(s"symbol $sym")
        analyses
          .map { analyses =>
            import sbt.internal.CompatParColls.Converters._
            val locations = analyses.par.flatMap { analysis =>
              val selectPotentials = textProcessor.potentialClsOrTraitOrObj(sym)
              val classes =
                (analysis.apis.allInternalClasses ++ analysis.apis.allExternals).collect {
                  selectPotentials
                }
              log.debug(s"$LspDefinitionLogHead potentials: $classes")
              classes
                .flatMap { className =>
                  analysis.relations.definesClass(className) ++
                    analysis.relations.libraryDefinesClass(className)
                }
                .flatMap { classFile: VirtualFileRef =>
                  val x = converter.toPath(classFile)
                  textProcessor.markPosition(x, sym).collect {
                    case (uri, line, from, to) =>
                      Location(
                        uri.toString,
                        Range(Position(line, from), Position(line, to)),
                      )
                  }
                }
            }.seq
            log.debug(s"$LspDefinitionLogHead locations $locations")
            import langserver.codec.JsonProtocol._
            send(commandSource, requestId)(locations.toArray)
          }
          .recover {
            case t =>
              log.warn(s"Problem with processing analyses $t for $jsonDefinitionString")
              val rsp = JsonRpcResponseError(
                ErrorCodes.InternalError,
                "Problem with processing analyses.",
                None,
              )
              import JsonRPCProtocol._
              send(commandSource, requestId)(rsp)
          }
        ()
      case None =>
        log.info(s"Symbol not found in definition request $jsonDefinitionString")
        import langserver.codec.JsonProtocol._
        send(commandSource, requestId)(Array.empty[Location])
    }
  }
}

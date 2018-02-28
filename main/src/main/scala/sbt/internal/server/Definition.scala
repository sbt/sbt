/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package server

import java.io.File
import java.net.URI
import java.nio.file._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.reflect.NameTransformer
import scala.tools.reflect.{ ToolBox, ToolBoxError }
import scala.util.matching.Regex

import sjsonnew.JsonFormat
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }

import scalacache._

import sbt.io.IO
import sbt.internal.inc.{ Analysis, MixedAnalyzingCompiler }
import sbt.internal.inc.JavaInterfaceUtil._
import sbt.internal.protocol.JsonRpcResponseError
import sbt.internal.protocol.codec.JsonRPCProtocol
import sbt.internal.langserver
import sbt.internal.langserver.{ ErrorCodes, Location, Position, Range, TextDocumentPositionParams }
import sbt.util.Logger
import sbt.Keys._

private[sbt] object Definition {
  def send[A: JsonFormat](source: CommandSource, execId: String)(params: A): Unit = {
    for {
      channel <- StandardMain.exchange.channels.collectFirst {
        case c if c.name == source.channelName => c
      }
    } {
      channel.publishEvent(params, Option(execId))
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
              } else z
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

    def markPosition(file: File, sym: String): Seq[(File, Long, Long, Long)] = {
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
                  (file, lineNumber.toLong, from.toLong, from.toLong + sym.length)
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

  object AnalysesAccess {
    private[this] val AnalysesKey = "lsp.definition.analyses.key"

    private[server] type Analyses = Set[((String, Boolean), Option[Analysis])]

    private[server] def getFrom[F[_]](
        cache: Cache[Any]
    )(implicit mode: Mode[F], flags: Flags): F[Option[Analyses]] =
      mode.M.map(cache.get(AnalysesKey))(_ map (_.asInstanceOf[Analyses]))

    private[server] def putIn[F[_]](
        cache: Cache[Any],
        value: Analyses,
        ttl: Option[Duration],
    )(implicit mode: Mode[F], flags: Flags): F[Any] =
      cache.put(AnalysesKey)(value, ttl)
  }

  private def storeAnalysis(cacheFile: File, useBinary: Boolean): Option[Analysis] =
    MixedAnalyzingCompiler
      .staticCachedStore(cacheFile, !useBinary)
      .get
      .toOption
      .map { _.getAnalysis }
      .collect { case a: Analysis => a }

  private[sbt] def updateCache[F[_]](cache: Cache[Any])(cacheFile: String, useBinary: Boolean)(
      implicit
      mode: Mode[F],
      flags: Flags): F[Any] = {
    mode.M.flatMap(AnalysesAccess.getFrom(cache)) {
      case None =>
        AnalysesAccess.putIn(cache, Set(cacheFile -> useBinary -> None), Option(Duration.Inf))
      case Some(set) =>
        val newSet = set
          .filterNot { case ((file, _), _) => file == cacheFile }
          .+(cacheFile -> useBinary -> None)
        AnalysesAccess.putIn(cache, newSet, Option(Duration.Inf))
    }
  }

  def collectAnalysesTask = Def.task {
    val cacheFile = compileIncSetup.value.cacheFile.getAbsolutePath
    val useBinary = enableBinaryCompileAnalysis.value
    val s = state.value
    s.log.debug(s"analysis location ${(cacheFile -> useBinary)}")
    import scalacache.modes.sync._
    updateCache(StandardMain.cache)(cacheFile, useBinary)
  }

  private[sbt] def getAnalyses: Future[Seq[Analysis]] = {
    import scalacache.modes.scalaFuture._
    import scala.concurrent.ExecutionContext.Implicits.global
    AnalysesAccess
      .getFrom(StandardMain.cache)
      .collect { case Some(a) => a }
      .map { caches =>
        val (working, uninitialized) = caches.partition {
          case (_, Some(_)) => true
          case (_, None)    => false
        }
        val addToCache = uninitialized.collect {
          case (title @ (file, useBinary), _) if Files.exists(Paths.get(file)) =>
            (title, storeAnalysis(Paths.get(file).toFile, !useBinary))
        }
        val validCaches = working ++ addToCache
        if (addToCache.nonEmpty)
          AnalysesAccess.putIn(StandardMain.cache, validCaches, Option(Duration.Inf))
        validCaches.toSeq.collect {
          case (_, Some(analysis)) =>
            analysis
        }
      }
  }

  def lspDefinition(
      jsonDefinition: JValue,
      requestId: String,
      commandSource: CommandSource,
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
                .flatMap { classFile =>
                  textProcessor.markPosition(classFile, sym).collect {
                    case (file, line, from, to) =>
                      Location(
                        IO.toURI(file).toString,
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

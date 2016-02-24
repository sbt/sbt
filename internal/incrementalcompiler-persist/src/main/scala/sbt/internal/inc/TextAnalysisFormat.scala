package sbt
package internal
package inc

import java.io._
import sbt.internal.util.Relation
import xsbti.T2
import xsbti.api.{ Compilation, Source }
import xsbti.compile.{ CompileAnalysis, MultipleOutput, SingleOutput, MiniOptions, MiniSetup }
import javax.xml.bind.DatatypeConverter
import java.net.URI

// Very simple timer for timing repeated code sections.
// TODO: Temporary. Remove once we've milked all available performance gains.
private[inc] object FormatTimer {
  private val timers = scala.collection.mutable.Map[String, Long]()
  private val printTimings = "true" == System.getProperty("sbt.analysis.debug.timing")

  def aggregate[T](key: String)(f: => T) = {
    val start = System.nanoTime()
    val ret = f
    val elapsed = System.nanoTime() - start
    timers.update(key, timers.getOrElseUpdate(key, 0) + elapsed)
    ret
  }

  def time[T](key: String)(f: => T) = {
    val ret = aggregate(key)(f)
    close(key)
    ret
  }

  def close(key: String): Unit = {
    if (printTimings) {
      println("[%s] %dms".format(key, timers.getOrElse(key, 0L) / 1000000))
    }
    timers.remove(key)
    ()
  }
}

class ReadException(s: String) extends Exception(s) {
  def this(expected: String, found: String) = this("Expected: %s. Found: %s.".format(expected, found))
}

class EOFException extends ReadException("Unexpected EOF.")

// A text-based serialization format for Analysis objects.
// This code has been tuned for high performance, and therefore has non-idiomatic areas.
// Please refrain from making changes that significantly degrade read/write performance on large analysis files.
object TextAnalysisFormat {
  // Some types are not required for external inspection/manipulation of the analysis file,
  // and are complex to serialize as text. So we serialize them as base64-encoded sbinary-serialized blobs.
  // TODO: This is a big performance hit. Figure out a more efficient way to serialize API objects?
  import sbinary.DefaultProtocol._
  import sbinary.Format
  import xsbti.{ Position, Problem, Severity }
  import sbt.util.Logger.{ m2o, position, problem }

  private implicit val sourceFormat: Format[Source] = xsbt.api.SourceFormat
  private implicit val compilationF: Format[Compilation] = xsbt.api.CompilationFormat
  private implicit def problemFormat: Format[Problem] = asProduct4(problem _)(p => (p.category, p.position, p.message, p.severity))
  private implicit def positionFormat: Format[Position] =
    asProduct7(position _)(p => (m2o(p.line), p.lineContent, m2o(p.offset), m2o(p.pointer), m2o(p.pointerSpace), m2o(p.sourcePath), m2o(p.sourceFile)))
  private implicit val severityFormat: Format[Severity] =
    wrap[Severity, Byte](_.ordinal.toByte, b => Severity.values.apply(b.toInt))
  private implicit val integerFormat: Format[Integer] = wrap[Integer, Int](_.toInt, Integer.valueOf)
  private implicit def infoFormat: Format[SourceInfo] =
    wrap[SourceInfo, (Seq[Problem], Seq[Problem])](si => (si.reportedProblems, si.unreportedProblems), { case (a, b) => SourceInfos.makeInfo(a, b) })
  private implicit def seqFormat[T](implicit optionFormat: Format[T]): Format[Seq[T]] = viaSeq[Seq[T], T](x => x)
  private def t2[A1, A2](a1: A1, a2: A2): T2[A1, A2] =
    new T2[A1, A2] {
      val get1: A1 = a1
      val get2: A2 = a2
    }

  def write(out: Writer, analysis: CompileAnalysis, setup: MiniSetup): Unit = {
    val analysis0 = analysis match { case analysis: Analysis => analysis }
    VersionF.write(out)
    // We start with writing compile setup which contains value of the `nameHashing`
    // flag that is needed to properly deserialize relations
    FormatTimer.time("write setup") { MiniSetupF.write(out, setup) }
    // Next we write relations because that's the part of greatest interest to external readers,
    // who can abort reading early once they're read them.
    FormatTimer.time("write relations") { RelationsF.write(out, analysis0.relations) }
    FormatTimer.time("write stamps") { StampsF.write(out, analysis0.stamps) }
    FormatTimer.time("write apis") { APIsF.write(out, analysis0.apis) }
    FormatTimer.time("write sourceinfos") { SourceInfosF.write(out, analysis0.infos) }
    FormatTimer.time("write compilations") { CompilationsF.write(out, analysis0.compilations) }
    out.flush()
  }

  def read(in: BufferedReader): (CompileAnalysis, MiniSetup) = {
    VersionF.read(in)
    val setup = FormatTimer.time("read setup") { MiniSetupF.read(in) }
    val relations = FormatTimer.time("read relations") { RelationsF.read(in, setup.nameHashing) }
    val stamps = FormatTimer.time("read stamps") { StampsF.read(in) }
    val apis = FormatTimer.time("read apis") { APIsF.read(in) }
    val infos = FormatTimer.time("read sourceinfos") { SourceInfosF.read(in) }
    val compilations = FormatTimer.time("read compilations") { CompilationsF.read(in) }

    (Analysis.Empty.copy(stamps, apis, relations, infos, compilations), setup)
  }

  private[this] object VersionF {
    val currentVersion = "6"

    def write(out: Writer): Unit = {
      out.write("format version: %s\n".format(currentVersion))
    }

    private val versionPattern = """format version: (\w+)""".r
    def read(in: BufferedReader): Unit = {
      in.readLine() match {
        case versionPattern(version) => validateVersion(version)
        case s: String               => throw new ReadException("\"format version: <version>\"", s)
        case null                    => throw new EOFException
      }
    }

    def validateVersion(version: String): Unit = {
      // TODO: Support backwards compatibility?
      if (version != currentVersion) {
        throw new ReadException("File uses format version %s, but we are compatible with version %s only.".format(version, currentVersion))
      }
    }
  }

  private[sbt] val fileToString: File => String =
    { f: File => f.toURI.toString }
  private[sbt] val stringToFile: String => File =
    { s: String =>
      try {
        new File(new URI(s))
      } catch {
        case e: Exception => sys.error(e.getMessage + ": " + s)
      }
    }
  private[this] object RelationsF {
    object Headers {
      val srcProd = "products"
      val binaryDep = "binary dependencies"
      val directSrcDep = "direct source dependencies"
      val directExternalDep = "direct external dependencies"
      val internalSrcDepPI = "public inherited source dependencies"
      val externalDepPI = "public inherited external dependencies"
      val classes = "class names"

      val memberRefInternalDep = "member reference internal dependencies"
      val memberRefExternalDep = "member reference external dependencies"
      val inheritanceInternalDep = "inheritance internal dependencies"
      val inheritanceExternalDep = "inheritance external dependencies"

      val usedNames = "used names"
    }

    def write(out: Writer, relations: Relations): Unit = {

      def writeRelation[T](header: String, rel: Relation[File, T], t2s: T => String): Unit = {
        writeHeader(out, header)
        writeSize(out, rel.size)
        // We sort for ease of debugging and for more efficient reconstruction when reading.
        // Note that we don't share code with writeMap. Each is implemented more efficiently
        // than the shared code would be, and the difference is measurable on large analyses.
        rel.forwardMap.toSeq.sortBy(_._1).foreach {
          case (k, vs) =>
            val kStr = fileToString(k)
            // This ordering is used to persist all values in order. Since all values will be
            // persisted using their string representation, it makes sense to sort them using
            // their string representation.
            vs.toSeq.sorted(new Ordering[T] {
              def compare(a: T, b: T) = t2s(a) compare t2s(b)
            }) foreach { v =>
              out.write(kStr)
              out.write(" -> ")
              out.write(t2s(v))
              out.write("\n")
            }
        }
      }

      ((relations.allRelations: List[(String, Relation[File, _])]) zip (Relations.existingRelations: List[(String, String)])) foreach {
        case ((header, rel), (x, "File:File")) =>
          writeRelation[File](header, rel.asInstanceOf[Relation[File, File]], fileToString)
        case ((header, rel), (x, "File:String")) =>
          writeRelation[String](header, rel.asInstanceOf[Relation[File, String]], identity[String] _)
      }
    }

    def read(in: BufferedReader, nameHashing: Boolean): Relations = {
      def readRelation[T](expectedHeader: String, s2t: String => T): Relation[File, T] = {
        val items = readPairs(in)(expectedHeader, stringToFile, s2t).toIterator
        // Reconstruct the forward map. This is more efficient than Relation.empty ++ items.
        var forward: List[(File, Set[T])] = Nil
        var currentItem: (File, T) = null
        var currentFile: File = null
        var currentVals: List[T] = Nil
        def closeEntry(): Unit = {
          if (currentFile != null) forward = (currentFile, currentVals.toSet) :: forward
          currentFile = currentItem._1
          currentVals = currentItem._2 :: Nil
        }
        while (items.hasNext) {
          currentItem = items.next()
          if (currentItem._1 == currentFile) currentVals = currentItem._2 :: currentVals else closeEntry()
        }
        if (currentItem != null) closeEntry()
        Relation.reconstruct(forward.toMap)
      }

      val relations = Relations.existingRelations map {
        case (header, "File:File")   => readRelation[File](header, stringToFile)
        case (header, "File:String") => readRelation[String](header, identity[String] _)
      }

      Relations.construct(nameHashing, relations)
    }
  }

  private[this] object StampsF {
    object Headers {
      val products = "product stamps"
      val sources = "source stamps"
      val binaries = "binary stamps"
      val classNames = "class names"
    }

    def write(out: Writer, stamps: Stamps): Unit = {
      def doWriteMap[V](header: String, m: Map[File, V]) = writeMap(out)(header, m, fileToString, { v: V => v.toString })

      doWriteMap(Headers.products, stamps.products)
      doWriteMap(Headers.sources, stamps.sources)
      doWriteMap(Headers.binaries, stamps.binaries)
      doWriteMap(Headers.classNames, stamps.classNames)
    }

    def read(in: BufferedReader): Stamps = {
      def doReadMap[V](expectedHeader: String, s2v: String => V) = readMap(in)(expectedHeader, stringToFile, s2v)
      val products = doReadMap(Headers.products, Stamp.fromString)
      val sources = doReadMap(Headers.sources, Stamp.fromString)
      val binaries = doReadMap(Headers.binaries, Stamp.fromString)
      val classNames = doReadMap(Headers.classNames, identity[String])

      Stamps(products, sources, binaries, classNames)
    }
  }

  private[this] object APIsF {
    object Headers {
      val internal = "internal apis"
      val external = "external apis"
    }

    val stringToSource = ObjectStringifier.stringToObj[Source] _
    val sourceToString = ObjectStringifier.objToString[Source] _

    def write(out: Writer, apis: APIs): Unit = {
      writeMap(out)(Headers.internal, apis.internal, fileToString, sourceToString, inlineVals = false)
      writeMap(out)(Headers.external, apis.external, identity[String] _, sourceToString, inlineVals = false)
      FormatTimer.close("bytes -> base64")
      FormatTimer.close("byte copy")
      FormatTimer.close("sbinary write")
    }

    def read(in: BufferedReader): APIs = {
      val internal = readMap(in)(Headers.internal, stringToFile, stringToSource)
      val external = readMap(in)(Headers.external, identity[String], stringToSource)
      FormatTimer.close("base64 -> bytes")
      FormatTimer.close("sbinary read")
      APIs(internal, external)
    }
  }

  private[this] object SourceInfosF {
    object Headers {
      val infos = "source infos"
    }

    val stringToSourceInfo = ObjectStringifier.stringToObj[SourceInfo] _
    val sourceInfoToString = ObjectStringifier.objToString[SourceInfo] _

    def write(out: Writer, infos: SourceInfos): Unit = writeMap(out)(Headers.infos, infos.allInfos, fileToString, sourceInfoToString, inlineVals = false)
    def read(in: BufferedReader): SourceInfos = SourceInfos.make(readMap(in)(Headers.infos, stringToFile, stringToSourceInfo))
  }

  private[this] object CompilationsF {
    object Headers {
      val compilations = "compilations"
    }

    val stringToCompilation = ObjectStringifier.stringToObj[Compilation] _
    val compilationToString = ObjectStringifier.objToString[Compilation] _

    def write(out: Writer, compilations: Compilations): Unit =
      writeSeq(out)(Headers.compilations, compilations.allCompilations, compilationToString)

    def read(in: BufferedReader): Compilations = Compilations.make(
      readSeq[Compilation](in)(Headers.compilations, stringToCompilation)
    )
  }

  private[this] object MiniSetupF {
    object Headers {
      val outputMode = "output mode"
      val outputDir = "output directories"
      val compileOptions = "compile options"
      val javacOptions = "javac options"
      val compilerVersion = "compiler version"
      val compileOrder = "compile order"
      val nameHashing = "name hashing"
      val extra = "extra"
    }

    private[this] val singleOutputMode = "single"
    private[this] val multipleOutputMode = "multiple"
    private[this] val singleOutputKey = new File("/output_dir")

    def write(out: Writer, setup: MiniSetup): Unit = {
      val (mode, outputAsMap) = setup.output match {
        case s: SingleOutput   => (singleOutputMode, Map(singleOutputKey -> s.outputDirectory))
        case m: MultipleOutput => (multipleOutputMode, m.outputGroups.map(x => x.sourceDirectory -> x.outputDirectory).toMap)
      }

      writeSeq(out)(Headers.outputMode, mode :: Nil, identity[String])
      writeMap(out)(Headers.outputDir, outputAsMap, fileToString, fileToString)
      writeSeq(out)(Headers.compileOptions, setup.options.scalacOptions, identity[String])
      writeSeq(out)(Headers.javacOptions, setup.options.javacOptions, identity[String])
      writeSeq(out)(Headers.compilerVersion, setup.compilerVersion :: Nil, identity[String])
      writeSeq(out)(Headers.compileOrder, setup.order.name :: Nil, identity[String])
      writeSeq(out)(Headers.nameHashing, setup.nameHashing :: Nil, (b: Boolean) => b.toString)
      writePairs[String, String](out)(Headers.extra, setup.extra.toList map { x => (x.get1, x.get2) }, identity[String], identity[String])
    }

    def read(in: BufferedReader): MiniSetup = {
      def s2b(s: String): Boolean = s.toBoolean
      val outputDirMode = readSeq(in)(Headers.outputMode, identity[String]).headOption
      val outputAsMap = readMap(in)(Headers.outputDir, stringToFile, stringToFile)
      val compileOptions = readSeq(in)(Headers.compileOptions, identity[String])
      val javacOptions = readSeq(in)(Headers.javacOptions, identity[String])
      val compilerVersion = readSeq(in)(Headers.compilerVersion, identity[String]).head
      val compileOrder = readSeq(in)(Headers.compileOrder, identity[String]).head
      val nameHashing = readSeq(in)(Headers.nameHashing, s2b).head
      val extra = readPairs(in)(Headers.extra, identity[String], identity[String]) map { case (a, b) => t2[String, String](a, b) }

      val output = outputDirMode match {
        case Some(s) => s match {
          case `singleOutputMode` => new SingleOutput {
            val outputDirectory = outputAsMap(singleOutputKey)
          }
          case `multipleOutputMode` => new MultipleOutput {
            val outputGroups: Array[MultipleOutput.OutputGroup] = outputAsMap.toArray.map {
              case (src: File, out: File) => new MultipleOutput.OutputGroup {
                val sourceDirectory = src
                val outputDirectory = out
                override def toString = s"OutputGroup($src -> $out)"
              }
            }
            override def toString = s"MultipleOuput($outputGroups)"
          }
          case str: String => throw new ReadException("Unrecognized output mode: " + str)
        }
        case None => throw new ReadException("No output mode specified")
      }

      new MiniSetup(output, new MiniOptions(compileOptions.toArray, javacOptions.toArray), compilerVersion,
        xsbti.compile.CompileOrder.valueOf(compileOrder), nameHashing, extra.toArray)
    }
  }

  private[this] object ObjectStringifier {
    def objToString[T](o: T)(implicit fmt: sbinary.Format[T]) = {
      val baos = new ByteArrayOutputStream()
      val out = new sbinary.JavaOutput(baos)
      FormatTimer.aggregate("sbinary write") { try { fmt.writes(out, o) } finally { baos.close() } }
      val bytes = FormatTimer.aggregate("byte copy") { baos.toByteArray }
      FormatTimer.aggregate("bytes -> base64") { DatatypeConverter.printBase64Binary(bytes) }
    }

    def stringToObj[T](s: String)(implicit fmt: sbinary.Format[T]) = {
      val bytes = FormatTimer.aggregate("base64 -> bytes") { DatatypeConverter.parseBase64Binary(s) }
      val in = new sbinary.JavaInput(new ByteArrayInputStream(bytes))
      FormatTimer.aggregate("sbinary read") { fmt.reads(in) }
    }
  }

  // Various helper functions.

  private[this] def writeHeader(out: Writer, header: String): Unit = out.write(header + ":\n")

  private[this] def expectHeader(in: BufferedReader, expectedHeader: String): Unit = {
    val header = in.readLine()
    if (header != expectedHeader + ":") throw new ReadException(expectedHeader, if (header == null) "EOF" else header)
  }

  private[this] def writeSize(out: Writer, n: Int): Unit = out.write("%d items\n".format(n))

  private val itemsPattern = """(\d+) items""".r
  private[this] def readSize(in: BufferedReader): Int = {
    in.readLine() match {
      case itemsPattern(nStr) => Integer.parseInt(nStr)
      case s: String          => throw new ReadException("\"<n> items\"", s)
      case null               => throw new EOFException
    }
  }

  private[this] def writeSeq[T](out: Writer)(header: String, s: Seq[T], t2s: T => String): Unit = {
    // We write sequences as idx -> element maps, for uniformity with maps/relations.
    def n = s.length
    val numDigits = if (n < 2) 1 else math.log10(n.toDouble - 1).toInt + 1
    val fmtStr = "%%0%dd".format(numDigits)
    // We only use this for relatively short seqs, so creating this extra map won't be a performance hit.
    val m: Map[String, T] = s.zipWithIndex.map(x => fmtStr.format(x._2) -> x._1).toMap
    writeMap(out)(header, m, identity[String] _, t2s)
  }

  private[this] def readSeq[T](in: BufferedReader)(expectedHeader: String, s2t: String => T): Seq[T] =
    (readPairs(in)(expectedHeader, identity[String], s2t).toSeq.sortBy(_._1) map (_._2))

  private[this] def writeMap[K, V](out: Writer)(header: String, m: Map[K, V], k2s: K => String, v2s: V => String, inlineVals: Boolean = true)(implicit ord: Ordering[K]): Unit =
    writePairs(out)(header, m.keys.toSeq.sorted map { k => (k, (m(k))) }, k2s, v2s, inlineVals)

  private[this] def readMap[K, V](in: BufferedReader)(expectedHeader: String, s2k: String => K, s2v: String => V): Map[K, V] = {
    readPairs(in)(expectedHeader, s2k, s2v).toMap
  }

  private[this] def writePairs[K, V](out: Writer)(header: String, s: Seq[(K, V)], k2s: K => String, v2s: V => String, inlineVals: Boolean = true): Unit = {
    writeHeader(out, header)
    writeSize(out, s.size)
    s foreach {
      case (k, v) =>
        out.write(k2s(k))
        out.write(" -> ")
        if (!inlineVals) out.write("\n") // Put large vals on their own line, to save string munging on read.
        out.write(v2s(v))
        out.write("\n")
    }
  }

  private[this] def readPairs[K, V](in: BufferedReader)(expectedHeader: String, s2k: String => K, s2v: String => V): Traversable[(K, V)] = {
    def toPair(s: String): (K, V) = {
      if (s == null) throw new EOFException
      val p = s.indexOf(" -> ")
      val k = s2k(s.substring(0, p))
      // Pair is either "a -> b" or "a -> \nb". This saves us a lot of substring munging when b is a large blob.
      val v = s2v(if (p == s.length - 4) in.readLine() else s.substring(p + 4))
      (k, v)
    }
    expectHeader(in, expectedHeader)
    val n = readSize(in)
    for (i <- 0 until n) yield toPair(in.readLine())
  }
}

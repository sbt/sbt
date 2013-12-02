package sbt
package inc

import java.io._
import sbt.{CompileSetup, Relation}
import xsbti.api.{Compilation, Source}
import javax.xml.bind.DatatypeConverter


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

	def close(key: String) {
		if (printTimings) {
			println("[%s] %dms".format(key, timers.getOrElse(key, 0L) / 1000000))
		}
		timers.remove(key)
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
	import sbinary.DefaultProtocol.{immutableMapFormat, immutableSetFormat, StringFormat, tuple2Format}
	import AnalysisFormats._
	implicit val compilationF = xsbt.api.CompilationFormat

	def write(out: Writer, analysis: Analysis, setup: CompileSetup) {
    VersionF.write(out)
		// We start with relations because that's the part of greatest interest to external readers,
		// who can abort reading early once they're read them.
		FormatTimer.time("write relations") { RelationsF.write(out, analysis.relations) }
		FormatTimer.time("write stamps") { StampsF.write(out, analysis.stamps) }
		FormatTimer.time("write apis") { APIsF.write(out, analysis.apis) }
		FormatTimer.time("write sourceinfos") { SourceInfosF.write(out, analysis.infos) }
		FormatTimer.time("write compilations") { CompilationsF.write(out, analysis.compilations) }
		FormatTimer.time("write setup") { CompileSetupF.write(out, setup) }
		out.flush()
	}

	def read(in: BufferedReader): (Analysis, CompileSetup) = {
    VersionF.read(in)
		val relations = FormatTimer.time("read relations") { RelationsF.read(in) }
		val stamps = FormatTimer.time("read stamps") { StampsF.read(in) }
		val apis = FormatTimer.time("read apis") { APIsF.read(in) }
		val infos = FormatTimer.time("read sourceinfos") { SourceInfosF.read(in) }
		val compilations = FormatTimer.time("read compilations") { CompilationsF.read(in) }
		val setup = FormatTimer.time("read setup") { CompileSetupF.read(in) }

		(Analysis.Empty.copy(stamps, apis, relations, infos, compilations), setup)
	}

  private[this] object VersionF {
    val currentVersion = "2"

    def write(out: Writer) {
      out.write("format version: %s\n".format(currentVersion))
    }

    private val versionPattern = """format version: (\w+)""".r
    def read(in: BufferedReader) {
      in.readLine() match {
        case versionPattern(version) => validateVersion(version)
        case s: String => throw new ReadException("\"format version: <version>\"", s)
        case null => throw new EOFException
      }
    }

    def validateVersion(version: String) {
      // TODO: Support backwards compatibility?
      if (version != currentVersion) {
        throw new ReadException("File uses format version %s, but we are compatible with version %s only.".format(version, currentVersion))
      }
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
		}

		def write(out: Writer, relations: Relations) {
			def writeRelation[T](header: String, rel: Relation[File, T])(implicit ord: Ordering[T]) {
				writeHeader(out, header)
				writeSize(out, rel.size)
				// We sort for ease of debugging and for more efficient reconstruction when reading.
				// Note that we don't share code with writeMap. Each is implemented more efficiently
				// than the shared code would be, and the difference is measurable on large analyses.
        rel.forwardMap.toSeq.sortBy(_._1).foreach { case (k, vs) =>
          val kStr = k.toString
					vs.toSeq.sorted foreach { v =>
						out.write(kStr); out.write(" -> "); out.write(v.toString); out.write("\n")
					}
				}
			}

			val memberRefAndInheritanceDeps = relations.memberRefAndInheritanceDeps
			writeRelation(Headers.srcProd,          relations.srcProd)
			writeRelation(Headers.binaryDep,        relations.binaryDep)

			val direct = if (memberRefAndInheritanceDeps) Relations.emptySource else relations.direct
			val publicInherited = if (memberRefAndInheritanceDeps)
				Relations.emptySource else relations.publicInherited

			val memberRef = if (memberRefAndInheritanceDeps)
				relations.memberRef else Relations.emptySourceDependencies
			val inheritance = if (memberRefAndInheritanceDeps)
				relations.inheritance else Relations.emptySourceDependencies

			writeRelation(Headers.directSrcDep, direct.internal)
			writeRelation(Headers.directExternalDep, direct.external)
			writeRelation(Headers.internalSrcDepPI, publicInherited.internal)
			writeRelation(Headers.externalDepPI, publicInherited.external)

			writeRelation(Headers.memberRefInternalDep, memberRef.internal)
			writeRelation(Headers.memberRefExternalDep, memberRef.external)
			writeRelation(Headers.inheritanceInternalDep, inheritance.internal)
			writeRelation(Headers.inheritanceExternalDep, inheritance.external)

			writeRelation(Headers.classes,        relations.classes)
		}

		def read(in: BufferedReader): Relations = {
			def readRelation[T](expectedHeader: String, s2t: String => T): Relation[File, T] = {
				val items = readPairs(in)(expectedHeader, new File(_), s2t).toIterator
				// Reconstruct the forward map. This is more efficient than Relation.empty ++ items.
				var forward: List[(File, Set[T])] = Nil
				var currentItem: (File, T) = null
				var currentFile: File = null
				var currentVals: List[T] = Nil
				def closeEntry() {
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

			def readFileRelation(expectedHeader: String) = readRelation(expectedHeader, { new File(_) })
			def readStringRelation(expectedHeader: String) = readRelation(expectedHeader, identity[String])

			val srcProd =          readFileRelation(Headers.srcProd)
			val binaryDep =        readFileRelation(Headers.binaryDep)

			import sbt.inc.Relations.{Source, SourceDependencies, makeSourceDependencies, emptySource,
				makeSource, emptySourceDependencies}
			val directSrcDeps: Source = {
				val internalSrcDep =   readFileRelation(Headers.directSrcDep)
				val externalDep =      readStringRelation(Headers.directExternalDep)
				makeSource(internalSrcDep, externalDep)
			}
			val publicInheritedSrcDeps: Source = {
				val internalSrcDepPI = readFileRelation(Headers.internalSrcDepPI)
				val externalDepPI =    readStringRelation(Headers.externalDepPI)
				makeSource(internalSrcDepPI, externalDepPI)
			}
			val memberRefSrcDeps: SourceDependencies = {
				val internalMemberRefDep = readFileRelation(Headers.memberRefInternalDep)
				val externalMemberRefDep = readStringRelation(Headers.memberRefExternalDep)
				makeSourceDependencies(internalMemberRefDep, externalMemberRefDep)
			}
			val inheritanceSrcDeps: SourceDependencies = {
				val internalInheritanceDep = readFileRelation(Headers.inheritanceInternalDep)
				val externalInheritanceDep = readStringRelation(Headers.inheritanceExternalDep)
				makeSourceDependencies(internalInheritanceDep, externalInheritanceDep)
			}
			// we don't check for emptiness of publicInherited/inheritance relations because
			// we assume that invariant that says they are subsets of direct/memberRef holds
			assert((directSrcDeps == emptySource) || (memberRefSrcDeps == emptySourceDependencies),
					"One mechanism is supported for tracking source dependencies at the time")
			val memberRefAndInheritanceDeps = memberRefSrcDeps != emptySourceDependencies
			val classes =          readStringRelation(Headers.classes)

			if (memberRefAndInheritanceDeps)
				Relations.make(srcProd, binaryDep, memberRefSrcDeps, inheritanceSrcDeps, classes)
			else
				Relations.make(srcProd, binaryDep, directSrcDeps, publicInheritedSrcDeps, classes)
		}
	}

	private[this] object StampsF {
		object Headers {
			val products = "product stamps"
			val sources = "source stamps"
			val binaries = "binary stamps"
			val classNames = "class names"
		}

		def write(out: Writer, stamps: Stamps) {
			def doWriteMap[V](header: String, m: Map[File, V]) = writeMap(out)(header, m, { v: V => v.toString })

			doWriteMap(Headers.products, stamps.products)
			doWriteMap(Headers.sources, stamps.sources)
			doWriteMap(Headers.binaries, stamps.binaries)
			doWriteMap(Headers.classNames, stamps.classNames)
		}

		def read(in: BufferedReader): Stamps = {
			def doReadMap[V](expectedHeader: String, s2v: String => V) = readMap(in)(expectedHeader, new File(_), s2v)
			val products =   doReadMap(Headers.products, Stamp.fromString)
			val sources =    doReadMap(Headers.sources, Stamp.fromString)
			val binaries =   doReadMap(Headers.binaries, Stamp.fromString)
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

		def write(out: Writer, apis: APIs) {
			writeMap(out)(Headers.internal, apis.internal, sourceToString, inlineVals=false)
			writeMap(out)(Headers.external, apis.external, sourceToString, inlineVals=false)
			FormatTimer.close("bytes -> base64")
			FormatTimer.close("byte copy")
			FormatTimer.close("sbinary write")
		}

		def read(in: BufferedReader): APIs = {
			val internal = readMap(in)(Headers.internal, new File(_), stringToSource)
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

		def write(out: Writer, infos: SourceInfos) { writeMap(out)(Headers.infos, infos.allInfos, sourceInfoToString, inlineVals=false) }
		def read(in: BufferedReader): SourceInfos = SourceInfos.make(readMap(in)(Headers.infos, new File(_), stringToSourceInfo))
	}

	private[this] object CompilationsF {
    object Headers {
      val compilations = "compilations"
    }

		val stringToCompilation = ObjectStringifier.stringToObj[Compilation] _
		val compilationToString = ObjectStringifier.objToString[Compilation] _

		def write(out: Writer, compilations: Compilations) {
			def toMapEntry(x: (Compilation, Int)): (String, Compilation) = "%03d".format(x._2) -> x._1
			writeMap(out)(Headers.compilations, compilations.allCompilations.zipWithIndex.map(toMapEntry).toMap, compilationToString, inlineVals=false)
		}
		def read(in: BufferedReader): Compilations =
			Compilations.make(readMap(in)(Headers.compilations, identity[String], stringToCompilation).values.toSeq)
	}

	private[this] object CompileSetupF {
    object Headers {
      val setup = "compile setup"
    }

		val stringToSetup = ObjectStringifier.stringToObj[CompileSetup] _
		val setupToString = ObjectStringifier.objToString[CompileSetup] _

		def write(out: Writer, setup: CompileSetup) { writeMap(out)(Headers.setup, Map("1" -> setup), setupToString, inlineVals=false)}
		def read(in: BufferedReader): CompileSetup = readMap(in)(Headers.setup, identity[String], stringToSetup).head._2
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

	private[this] def writeHeader(out: Writer, header: String) {
		out.write(header + ":\n")
	}

	private[this] def expectHeader(in: BufferedReader, expectedHeader: String) {
    val header = in.readLine()
    if (header != expectedHeader + ":") throw new ReadException(expectedHeader, if (header == null) "EOF" else header)
	}

	private[this] def writeSize(out: Writer, n: Int) {
		out.write("%d items\n".format(n))
	}

	private val itemsPattern = """(\d+) items""".r
	private[this] def readSize(in: BufferedReader): Int = {
		in.readLine() match {
			case itemsPattern(nStr) => Integer.parseInt(nStr)
			case s: String => throw new ReadException("\"<n> items\"", s)
      case null => throw new EOFException
		}
	}

	private[this] def writeMap[K, V](out: Writer)(header: String, m: Map[K, V], v2s: V => String, inlineVals: Boolean=true)(implicit ord: Ordering[K]) {
		writeHeader(out, header)
		writeSize(out, m.size)
		m.keys.toSeq.sorted foreach { k =>
			out.write(k.toString)
			out.write(" -> ")
			if (!inlineVals) out.write("\n")  // Put large vals on their own line, to save string munging on read.
			out.write(v2s(m(k)))
			out.write("\n")
		}
	}

	private[this] def readPairs[K, V](in: BufferedReader)(expectedHeader: String, s2k: String => K, s2v: String => V): Traversable[(K, V)] = {
		def toPair(s: String): (K, V) = {
      if (s == null) throw new EOFException
			val p = s.indexOf(" -> ")
			val k = s2k(s.substring(0, p))
			// Pair is either "a -> b" or "a -> \nb". This saves us a lot of substring munging when b is a large blob.
			val v = s2v(if (p == s.length - 4)  in.readLine() else s.substring(p + 4))
			(k, v)
		}
		expectHeader(in, expectedHeader)
    val n = readSize(in)
    for (i <- 0 until n) yield toPair(in.readLine())
	}

	private[this] def readMap[K, V](in: BufferedReader)(expectedHeader: String, s2k: String => K, s2v: String => V): Map[K, V] = {
		readPairs(in)(expectedHeader, s2k, s2v).toMap
	}
}

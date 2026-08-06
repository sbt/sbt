package sbt.librarymanagement

import java.io.{ File, PrintWriter, StringWriter }
import java.nio.ByteBuffer
import java.nio.file.{ Files, Path }
import java.nio.file.attribute.FileTime

import sbt.io.IO
import sjsonnew.HashUtil
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }
import verify.BasicTestSuite

object MemoizedFileFormatsSpec extends BasicTestSuite:
  private object Codec extends MemoizedFileFormats
  import Codec.{ fileStringLongIso, pathStringLongIso }

  // sjsonnew.HashUtil.sha256ToLong is private[sjsonnew]; reproduce its semantics via the
  // public sha256 so the tests can compare against the stock (non-memoized) hash.
  private def stockHash(path: Path): Long =
    val buf = HashUtil.sha256(path.toFile())
    if buf.length < 8 then 0L else ByteBuffer.wrap(buf).getLong()

  private def withTempFile[A](content: String)(f: Path => A): A =
    IO.withTemporaryFile("memoized-file-formats", ".txt") { file =>
      IO.write(file, content)
      f(file.toPath())
    }

  test("fileStringLongIso computes the same hash as the stock sha256"):
    withTempFile("hello, world") { path =>
      val (_, hash) = fileStringLongIso.to(path.toFile())
      assert(hash == stockHash(path))
    }

  test("pathStringLongIso computes the same hash as the stock sha256"):
    withTempFile("hello, world") { path =>
      val (_, hash) = pathStringLongIso.to(path)
      assert(hash == stockHash(path))
    }

  test("hash is served from the cache while (path, size, mtime) is unchanged"):
    withTempFile("aaaa") { path =>
      val mtime = Files.getLastModifiedTime(path)
      val (_, first) = fileStringLongIso.to(path.toFile())
      // Same-length content plus a restored mtime keeps the (path, size, mtime) cache key
      // identical, so a recomputation would be observable as a hash change.
      Files.write(path, "bbbb".getBytes("UTF-8"))
      Files.setLastModifiedTime(path, mtime)
      val (_, second) = fileStringLongIso.to(path.toFile())
      assert(second == first)
      assert(second != stockHash(path))
    }

  test("hash is recomputed when the mtime changes"):
    withTempFile("aaaa") { path =>
      val mtime = Files.getLastModifiedTime(path)
      val (_, first) = fileStringLongIso.to(path.toFile())
      Files.write(path, "bbbb".getBytes("UTF-8"))
      // A full second ahead so the change survives filesystems with coarse mtime granularity.
      Files.setLastModifiedTime(path, FileTime.fromMillis(mtime.toMillis + 1000))
      val (_, second) = fileStringLongIso.to(path.toFile())
      assert(second != first)
      assert(second == stockHash(path))
    }

  test("hash is recomputed when the size changes"):
    withTempFile("aaaa") { path =>
      val mtime = Files.getLastModifiedTime(path)
      val (_, first) = fileStringLongIso.to(path.toFile())
      Files.write(path, "bbbbbb".getBytes("UTF-8"))
      Files.setLastModifiedTime(path, mtime)
      val (_, second) = fileStringLongIso.to(path.toFile())
      assert(second != first)
      assert(second == stockHash(path))
    }

  test("a missing file hashes to 0"):
    IO.withTemporaryDirectory { dir =>
      val missing = new File(dir, "does-not-exist.txt")
      val (_, hash) = fileStringLongIso.to(missing)
      assert(hash == 0L)
    }

  test("a directory hashes to 0"):
    IO.withTemporaryDirectory { dir =>
      val (_, hash) = fileStringLongIso.to(dir)
      assert(hash == 0L)
    }

  test("round-trips a File through the iso"):
    withTempFile("hello") { path =>
      val file = path.toFile()
      val pair = fileStringLongIso.to(file)
      assert(fileStringLongIso.from(pair).getCanonicalFile == file.getCanonicalFile)
    }

  test("the memoized iso is wired into the generated LibraryManagementCodec"):
    IO.withTemporaryDirectory { dir =>
      val jar = new File(dir, "lib.jar")
      IO.write(jar, "aaaa")
      val mtime = Files.getLastModifiedTime(jar.toPath())
      val ur = report(dir, jar)
      val before = render(ur)
      // 0 is the missing-file/directory sentinel; a real hash proves the artifact was
      // actually hashed, so the unchanged-render assertion below is meaningful.
      assert(before.contains("\"second\":"))
      assert(!before.contains("\"second\":0,"))
      // Same-size rewrite plus a restored mtime keeps the cache key identical; an unchanged
      // render then proves the generated codec goes through the memoized iso (the wiring).
      IO.write(jar, "bbbb")
      Files.setLastModifiedTime(jar.toPath(), mtime)
      val after = render(ur)
      assert(after == before)
    }

  private def render(ur: UpdateReport): String =
    val js = Converter.toJson(ur)(using LibraryManagementCodec.UpdateReportFormat).get
    val out = new StringWriter
    CompactPrinter.print(js, new PrintWriter(out))
    out.toString

  private def report(dir: File, jar: File): UpdateReport =
    val modId = ModuleID("org.example", "lib", "1.0.0")
    val artifact = Artifact("lib", "jar", "jar", None, Vector.empty, None, Map.empty, None)
    val mr = ModuleReport(modId, Vector((artifact, jar)), Vector.empty)
    val descriptor = new File(dir, "ivy.xml")
    IO.touch(descriptor)
    UpdateReport(
      descriptor,
      Vector(ConfigurationReport(ConfigRef("compile"), Vector(mr), Vector.empty)),
      UpdateStats(0L, 0L, 0L, false),
      Map.empty
    )

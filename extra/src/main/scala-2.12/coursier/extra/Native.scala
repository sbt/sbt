package coursier.extra

import java.io.{File, FileInputStream, FileOutputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

import scala.collection.mutable
import scala.scalanative.{nir, tools}
import scala.sys.process._
import scala.util.Try

object Native {

  def discover(binaryName: String,
               binaryVersions: Seq[(String, String)]): File = {
    val docSetup =
      "http://www.scala-native.org/en/latest/user/setup.html"

    val envName =
      if (binaryName == "clang") "CLANG"
      else if (binaryName == "clang++") "CLANGPP"
      else binaryName

    sys.env.get(s"${envName}_PATH") match {
      case Some(path) => new File(path)
      case None =>
        val binaryNames = binaryVersions.flatMap {
          case (major, minor) =>
            Seq(s"$binaryName$major$minor", s"$binaryName-$major.$minor")
        } :+ binaryName

        Process("which" +: binaryNames).lines_!
          .map(new File(_))
          .headOption
          .getOrElse {
            sys.error(
              s"no ${binaryNames.mkString(", ")} found in $$PATH. Install clang ($docSetup)")
          }
    }
  }

  val clangVersions =
    Seq(("4", "0"), ("3", "9"), ("3", "8"), ("3", "7"))

  def checkThatClangIsRecentEnough(pathToClangBinary: File): Unit = {
    def maybeFile(f: File) = f match {
      case file if file.exists => Some(file.getAbsolutePath)
      case none                => None
    }

    def definesBuiltIn(
                        pathToClangBinary: Option[String]): Option[Seq[String]] = {
      def commandLineToListBuiltInDefines(clang: String) =
        Seq("echo", "") #| Seq(clang, "-dM", "-E", "-")
      def splitIntoLines(s: String)      = s.split(f"%n")
      def removeLeadingDefine(s: String) = s.substring(s.indexOf(' ') + 1)

      for {
        clang <- pathToClangBinary
        output = commandLineToListBuiltInDefines(clang).!!
        lines  = splitIntoLines(output)
      } yield lines map removeLeadingDefine
    }

    val clang                = maybeFile(pathToClangBinary)
    val defines: Seq[String] = definesBuiltIn(clang).to[Seq].flatten
    val clangIsRecentEnough =
      defines.contains("__DECIMAL_DIG__ __LDBL_DECIMAL_DIG__")

    if (!clangIsRecentEnough) {
      sys.error(
        s"No recent installation of clang found " +
          s"at $pathToClangBinary.\nSee http://scala-native.readthedocs.io" +
          s"/en/latest/user/setup.html for details.")
    }
  }

  implicit class FileOps(val f: File) extends AnyVal {
    def **(expression: String): Seq[File] = {
      def quote(s: String) = if (s.isEmpty) "" else Pattern.quote(s.replaceAll("\n", """\n"""))
      val pattern = Pattern.compile(expression.split("\\*", -1).map(quote).mkString(".*"))
      **(pattern)
    }

    def **(pattern: Pattern): Seq[File] =
      if (f.isDirectory)
        f.listFiles().flatMap(_.**(pattern))
      else if (pattern.matcher(f.getName).matches())
        Seq(f)
      else
        Nil
  }

  def include(linkerResult: tools.LinkerResult, gc: String, path: String) = {
    val sep = java.io.File.separator

    if (path.contains(sep + "optional" + sep)) {
      val name = new File(path).getName.split("\\.").head
      linkerResult.links.map(_.name).contains(name)
    } else if (path.contains(sep + "gc" + sep)) {
      path.contains("gc" + sep + gc)
    } else {
      true
    }
  }

  sealed abstract class GarbageCollector(val name: String,
                                         val links: Seq[String] = Nil)
  object GarbageCollector {
    object None  extends GarbageCollector("none")
    object Boehm extends GarbageCollector("boehm", Seq("gc"))
    object Immix extends GarbageCollector("immix")
  }

  def garbageCollector(gc: String) = gc match {
    case "none"  => GarbageCollector.None
    case "boehm" => GarbageCollector.Boehm
    case "immix" => GarbageCollector.Immix
    case value =>
      sys.error("nativeGC can be either \"none\", \"boehm\" or \"immix\", not: " + value)
  }

  def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)

    f.delete()
  }

  def hash(f: File): Array[Byte] = {

    val md = MessageDigest.getInstance("SHA-1")

    val is = new FileInputStream(f)
    try withContent(is, md.update(_, 0, _))
    finally is.close()

    md.digest()
  }

  def unzip(from: File, toDirectory: File): Set[File] = {

    toDirectory.mkdirs()

    def extract(from: ZipInputStream, toDirectory: File) = {
      val set = new mutable.HashSet[File]
      def next(): Unit = {
        val entry = from.getNextEntry
        if (entry != null) {
          val name = entry.getName
          val target = new File(toDirectory, name)

          if (entry.isDirectory)
            target.mkdirs()
          else {
            set += target
            target.getParentFile.mkdirs()

            var fos: FileOutputStream = null

            try {
              fos = new FileOutputStream(target)
              withContent(
                from,
                (b, n) => fos.write(b, 0, n)
              )
            } finally {
              if (fos != null) fos.close()
            }
          }

          target.setLastModified(entry.getTime)
          from.closeEntry()
          next()
        }
      }
      next()
      Set() ++ set
    }

    var fis: InputStream = null
    var zis: ZipInputStream = null

    try {
      fis = new FileInputStream(from)
      zis = new ZipInputStream(fis)
      extract(zis, toDirectory)
    } finally {
      if (fis != null) fis.close()
      if (zis != null) zis.close()
    }
  }

  private def withContent(is: InputStream, f: (Array[Byte], Int) => Unit): Unit = {
    val data = Array.ofDim[Byte](16384)

    var nRead = is.read(data, 0, data.length)
    while (nRead != -1) {
      f(data, nRead)
      nRead = is.read(data, 0, data.length)
    }
  }


  def create(
    mainClass: String,
    files: Seq[File],
    output0: File,
    wd: File,
    log: String => Unit = s => Console.err.println(s),
    verbosity: Int = 0
  ) = {

    val entry = nir.Global.Top(mainClass + "$")

    val clang = {
      val clang = Native.discover("clang", Native.clangVersions)
      Native.checkThatClangIsRecentEnough(clang)
      clang
    }

    val clangpp = {
      val clang = Native.discover("clang++", Native.clangVersions)
      Native.checkThatClangIsRecentEnough(clang)
      clang
    }

    val nativeTarget = {
      // Use non-standard extension to not include the ll file when linking (#639)
      val targetc  = new File(wd, "target/c.probe")
      val targetll = new File(wd, "target/ll.probe")
      val compilec =
        Seq(
          clang.getAbsolutePath,
          "-S",
          "-xc",
          "-emit-llvm",
          "-o",
          targetll.getAbsolutePath,
          targetc.getAbsolutePath
        )

      def fail =
        sys.error("Failed to detect native target.")

      targetc.getParentFile.mkdirs()
      Files.write(targetc.toPath, "int probe;".getBytes(StandardCharsets.UTF_8))
      Console.err.println(compilec)
      val exit = sys.process.Process(compilec, wd).!
      if (exit == 0)
        scala.io.Source.fromFile(targetll)(scala.io.Codec.UTF8)
          .getLines()
          .collectFirst {
            case line if line.startsWith("target triple") =>
              line.split("\"").apply(1)
          }
          .getOrElse(fail)
      else
        fail
    }

    def running(command: Seq[String]): Unit =
      if (verbosity >= 2)
        log("running" + System.lineSeparator() + command.mkString(System.lineSeparator() + "\t"))

    log(s"${files.length} files in classpath:")
    if (verbosity >= 1)
      for (f <- files)
        log(f.toString)

    val nativeMode: tools.Mode = tools.Mode.Debug

    val config =
      tools.Config.empty
        .withEntry(entry)
        .withPaths(files)
        .withWorkdir(wd)
        .withTarget(nativeTarget)
        .withMode(nativeMode)

    val driver =
      tools.OptimizerDriver(config)

    val linkingReporter =
      tools.LinkerReporter.empty


    log("Linking")
    val linkerResult = tools.link(config, driver, linkingReporter)


    if (linkerResult.unresolved.isEmpty) {
      val classCount = linkerResult.defns.count {
        case _: nir.Defn.Class | _: nir.Defn.Module | _: nir.Defn.Trait => true
        case _                                                          => false
      }

      val methodCount = linkerResult.defns.count(_.isInstanceOf[nir.Defn.Define])

      log(s"Discovered $classCount classes and $methodCount methods")
    } else {
      for (signature <- linkerResult.unresolved.map(_.show).sorted)
        log(s"cannot link: $signature")

      sys.error("unable to link")
    }


    val optimizeReporter = tools.OptimizerReporter.empty

    log("Optimizing")
    val optimized = tools.optimize(config, driver, linkerResult.defns, linkerResult.dyns, optimizeReporter)

    log("Generating intermediate code")
    tools.codegen(config, optimized)
    val generated = wd ** "*.ll"

    log(s"Produced ${generated.length} files")

    log("Compiling to native code")

    val compileOpts = {
      val includes = {
        val includedir =
          Try(Process("llvm-config --includedir").lines_!)
            .getOrElse(Seq.empty)
        ("/usr/local/include" +: includedir).map(s => s"-I$s")
      }
      includes :+ "-Qunused-arguments" :+
        (nativeMode match {
          case tools.Mode.Debug   => "-O0"
          case tools.Mode.Release => "-O2"
        })
    }

    val apppaths = generated
      .par
      .map { ll =>
        val apppath = ll.getAbsolutePath
        val outpath = apppath + ".o"
        val compile = Seq(clangpp.getAbsolutePath, "-c", apppath, "-o", outpath) ++ compileOpts
        running(compile)
        Process(compile, wd).!
        new File(outpath)
      }
      .seq


    // this unpacks extra source files
    val nativelib = {

      val lib = new File(wd, "lib")
      val jar =
        files
          .map(entry => entry.getAbsolutePath)
          .collectFirst {
            case p if p.contains("scala-native") && p.contains("nativelib") =>
              new File(p)
          }
          .get
      val jarhash     = Native.hash(jar).toSeq
      val jarhashfile = new File(lib, "jarhash")
      val unpacked =
        lib.exists &&
          jarhashfile.exists() &&
          jarhash == Files.readAllBytes(jarhashfile.toPath).toSeq

      if (!unpacked) {
        Native.deleteRecursive(lib)
        Native.unzip(jar, lib)
        Files.write(jarhashfile.toPath, Native.hash(jar))
      }

      lib
    }

    val cpaths   = (wd ** "*.c").map(_.getAbsolutePath)
    val cpppaths = (wd ** "*.cpp").map(_.getAbsolutePath)
    val paths    = cpaths ++ cpppaths

    val gc = "boehm"

    for (path <- paths if !Native.include(linkerResult, gc, path)) {
      val ofile = new File(path + ".o")
      if (ofile.exists())
        ofile.delete()
    }

    val nativeCompileOptions = {
      val includes = {
        val includedir =
          Try(Process("llvm-config --includedir").lines_!)
            .getOrElse(Seq.empty)
        ("/usr/local/include" +: includedir).map(s => s"-I$s")
      }
      includes :+ "-Qunused-arguments" :+
        (nativeMode match {
          case tools.Mode.Debug   => "-O0"
          case tools.Mode.Release => "-O2"
        })
    }

    val opts = nativeCompileOptions ++ Seq("-O2")

    paths
      .par
      .map { path =>
        val opath = path + ".o"
        if (Native.include(linkerResult, gc, path) && !new File(opath).exists()) {
          val isCpp    = path.endsWith(".cpp")
          val compiler = (if (isCpp) clangpp else clang).getAbsolutePath
          val flags    = (if (isCpp) Seq("-std=c++11") else Seq()) ++ opts
          val compilec = Seq(compiler) ++ flags ++ Seq("-c", path, "-o", opath)

          running(compilec)
          val result = Process(compilec, wd).!
          if (result != 0)
            sys.error("Failed to compile native library runtime code.")
          result
        }
      }
      .seq


    val links = {
      val os   = Option(sys.props("os.name")).getOrElse("")
      val arch = nativeTarget.split("-").head
      // we need re2 to link the re2 c wrapper (cre2.h)
      val librt = os match {
        case "Linux" => Seq("rt")
        case _       => Seq.empty
      }
      val libunwind = os match {
        case "Mac OS X" => Seq.empty
        case _          => Seq("unwind", "unwind-" + arch)
      }
      librt ++ libunwind ++ linkerResult.links
        .map(_.name) ++ Native.garbageCollector(gc).links
    }

    val nativeLinkingOptions = {
      val libs = {
        val libdir =
          Try(Process("llvm-config --libdir").lines_!)
            .getOrElse(Seq.empty)
        ("/usr/local/lib" +: libdir).map(s => s"-L$s")
      }
      libs
    }

    val linkopts  = links.map("-l" + _) ++ nativeLinkingOptions
    val targetopt = Seq("-target", nativeTarget)
    val flags     = Seq("-o", output0.getAbsolutePath) ++ linkopts ++ targetopt
    val opaths    = (nativelib ** "*.o").map(_.getAbsolutePath)
    val paths0    = apppaths.map(_.getAbsolutePath) ++ opaths
    val compile   = clangpp.getAbsolutePath +: (flags ++ paths0)

    log("Linking native code")
    running(compile)
    Process(compile, wd).!
  }

}

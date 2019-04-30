import java.nio.file.{ Files, Path }
import scala.sys.process._

val compileLib = taskKey[Seq[Path]]("Compile the library")
compileLib / sourceDirectory := sourceDirectory.value / "lib"
compileLib / fileInputs := {
  val base: Glob = (compileLib / sourceDirectory).value.toGlob
  base / ** / "*.c" :: base / "include" / "*.h" :: Nil
}
compileLib / target := baseDirectory.value / "out" / "objects"
compileLib := {
  val allFiles: Seq[Path] = (compileLib / allInputFiles).value
  val changedFiles: Option[Seq[Path]] = (compileLib / changedInputFiles).value match {
    case Some(ChangedFiles(c, _, u)) => Some(c ++ u)
    case None => None
  }
  val include = (compileLib / sourceDirectory).value / "include"
  val objectDir: Path = (compileLib / target).value.toPath / "objects"
  val logger = streams.value.log
  def objectFileName(path: Path): String = {
    val name = path.getFileName.toString
    name.substring(0, name.lastIndexOf('.')) + ".o"
  }
  compileLib.previous match {
    case Some(outputs: Seq[Path]) if changedFiles.isEmpty =>
      logger.info("Not compiling libfoo: no inputs have changed.")
      outputs
    case _ =>
      Files.createDirectories(objectDir)
      def extensionFilter(ext: String): Path => Boolean = _.getFileName.toString.endsWith(s".$ext")
      val cFiles: Seq[Path] =
        if (changedFiles.fold(false)(_.exists(extensionFilter("h")))) allFiles.filter(extensionFilter("c"))
        else changedFiles.getOrElse(allFiles).filter(extensionFilter("c"))
      cFiles.map { file =>
        val outFile = objectDir.resolve(objectFileName(file))
        logger.info(s"Compiling $file to $outFile")
        Seq("gcc", "-c", file.toString, s"-I$include", "-o", outFile.toString).!!
        outFile
      }
  }
}

val linkLib = taskKey[Path]("")
linkLib / target := baseDirectory.value / "out" / "lib"
linkLib := {
  val changedObjects = (compileLib / changedOutputFiles).value
  val outPath = (linkLib / target).value.toPath
  val allObjects = (compileLib / allOutputFiles).value.map(_.toString)
  val logger = streams.value.log
  linkLib.previous match {
    case Some(p: Path) if changedObjects.isEmpty =>
      logger.info("Not running linker: no outputs have changed.")
      p
    case _ =>
      val (linkOptions, libraryPath) = if (scala.util.Properties.isMac) {
        val path = outPath.resolve("libfoo.dylib")
        (Seq("-dynamiclib", "-o", path.toString), path)
      } else {
        val path = outPath.resolve("libfoo.so")
        (Seq("-shared", "-fPIC", "-o", path.toString), path)
      }
      logger.info(s"Linking $libraryPath")
      Files.createDirectories(outPath)
      ("gcc" +: (linkOptions ++ allObjects)).!!
      libraryPath
  }
}

val compileMain = taskKey[Path]("compile main")
compileMain / sourceDirectory := sourceDirectory.value / "main"
compileMain / fileInputs := (compileMain / sourceDirectory).value.toGlob / "main.c" :: Nil
compileMain / target := baseDirectory.value / "out" / "main"
compileMain := {
  val library = linkLib.value
  val changed: Boolean = (compileMain / changedInputFiles).value.nonEmpty ||
    (linkLib / changedOutputFiles).value.nonEmpty
  val include = (compileLib / sourceDirectory).value / "include"
  val logger = streams.value.log
  val outDir = (compileMain / target).value.toPath
  val outPath = outDir.resolve("main.out")
  compileMain.previous match {
    case Some(p: Path) if changed =>
      logger.info(s"Not building $outPath: no dependencies have changed")
      p
    case _ =>
      (compileMain / allInputFiles).value match {
        case Seq(main) =>
          Files.createDirectories(outDir)
          logger.info(s"Building executable $outPath")
          Seq(
            "gcc",
            main.toString,
            s"-I$include",
            "-o",
            outPath.toString,
            s"-L${library.getParent}",
            "-lfoo"
          ).!!
          outPath
        case main =>
          throw new IllegalStateException(s"multiple main files detected: ${main.mkString(",")}")
      }
  }
}

val executeMain = inputKey[Unit]("run the main method")
executeMain := {
  val args = Def.spaceDelimited("<arguments>").parsed
  val binary: Seq[Path] = (compileMain / allOutputFiles).value
  val logger = streams.value.log
  binary match {
    case Seq(b) =>
      val argString =
        if (args.nonEmpty) s" with arguments: ${args.mkString("'", "', '", "'")}" else ""
      logger.info(s"Running $b$argString")
      logger.info(RunBinary(b, args, linkLib.value).mkString("\n"))

    case b =>
      throw new IllegalArgumentException(
        s"compileMain generated multiple binaries: ${b.mkString(", ")}"
      )
  }
}

val checkOutput = inputKey[Unit]("check the output value")
checkOutput := {
  val args @ Seq(arg, res) = Def.spaceDelimited("").parsed
  val binary: Path = (compileMain / allOutputFiles).value.head
  val output = RunBinary(binary, args, linkLib.value)
  assert(output.contains(s"f($arg) = $res"))
  ()
}

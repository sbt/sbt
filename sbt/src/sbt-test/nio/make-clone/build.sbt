import java.nio.file.{ Files, Path }
import scala.sys.process._

val compileLib = taskKey[Seq[Path]]("Compile the library")
compileLib / sourceDirectory := sourceDirectory.value / "lib"
compileLib / fileInputs := {
  val base: Glob = (compileLib / sourceDirectory).value.toGlob
  base / ** / "*.c" :: base / "include" / "*.h" :: Nil
}
compileLib / target := baseDirectory.value / "out" / "lib"
compileLib := {
  val inputs: Seq[Path] = (compileLib / changedInputFiles).value
  val include = (compileLib / sourceDirectory).value / "include"
  val objectDir: Path = (compileLib / target).value.toPath / "objects"
  val logger = streams.value.log
  def objectFileName(path: Path): String = {
    val name = path.getFileName.toString
    name.substring(0, name.lastIndexOf('.')) + ".o"
  }
  compileLib.previous match {
    case Some(outputs: Seq[Path]) if inputs.isEmpty =>
      logger.info("Not compiling libfoo: no inputs have changed.")
      outputs
    case _ =>
      Files.createDirectories(objectDir)
      def extensionFilter(ext: String): Path => Boolean = _.getFileName.toString.endsWith(s".$ext")
      val allInputs = (compileLib / allInputFiles).value
      val cFiles: Seq[Path] =
        if (inputs.exists(extensionFilter("h"))) allInputs.filter(extensionFilter("c"))
        else inputs.filter(extensionFilter("c"))
      cFiles.map { file =>
        val outFile = objectDir.resolve(objectFileName(file))
        logger.info(s"Compiling $file to $outFile")
        Seq("gcc", "-c", file.toString, s"-I$include", "-o", outFile.toString).!!
        outFile
      }
  }
}

val linkLib = taskKey[Path]("")
linkLib := {
  val objects = (compileLib / changedOutputPaths).value
  val outPath = (compileLib / target).value.toPath
  val allObjects = (compileLib / allOutputPaths).value.map(_.toString)
  val logger = streams.value.log
  linkLib.previous match {
    case Some(p: Path) if objects.isEmpty =>
      logger.info("Not running linker: no outputs have changed.")
      p
    case _ =>
      val (linkOptions, libraryPath) = if (scala.util.Properties.isMac) {
        val path = outPath.resolve("libfoo.dylib")
        (Seq("-dynamiclib", "-o", path.toString), path)
      } else {
        val path = outPath.resolve("libfoo.so")
        (Seq("-shared", "-o", path.toString), path)
      }
      logger.info(s"Linking $libraryPath")
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
  val changed = (compileMain / changedInputFiles).value ++ (linkLib / changedOutputPaths).value
  val include = (compileLib / sourceDirectory).value / "include"
  val logger = streams.value.log
  val outDir = (compileMain / target).value.toPath
  val outPath = outDir.resolve("main.out")
  compileMain.previous match {
    case Some(p: Path) if changed.isEmpty =>
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
  val binary = (compileMain / allOutputPaths).value
  val logger = streams.value.log
  binary match {
    case Seq(b) =>
      val argString =
        if (args.nonEmpty) s" with arguments: ${args.mkString("'", "', '", "'")}" else ""
      logger.info(s"Running $b$argString")
      logger.info((b.toString +: args).!!)
    case b =>
      throw new IllegalArgumentException(
        s"compileMain generated multiple binaries: ${b.mkString(", ")}"
      )
  }
}

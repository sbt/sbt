import java.nio.file.{ Files, Path }
import scala.sys.process._

val compileOpts = settingKey[Seq[String]]("Extra compile options")
compileOpts := { "-fPIC" :: "-std=gnu99" :: Nil }
val compileLib = taskKey[Seq[Path]]("Compile the library")
compileLib / sourceDirectory := sourceDirectory.value / "lib"
compileLib / fileInputs := {
  val base: Glob = (compileLib / sourceDirectory).value.toGlob
  base / ** / "*.c" :: base / "include" / "*.h" :: Nil
}
compileLib / target := baseDirectory.value / "out" / "objects"
compileLib := {
  val outputDir = Files.createDirectories(streams.value.cacheDirectory.toPath)
  val logger = streams.value.log
  val include = (compileLib / sourceDirectory).value / "include"
  def outputPath(path: Path): Path =
    outputDir / path.getFileName.toString.replaceAll(".c$", ".o")
  def compile(path: Path): Path = {
    val output = outputPath(path)
    logger.info(s"Compiling $path to $output")
    Seq("gcc", "-fPIC", "-std=gnu99", s"-I$include", "-c", s"$path", "-o", s"$output").!!
    output
  }
  val report = compileLib.inputFileChanges
  val sourceMap = compileLib.inputFiles.view.collect {
    case p: Path if p.getFileName.toString.endsWith(".c") => outputPath(p) -> p
  }.toMap
  val existingTargets = fileTreeView.value.list(outputDir.toGlob / **).flatMap { case (p, _) =>
    if (!sourceMap.contains(p)) {
      Files.deleteIfExists(p)
      None
    } else {
      Some(p)
    }
  }.toSet
  val updatedPaths = (report.created ++ report.modified).toSet
  val needCompile =
    if (updatedPaths.exists(_.getFileName.toString.endsWith(".h"))) sourceMap.values
    else updatedPaths ++ sourceMap.filterKeys(!existingTargets(_)).values
  needCompile.foreach(compile)
  sourceMap.keys.toVector
}

val linkLib = taskKey[Path]("")
linkLib / target := baseDirectory.value / "out" / "lib"
linkLib := {
  val outputDir = Files.createDirectories(streams.value.cacheDirectory.toPath)
  val logger = streams.value.log
  val isMac = scala.util.Properties.isMac
  val library = outputDir / s"libfoo.${if (isMac) "dylib" else "so"}"
  val (report, objects) = (compileLib.outputFileChanges, compileLib.outputFiles)
  val linkOpts = if (isMac) Seq("-dynamiclib") else Seq("-shared", "-fPIC")
  if (report.hasChanges || !Files.exists(library)) {
    logger.info(s"Linking $library")
    (Seq("gcc") ++ linkOpts ++ Seq("-o", s"$library") ++ objects.map(_.toString)).!!
  } else {
    logger.debug(s"Skipping linking of $library")
  }
  library
}

val compileMain = taskKey[Path]("compile main")
compileMain / sourceDirectory := sourceDirectory.value / "main"
compileMain / fileInputs := (compileMain / sourceDirectory).value.toGlob / "main.c" :: Nil
compileMain / target := baseDirectory.value / "out" / "main"
compileMain := {
  val library = linkLib.value
  val changed: Boolean = compileMain.inputFileChanges.hasChanges ||
    linkLib.outputFileChanges.hasChanges
  val include = (compileLib / sourceDirectory).value / "include"
  val logger = streams.value.log
  val outDir = (compileMain / target).value.toPath
  val outPath = outDir.resolve("main.out")
  val inputs = compileMain.inputFiles
  if (changed || !Files.exists(outPath)) {
    inputs match {
      case Seq(main) =>
        Files.createDirectories(outDir)
        logger.info(s"Building executable $outPath")
        (Seq("gcc") ++ compileOpts.value ++ Seq(
          main.toString,
          s"-I$include",
          "-o",
          outPath.toString,
          s"-L${library.getParent}",
          "-lfoo"
        )).!!
      case main =>
        throw new IllegalStateException(s"multiple main files detected: ${main.mkString(",")}")
    }
  } else {
    logger.info(s"Not building $outPath: no dependencies have changed")
  }
  outPath
}

val executeMain = inputKey[Unit]("run the main method")
executeMain := {
  val args = Def.spaceDelimited("<arguments>").parsed
  val binary: Seq[Path] = compileMain.outputFiles
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
  val Seq(arg, res) = Def.spaceDelimited("").parsed
  val binary: Path = compileMain.outputFiles.head
  val output = RunBinary(binary, arg :: Nil, linkLib.value)
  assert(output.contains(s"f($arg) = $res"))
  ()
}

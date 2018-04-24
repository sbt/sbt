package coursier.extra

import java.io.File
import java.nio.file.Path
import scala.scalanative.{build => sn}

object Native {
  def create(
    mainClass: String,
    files: Seq[File],
    output0: File,
    wd: File,
    log: String => Unit = s => Console.err.println(s),
    verbosity: Int = 0
  ): Unit = {
    val classpath: Seq[Path] = files.map(_.toPath)
    val workdir: Path        = wd.toPath
    val main: String         = mainClass + "$"
    val outpath              = output0.toPath

    val logger    = sn.Logger(log, log, log, log)
    val clang     = sn.Discover.clang()
    val clangpp   = sn.Discover.clangpp()
    val linkopts  = sn.Discover.linkingOptions()
    val compopts  = sn.Discover.compileOptions()
    val triple    = sn.Discover.targetTriple(clang, workdir)
    val nativelib = sn.Discover.nativelib(classpath).get

    val config =
      sn.Config.empty
        .withGC(sn.GC.immix)
        .withMode(sn.Mode.release)
        .withClang(clang)
        .withClangPP(clangpp)
        .withLinkingOptions(linkopts)
        .withCompileOptions(compopts)
        .withTargetTriple(triple)
        .withNativelib(nativelib)
        .withMainClass(main)
        .withClassPath(classpath)
        .withLinkStubs(true)
        .withWorkdir(workdir)

    sn.Build.build(config, outpath)
  }

  def deleteRecursive(f: File): Unit = {
    if (f.isDirectory) {
      f.listFiles().foreach(deleteRecursive)
    }
    f.delete()
  }
}

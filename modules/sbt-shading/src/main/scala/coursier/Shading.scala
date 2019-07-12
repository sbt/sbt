package coursier

import java.io.{File, FileInputStream}
import java.util.jar.JarInputStream
import java.util.zip.{ZipEntry, ZipInputStream}

import coursier.core.{Configuration, Orders}
import org.pantsbuild.jarjar._
import org.pantsbuild.jarjar.util.CoursierJarProcessor

object Shading {

  // FIXME Also vaguely in cli
  def zipEntries(zipStream: ZipInputStream): Iterator[ZipEntry] =
    new Iterator[ZipEntry] {
      var nextEntry = Option.empty[ZipEntry]
      def update() =
        nextEntry = Option(zipStream.getNextEntry)

      update()

      def hasNext = nextEntry.nonEmpty
      def next() = {
        val ent = nextEntry.get
        update()
        ent
      }
    }

  def jarClassNames(jar: File): Seq[String] = {

    var fis: FileInputStream = null
    var zis: JarInputStream = null

    try {
      fis = new FileInputStream(jar)
      zis = new JarInputStream(fis)

      zipEntries(zis)
        .map(_.getName)
        .filter(_.endsWith(".class"))
        .map(_.stripSuffix(".class").replace('/', '.'))
        .toVector
    } finally {
      if (zis != null)
        zis.close()
      if (fis != null)
        fis.close()
    }
  }

  def toShadeJars(
    currentProject: Project,
    res: Resolution,
    configs: Map[Configuration, Set[Configuration]],
    artifactFilesOrErrors: Map[Artifact, File],
    classpathTypes: Set[Type],
    baseConfig: Configuration,
    shadedConf: Configuration,
    log: sbt.Logger
  ): Seq[File] = {

    def configDependencies(config: Configuration) = {

      def minDependencies(dependencies: Seq[Dependency]): Seq[Dependency] =
        Orders.minDependencies(
          dependencies.toSet,
          dep =>
            res
              .projectCache
              .get(dep)
              .map(_._2.configurations)
              .getOrElse(Map.empty)
        ).toSeq // sort so that this is deterministic?

      val includedConfigs = configs.getOrElse(config, Set.empty) + config

      minDependencies(
        currentProject
          .dependencies
          .collect {
            case (cfg, dep) if includedConfigs(cfg) =>
              dep
          }
      )
    }

    val dependencyArtifacts = res
      .dependencyArtifacts()
      .filter { case (_, attr, _) => classpathTypes(attr.`type`) }
      .groupBy(_._1)
      .mapValues(_.map(t => (t._2, t._3)))
      .iterator
      .toMap

    val artifactFilesOrErrors0 = artifactFilesOrErrors
      .collect {
        case (a, f) => a.url -> f
      }

    val compileDeps = configDependencies(baseConfig)
    val shadedDeps = configDependencies(shadedConf)

    val compileOnlyDeps = compileDeps.filterNot(shadedDeps.toSet)

    log.debug(
      s"Found ${compileDeps.size} dependencies in $baseConfig\n" +
        compileDeps.toVector.map("  " + _).sorted.mkString("\n")
    )
    log.debug(
      s"Found ${compileOnlyDeps.size} dependencies only in $baseConfig\n" +
        compileOnlyDeps.toVector.map("  " + _).sorted.mkString("\n")
    )
    log.debug(
      s"Found ${shadedDeps.size} dependencies in $shadedConf\n" +
        shadedDeps.toVector.map("  " + _).sorted.mkString("\n")
    )

    def files(deps: Seq[Dependency]) = res
      .subset(deps)
      .dependencies
      .toSeq
      .flatMap(dependencyArtifacts.get)
      .flatten
      .map(_._2.url)
      .flatMap(artifactFilesOrErrors0.get)

    val noShadeJars = files(compileOnlyDeps)
    val allShadedConfJars = files(shadedDeps)

    log.debug(
      s"Found ${noShadeJars.length} JAR(s) only in $baseConfig\n" +
        noShadeJars.map("  " + _).sorted.mkString("\n")
    )
    log.debug(
      s"Found ${allShadedConfJars.length} JAR(s) in $shadedConf\n" +
        allShadedConfJars.map("  " + _).sorted.mkString("\n")
    )

    allShadedConfJars.filterNot(noShadeJars.toSet)
  }

  def toShadeClasses(
    shadeNamespaces: Set[String],
    toShadeJars: Seq[File],
    log: sbt.Logger
  ): Seq[String] = {

    log.info(
      s"Shading ${toShadeJars.length} JAR(s):\n" +
        toShadeJars.map("  " + _).sorted.mkString("\n")
    )

    val toShadeClasses0 = toShadeJars.flatMap(jarClassNames)

    log.info(s"Found ${toShadeClasses0.length} class(es) in JAR(s) to be shaded")
    log.debug(toShadeClasses0.map("  " + _).sorted.mkString("\n"))

    val toShadeClasses = shadeNamespaces.toVector.sorted.foldLeft(toShadeClasses0) {
      (toShade, namespace) =>
        val prefix = namespace + "."
        val (filteredOut, remaining) = toShade.partition(_.startsWith(prefix))

        log.info(s"${filteredOut.length} classes already filtered out by shaded namespace $namespace")
        log.debug(filteredOut.map("  " + _).sorted.mkString("\n"))

        remaining
    }

    if (shadeNamespaces.nonEmpty) {
      log.info(s"${toShadeClasses.length} remaining class(es) to be shaded")
      log.debug(toShadeClasses.map("  " + _).sorted.mkString("\n"))
    }

    toShadeClasses
  }

  def createPackage(
    baseJar: File,
    shadingNamespace: String,
    shadeNamespaces: Set[String],
    toShadeClasses: Seq[String],
    toShadeJars: Seq[File]
  ) = {

    val outputJar = new File(
      baseJar.getParentFile,
      baseJar.getName.stripSuffix(".jar") + "-shading.jar"
    )

    def rename(from: String, to: String): Rule = {
      val rule = new Rule
      rule.setPattern(from)
      rule.setResult(to)
      rule
    }

    val nsRules = shadeNamespaces.toVector.sorted.map { namespace =>
      rename(namespace + ".**", shadingNamespace + ".@0")
    }
    val clsRules = toShadeClasses.map { cls =>
      rename(cls, shadingNamespace + ".@0")
    }

    val processor = JJProcessor(nsRules ++ clsRules, verbose = true, skipManifest = false)
    CoursierJarProcessor.run((baseJar +: toShadeJars).toArray, outputJar, processor.proc, true)

    outputJar
  }

}

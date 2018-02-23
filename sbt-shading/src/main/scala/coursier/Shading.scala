package coursier

import java.io.{File, FileInputStream}
import java.util.jar.JarInputStream
import java.util.zip.{ZipEntry, ZipInputStream}

import com.tonicsystems.jarjar.classpath.ClassPath
import com.tonicsystems.jarjar.transform.JarTransformer
import com.tonicsystems.jarjar.transform.config.ClassRename
import com.tonicsystems.jarjar.transform.jar.DefaultJarProcessor
import coursier.core.Orders
import sbt.file

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
    configs: Map[String, Set[String]],
    artifactFilesOrErrors: Map[Artifact, Either[FileError, File]],
    classpathTypes: Set[String],
    baseConfig: String,
    shadedConf: String,
    log: sbt.Logger
  ): Seq[File] = {

    def configDependencies(config: String) = {

      def minDependencies(dependencies: Set[Dependency]): Set[Dependency] =
        Orders.minDependencies(
          dependencies,
          dep =>
            res
              .projectCache
              .get(dep)
              .map(_._2.configurations)
              .getOrElse(Map.empty)
        )

      val includedConfigs = configs.getOrElse(config, Set.empty) + config

      minDependencies(
        currentProject
          .dependencies
          .collect {
            case (cfg, dep) if includedConfigs(cfg) =>
              dep
          }
          .toSet
      )
    }

    val dependencyArtifacts = res
      .dependencyArtifacts(withOptional = true)
      .filter { case (_, a) => classpathTypes(a.`type`) }
      .groupBy(_._1)
      .mapValues(_.map(_._2))
      .iterator
      .toMap

    val artifactFilesOrErrors0 = artifactFilesOrErrors
      .collect {
        case (a, Right(f)) => a.url -> f
      }

    val compileDeps = configDependencies(baseConfig)
    val shadedDeps = configDependencies(shadedConf)

    val compileOnlyDeps = compileDeps.filterNot(shadedDeps)

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

    def files(deps: Set[Dependency]) = res
      .subset(deps)
      .dependencies
      .toSeq
      .flatMap(dependencyArtifacts.get)
      .flatten
      .map(_.url)
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

    val processor = new DefaultJarProcessor

    for (namespace <- shadeNamespaces)
      processor.addClassRename(new ClassRename(namespace + ".**", shadingNamespace + ".@0"))

    for (cls <- toShadeClasses)
      processor.addClassRename(new ClassRename(cls, shadingNamespace + ".@0"))

    val transformer = new JarTransformer(outputJar, processor)
    val cp = new ClassPath(file(sys.props("user.dir")), (baseJar +: toShadeJars).toArray)
    transformer.transform(cp)

    outputJar
  }

}

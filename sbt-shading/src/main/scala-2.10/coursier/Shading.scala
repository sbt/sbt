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

import scalaz.{\/, \/-}

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

  def createPackage(
    baseJar: File,
    currentProject: Project,
    res: Resolution,
    configs: Map[String, Set[String]],
    artifactFilesOrErrors: Map[Artifact, FileError \/ File],
    classpathTypes: Set[String],
    shadingNamespace: String,
    baseConfig: String,
    shadedConf: String,
    log: sbt.Logger
  ) = {

    val outputJar = new File(
      baseJar.getParentFile,
      baseJar.getName.stripSuffix(".jar") + "-shading.jar"
    )

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

    val dependencyArtifacts = res.dependencyArtifacts
      .filter { case (_, a) => classpathTypes(a.`type`) }
      .groupBy(_._1)
      .mapValues(_.map(_._2))
      .iterator
      .toMap

    val artifactFilesOrErrors0 = artifactFilesOrErrors
      .collect {
        case (a, \/-(f)) => a.url -> f
      }

    val compileDeps = configDependencies(baseConfig)
    val shadedDeps = configDependencies(shadedConf)

    val compileOnlyDeps = compileDeps.filterNot(shadedDeps)

    log.info(s"Found ${compileDeps.size} dependencies in $baseConfig")
    log.debug(compileDeps.toVector.map("  " + _).sorted.mkString("\n"))
    log.info(s"Found ${compileOnlyDeps.size} dependencies only in $baseConfig")
    log.debug(compileOnlyDeps.toVector.map("  " + _).sorted.mkString("\n"))
    log.info(s"Found ${shadedDeps.size} dependencies in $shadedConf")
    log.debug(shadedDeps.toVector.map("  " + _).sorted.mkString("\n"))

    def files(deps: Set[Dependency]) = res
      .subset(deps)
      .dependencies
      .toSeq
      .flatMap(dependencyArtifacts.get)
      .flatten
      .map(_.url)
      .flatMap(artifactFilesOrErrors0.get)

    val compileOnlyJars = files(compileOnlyDeps)
    val shadedJars = files(shadedDeps)

    log.info(s"Found ${compileOnlyJars.length} JAR(s) only in $baseConfig")
    log.debug(compileOnlyJars.map("  " + _).sorted.mkString("\n"))
    log.info(s"Found ${shadedJars.length} JAR(s) in $shadedConf")
    log.debug(shadedJars.map("  " + _).sorted.mkString("\n"))

    val shadeJars = shadedJars.filterNot(compileOnlyJars.toSet)
    val shadeClasses = shadeJars.flatMap(Shading.jarClassNames)

    log.info(s"Will shade ${shadeClasses.length} class(es)")
    log.debug(shadeClasses.map("  " + _).sorted.mkString("\n"))

    val processor = new DefaultJarProcessor
    for (cls <- shadeClasses)
      processor.addClassRename(new ClassRename(cls, shadingNamespace + ".@0"))

    val transformer = new JarTransformer(outputJar, processor)
    val cp = new ClassPath(file(sys.props("user.dir")), (baseJar +: shadeJars).toArray)
    transformer.transform(cp)

    outputJar
  }

}
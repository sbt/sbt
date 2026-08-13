ThisBuild / scalaVersion := "2.13.18"
ThisBuild / usePipelining := true

def countedCompilers(counter: File)(cs: xsbti.compile.Compilers): xsbti.compile.Compilers = {
  val tools = cs.javaTools()
  val underlying = tools.javac()
  val counted = new xsbti.compile.JavaCompiler {
    override def run(
        sources: Array[xsbti.VirtualFile],
        options: Array[String],
        output: xsbti.compile.Output,
        incToolOptions: xsbti.compile.IncToolOptions,
        reporter: xsbti.Reporter,
        log: xsbti.Logger
    ): Boolean = {
      IO.append(counter, "javac\n")
      underlying.run(sources, options, output, incToolOptions, reporter, log)
    }

    override def supportsDirectToJar(): Boolean =
      underlying.supportsDirectToJar()
  }
  cs.withJavaTools(new xsbti.compile.JavaTools {
    override def javac(): xsbti.compile.JavaCompiler = counted
    override def javadoc(): xsbti.compile.Javadoc = tools.javadoc()
  })
}

lazy val root = (project in file("."))
  .aggregate(upstream, downstream)

lazy val upstream = project
  .settings(
    exportJars := true,
    exportPipelining := false,
    Compile / compilers ~= countedCompilers(file("javac-invocations")),
  )

lazy val downstream = project
  .dependsOn(upstream)

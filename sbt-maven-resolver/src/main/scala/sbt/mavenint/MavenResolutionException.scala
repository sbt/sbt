package sbt.mavenint

/** An exception we can throw if we encounter issues. */
class MavenResolutionException(msg: String) extends RuntimeException(msg) {}

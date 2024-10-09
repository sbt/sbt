package sbt.internal.librarymanagement.mavenint;

/** Extra properties we dump from Aether into the properties list. */
public class SbtPomExtraProperties {

  public static final String MAVEN_PACKAGING_KEY = "sbt.pom.packaging";
  public static final String SCALA_VERSION_KEY = "sbt.pom.scalaversion";
  public static final String SBT_VERSION_KEY = "sbt.pom.sbtversion";

  public static final String POM_INFO_KEY_PREFIX = "info.";
  public static final String POM_SCALA_VERSION = "scalaVersion";
  public static final String POM_SBT_VERSION = "sbtVersion";
  public static final String POM_API_KEY = "info.apiURL";
  public static final String VERSION_SCHEME_KEY = "info.versionScheme";
  public static final String POM_RELEASE_NOTES_KEY = "info.releaseNotesUrl";

  public static final String LICENSE_COUNT_KEY = "license.count";

  public static String makeLicenseName(int i) {
    return "license." + i + ".name";
  }

  public static String makeLicenseUrl(int i) {
    return "license." + i + ".url";
  }
}

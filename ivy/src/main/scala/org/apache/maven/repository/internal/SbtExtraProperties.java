package org.apache.maven.repository.internal;

/**
 * Created by jsuereth on 12/20/14.
 */
public class SbtExtraProperties {
    public static final String MAVEN_PACKAGING_KEY = "sbt.pom.packaging";
    public static final String SCALA_VERSION_KEY = "sbt.pom.scalaversion";
    public static final String SBT_VERSION_KEY = "sbt.pom.sbtversion";

    public static final String POM_SCALA_VERSION = "scalaVersion";
    public static final String POM_SBT_VERSION = "sbtVersion";

    public static final String LICENSE_COUNT_KEY = "license.count";

    public static String makeLicenseName(int i) {
        return "license." + i + ".name";
    }
    public static String makeLicenseUrl(int i) {
        return "license." + i + ".url";
    }

}

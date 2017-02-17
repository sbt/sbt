package coursier
package test

import utest._
import scalaz._

import coursier.maven.Pom

import coursier.core.compatibility._

object PomParsingTests extends TestSuite {

  val tests = TestSuite {
    'readClassifier{
      val depNode ="""
        <dependency>
          <groupId>comp</groupId>
          <artifactId>lib</artifactId>
          <version>2.1</version>
          <classifier>extra</classifier>
        </dependency>
                   """

      val expected = \/-(
        "" -> Dependency(
          Module("comp", "lib"),
          "2.1",
          attributes = Attributes(classifier = "extra")
        )
      )

      val result = Pom.dependency(xmlParse(depNode).right.get)

      assert(result == expected)
    }
    'readProfileWithNoActivation{
      val profileNode ="""
        <profile>
          <id>profile1</id>
        </profile>
                       """

      val expected = \/-(Profile("profile1", None, Profile.Activation(Nil), Nil, Nil, Map.empty))

      val result = Pom.profile(xmlParse(profileNode).right.get)

      assert(result == expected)
    }
    'beFineWithProfilesWithNoId{
      val profileNode = """
        <profile>
          <activation>
            <activeByDefault>true</activeByDefault>
          </activation>
        </profile>
                       """

      val expected = \/-(Profile("", Some(true), Profile.Activation(Nil), Nil, Nil, Map.empty))

      val result = Pom.profile(xmlParse(profileNode).right.get)

      assert(result == expected)
    }
    'readProfileActivatedByDefault{
      val profileNode ="""
        <profile>
          <id>profile1</id>
          <activation>
            <activeByDefault>true</activeByDefault>
          </activation>
        </profile>
                       """

      val expected = \/-(Profile("profile1", Some(true), Profile.Activation(Nil), Nil, Nil, Map.empty))

      val result = Pom.profile(xmlParse(profileNode).right.get)

      assert(result == expected)
    }
    'readProfileActiveByPropertyWithoutValue{
      val profileNode ="""
        <profile>
          <id>profile1</id>
          <activation>
            <property>
              <name>hadoop.profile</name>
            </property>
          </activation>
        </profile>
                       """
      val expected = \/-(Profile("profile1", None, Profile.Activation(List("hadoop.profile" -> None)), Nil, Nil, Map.empty))
      val result = Pom.profile(xmlParse(profileNode).right.get)

      assert(result == expected)
    }

    'readProfileActiveByPropertyWithValue{
      val profileNode ="""
        <profile>
          <id>profile1</id>
          <activation>
            <property>
              <name>hadoop.profile</name>
              <value>yes</value>
            </property>
          </activation>
        </profile>
                       """
      val expected = \/-(Profile("profile1", None, Profile.Activation(List("hadoop.profile" -> Some("yes"))), Nil, Nil, Map.empty))
      val result = Pom.profile(xmlParse(profileNode).right.get)

      assert(result == expected)
    }

    'readProfileDependencies{
      val profileNode ="""
        <profile>
          <id>profile1</id>
          <dependencies>
            <dependency>
              <groupId>comp</groupId>
              <artifactId>lib</artifactId>
              <version>0.2</version>
            </dependency>
          </dependencies>
        </profile>
                       """

      val expected = \/-(Profile(
        "profile1",
        None,
        Profile.Activation(Nil),
        Seq(
          "" -> Dependency(Module("comp", "lib"), "0.2")),
        Nil,
        Map.empty
      ))

      val result = Pom.profile(xmlParse(profileNode).right.get)

      assert(result == expected)
    }
    'readProfileDependenciesMgmt{
      val profileNode ="""
        <profile>
          <id>profile1</id>
          <dependencyManagement>
            <dependencies>
              <dependency>
                <groupId>comp</groupId>
                <artifactId>lib</artifactId>
                <version>0.2</version>
                <scope>test</scope>
              </dependency>
            </dependencies>
          </dependencyManagement>
        </profile>
                       """

      val expected = \/-(Profile(
        "profile1",
        None,
        Profile.Activation(Nil),
        Nil,
        Seq(
          "test" -> Dependency(Module("comp", "lib"), "0.2")),
        Map.empty
      ))

      val result = Pom.profile(xmlParse(profileNode).right.get)

      assert(result == expected)
    }
    'readProfileProperties{
      val profileNode ="""
        <profile>
          <id>profile1</id>
          <properties>
            <first.prop>value1</first.prop>
          </properties>
        </profile>
                       """

      val expected = \/-(Profile(
        "profile1",
        None,
        Profile.Activation(Nil),
        Nil,
        Nil,
        Map("first.prop" -> "value1")
      ))

      val result = Pom.profile(xmlParse(profileNode).right.get)

      assert(result == expected)
    }
    'propertyWithSpaces{
      val profileNode ="""
        <profile>
          <id>profile1</id>
          <properties>
            <first.prop>   value1   </first.prop>
          </properties>
        </profile>
                       """

      val expected = \/-(Profile(
        "profile1",
        None,
        Profile.Activation(Nil),
        Nil,
        Nil,
        Map("first.prop" -> "value1")
      ))

      val result = Pom.profile(xmlParse(profileNode).right.get)

      assert(result == expected)
    }
    'beFineWithCommentsInProperties{
      import scalaz._, Scalaz._

      val properties =
        """
          |  <properties>
          |    <maven.compile.source>1.6</maven.compile.source>
          |    <maven.compile.target>1.6</maven.compile.target>
          |    <commons.componentid>io</commons.componentid>
          |    <commons.rc.version>RC1</commons.rc.version>
          |    <commons.release.version>2.4</commons.release.version>
          |    <commons.release.desc>(requires JDK 1.6+)</commons.release.desc>
          |    <commons.release.2.version>2.2</commons.release.2.version>
          |    <commons.release.2.desc>(requires JDK 1.5+)</commons.release.2.desc>
          |    <commons.jira.id>IO</commons.jira.id>
          |    <commons.jira.pid>12310477</commons.jira.pid>
          |    <commons.osgi.export>
          |        <!-- Explicit list of packages from IO 1.4 -->
          |        org.apache.commons.io;
          |        org.apache.commons.io.comparator;
          |        org.apache.commons.io.filefilter;
          |        org.apache.commons.io.input;
          |        org.apache.commons.io.output;version=1.4.9999;-noimport:=true,
          |        <!-- Same list plus * for new packages -->
          |        org.apache.commons.io;
          |        org.apache.commons.io.comparator;
          |        org.apache.commons.io.filefilter;
          |        org.apache.commons.io.input;
          |        org.apache.commons.io.output;
          |        org.apache.commons.io.*;version=${project.version};-noimport:=true
          |    </commons.osgi.export>
          |  </properties>
          |
        """.stripMargin

      val parsed = core.compatibility.xmlParse(properties)
      assert(parsed.isRight)

      val node = parsed.right.get
      assert(node.label == "properties")

      val children = node.children.collect{case elem if elem.isElement => elem}
      val props0 = children.toList.traverseU(Pom.property)

      assert(props0.isRight)

      val props = props0.getOrElse(???).toMap

      assert(props.contains("commons.release.2.desc"))
      assert(props.contains("commons.osgi.export"))

      assert(props("commons.rc.version") == "RC1")
      assert(props("commons.release.2.desc") == "(requires JDK 1.5+)")
      assert(props("commons.osgi.export").contains("org.apache.commons.io.filefilter;"))
      assert(props("commons.osgi.export").contains("org.apache.commons.io.input;"))
    }
  }

}

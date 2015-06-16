package coursier
package test

import utest._
import scalaz._

import coursier.core.Xml
import coursier.Profile.Activation
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

      val expected = \/-(Dependency(Module("comp", "lib", "2.1"), classifier = "extra"))

      val result = Xml.dependency(xmlParse(depNode).right.get)

      assert(result == expected)
    }
    'readProfileWithNoActivation{
      val profileNode ="""
        <profile>
          <id>profile1</id>
        </profile>
                       """

      val expected = \/-(Profile("profile1", None, Activation(Nil), Nil, Nil, Map.empty))

      val result = Xml.profile(xmlParse(profileNode).right.get)

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

      val expected = \/-(Profile("profile1", Some(true), Activation(Nil), Nil, Nil, Map.empty))

      val result = Xml.profile(xmlParse(profileNode).right.get)

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
        Activation(Nil),
        Seq(
          Dependency(Module("comp", "lib", "0.2"))),
        Nil,
        Map.empty
      ))

      val result = Xml.profile(xmlParse(profileNode).right.get)

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
        Activation(Nil),
        Nil,
        Seq(
          Dependency(Module("comp", "lib", "0.2"), scope = Scope.Test)),
        Map.empty
      ))

      val result = Xml.profile(xmlParse(profileNode).right.get)

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
        Activation(Nil),
        Nil,
        Nil,
        Map("first.prop" -> "value1")
      ))

      val result = Xml.profile(xmlParse(profileNode).right.get)

      assert(result == expected)
    }
  }

}

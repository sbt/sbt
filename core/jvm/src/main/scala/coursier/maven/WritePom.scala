package coursier.maven

import coursier.core.{ Dependency, Project }

object WritePom {

  def project(proj: Project, packaging: Option[String]) = {

    def dependencyNode(config: String, dep: Dependency) = {
      <dependency>
        <groupId>{dep.module.organization}</groupId>
        <artifactId>{dep.module.name}</artifactId>
        {
        if (dep.version.isEmpty)
          Nil
        else
          Seq(<version>{dep.version}</version>)
        }
        {
        if (config.isEmpty)
          Nil
        else
          Seq(<scope>{config}</scope>)
        }
      </dependency>
    }

    <project>
      // parent
      <groupId>{proj.module.organization}</groupId>
      <artifactId>{proj.module.name}</artifactId>
      {
      packaging
        .map(p => <packaging>{p}</packaging>)
        .toSeq
      }
      <description>{proj.info.description}</description>
      <url>{proj.info.homePage}</url>
      <version>{proj.version}</version>
      // licenses
      <name>{proj.module.name}</name>
      <organization>
        <name>{proj.module.name}</name>
        <url>{proj.info.homePage}</url>
      </organization>
      // SCM
      // developers
      {
      if (proj.dependencies.isEmpty)
        Nil
      else
        <dependencies>{
          proj.dependencies.map {
            case (config, dep) =>
              dependencyNode(config, dep)
          }
          }</dependencies>
      }
      {
      if (proj.dependencyManagement.isEmpty)
        Nil
      else
        <dependencyManagement>
          <dependencies>{
            proj.dependencyManagement.map {
              case (config, dep) =>
                dependencyNode(config, dep)
            }
            }</dependencies>
        </dependencyManagement>
      }
      // properties
      // repositories
    </project>
  }

}

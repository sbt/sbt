package coursier

import scala.xml.{ Node, PrefixedAttribute }

object MakeIvyXml {

  def apply(project: Project): Node = {

    val baseInfoAttrs = <x
      organisation={project.module.organization}
      module={project.module.name}
      revision={project.version}
    />.attributes

    val infoAttrs = project.module.attributes.foldLeft(baseInfoAttrs) {
      case (acc, (k, v)) =>
        new PrefixedAttribute("e", k, v, acc)
    }

    val licenseElems = project.info.licenses.map {
      case (name, urlOpt) =>
        var n = <license name={name} />
        for (url <- urlOpt)
          n = n % <x url={url} />.attributes
        n
    }

    val infoElem = {
      <info>
        {licenseElems}
        <description>{project.info.description}</description>
      </info>
    } % infoAttrs

    val confElems = project.configurations.toVector.map {
      case (name, extends0) =>
        var n = <conf name={name} visibility="public" description="" />
        if (extends0.nonEmpty)
          n = n % <x extends={extends0.mkString(",")} />.attributes
        n
    }

    val publications = project
      .publications
      .groupBy { case (_, p) => p }
      .mapValues { _.map { case (cfg, _) => cfg } }

    val publicationElems = publications.map {
      case (pub, configs) =>
        var n = <artifact name={pub.name} type={pub.`type`} ext={pub.ext} conf={configs.mkString(",")} />
        if (pub.classifier.nonEmpty)
          n = n % <x e:classifier={pub.classifier} />.attributes
        n
    }

    val dependencyElems = project.dependencies.toVector.map {
      case (conf, dep) =>
        val excludes = dep.exclusions.toSeq.map {
          case (org, name) =>
            <exclude org={org} module={name} name="*" type="*" ext="*" conf="" matcher="exact"/>
        }

        <dependency org={dep.module.organization} name={dep.module.name} rev={dep.version} conf={s"$conf->${dep.configuration}"}>
          {excludes}
        </dependency>
    }

    <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
      {infoElem}
      <configurations>{confElems}</configurations>
      <publications>{publicationElems}</publications>
      <dependencies>{dependencyElems}</dependencies>
    </ivy-module>
  }

}

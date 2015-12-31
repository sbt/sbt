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

    val publicationElems = project.publications.map {
      case (conf, pub) =>
        var n = <artifact name={pub.name} type={pub.`type`} ext={pub.ext} conf={conf} />
        if (pub.classifier.nonEmpty)
          n = n % <x e:classifier={pub.classifier} />.attributes
        n
    }

    val dependencyElems = project.dependencies.toVector.map {
      case (conf, dep) =>
        <dependency org={dep.module.organization} name={dep.module.name} rev={dep.version} conf={s"$conf->${dep.configuration}"} />
    }

    <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
      {infoElem}
      <configurations>{confElems}</configurations>
      <publications>{publicationElems}</publications>
      <dependencies>{dependencyElems}</dependencies>
    </ivy-module>
  }

}

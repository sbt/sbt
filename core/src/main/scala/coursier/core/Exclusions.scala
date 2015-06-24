package coursier.core

object Exclusions {

  def partition(exclusions: Set[(String, String)]): (Boolean, Set[String], Set[String], Set[(String, String)]) = {

    val (wildCards, remaining) = exclusions
      .partition{case (org, name) => org == "*" || name == "*" }

    val all = wildCards
      .contains(one.head)

    val excludeByOrg = wildCards
      .collect{case (org, "*") if org != "*" => org }
    val excludeByName = wildCards
      .collect{case ("*", name) if name != "*" => name }

    (all, excludeByOrg, excludeByName, remaining)
  }

  def apply(exclusions: Set[(String, String)]): (String, String) => Boolean = {

    val (all, excludeByOrg, excludeByName, remaining) = partition(exclusions)

    if (all) (_, _) => false
    else
      (org, name) => {
        !excludeByName(name) &&
        !excludeByOrg(org) &&
        !remaining((org, name))
      }
  }

  def minimize(exclusions: Set[(String, String)]): Set[(String, String)] = {

    val (all, excludeByOrg, excludeByName, remaining) = partition(exclusions)

    if (all) one
    else {
      val filteredRemaining = remaining
        .filter{case (org, name) =>
          !excludeByOrg(org) &&
          !excludeByName(name)
        }

      excludeByOrg.map((_, "*")) ++
        excludeByName.map(("*", _)) ++
        filteredRemaining
    }
  }

  val zero = Set.empty[(String, String)]
  val one = Set(("*", "*"))

  def join(x: Set[(String, String)], y: Set[(String, String)]): Set[(String, String)] =
    minimize(x ++ y)

  def meet(x: Set[(String, String)], y: Set[(String, String)]): Set[(String, String)] = {

    val ((xAll, xExcludeByOrg, xExcludeByName, xRemaining), (yAll, yExcludeByOrg, yExcludeByName, yRemaining)) =
      (partition(x), partition(y))

    val all = xAll && yAll

    if (all) one
    else {
      val excludeByOrg =
        if (xAll) yExcludeByOrg
        else if (yAll) xExcludeByOrg
        else xExcludeByOrg intersect yExcludeByOrg
      val excludeByName =
        if (xAll) yExcludeByName
        else if (yAll) xExcludeByName
        else xExcludeByName intersect yExcludeByName

      val remaining =
        xRemaining.filter{case (org, name) => yAll || yExcludeByOrg(org) || yExcludeByName(name)} ++
        yRemaining.filter{case (org, name) => xAll || xExcludeByOrg(org) || xExcludeByName(name)} ++
          (xRemaining intersect yRemaining)

      excludeByOrg.map((_, "*")) ++
        excludeByName.map(("*", _)) ++
        remaining
    }
  }

}

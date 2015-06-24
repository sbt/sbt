package coursier.core

object Exclusions {

  def partition(exclusions: Set[(String, String)]): (Boolean, Set[String], Set[String], Set[(String, String)]) = {

    val (wildCards, remaining) = exclusions
      .partition{case (org, name) => org == "*" || name == "*" }

    val all = wildCards
      .contains(("*", "*"))

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

    if (all) Set(("*", "*"))
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


}

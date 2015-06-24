package coursier.core

object Orders {

  /** Minimal ad-hoc partial order */
  trait PartialOrder[A] {
    /** 
     * x <  y: Some(neg. integer)
     * x == y: Some(0)
     * x >  y: Some(pos. integer)
     * x, y not related: None
     */
    def cmp(x: A, y: A): Option[Int]
  }

  /**
   * Only relations:
   *   Compile < Runtime < Test
   */
  implicit val mavenScopePartialOrder: PartialOrder[Scope] =
    new PartialOrder[Scope] {
      val higher = Map[Scope, Set[Scope]](
        Scope.Compile -> Set(Scope.Runtime, Scope.Test),
        Scope.Runtime -> Set(Scope.Test)
      )

      def cmp(x: Scope, y: Scope) =
        if (x == y) Some(0)
        else if (higher.get(x).exists(_(y))) Some(-1)
        else if (higher.get(y).exists(_(x))) Some(1)
        else None
    }

  /** Non-optional < optional */
  implicit val optionalPartialOrder: PartialOrder[Boolean] =
    new PartialOrder[Boolean] {
      def cmp(x: Boolean, y: Boolean) =
        Some(
          if (x == y) 0
          else if (x) 1
          else -1
        )
    }

  /**
   * Exclusions partial order.
   *
   * x <= y iff all that x excludes is also excluded by y.
   * x and y not related iff x excludes some elements not excluded by y AND
   *                         y excludes some elements not excluded by x.
   *
   * In particular, no exclusions <= anything <= Set(("*", "*"))
   */
  implicit val exclusionsPartialOrder: PartialOrder[Set[(String, String)]] =
    new PartialOrder[Set[(String, String)]] {
      def boolCmp(a: Boolean, b: Boolean) = (a, b) match {
        case (true, true) => Some(0)
        case (true, false) => Some(1)
        case (false, true) => Some(-1)
        case (false, false) => None
      }

      def cmp(x: Set[(String, String)], y: Set[(String, String)]) = {
        val (xAll, xExcludeByOrg1, xExcludeByName1, xRemaining0) = Exclusions.partition(x)
        val (yAll, yExcludeByOrg1, yExcludeByName1, yRemaining0) = Exclusions.partition(y)

        boolCmp(xAll, yAll).orElse {
          def filtered(e: Set[(String, String)]) =
            e.filter{case (org, name) =>
              !xExcludeByOrg1(org) && !yExcludeByOrg1(org) &&
                !xExcludeByName1(name) && !yExcludeByName1(name)
            }

          def removeIntersection[T](a: Set[T], b: Set[T]) =
            (a -- b, b -- a)

          def allEmpty(set: Set[_]*) = set.forall(_.isEmpty)

          val (xRemaining1, yRemaining1) =
            (filtered(xRemaining0), filtered(yRemaining0))

          val (xProperRemaining, yProperRemaining) =
            removeIntersection(xRemaining1, yRemaining1)

          val (onlyXExcludeByOrg, onlyYExcludeByOrg) =
            removeIntersection(xExcludeByOrg1, yExcludeByOrg1)

          val (onlyXExcludeByName, onlyYExcludeByName) =
            removeIntersection(xExcludeByName1, yExcludeByName1)

          val (noXProper, noYProper) = (
            allEmpty(xProperRemaining, onlyXExcludeByOrg, onlyXExcludeByName),
            allEmpty(yProperRemaining, onlyYExcludeByOrg, onlyYExcludeByName)
          )

          boolCmp(noYProper, noXProper) // order matters
        }
      }
    }

  /**
   * Assume all dependencies have same `module`, `version`, and `artifact`; see `minDependencies`
   * if they don't.
   */
  def minDependenciesUnsafe(dependencies: Set[Dependency]): Set[Dependency] = {
    val groupedDependencies = dependencies
      .groupBy(dep => (dep.optional, dep.scope))
      .toList

    /*
     * Iterates over all pairs (xDep, yDep) from `dependencies`.
     * If xDep < yDep (all that yDep brings is already brought by xDep), remove yDep.
     *
     * The (partial) order on dependencies is made of the ones on scope, optional, and exclusions.
     */

    val remove =
      for {
        List(((xOpt, xScope), xDeps), ((yOpt, yScope), yDeps)) <- groupedDependencies.combinations(2)
        optCmp <- optionalPartialOrder.cmp(xOpt, yOpt).iterator
        scopeCmp <- mavenScopePartialOrder.cmp(xScope, yScope).iterator
        if optCmp*scopeCmp >= 0
        xDep <- xDeps.iterator
        yDep <- yDeps.iterator
        exclCmp <- exclusionsPartialOrder.cmp(xDep.exclusions, yDep.exclusions).iterator
        if optCmp*exclCmp >= 0
        if scopeCmp*exclCmp >= 0
        xIsMin = optCmp < 0 || scopeCmp < 0 || exclCmp < 0
        yIsMin = optCmp > 0 || scopeCmp > 0 || exclCmp > 0
        if xIsMin || yIsMin // should be always true, unless xDep == yDep, which shouldn't happen
      } yield if (xIsMin) yDep else xDep

    dependencies -- remove
  }

  /**
   * Minified representation of `dependencies`.
   *
   * The returned set brings exactly the same things as `dependencies`, with no redundancy.
   */
  def minDependencies(dependencies: Set[Dependency]): Set[Dependency] = {
    dependencies
      .groupBy(_.copy(scope = Scope.Other(""), exclusions = Set.empty, optional = false))
      .mapValues(minDependenciesUnsafe)
      .valuesIterator
      .fold(Set.empty)(_ ++ _)
  }

}

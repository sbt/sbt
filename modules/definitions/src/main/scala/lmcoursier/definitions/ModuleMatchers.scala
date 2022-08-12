package lmcoursier.definitions

import dataclass.data

/**
 * @param exclude Use "*" in either organization or name to match any.
 * @param include Use "*" in either organization or name to match any.
 */
@data class ModuleMatchers(
  exclude: Set[Module],
  include: Set[Module],
  includeByDefault: Boolean = true
)

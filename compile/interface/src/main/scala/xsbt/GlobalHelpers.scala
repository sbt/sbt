package xsbt

import scala.tools.nsc.Global

trait GlobalHelpers extends Compat {
  val global: CallbackGlobal
  import global.{ Tree, Type, Symbol, TypeTraverser }

  def symbolsInType(tp: Type): Set[Symbol] = {
    val typeSymbolCollector =
      new CollectTypeTraverser({
        case tpe if (tpe != null) && !tpe.typeSymbolDirect.isPackage => tpe.typeSymbolDirect
      })

    typeSymbolCollector.traverse(tp)
    typeSymbolCollector.collected.toSet
  }
}

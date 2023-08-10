/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import sbt.internal.util.Types.Id
import sbt.internal.util.complete.Parser
import scala.quoted.*

object InputTaskMacro:
  import TaskMacro.ContSyntax.*

  def inputTaskMacroImpl[A1: Type](tree: Expr[A1])(using
      qctx: Quotes
  ): Expr[Def.Initialize[InputTask[A1]]] =
    inputTaskMacro0[A1](tree)

  // def inputTaskDynMacroImpl[A1: Type](t: c.Expr[Initialize[Task[A1]]])(using qctx: Quotes): c.Expr[Initialize[InputTask[A1]]] =
  //   inputTaskDynMacro0[A1](c)(t)

  private[this] def inputTaskMacro0[A1: Type](tree: Expr[A1])(using
      qctx: Quotes
  ): Expr[Def.Initialize[InputTask[A1]]] =
    import qctx.reflect.*
    // println(s"tree = ${tree.show}")
    iInitializeMacro(tree) { et =>
      val pt: Expr[State => Parser[Task[A1]]] = iParserMacro(et) { pt =>
        val tt = iTaskMacro(pt)
        // println(s"tt = ${tt.show}")
        tt
      }
      '{ InputTask.make($pt) }
    }

  private[this] def iInitializeMacro[F1[_]: Type, A1: Type](tree: Expr[A1])(
      f: Expr[A1] => Expr[F1[A1]]
  )(using qctx: Quotes): Expr[Def.Initialize[F1[A1]]] =
    import qctx.reflect.*
    val convert1 = new InputInitConvert(qctx, 0)
    import convert1.Converted

    def wrapInitTask[A2: Type](tree: Term): Term =
      val expr = tree.asExprOf[Def.Initialize[Task[A2]]]
      '{
        InputWrapper.`wrapTask_\u2603\u2603`[A2](
          InputWrapper.`wrapInit_\u2603\u2603`[Task[A2]]($expr)
        )
      }.asTerm

    def wrapInitParser[A2: Type](tree: Term): Term =
      val expr = tree.asExprOf[Def.Initialize[State => Parser[A2]]]
      '{
        ParserInput.`parser_\u2603\u2603`[A2](
          InputWrapper.`wrapInit_\u2603\u2603`[State => Parser[A2]]($expr)
        )
      }.asTerm

    def wrapInitInput[A2: Type](tree: Term): Term =
      val expr = tree.asExprOf[Def.Initialize[InputTask[A2]]]
      wrapInput[A2](wrapInit[InputTask[A2]](tree))

    def wrapInput[A2: Type](tree: Term): Term =
      val expr = tree.asExprOf[InputTask[A2]]
      '{
        InputWrapper.`wrapTask_\u2603\u2603`[A2](
          ParserInput.`parser_\u2603\u2603`[Task[A2]]($expr.parser)
        )
      }.asTerm

    def wrapInit[A2: Type](tree: Term): Term =
      val expr = tree.asExpr
      '{
        InputWrapper.`wrapInit_\u2603\u2603`[A2]($expr)
      }.asTerm

    def expand[A2](nme: String, tpe: Type[A2], tree: Term): Converted =
      given Type[A2] = tpe
      nme match
        case InputWrapper.WrapInitTaskName  => Converted.success(wrapInitTask[A2](tree))
        case InputWrapper.WrapPreviousName  => Converted.success(wrapInitTask[A2](tree))
        case ParserInput.WrapInitName       => Converted.success(wrapInitParser[A2](tree))
        case InputWrapper.WrapInitInputName => Converted.success(wrapInitInput[A2](tree))
        case InputWrapper.WrapInputName     => Converted.success(wrapInput[A2](tree))
        case _                              => Converted.NotApplicable()

    def conditionInputTaskTree(t: Term): Term =
      convert1.transformWrappers(
        tree = t,
        subWrapper = [a] =>
          (nme: String, tpe: Type[a], tree: Term, original: Term) => expand[a](nme, tpe, tree),
        owner = Symbol.spliceOwner,
      )

    val inner: convert1.TermTransform[F1] = (in: Term) => f(in.asExprOf[A1]).asTerm
    val cond = conditionInputTaskTree(tree.asTerm).asExprOf[A1]
    convert1.contMapN[A1, Def.Initialize, F1](cond, convert1.appExpr, None, inner)

  private[this] def iParserMacro[F1[_]: Type, A1: Type](tree: Expr[A1])(
      f: Expr[A1] => Expr[F1[A1]]
  )(using qctx: Quotes): Expr[State => Parser[F1[A1]]] =
    import qctx.reflect.*
    val convert1 = new ParserConvert(qctx, 1000)
    val inner: convert1.TermTransform[F1] = (in: Term) => f(in.asExprOf[A1]).asTerm
    convert1.contMapN[A1, ParserInstance.F1, F1](tree, convert1.appExpr, None, inner)

  private[this] def iTaskMacro[A1: Type](tree: Expr[A1])(using qctx: Quotes): Expr[Task[A1]] =
    import qctx.reflect.*
    val convert1 = new TaskConvert(qctx, 2000)
    convert1.contMapN[A1, Task, Id](tree, convert1.appExpr, None)
  /*
  private[this] def inputTaskDynMacro0[A1: Type](
      expr: Expr[Def.Initialize[Task[A1]]]
  )(using qctx: Quotes): Expr[Def.Initialize[InputTask[A1]]] = {
    import qctx.reflect.{ Apply => ApplyTree, * }
    // import internal.decorators._
    val tag: Type[A1] = summon[Type[A1]]
    // val util = ContextUtil[c.type](c)
    val convert1 = new InitParserConvert(qctx)
    import convert1.Converted

    // val it = Ident(convert1.singleton(InputTask))
    val isParserWrapper = new InitParserConvert(qctx).asPredicate
    val isTaskWrapper = new FullConvert(qctx).asPredicate
    val isAnyWrapper =
      (n: String, tpe: TypeRepr, tr: Term) =>
        isParserWrapper(n, tpe, tr) || isTaskWrapper(n, tpe, tr)
    val ttree = expr.asTerm
    val defs = convert1.collectDefs(ttree, isAnyWrapper)
    val checkQual =
      util.checkReferences(defs, isAnyWrapper, weakTypeOf[Def.Initialize[InputTask[Any]]])

    // the Symbol for the anonymous function passed to the appropriate Instance.map/flatMap/pure method
    // this Symbol needs to be known up front so that it can be used as the owner of synthetic vals

    // val functionSym = util.functionSymbol(ttree.pos)
    var result: Option[(Term, TypeRepr, ValDef)] = None

    // original is the Tree being replaced.  It is needed for preserving attributes.
    def subWrapper(tpe: TypeRepr, qual: Term, original: Term): Tree =
      if result.isDefined then
        report.errorAndAbort(
          "implementation restriction: a dynamic InputTask can only have a single input parser.",
          qual.pos,
        )
        Literal(UnitConstant())
      else {
        // qual.foreach(checkQual)
        val vd = util.freshValDef(tpe, qual.symbol.pos, functionSym) // val $x: <tpe>
        result = Some((qual, tpe, vd))
        val tree = util.refVal(original, vd) // $x
        tree.setPos(
          qual.pos
        ) // position needs to be set so that wrapKey passes the position onto the wrapper
        assert(tree.tpe != null, "Null type: " + tree)
        tree.setType(tpe)
        tree
      }
    // Tree for InputTask.<name>[<tpeA>, <tpeB>](arg1)(arg2)
    def inputTaskCreate(name: String, tpeA: Type, tpeB: Type, arg1: Tree, arg2: Tree) = {
      val typedApp = TypeApply(util.select(it, name), TypeTree(tpeA) :: TypeTree(tpeB) :: Nil)
      val app = ApplyTree(ApplyTree(typedApp, arg1 :: Nil), arg2 :: Nil)
      Expr[Def.Initialize[InputTask[A1]]](app)
    }
    // Tree for InputTask.createFree[<tpe>](arg1)
    def inputTaskCreateFree(tpe: Type, arg: Tree) = {
      val typedApp = TypeApply(util.select(it, InputTaskCreateFreeName), TypeTree(tpe) :: Nil)
      val app = ApplyTree(typedApp, arg :: Nil)
      Expr[Def.Initialize[InputTask[A1]]](app)
    }
    def expandTask[I: Type](dyn: Boolean, tx: Tree): c.Expr[Initialize[Task[I]]] =
      if dyn then taskDynMacroImpl[I](c)(c.Expr[Initialize[Task[I]]](tx))
      else taskMacroImpl[I](c)(c.Expr[I](tx))
    def wrapTag[I: Type]: Type[Initialize[Task[I]]] = weakTypeTag

    def sub(name: String, tpe: TypeRepr, qual: Term, oldTree: Term): Converted =
      convert1.convert[A1](name, qual) transform { (tree: Term) =>
        subWrapper(tpe, tree, oldTree)
      }

    val tx =
      convert1.transformWrappers(expr.asTerm, sub, Symbol.spliceOwner)
    result match {
      case Some((p, tpe, param)) =>
        val fCore = util.createFunction(param :: Nil, tx, functionSym)
        val bodyTpe = wrapTag(tag).tpe
        val fTpe = util.functionType(tpe :: Nil, bodyTpe)
        val fTag = Type[Any](fTpe) // don't know the actual type yet, so use Any
        val fInit = expandTask(false, fCore)(fTag).tree
        inputTaskCreate(InputTaskCreateDynName, tpe, tag.tpe, p, fInit)
      case None =>
        val init = expandTask[A1](true, tx).tree
        inputTaskCreateFree(tag.tpe, init)
    }
  }
   */

  def parserGenInputTaskMacroImpl[A1: Type, A2: Type](
      parserGen: Expr[ParserGen[A1]],
      tree: Expr[A1 => A2]
  )(using
      qctx: Quotes
  ): Expr[Def.Initialize[InputTask[A2]]] =
    inputTaskMacro0[A2]('{
      val `arg$` = $parserGen.p.parsed
      $tree(`arg$`)
    })

  def parserGenFlatMapTaskImpl[A1: Type, A2: Type](
      parserGen: Expr[ParserGen[A1]],
      tree: Expr[A1 => Def.Initialize[Task[A2]]]
  )(using
      qctx: Quotes
  ): Expr[Def.Initialize[InputTask[A2]]] =
    import qctx.reflect.*
    val convert1 = new FullConvert(qctx, 1000)
    import convert1.Converted
    def mkInputTask(params: List[ValDef], body: Term): Expr[Def.Initialize[InputTask[A2]]] =
      val lambdaTpe =
        MethodType(params.map(_.name))(
          _ => List(TypeRepr.of[A1]),
          _ => TypeRepr.of[Def.Initialize[Task[A2]]]
        )
      val lambda = Lambda(
        owner = Symbol.spliceOwner,
        tpe = lambdaTpe,
        rhsFn = (sym, params) => {
          val p0 = params.head.asInstanceOf[Ident]
          val body2 =
            convert1
              .contFlatMap[A2, TaskMacro.F, Id](
                body.asExprOf[TaskMacro.F[A2]],
                convert1.appExpr,
                None,
              )
              .asTerm
          object refTransformer extends TreeMap:
            override def transformTerm(tree: Term)(owner: Symbol): Term =
              tree match
                case Ident(name) if name == p0.name => Ref(p0.symbol)
                case _                              => super.transformTerm(tree)(owner)
          end refTransformer
          refTransformer.transformTerm(body2.changeOwner(sym))(sym)
        }
      )
      val action = lambda.asExprOf[A1 => Def.Initialize[Task[A2]]]
      '{
        InputTask.createDyn[A1, A2](${ parserGen }.p)(
          Def.valueStrict(TaskExtra.task[A1 => Def.Initialize[Task[A2]]]($action))
        )
      }
    tree.asTerm match
      case Lambda(params, body) =>
        mkInputTask(params, body)
      case Inlined(
            _,
            _,
            Lambda(params, body),
          ) =>
        mkInputTask(params, body)
      case Inlined(
            _,
            _,
            Block(List(), Lambda(params, body)),
          ) =>
        mkInputTask(params, body)
end InputTaskMacro

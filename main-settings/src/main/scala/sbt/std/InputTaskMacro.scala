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
import scala.collection.mutable.ListBuffer

object InputTaskMacro:
  import TaskMacro.ContSyntax.*

  def inputTaskMacroImpl[A1: Type](tree: Expr[A1])(using
      qctx: Quotes
  ): Expr[Def.Initialize[InputTask[A1]]] =
    inputTaskMacro0[A1](tree)

  def inputTaskDynMacroImpl[A1: Type](tree: Expr[Def.Initialize[Task[A1]]])(using
      qctx: Quotes
  ): Expr[Def.Initialize[InputTask[A1]]] =
    inputTaskDynMacro0[A1](tree)

  private def inputTaskMacro0[A1: Type](tree: Expr[A1])(using
      Quotes
  ): Expr[Def.Initialize[InputTask[A1]]] =
    // println(s"tree = ${tree.show}")
    iInitializeMacro(tree) { et =>
      val pt: Expr[State => Parser[Task[A1]]] = iParserMacro(et) { pt =>
        val tt = iTaskMacro(pt)
        // println(s"tt = ${tt.show}")
        tt
      }
      '{ InputTask.make($pt) }
    }

  private def iInitializeMacro[F1[_]: Type, A1: Type](tree: Expr[A1])(
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

  private def iParserMacro[F1[_]: Type, A1: Type](tree: Expr[A1])(
      f: Expr[A1] => Expr[F1[A1]]
  )(using qctx: Quotes): Expr[State => Parser[F1[A1]]] =
    import qctx.reflect.*
    val convert1 = new ParserConvert(qctx, 1000)
    val inner: convert1.TermTransform[F1] = (in: Term) => f(in.asExprOf[A1]).asTerm
    convert1.contMapN[A1, ParserInstance.F1, F1](tree, convert1.appExpr, None, inner)

  private def iTaskMacro[A1: Type](tree: Expr[A1])(using qctx: Quotes): Expr[Task[A1]] =
    val convert1 = new TaskConvert(qctx, 2000)
    convert1.contMapN[A1, Task, Id](tree, convert1.appExpr, None)

  private def inputTaskDynMacro0[A1: Type](
      expr: Expr[Def.Initialize[Task[A1]]]
  )(using qctx: Quotes): Expr[Def.Initialize[InputTask[A1]]] =
    // convert1 detects x.parsed where x is Parser[a], State => Parser[a], Initialize[Parser[a]], or Initialize[State => Parser[a]]
    //   val it6a = Def.inputTaskDyn {
    //     val d3 = dummy3.parsed
    //     val i = d3._2
    //     Def.task { tk.value + i }
    //   }
    val convert1 = InitParserConvert(qctx, 0)
    import convert1.qctx.reflect.*
    def expandTask[A2: Type](dyn: Boolean, tree: Tree): Expr[Def.Initialize[Task[A2]]] =
      if dyn then
        TaskMacro.taskDynMacroImpl[A2](
          tree.asExprOf[Def.Initialize[Task[A2]]]
        )
      else TaskMacro.taskMacroImpl[A2](tree.asExprOf[A2], false)
    val inputBuf = ListBuffer[(String, TypeRepr, Term, Term)]()
    val record = [a] =>
      (name: String, tpe: Type[a], qual: Term, oldTree: Term) =>
        given t: Type[a] = tpe
        convert1
          .convert[a](name, qual)
          .transform { (replacement: Term) =>
            inputBuf.append((name, TypeRepr.of[a](using tpe), qual, replacement))
            oldTree
          }
    def genCreateFree(body: Term) =
      val init = expandTask[A1](true, body)
      '{
        InputTask.createFree($init)
      }
    // This is roughly based on getMap in Cont.scala
    def genCreateDyn[Arg: Type](parser: Term, body: Term) =
      val param = parser.asExprOf[Def.Initialize[State => Parser[Arg]]]
      val tpe =
        MethodType(List("$p"))(
          _ => List(TypeRepr.of[Arg]),
          _ => TypeRepr.of[Def.Initialize[Task[A1]]]
        )
      val lambda = Lambda(
        owner = Symbol.spliceOwner,
        tpe = tpe,
        rhsFn = (sym, params) => {
          val param = params.head.asInstanceOf[Term]
          val substitute = [a] =>
            (name: String, tpe: Type[a], qual: Term, replace: Term) =>
              given t: Type[a] = tpe
              convert1
                .convert[a](name, qual)
                .transform { _ => Ref(param.symbol) }
          val modifiedBody =
            convert1
              .transformWrappers(body.changeOwner(sym), substitute, sym)
          modifiedBody
        }
      )
      val action = expandTask[Arg => Def.Initialize[Task[A1]]](false, lambda)
      '{
        InputTask.createDyn[Arg, A1](p = $param)(action = $action)
      }
    val body = convert1.transformWrappers(expr.asTerm, record, Symbol.spliceOwner)
    inputBuf.toList match
      case Nil                           => genCreateFree(body)
      case (_, tpe, _, paramTree) :: Nil =>
        tpe.asType match
          case '[a] => genCreateDyn[a](paramTree, body)
      case xs =>
        report.errorAndAbort("a dynamic InputTask can only have a single input parser.")
        ???
  end inputTaskDynMacro0

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

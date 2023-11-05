package sbt
package internal
package util
package appmacro

import scala.collection.mutable.ListBuffer
import scala.reflect.{ ClassTag, TypeTest }
import scala.quoted.*
import sjsonnew.{ BasicJsonProtocol, HashWriter, JsonFormat }
import sbt.util.{ ActionCache, ActionCacheStore, CacheConfiguration }
import sbt.util.Applicative
import sbt.util.Monad
import xsbti.VirtualFile
import Types.Id
import sbt.util.ActionValue
import sbt.util.Cache

/**
 * Implementation of a macro that provides a direct syntax for applicative functors and monads. It
 * is intended to be used in conjunction with another macro that conditions the inputs.
 */
trait Cont:
  final val InstanceTCName = "F"

  extension [C <: Quotes & Singleton](conv: Convert[C])
    /**
     * Implementation of a macro that provides a direct syntax for applicative functors. It is
     * intended to be used in conjunction with another macro that conditions the inputs.
     */
    def contMapN[A: Type, F[_], Effect[_]: Type](
        tree: Expr[A],
        applicativeExpr: Expr[Applicative[F]],
        cacheConfigExpr: Option[Expr[CacheConfiguration]],
    )(using
        iftpe: Type[F],
        eatpe: Type[Effect[A]],
    ): Expr[F[Effect[A]]] =
      contMapN[A, F, Effect](tree, applicativeExpr, cacheConfigExpr, conv.idTransform)

    /**
     * Implementation of a macro that provides a direct syntax for applicative functors. It is
     * intended to be used in conjunction with another macro that conditions the inputs.
     */
    def contMapN[A: Type, F[_], Effect[_]: Type](
        tree: Expr[A],
        applicativeExpr: Expr[Applicative[F]],
        cacheConfigExpr: Option[Expr[CacheConfiguration]],
        inner: conv.TermTransform[Effect]
    )(using
        iftpe: Type[F],
        eatpe: Type[Effect[A]],
    ): Expr[F[Effect[A]]] =
      contImpl[A, F, Effect](Left(tree), applicativeExpr, cacheConfigExpr, inner)

    /**
     * Implementation of a macro that provides a direct syntax for applicative functors. It is
     * intended to be used in conjunction with another macro that conditions the inputs.
     */
    def contFlatMap[A: Type, F[_], Effect[_]: Type](
        tree: Expr[F[A]],
        applicativeExpr: Expr[Applicative[F]],
        cacheConfigExpr: Option[Expr[CacheConfiguration]],
    )(using
        iftpe: Type[F],
        eatpe: Type[Effect[A]],
    ): Expr[F[Effect[A]]] =
      contFlatMap[A, F, Effect](tree, applicativeExpr, cacheConfigExpr, conv.idTransform)

    /**
     * Implementation of a macro that provides a direct syntax for applicative functors. It is
     * intended to be used in conjunction with another macro that conditions the inputs.
     */
    def contFlatMap[A: Type, F[_], Effect[_]: Type](
        tree: Expr[F[A]],
        applicativeExpr: Expr[Applicative[F]],
        cacheConfigExpr: Option[Expr[CacheConfiguration]],
        inner: conv.TermTransform[Effect]
    )(using
        iftpe: Type[F],
        eatpe: Type[Effect[A]],
    ): Expr[F[Effect[A]]] =
      contImpl[A, F, Effect](Right(tree), applicativeExpr, cacheConfigExpr, inner)

    def summonAppExpr[F[_]: Type]: Expr[Applicative[F]] =
      import conv.qctx
      import qctx.reflect.*
      given qctx.type = qctx
      Expr
        .summon[Applicative[F]]
        .getOrElse(sys.error(s"Applicative[F] not found for ${TypeRepr.of[F].typeSymbol}"))

    def summonHashWriter[A: Type]: Expr[HashWriter[A]] =
      import conv.qctx
      import qctx.reflect.*
      given qctx.type = qctx
      Expr
        .summon[HashWriter[A]]
        .getOrElse(sys.error(s"HashWriter[A] not found for ${TypeRepr.of[A].show}"))

    def summonJsonFormat[A: Type]: Expr[JsonFormat[A]] =
      import conv.qctx
      import qctx.reflect.*
      given qctx.type = qctx
      Expr
        .summon[JsonFormat[A]]
        .getOrElse(sys.error(s"JsonFormat[A] not found for ${TypeRepr.of[A].show}"))

    def summonClassTag[A: Type]: Expr[ClassTag[A]] =
      import conv.qctx
      import qctx.reflect.*
      given qctx.type = qctx
      Expr
        .summon[ClassTag[A]]
        .getOrElse(sys.error(s"ClassTag[A] not found for ${TypeRepr.of[A].show}"))

    /**
     * Implementation of a macro that provides a direct syntax for applicative functors and monads.
     * It is intended to bcke used in conjunction with another macro that conditions the inputs.
     *
     * This method processes the Term `t` to find inputs of the form `wrap[A]( input )` This form is
     * typically constructed by another macro that pretends to be able to get a value of type `A`
     * from a value convertible to `F[A]`. This `wrap(input)` form has two main purposes. First, it
     * identifies the inputs that should be transformed. Second, it allows the input trees to be
     * wrapped for later conversion into the appropriate `F[A]` type by `convert`. This wrapping is
     * necessary because applying the first macro must preserve the original type, but it is useful
     * to delay conversion until the outer, second macro is called. The `wrap` method accomplishes
     * this by allowing the original `Term` and `Type` to be hidden behind the raw `A` type. This
     * method will remove the call to `wrap` so that it is not actually called at runtime.
     *
     * Each `input` in each expression of the form `wrap[A]( input )` is transformed by `convert`.
     * This transformation converts the input Term to a Term of type `F[A]`. The original wrapped
     * expression `wrap(input)` is replaced by a reference to a new local `val x: A`, where `x` is a
     * fresh name. These converted inputs are passed to `builder` as well as the list of these
     * synthetic `ValDef`s. The `TupleBuilder` instance constructs a tuple (Tree) from the inputs
     * and defines the right hand side of the vals that unpacks the tuple containing the results of
     * the inputs.
     *
     * The constructed tuple of inputs and the code that unpacks the results of the inputs are then
     * passed to the `i`, which is an implementation of `Instance` that is statically accessible. An
     * Instance defines a applicative functor associated with a specific type constructor and, if it
     * implements MonadInstance as well, a monad. Typically, it will be either a top-level module or
     * a stable member of a top-level module (such as a val or a nested module). The `with
     * Singleton` part of the type verifies some cases at macro compilation time, while the full
     * check for static accessibility is done at macro expansion time. Note: Ideally, the types
     * would verify that `i: MonadInstance` when `t.isRight`. With the various dependent types
     * involved, this is not worth it.
     *
     * The `eitherTree` argument is the argument of the macro that will be transformed as described
     * above. If the macro that calls this method is for a multi-input map (app followed by map),
     * `in` should be the argument wrapped in Left. If this is for multi-input flatMap (app followed
     * by flatMap), this should be the argument wrapped in Right.
     */
    def contImpl[A: Type, F[_], Effect[_]: Type](
        eitherTree: Either[Expr[A], Expr[F[A]]],
        applicativeExpr: Expr[Applicative[F]],
        cacheConfigExprOpt: Option[Expr[CacheConfiguration]],
        inner: conv.TermTransform[Effect]
    )(using
        iftpe: Type[F],
        eatpe: Type[Effect[A]],
    ): Expr[F[Effect[A]]] =
      import conv.*
      import qctx.reflect.*
      given qctx.type = qctx

      val fTypeCon = TypeRepr.of[F]
      val faTpe = fTypeCon.appliedTo(TypeRepr.of[Effect[A]])
      val (expr, treeType) = eitherTree match
        case Left(l)  => (l, TypeRepr.of[Effect[A]])
        case Right(r) => (r, faTpe)

      val inputBuf = ListBuffer[Input]()
      val outputBuf = ListBuffer[Output]()

      def makeApp(body: Term, inputs: List[Input]): Expr[F[Effect[A]]] = inputs match
        case Nil      => pure(body)
        case x :: Nil => genMap(body, x)
        case xs       => genMapN(body, xs)

      // no inputs, so construct F[A] via Instance.pure or pure+flatten
      def pure(body: Term): Expr[F[Effect[A]]] =
        def pure0[A1: Type](body: Expr[A1]): Expr[F[A1]] =
          cacheConfigExprOpt match
            case Some(cacheConfigExpr) =>
              '{
                $applicativeExpr.pure[A1] { () =>
                  ${ callActionCacheWithUnit(outputBuf.toList, cacheConfigExpr)(body) }
                }
              }
            case None =>
              '{
                $applicativeExpr.pure[A1] { () => $body }
              }
        eitherTree match
          case Left(_) => pure0[Effect[A]](inner(body).asExprOf[Effect[A]])
          case Right(_) =>
            flatten(pure0[F[Effect[A]]](inner(body).asExprOf[F[Effect[A]]]))

      // m should have type F[F[A]]
      // the returned Tree will have type F[A]
      def flatten(m: Expr[F[F[Effect[A]]]]): Expr[F[Effect[A]]] =
        '{
          {
            val i1 = $applicativeExpr.asInstanceOf[Monad[F]]
            i1.flatten[Effect[A]]($m.asInstanceOf[F[F[Effect[A]]]])
          }
        }

      def genMap(body: Term, input: Input): Expr[F[Effect[A]]] =
        def genMap0[A1: Type](body: Expr[A1]): Expr[F[A1]] =
          input.tpe.asType match
            case '[a] =>
              val tpe =
                MethodType(List(input.name))(_ => List(TypeRepr.of[a]), _ => TypeRepr.of[A1])
              val lambda = Lambda(
                owner = Symbol.spliceOwner,
                tpe = tpe,
                rhsFn = (sym, params) => {
                  val param = params.head.asInstanceOf[Term]
                  // Called when transforming the tree to add an input.
                  //  For `qual` of type F[A], and a `selection` qual.value,
                  //  the call is addType(Type A, Tree qual)
                  // The result is a Tree representing a reference to
                  //  the bound value of the input.
                  val substitute = [x] =>
                    (name: String, tpe: Type[x], qual: Term, replace: Term) =>
                      given t: Type[x] = tpe
                      convert[x](name, qual) transform { (tree: Term) =>
                        typed[x](Ref(param.symbol))
                    }
                  val modifiedBody =
                    transformWrappers(body.asTerm.changeOwner(sym), substitute, sym).asExprOf[A1]
                  cacheConfigExprOpt match
                    case Some(cacheConfigExpr) =>
                      if input.isCacheInput then
                        callActionCacheWithA2(outputBuf.toList, cacheConfigExpr)(
                          body = modifiedBody,
                          input = Ref(param.symbol).asExprOf[a],
                        ).asTerm.changeOwner(sym)
                      else
                        callActionCacheWithUnit(outputBuf.toList, cacheConfigExpr)(
                          modifiedBody
                        ).asTerm.changeOwner(sym)
                    case None => modifiedBody.asTerm
                }
              ).asExprOf[a => A1]
              val expr = input.term.asExprOf[F[a]]
              typed[F[A1]](
                '{
                  $applicativeExpr.map[a, A1]($expr.asInstanceOf[F[a]])($lambda)
                }.asTerm
              ).asExprOf[F[A1]]
        eitherTree match
          case Left(_) =>
            genMap0[Effect[A]](inner(body).asExprOf[Effect[A]])
          case Right(_) =>
            flatten(genMap0[F[Effect[A]]](inner(body).asExprOf[F[Effect[A]]]))

      def genMapN(body: Term, inputs: List[Input]): Expr[F[Effect[A]]] =
        def genMapN0[A1: Type](body: Expr[A1]): Expr[F[A1]] =
          val br = makeTuple(inputs)
          val lambdaTpe =
            MethodType(List("$p0"))(_ => List(br.inputTupleTypeRepr), _ => TypeRepr.of[A1])
          br.inputTupleTypeRepr.asType match
            case '[inputTypeTpe] =>
              val lambda = Lambda(
                owner = Symbol.spliceOwner,
                tpe = lambdaTpe,
                rhsFn = (sym, params) => {
                  val p0 = params.head.asInstanceOf[Term]
                  // Called when transforming the tree to add an input.
                  //  For `qual` of type F[A], and a `selection` qual.value,
                  //  the call is addType(Type A, Tree qual)
                  // The result is a Tree representing a reference to
                  //  the bound value of the input.
                  val substitute = [x] =>
                    (name: String, tpe: Type[x], qual: Term, oldTree: Term) =>
                      given Type[x] = tpe
                      convert[x](name, qual) transform { (replacement: Term) =>
                        val idx = inputs.indexWhere(input => input.qual == qual)
                        applyTuple(p0, br.inputTupleTypeRepr, idx)
                    }
                  val modifiedBody =
                    transformWrappers(body.asTerm.changeOwner(sym), substitute, sym).asExprOf[A1]
                  cacheConfigExprOpt match
                    case Some(cacheConfigExpr) =>
                      if inputs.exists(_.isCacheInput) then
                        br.cacheInputTupleTypeRepr.asType match
                          case '[cacheInputTpe] =>
                            callActionCacheWithA2(outputBuf.toList, cacheConfigExpr)(
                              body = modifiedBody,
                              input = br.cacheInputExpr(p0).asExprOf[cacheInputTpe],
                            ).asTerm.changeOwner(sym)
                      else
                        callActionCacheWithUnit(outputBuf.toList, cacheConfigExpr)(
                          modifiedBody
                        ).asTerm.changeOwner(sym)
                    case None =>
                      modifiedBody.asTerm
                }
              )
              val tupleMapRepr = TypeRepr
                .of[Tuple.Map]
                .appliedTo(List(br.inputTupleTypeRepr, TypeRepr.of[F]))
              tupleMapRepr.asType match
                case '[tupleMap] =>
                  '{
                    given Applicative[F] = $applicativeExpr
                    AList
                      .tuple[inputTypeTpe & Tuple]
                      .mapN[F, A1](${
                        br.tupleExpr.asInstanceOf[Expr[Tuple.Map[inputTypeTpe & Tuple, F]]]
                      })(
                        ${ lambda.asExprOf[Tuple.Map[inputTypeTpe & Tuple, Id] => A1] }
                      )
                  }
        eitherTree match
          case Left(_) =>
            genMapN0[Effect[A]](inner(body).asExprOf[Effect[A]])
          case Right(_) =>
            flatten(genMapN0[F[Effect[A]]](inner(body).asExprOf[F[Effect[A]]]))

      def callActionCacheWithUnit[A1: Type](
          outputs: List[Output],
          cacheConfigExpr: Expr[CacheConfiguration]
      )(body: Expr[A1]): Expr[A1] =
        callActionCacheWithA2[A1, Unit](outputs, cacheConfigExpr)(
          body = body,
          input = '{ () }
        )

      def callActionCacheWithA2[A1: Type, A2: Type](
          outputs: List[Output],
          cacheConfigExpr: Expr[CacheConfiguration]
      )(body: Expr[A1], input: Expr[A2]): Expr[A1] =
        val codeContentHash = Expr[Long](body.show.##)
        val aJsonFormat = summonJsonFormat[A1]
        val aClassTag = summonClassTag[A1]
        val inputHashWriter =
          if TypeRepr.of[A2] =:= TypeRepr.of[Unit] then
            '{
              import BasicJsonProtocol.*
              summon[HashWriter[Unit]]
            }.asExprOf[HashWriter[A2]]
          else summonHashWriter[A2]
        val block = letOutput(outputs)(body)
        '{
          given HashWriter[A2] = $inputHashWriter
          given JsonFormat[A1] = $aJsonFormat
          given ClassTag[A1] = $aClassTag
          ActionCache
            .cache($input, $codeContentHash)({ _ =>
              $block
            })($cacheConfigExpr)
            .value
        }

      // wrap body in between output var declarations and var references
      def letOutput[A1: Type](
          outputs: List[Output]
      )(body: Expr[A1]): Expr[(A1, Seq[VirtualFile])] =
        Block(
          outputs.map(_.toVarDef),
          '{
            (
              $body,
              List(${ Varargs[VirtualFile](outputs.map(_.toRef.asExprOf[VirtualFile])) }: _*)
            )
          }.asTerm
        ).asExprOf[(A1, Seq[VirtualFile])]

      val WrapOutputName = "wrapOutput_\u2603\u2603"
      // Called when transforming the tree to add an input.
      //  For `qual` of type F[A], and a `selection` qual.value.
      val record = [a] =>
        (name: String, tpe: Type[a], qual: Term, oldTree: Term) =>
          given t: Type[a] = tpe
          convert[a](name, qual) transform { (replacement: Term) =>
            if name != WrapOutputName then
              // todo cache opt-out attribute
              inputBuf += Input(TypeRepr.of[a], qual, replacement, freshName("q"))
              oldTree
            else
              val output = Output(TypeRepr.of[a], qual, freshName("o"), Symbol.spliceOwner)
              outputBuf += output
              if cacheConfigExprOpt.isDefined then output.toAssign
              else oldTree
            end if
        }
      val tx = transformWrappers(expr.asTerm, record, Symbol.spliceOwner)
      val tr = makeApp(tx, inputBuf.toList)
      tr
end Cont

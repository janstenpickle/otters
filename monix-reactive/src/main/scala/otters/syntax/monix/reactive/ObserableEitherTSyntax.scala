package otters.syntax.monix.reactive

import cats.data.EitherT
import monix.reactive.Observable
import otters.FunctionPipe
import otters.instances.monix.reactive.{Pipe, Sink}
import otters.syntax.{EitherTApply, EitherTApplyEither, EitherTExtendedSyntax, EitherTSyntax}

trait ObserableEitherTSyntax extends EitherTSyntax with EitherTExtendedSyntax[Pipe, Sink] {
  implicit class EitherTPipeOps[A, B, C](override val stream: EitherT[FunctionPipe[Observable, A, ?], B, C])
      extends AllOps[FunctionPipe[Observable, A, ?], B, C]

  implicit class EitherTFlowApply[A, B](override val stream: FunctionPipe[Observable, A, B])
      extends EitherTApply[FunctionPipe[Observable, A, ?], B]

  implicit class EitherTFlowApplyEither[A, B, C](override val stream: FunctionPipe[Observable, A, Either[B, C]])
      extends EitherTApplyEither[FunctionPipe[Observable, A, ?], B, C]
}

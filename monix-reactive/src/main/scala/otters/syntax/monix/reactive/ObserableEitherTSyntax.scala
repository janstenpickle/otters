package otters.syntax.monix.reactive

import cats.data.EitherT
import monix.reactive.Observable
import otters.FunctionPipe
import otters.instances.monix.reactive.{Pipe, Sink}
import otters.syntax.{EitherTApply, EitherTApplyEither, EitherTExtendedSyntax, EitherTSyntax}

trait ObserableEitherTSyntax extends EitherTSyntax with EitherTExtendedSyntax[Pipe, Sink] {
  implicit class EitherTPipeOps[I, A, B](override val stream: EitherT[FunctionPipe[Observable, I, ?], A, B])
      extends AllOps[FunctionPipe[Observable, I, ?], A, B]

  implicit class EitherTFlowApply[I, A](override val stream: FunctionPipe[Observable, I, A])
      extends EitherTApply[FunctionPipe[Observable, I, ?], A]

  implicit class EitherTFlowApplyEither[I, A, B](override val stream: FunctionPipe[Observable, I, Either[A, B]])
      extends EitherTApplyEither[FunctionPipe[Observable, I, ?], A, B]
}

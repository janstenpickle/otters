package otters.syntax.akkastream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import cats.data.EitherT
import otters.syntax.{EitherTApply, EitherTApplyEither, EitherTExtendedSyntax, EitherTSyntax}

trait AkkaStreamEitherTSyntax extends EitherTSyntax with EitherTExtendedSyntax[Flow[?, ?, NotUsed], Sink] {
  implicit class EitherTFlowOps[I, A, B](override val stream: EitherT[Flow[I, ?, NotUsed], A, B])
      extends AllOps[Flow[I, ?, NotUsed], A, B]

  implicit class EitherTFlowApply[I, A](override val stream: Flow[I, A, NotUsed])
      extends EitherTApply[Flow[I, ?, NotUsed], A]

  implicit class EitherTFlowApplyEither[I, A, B](override val stream: Flow[I, Either[A, B], NotUsed])
      extends EitherTApplyEither[Flow[I, ?, NotUsed], A, B]
}

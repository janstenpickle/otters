package otters.syntax.akkastream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import cats.data.EitherT
import otters.syntax.{EitherTApply, EitherTApplyEither, EitherTExtendedSyntax, EitherTSyntax}

trait AkkaStreamEitherTSyntax extends EitherTSyntax with EitherTExtendedSyntax[Flow[?, ?, NotUsed], Sink] {
  implicit class EitherTFlowOps[A, B, C](override val stream: EitherT[Flow[A, ?, NotUsed], B, C])
      extends AllOps[Flow[A, ?, NotUsed], B, C]

  implicit class EitherTFlowApply[A, B](override val stream: Flow[A, B, NotUsed])
      extends EitherTApply[Flow[A, ?, NotUsed], B]

  implicit class EitherTFlowApplyEither[A, B, C](override val stream: Flow[A, Either[B, C], NotUsed])
      extends EitherTApplyEither[Flow[A, ?, NotUsed], B, C]
}

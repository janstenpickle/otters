package otters.syntax.akkastream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import cats.data.WriterT
import otters.syntax._
import shapeless.LowPriority

trait AkkaStreamWriterTSyntax extends WriterTSyntax with WriterTExtendedSyntax[Flow[?, ?, NotUsed], Sink] {
  implicit class WriterTFlowOps[L, A, M, B](override val stream: WriterT[Flow[(L, A), ?, NotUsed], M, B])
      extends AllOps[Flow[(L, A), ?, NotUsed], M, B]

  implicit class WriterTFlowApply[A, B](override val stream: Flow[A, B, NotUsed])(implicit lp: LowPriority)
      extends WriterTApply[Flow[A, ?, NotUsed], B]

  implicit class WriterTFlowTupleApply[L, A, M, B](override val stream: Flow[(L, A), (M, B), NotUsed])
      extends WriterTApplyTuple[Flow[(L, A), ?, NotUsed], M, B]
}

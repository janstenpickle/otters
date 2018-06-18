package otters.syntax.akkastream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import cats.data.WriterT
import otters.syntax._
import shapeless.LowPriority

trait AkkaStreamWriterTSyntax extends WriterTSyntax with WriterTExtendedSyntax[Flow[?, ?, NotUsed], Sink] {
  implicit class WriterTFlowOps[I, L, A](override val stream: WriterT[Flow[I, ?, NotUsed], L, A])
      extends AllOps[Flow[I, ?, NotUsed], L, A]

  implicit class WriterTFlowApply[I, A](override val stream: Flow[I, A, NotUsed])(implicit lp: LowPriority)
      extends WriterTApply[Flow[I, ?, NotUsed], A]

  implicit class WriterTFlowTupleApply[I, L, A](override val stream: Flow[I, (L, A), NotUsed])
      extends WriterTApplyTuple[Flow[I, ?, NotUsed], L, A]
}

package otters.syntax.monix.reactive

import cats.data.WriterT
import monix.reactive.Observable
import otters.FunctionPipe
import otters.instances.monix.reactive.{Pipe, Sink}
import otters.syntax.{WriterTApply, WriterTApplyTuple, WriterTExtendedSyntax, WriterTSyntax}
import shapeless.LowPriority

trait ObserableWriterTSyntax extends WriterTSyntax with WriterTExtendedSyntax[Pipe, Sink] {
  implicit class WriterTPipeOps[L, A, M, B](override val stream: WriterT[FunctionPipe[Observable, (L, A), ?], M, B])
      extends AllOps[FunctionPipe[Observable, (L, A), ?], M, B]

  implicit class WriterTFlowApply[L, A, B](override val stream: FunctionPipe[Observable, A, B])(
    implicit lp: LowPriority
  ) extends WriterTApply[FunctionPipe[Observable, A, ?], B]

  implicit class WriterTFlowTupleApply[L, A, M, B](override val stream: FunctionPipe[Observable, (L, A), (M, B)])
      extends WriterTApplyTuple[FunctionPipe[Observable, (L, A), ?], M, B]
}

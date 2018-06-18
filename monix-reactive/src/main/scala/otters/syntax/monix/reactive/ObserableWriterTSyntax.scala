package otters.syntax.monix.reactive

import cats.data.WriterT
import monix.reactive.Observable
import otters.FunctionPipe
import otters.instances.monix.reactive.{Pipe, Sink}
import otters.syntax.{WriterTApply, WriterTApplyTuple, WriterTExtendedSyntax, WriterTSyntax}
import shapeless.LowPriority

trait ObserableWriterTSyntax extends WriterTSyntax with WriterTExtendedSyntax[Pipe, Sink] {
  implicit class WriterTPipeOps[I, L, A](override val stream: WriterT[FunctionPipe[Observable, I, ?], L, A])
      extends AllOps[FunctionPipe[Observable, I, ?], L, A]

  implicit class WriterTFlowApply[I, A](override val stream: FunctionPipe[Observable, I, A])(implicit lp: LowPriority)
      extends WriterTApply[FunctionPipe[Observable, I, ?], A]

  implicit class WriterTFlowTupleApply[I, L, A](override val stream: FunctionPipe[Observable, I, (L, A)])
      extends WriterTApplyTuple[FunctionPipe[Observable, I, ?], L, A]
}

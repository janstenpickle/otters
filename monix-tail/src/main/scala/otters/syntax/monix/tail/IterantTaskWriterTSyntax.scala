package otters.syntax.monix.tail

import cats.data.WriterT
import monix.eval.Task
import otters.syntax.{WriterTApply, WriterTApplyTuple, WriterTExtendedSyntax, WriterTSyntax}
import otters.{FunctionPipe, FunctionSink}
import shapeless.LowPriority

trait IterantTaskWriterTSyntax
    extends WriterTSyntax
    with WriterTExtendedSyntax[FunctionPipe[IterantTask, ?, ?], FunctionSink[IterantTask, Task, ?, ?]] {
  implicit class WriterTPipeOps[L, A, M, B](override val stream: WriterT[FunctionPipe[IterantTask, (L, A), ?], M, B])
      extends AllOps[FunctionPipe[IterantTask, (L, A), ?], M, B]

  implicit class WriterTFlowApply[L, A, B](override val stream: FunctionPipe[IterantTask, A, B])(
    implicit lp: LowPriority
  ) extends WriterTApply[FunctionPipe[IterantTask, A, ?], B]

  implicit class WriterTFlowTupleApply[L, A, M, B](override val stream: FunctionPipe[IterantTask, (L, A), (M, B)])
      extends WriterTApplyTuple[FunctionPipe[IterantTask, (L, A), ?], M, B]
}

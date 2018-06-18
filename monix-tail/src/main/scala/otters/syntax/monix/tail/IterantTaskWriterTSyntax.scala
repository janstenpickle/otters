package otters.syntax.monix.tail

import cats.data.WriterT
import monix.eval.Task
import otters.syntax.{WriterTApply, WriterTApplyTuple, WriterTExtendedSyntax, WriterTSyntax}
import otters.{FunctionPipe, FunctionSink}
import shapeless.LowPriority

trait IterantTaskWriterTSyntax
    extends WriterTSyntax
    with WriterTExtendedSyntax[FunctionPipe[IterantTask, ?, ?], FunctionSink[IterantTask, Task, ?, ?]] {
  implicit class WriterTPipeOps[I, L, A](override val stream: WriterT[FunctionPipe[IterantTask, I, ?], L, A])
      extends AllOps[FunctionPipe[IterantTask, I, ?], L, A]

  implicit class WriterTFlowApply[I, A](override val stream: FunctionPipe[IterantTask, I, A])(implicit lp: LowPriority)
      extends WriterTApply[FunctionPipe[IterantTask, I, ?], A]

  implicit class WriterTFlowTupleApply[I, L, A](override val stream: FunctionPipe[IterantTask, I, (L, A)])
      extends WriterTApplyTuple[FunctionPipe[IterantTask, I, ?], L, A]
}

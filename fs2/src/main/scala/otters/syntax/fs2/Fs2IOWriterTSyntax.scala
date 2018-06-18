package otters.syntax.fs2

import cats.data.WriterT
import cats.effect.IO
import otters.syntax.{WriterTApply, WriterTApplyTuple, WriterTExtendedSyntax, WriterTSyntax}
import otters.{FunctionPipe, FunctionSink}
import shapeless.LowPriority

trait Fs2IOWriterTSyntax
    extends WriterTSyntax
    with WriterTExtendedSyntax[FunctionPipe[StreamIO, ?, ?], FunctionSink[StreamIO, IO, ?, ?]] {
  implicit class WriterTPipeOps[I, L, A](override val stream: WriterT[FunctionPipe[StreamIO, I, ?], L, A])
      extends AllOps[FunctionPipe[StreamIO, I, ?], L, A]

  implicit class WriterTFlowApply[I, A](override val stream: FunctionPipe[StreamIO, I, A])(implicit lp: LowPriority)
      extends WriterTApply[FunctionPipe[StreamIO, I, ?], A]

  implicit class WriterTFlowTupleApply[I, L, A](override val stream: FunctionPipe[StreamIO, I, (L, A)])
      extends WriterTApplyTuple[FunctionPipe[StreamIO, I, ?], L, A]
}

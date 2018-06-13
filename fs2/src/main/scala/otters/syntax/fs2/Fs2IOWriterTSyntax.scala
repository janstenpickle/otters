package otters.syntax.fs2

import cats.data.WriterT
import cats.effect.IO
import otters.syntax.{WriterTApply, WriterTApplyTuple, WriterTExtendedSyntax, WriterTSyntax}
import otters.{FunctionPipe, FunctionSink}
import shapeless.LowPriority

trait Fs2IOWriterTSyntax
    extends WriterTSyntax
    with WriterTExtendedSyntax[FunctionPipe[StreamIO, ?, ?], FunctionSink[StreamIO, IO, ?, ?]] {
  implicit class WriterTPipeOps[L, A, M, B](override val stream: WriterT[FunctionPipe[StreamIO, (L, A), ?], M, B])
      extends AllOps[FunctionPipe[StreamIO, (L, A), ?], M, B]

  implicit class WriterTFlowApply[L, A, B](override val stream: FunctionPipe[StreamIO, A, B])(implicit lp: LowPriority)
      extends WriterTApply[FunctionPipe[StreamIO, A, ?], B]

  implicit class WriterTFlowTupleApply[L, A, M, B](override val stream: FunctionPipe[StreamIO, (L, A), (M, B)])
      extends WriterTApplyTuple[FunctionPipe[StreamIO, (L, A), ?], M, B]
}

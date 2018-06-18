package otters.syntax.fs2

import cats.data.EitherT
import cats.effect.IO
import otters.syntax.{EitherTApply, EitherTApplyEither, EitherTExtendedSyntax, EitherTSyntax}
import otters.{FunctionPipe, FunctionSink}

trait Fs2IOEitherTSyntax
    extends EitherTSyntax
    with EitherTExtendedSyntax[FunctionPipe[StreamIO, ?, ?], FunctionSink[StreamIO, IO, ?, ?]] {
  implicit class EitherTPipeOps[I, A, B](override val stream: EitherT[FunctionPipe[StreamIO, I, ?], A, B])
      extends AllOps[FunctionPipe[StreamIO, I, ?], A, B]

  implicit class EitherTFlowApply[I, A](override val stream: FunctionPipe[StreamIO, I, A])
      extends EitherTApply[FunctionPipe[StreamIO, I, ?], A]

  implicit class EitherTFlowApplyEither[I, A, B](override val stream: FunctionPipe[StreamIO, I, Either[A, B]])
      extends EitherTApplyEither[FunctionPipe[StreamIO, I, ?], A, B]
}

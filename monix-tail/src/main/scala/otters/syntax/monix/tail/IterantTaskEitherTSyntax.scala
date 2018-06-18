package otters.syntax.monix.tail

import cats.data.EitherT
import monix.eval.Task
import otters.syntax.{EitherTApply, EitherTApplyEither, EitherTExtendedSyntax, EitherTSyntax}
import otters.{FunctionPipe, FunctionSink}

trait IterantTaskEitherTSyntax
    extends EitherTSyntax
    with EitherTExtendedSyntax[FunctionPipe[IterantTask, ?, ?], FunctionSink[IterantTask, Task, ?, ?]] {
  implicit class EitherTPipeOps[I, A, B](override val stream: EitherT[FunctionPipe[IterantTask, I, ?], A, B])
      extends AllOps[FunctionPipe[IterantTask, I, ?], A, B]

  implicit class EitherTFlowApply[I, A](override val stream: FunctionPipe[IterantTask, I, A])
      extends EitherTApply[FunctionPipe[IterantTask, I, ?], A]

  implicit class EitherTFlowApplyEither[I, A, B](override val stream: FunctionPipe[IterantTask, I, Either[A, B]])
      extends EitherTApplyEither[FunctionPipe[IterantTask, I, ?], A, B]
}

package otters

import cats.data.EitherT
import monix.eval.Task
import monix.tail.Iterant

class IterantPipeEitherStreamSuite
    extends IterantPipeTestBase[Int]
    with EitherStreamSuite[FunctionPipe[Iterant[Task, ?], Int, ?], Task, FunctionSink[Iterant[Task, ?], Task, Int, ?], FunctionPipe[
      Iterant[Task, ?],
      ?,
      ?
    ], FunctionSink[Iterant[Task, ?], Task, ?, ?]] {

  import EitherSyntax._

  override def input: Iterant[Task, Int] = Iterant.pure(1)

  override def mkEitherStream[A, B](
    src: FunctionPipe[Iterant[Task, ?], Int, Either[A, B]]
  ): EitherT[FunctionPipe[Iterant[Task, ?], Int, ?], A, B] = src.toEitherT

  override def mkEitherStream[A](
    src: FunctionPipe[Iterant[Task, ?], Int, A],
    isLeft: A => Boolean
  ): EitherT[FunctionPipe[Iterant[Task, ?], Int, ?], A, A] =
    EitherT[FunctionPipe[Iterant[Task, ?], Int, ?], A, A](F.split[A](src)(isLeft))

  override def mkEitherStream[A, B, C](
    src: FunctionPipe[Iterant[Task, ?], Int, A],
    isLeft: A => Boolean,
    f: A => B,
    g: A => C
  ): EitherT[FunctionPipe[Iterant[Task, ?], Int, ?], B, C] =
    EitherT[FunctionPipe[Iterant[Task, ?], Int, ?], B, C](F.split[A, B, C](src)(isLeft, f, g))

  override def mkEitherStreamCatch[A, B](
    src: FunctionPipe[Iterant[Task, ?], Int, A],
    f: A => B
  ): EitherT[FunctionPipe[Iterant[Task, ?], Int, ?], Throwable, B] = src.catchNonFatal(f)
}

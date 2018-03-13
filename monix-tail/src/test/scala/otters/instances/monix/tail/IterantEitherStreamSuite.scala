package otters

import cats.data.EitherT
import monix.eval.Task
import monix.tail.Iterant
import otters.syntax.either._

class IterantEitherStreamSuite extends IterantTestBase with EitherStreamSuite[Iterant[Task, ?], Task, Task] {
  override def mkEitherStream[A, B](src: Iterant[Task, Either[A, B]]): EitherT[Iterant[Task, ?], A, B] = src.toEitherT

  override def mkEitherStream[A](src: Iterant[Task, A], isLeft: A => Boolean): EitherT[Iterant[Task, ?], A, A] =
    src.split(isLeft)

  override def mkEitherStream[A, B, C](
    src: Iterant[Task, A],
    isLeft: A => Boolean,
    f: A => B,
    g: A => C
  ): EitherT[Iterant[Task, ?], B, C] = src.split(isLeft, f, g)

  override def mkEitherStreamCatch[A, B](src: Iterant[Task, A], f: A => B): EitherT[Iterant[Task, ?], Throwable, B] =
    src.catchNonFatal(f)
}

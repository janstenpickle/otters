package otters

import cats.data.EitherT
import monix.eval.Task
import monix.reactive.Observable
import otters.syntax.either._

class ObservableEitherStreamSuite extends ObservableTestBase with EitherStreamSuite[Observable, Task, Task] {
  override def mkEitherStream[A, B](src: Observable[Either[A, B]]): EitherT[Observable, A, B] = src.toEitherT

  override def mkEitherStream[A](src: Observable[A], isLeft: A => Boolean): EitherT[Observable, A, A] =
    src.split(isLeft)

  override def mkEitherStream[A, B, C](
    src: Observable[A],
    isLeft: A => Boolean,
    f: A => B,
    g: A => C
  ): EitherT[Observable, B, C] = src.split(isLeft, f, g)

  override def mkEitherStreamCatch[A, B](src: Observable[A], f: A => B): EitherT[Observable, Throwable, B] =
    src.catchNonFatal(f)
}

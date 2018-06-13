package otters

import cats.data.EitherT
import monix.eval.Task
import monix.reactive.Observable
import otters.instances.monix.reactive.{Pipe, Sink}
import otters.syntax.monix.reactive.either._

class ObservablePipeEitherStreamSuite
    extends ObservablePipeTestBase[Int]
    with EitherStreamSuite[Pipe[Int, ?], Task, Sink[Int, ?], Pipe, Sink] {
  override def input: Observable[Int] = Observable.pure(1)

  override def mkEitherStream[A, B](src: Pipe[Int, Either[A, B]]): EitherT[Pipe[Int, ?], A, B] = src.toEitherT

  override def mkEitherStream[A](src: Pipe[Int, A], isLeft: A => Boolean): EitherT[Pipe[Int, ?], A, A] =
    src.split(isLeft)

  override def mkEitherStream[A, B, C](
    src: Pipe[Int, A],
    isLeft: A => Boolean,
    f: A => B,
    g: A => C
  ): EitherT[Pipe[Int, ?], B, C] = src.split(isLeft, f, g)

  override def mkEitherStreamCatch[A, B](src: Pipe[Int, A], f: A => B): EitherT[Pipe[Int, ?], Throwable, B] =
    src.catchNonFatal(f)
}

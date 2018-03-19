package otters

import cats.syntax.either._

trait EitherStreamBase[F[_]] { self: Stream[F] =>
  def catchNonFatal[A, B](fa: F[A])(f: A => B): F[Either[Throwable, B]] =
    map(fa)(a => Either.catchNonFatal(f(a)))

  def split[A, B, C](fa: F[A])(isLeft: A => Boolean, f: A => B, g: A => C): F[Either[B, C]] =
    map(fa)(
      a =>
        if (isLeft(a)) Left(f(a))
        else Right(g(a))
    )

  def split[A](fa: F[A])(isLeft: A => Boolean): F[Either[A, A]] = split[A, A, A](fa)(isLeft, identity, identity)

}

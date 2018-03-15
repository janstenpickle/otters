package otters

import cats.Id
import cats.data.EitherT

import otters.syntax.either._

class SeqEitherStreamSuite extends EitherStreamSuite[Seq, Id, Id] with SeqStreamBase {
  override def mkEitherStream[A, B](src: Seq[Either[A, B]]): EitherT[Seq, A, B] = src.toEitherT
  override def mkEitherStream[A](src: Seq[A], isLeft: A => Boolean): EitherT[Seq, A, A] = src.split(isLeft)
  override def mkEitherStream[A, B, C](src: Seq[A], isLeft: A => Boolean, f: A => B, g: A => C): EitherT[Seq, B, C] =
    src.split(isLeft, f, g)
  override def mkEitherStreamCatch[A, B](src: Seq[A], f: A => B): EitherT[Seq, Throwable, B] = src.catchNonFatal(f)
}

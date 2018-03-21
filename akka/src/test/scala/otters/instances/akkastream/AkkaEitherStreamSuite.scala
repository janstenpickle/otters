package otters

import akka.stream.scaladsl.{RunnableGraph, Sink}
import cats.data.EitherT
import otters.instances.akkastream.{Flw, Src}

import scala.concurrent.Future
import otters.syntax.akkastream.either._

class AkkaEitherStreamSuite extends AkkaBaseSuite with EitherStreamSuite[Src, Future, RunnableGraph, Flw, Sink] {
  override def mkEitherStream[A, B](src: Src[Either[A, B]]): EitherT[Src, A, B] = src.toEitherT

  override def mkEitherStream[A](src: Src[A], isLeft: A => Boolean): EitherT[Src, A, A] = src.split(isLeft)

  override def mkEitherStream[A, B, C](src: Src[A], isLeft: A => Boolean, f: A => B, g: A => C): EitherT[Src, B, C] =
    src.split(isLeft, f, g)

  override def mkEitherStreamCatch[A, B](src: Src[A], f: A => B): EitherT[Src, Throwable, B] = src.catchNonFatal(f)
}

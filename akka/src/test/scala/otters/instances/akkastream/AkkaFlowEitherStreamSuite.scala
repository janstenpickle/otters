package otters

import akka.stream.scaladsl.{Sink, Source}
import cats.data.EitherT
import otters.instances.akkastream.{Flw, Src}
import otters.syntax.akkastream.either._

import scala.concurrent.Future

class AkkaFlowEitherStreamSuite
    extends AkkaFlowBaseSuite[Int]
    with EitherStreamSuite[Flw[Int, ?], Future, Sink[Int, ?], Flw, Sink] {
  override def input: Src[Int] = Source.single(1)

  override def mkEitherStream[A, B](src: Flw[Int, Either[A, B]]): EitherT[Flw[Int, ?], A, B] = src.toEitherT

  override def mkEitherStream[A](src: Flw[Int, A], isLeft: A => Boolean): EitherT[Flw[Int, ?], A, A] = src.split(isLeft)

  override def mkEitherStream[A, B, C](
    src: Flw[Int, A],
    isLeft: A => Boolean,
    f: A => B,
    g: A => C
  ): EitherT[Flw[Int, ?], B, C] = src.split(isLeft, f, g)

  override def mkEitherStreamCatch[A, B](src: Flw[Int, A], f: A => B): EitherT[Flw[Int, ?], Throwable, B] =
    src.catchNonFatal(f)
}

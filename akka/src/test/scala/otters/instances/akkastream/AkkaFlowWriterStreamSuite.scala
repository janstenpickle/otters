package otters

import akka.stream.scaladsl.{Sink, Source}
import cats.data.WriterT
import cats.kernel.Monoid
import otters.instances.akkastream.{Flw, Src}
import otters.syntax.akkastream.writer._

import scala.concurrent.Future

class AkkaFlowWriterStreamSuite
    extends AkkaFlowBaseSuite[Int]
    with WriterStreamSuite[Flw[Int, ?], Future, Sink[Int, ?], Flw, Sink] {
  override def input: Src[Int] = Source.single(1)

  override def mkWriterStream[L: Monoid, A](src: Flw[Int, A]): WriterT[Flw[Int, ?], L, A] = src.toWriter

  override def mkWriterStream[L, A](src: Flw[Int, A], initial: L): WriterT[Flw[Int, ?], L, A] = src.toWriter(initial)

  override def mkWriterStream[L, A](src: Flw[Int, (L, A)]): WriterT[Flw[Int, ?], L, A] = src.toWriter
}

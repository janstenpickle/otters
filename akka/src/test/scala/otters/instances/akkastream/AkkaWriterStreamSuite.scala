package otters

import akka.stream.scaladsl.{RunnableGraph, Sink}
import cats.data.WriterT
import cats.kernel.Monoid
import otters.instances.akkastream.{Flw, Src}
import otters.syntax.akkastream.writer._

import scala.concurrent.Future

class AkkaWriterStreamSuite extends AkkaBaseSuite with WriterStreamSuite[Src, Future, RunnableGraph, Flw, Sink] {

  override def mkWriterStream[S: Monoid, A](src: Src[A]): WriterT[Src, S, A] = src.toWriter

  override def mkWriterStream[S, A](src: Src[A], initial: S): WriterT[Src, S, A] = src.toWriter(initial)

  override def mkWriterStream[S, A](src: Src[(S, A)]): WriterT[Src, S, A] = src.toWriter
}

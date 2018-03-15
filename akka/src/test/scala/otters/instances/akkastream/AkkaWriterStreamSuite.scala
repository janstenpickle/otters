package otters

import akka.stream.scaladsl.RunnableGraph
import cats.data.WriterT
import cats.kernel.Monoid
import otters.instances.akkastream.Src
import otters.syntax.writer._

import scala.concurrent.Future

class AkkaWriterStreamSuite extends AkkaBaseSuite with WriterStreamSuite[Src, Future, RunnableGraph] {

  override def mkWriterStream[S: Monoid, A](src: Src[A]): WriterT[Src, S, A] = src.toWriter

  override def mkWriterStream[S, A](src: Src[A], initial: S): WriterT[Src, S, A] = src.toWriter(initial)

  override def mkWriterStream[S, A](src: Src[(S, A)]): WriterT[Src, S, A] = src.toWriter
}

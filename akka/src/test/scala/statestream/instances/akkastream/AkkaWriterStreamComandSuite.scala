package statestream

import akka.stream.scaladsl.RunnableGraph
import cats.kernel.Monoid
import cats.{~>, Bimonad, Eval, Monad}
import org.scalatest.BeforeAndAfterAll
import statestream.instances.akkastream.Src
import statestream.syntax.akkastream.comonad._

import scala.concurrent.Future

class AkkaWriterStreamComandSuite extends AkkaWriterStreamSuite[Eval] with BeforeAndAfterAll {

  override implicit def G: Monad[Eval] = implicitly[Bimonad[Eval]]
  override implicit def nat: ~>[Eval, Future] = new ~>[Eval, Future] {
    override def apply[A](fa: Eval[A]): Future[A] = Future(fa.value)
  }

  override def extract[A](fa: Eval[A]): A = fa.value

  override def mkWriterStream[S: Monoid, A](src: Src[A]): WriterStream[Src, Eval, Future, RunnableGraph, S, A] =
    src.toWriterStream[Eval, S]

  override def mkWriterStream[S, A](src: Src[(S, A)]): WriterStream[Src, Eval, Future, RunnableGraph, S, A] =
    src.toWriterStream[Eval]

}

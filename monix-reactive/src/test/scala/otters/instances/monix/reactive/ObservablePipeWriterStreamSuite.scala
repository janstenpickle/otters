package otters

import cats.data.WriterT
import cats.kernel.Monoid
import monix.eval.Task
import monix.reactive.Observable
import otters.instances.monix.reactive.{Pipe, Sink}
import otters.syntax.monix.reactive.writer._

class ObservablePipeWriterStreamSuite
    extends ObservablePipeTestBase[Int]
    with WriterStreamSuite[Pipe[Int, ?], Task, Sink[Int, ?], Pipe, Sink] {
  override def input: Observable[Int] = Observable.pure(1)

  override def mkWriterStream[L: Monoid, A](src: Pipe[Int, A]): WriterT[Pipe[Int, ?], L, A] = src.toWriter

  override def mkWriterStream[L, A](src: Pipe[Int, A], initial: L): WriterT[Pipe[Int, ?], L, A] = src.toWriter(initial)

  override def mkWriterStream[L, A](src: Pipe[Int, (L, A)]): WriterT[Pipe[Int, ?], L, A] = src.toWriter
}

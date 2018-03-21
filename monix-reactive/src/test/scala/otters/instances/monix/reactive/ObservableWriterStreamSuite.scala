package otters

import cats.data.WriterT
import cats.kernel.Monoid
import monix.eval.Task
import monix.reactive.Observable
import otters.instances.monix.reactive.{Pipe, Sink}
import otters.syntax.monix.reactive.writer._

class ObservableWriterStreamSuite
    extends ObservableTestBase
    with WriterStreamSuite[Observable, Task, Task, Pipe, Sink] {
  override def mkWriterStream[S: Monoid, A](src: Observable[A]): WriterT[Observable, S, A] = src.toWriter

  override def mkWriterStream[S, A](src: Observable[A], initial: S): WriterT[Observable, S, A] = src.toWriter(initial)

  override def mkWriterStream[S, A](src: Observable[(S, A)]): WriterT[Observable, S, A] = src.toWriter
}

package otters

import cats.data.WriterT
import cats.kernel.Monoid
import monix.eval.Task
import monix.reactive.Observable
import otters.syntax.writer._

class ObservableWriterStreamSuite extends ObservableTestBase with WriterStreamSuite[Observable, Task, Task] {
  override def mkWriterStream[S: Monoid, A](src: Observable[A]): WriterT[Observable, S, A] = src.toWriter

  override def mkWriterStream[S, A](src: Observable[A], initial: S): WriterT[Observable, S, A] = src.toWriter(initial)

  override def mkWriterStream[S, A](src: Observable[(S, A)]): WriterT[Observable, S, A] = src.toWriter
}

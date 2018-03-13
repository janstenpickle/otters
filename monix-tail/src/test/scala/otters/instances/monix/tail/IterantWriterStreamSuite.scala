package otters

import cats.data.WriterT
import cats.kernel.Monoid
import monix.eval.Task
import monix.tail.Iterant
import otters.syntax.writer._

class IterantWriterStreamSuite extends IterantTestBase with WriterStreamSuite[Iterant[Task, ?], Task, Task] {
  override def mkWriterStream[S: Monoid, A](src: Iterant[Task, A]): WriterT[Iterant[Task, ?], S, A] = src.toWriter

  override def mkWriterStream[S, A](src: Iterant[Task, A], initial: S): WriterT[Iterant[Task, ?], S, A] =
    src.toWriter(initial)

  override def mkWriterStream[S, A](src: Iterant[Task, (S, A)]): WriterT[Iterant[Task, ?], S, A] = src.toWriter
}

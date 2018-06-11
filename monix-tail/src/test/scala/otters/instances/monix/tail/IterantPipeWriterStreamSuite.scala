package otters

import cats.Monoid
import cats.data.WriterT
import monix.eval.Task
import monix.tail.Iterant

import otters.syntax.monix.tail.writer._

class IterantPipeWriterStreamSuite
    extends IterantPipeTestBase[Int]
    with WriterStreamSuite[FunctionPipe[Iterant[Task, ?], Int, ?], Task, FunctionSink[Iterant[Task, ?], Task, Int, ?], FunctionPipe[
      Iterant[Task, ?],
      ?,
      ?
    ], FunctionSink[Iterant[Task, ?], Task, ?, ?]] {

  override def input: Iterant[Task, Int] = Iterant.pure(1)

  override def mkWriterStream[L: Monoid, A](
    src: FunctionPipe[Iterant[Task, ?], Int, A]
  ): WriterT[FunctionPipe[Iterant[Task, ?], Int, ?], L, A] = src.toWriter

  override def mkWriterStream[L, A](
    src: FunctionPipe[Iterant[Task, ?], Int, A],
    initial: L
  ): WriterT[FunctionPipe[Iterant[Task, ?], Int, ?], L, A] = src.toWriter(initial)

  override def mkWriterStream[L, A](
    src: FunctionPipe[Iterant[Task, ?], Int, (L, A)]
  ): WriterT[FunctionPipe[Iterant[Task, ?], Int, ?], L, A] = src.toWriter
}

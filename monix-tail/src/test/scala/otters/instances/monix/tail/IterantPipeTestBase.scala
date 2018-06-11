package otters

import cats.Monad
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.tail.Iterant

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait IterantPipeTestBase[I]
    extends TestBase[FunctionPipe[Iterant[Task, ?], I, ?], Task, FunctionSink[Iterant[Task, ?], Task, I, ?], FunctionPipe[
      Iterant[Task, ?],
      ?,
      ?
    ], FunctionSink[Iterant[Task, ?], Task, ?, ?]] {

  implicit val ev = otters.instances.monix.tail.iterantInstances[Task]

  def input: Iterant[Task, I]

  override implicit def F: EitherStream[
    FunctionPipe[Iterant[Task, ?], I, ?],
    Task,
    FunctionSink[Iterant[Task, ?], Task, I, ?],
    FunctionPipe[Iterant[Task, ?], ?, ?],
    FunctionSink[Iterant[Task, ?], Task, ?, ?]
  ] = otters.instances.monix.tail.iterantPipeInstances[Task, I]

  override def mkPipe[A, B](f: A => B): FunctionPipe[Iterant[Task, ?], A, B] = _.map(f)

  override def mkSeqSink[A]: FunctionSink[Iterant[Task, ?], Task, A, Task[Seq[A]]] = _.toListL.map(Task(_))

  override def runStream[A](stream: FunctionPipe[Iterant[Task, ?], I, A]): Seq[A] =
    waitFor(materialize(stream.andThen(mkSeqSink)))

  override def materialize[A](i: FunctionSink[Iterant[Task, ?], Task, I, A]): A = waitFor(i(input))

  override def waitFor[A](fut: Task[A]): A = Await.result(fut.runAsync, Duration.Inf)

  override implicit def G: Monad[Task] = monix.eval.instances.CatsConcurrentForTask

}

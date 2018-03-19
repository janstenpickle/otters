package otters

import cats.Monad
import cats.effect.Effect
import monix.eval.Task
import monix.eval.instances.CatsEffectForTask
import monix.tail.Iterant
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait IterantTestBase
    extends TestBase[Iterant[Task, ?], Task, Task, FunctionPipe[Iterant[Task, ?], ?, ?], FunctionSink[
      Iterant[Task, ?],
      Task,
      ?,
      ?
    ]] {
  implicit val taskEffect: Effect[Task] = new CatsEffectForTask()(global)

  override implicit def F: EitherStream[
    Iterant[Task, ?],
    Task,
    Task,
    FunctionPipe[Iterant[Task, ?], ?, ?],
    FunctionSink[Iterant[Task, ?], Task, ?, ?]
  ] =
    otters.instances.monix.tail.iterantInstances[Task]

  override def mkPipe[A, B](f: A => B): FunctionPipe[Iterant[Task, ?], A, B] = _.map(f)

  override def mkSeqSink[A]: FunctionSink[Iterant[Task, ?], Task, A, Task[Seq[A]]] =
    _.toListL.map(Task(_))

  override def runStream[A](stream: Iterant[Task, A]): Seq[A] = waitFor(waitFor(mkSeqSink(stream)))

  override def materialize[A](i: Task[A]): A = waitFor(i)

  override def waitFor[A](fut: Task[A]): A = Await.result(fut.runAsync, Duration.Inf)

  override implicit def G: Monad[Task] = monix.eval.instances.CatsConcurrentForTask

}

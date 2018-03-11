package statestream

import cats.Monad
import cats.effect.Effect
import monix.eval.Task
import monix.eval.instances.CatsEffectForTask
import monix.execution.Scheduler.Implicits.global
import monix.tail.Iterant
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait IterantWriterStreamIterantSuite[G[_]]
    extends WriterStreamSuite[Iterant[Task, ?], G, Task, Task]
    with BeforeAndAfterAll {

  implicit val taskEffect: Effect[Task] = new CatsEffectForTask()(global)

  override implicit def F: TupleStream[Iterant[Task, ?], Task, Task] =
    statestream.instances.monix.tail.iterantInstances[Task]

  override implicit def H: Monad[Task] = monix.eval.instances.CatsAsyncForTask

  override def mkPipe[A, B](f: A => B): Pipe[Iterant[Task, ?], A, B] = _.map(f)

  override def mkSeqSink[A]: Sink[Iterant[Task, ?], Task, A, Task[Seq[A]]] =
    _.toListL.map(Task(_))

  override def runStream[A](stream: Iterant[Task, A]): Seq[A] = waitFor(waitFor(mkSeqSink(stream)))

  override def materialize[A](i: Task[A]): A = waitFor(i)

  override def waitFor[A](fut: Task[A]): A = Await.result(fut.runAsync, Duration.Inf)
}

package otters

import cats.Monad
import monix.eval.Task
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Consumer, Observable, Observer}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait ObservableTestBase extends TestBase[Observable, Task, Task] {
  override implicit def F: EitherStream[Observable, Task, Task] =
    otters.instances.monix.reactive.observableInstances

  override def mkPipe[A, B](f: A => B): Pipe[Observable, A, B] = _.map(f)

  override def mkSeqSink[A]: Sink[Observable, Task, A, Task[Seq[A]]] =
    _.consumeWith(
      Consumer.create(
        (_, _, callback) =>
          new Observer.Sync[A] {
            private val data = scala.collection.mutable.ArrayBuffer.empty[A]

            override def onNext(elem: A): Ack = {
              data += elem
              Continue
            }

            override def onError(ex: Throwable): Unit = callback.onError(ex)

            override def onComplete(): Unit = callback.onSuccess(Task(Seq(data: _*)))
        }
      )
    )

  override def runStream[A](stream: Observable[A]): Seq[A] = waitFor(waitFor(mkSeqSink(stream)))

  override def materialize[A](i: Task[A]): A = waitFor(i)

  override def waitFor[A](fut: Task[A]): A = Await.result(fut.runAsync, Duration.Inf)

  override implicit def G: Monad[Task] = monix.eval.instances.CatsAsyncForTask
}

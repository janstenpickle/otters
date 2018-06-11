package otters

import cats.Monad
import monix.eval.Task
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Consumer, Observable, Observer}
import otters.instances.monix.reactive.{Pipe, Sink}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait ObservablePipeTestBase[T] extends TestBase[Pipe[T, ?], Task, Sink[T, ?], Pipe, Sink] {
  def input: Observable[T]
  import otters.instances.monix.reactive.observableInstances

  override implicit def F: EitherStream[Pipe[T, ?], Task, Sink[T, ?], Pipe, Sink] =
    otters.instances.monix.reactive.observablePipeInstances[T]

  override def mkPipe[A, B](f: A => B): Pipe[A, B] = _.map(f)

  override def mkSeqSink[A]: Sink[A, Task[Seq[A]]] =
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

  override def runStream[A](stream: Pipe[T, A]): Seq[A] = waitFor(waitFor(mkSeqSink(stream(input))))

  override def materialize[A](i: Sink[T, A]): A = waitFor(i(input))

  override def waitFor[A](fut: Task[A]): A = Await.result(fut.runAsync, Duration.Inf)

  override implicit def G: Monad[Task] = monix.eval.instances.CatsConcurrentForTask
}

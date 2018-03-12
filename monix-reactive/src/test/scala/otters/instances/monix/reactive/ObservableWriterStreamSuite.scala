package otters

import cats.Monad
import monix.eval.Task
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Consumer, Observable, Observer}
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait ObservableWriterStreamSuite[G[_]] extends WriterStreamSuite[Observable, G, Task, Task] with BeforeAndAfterAll {

  override implicit def F: TupleStream[Observable, Task, Task] =
    otters.instances.monix.reactive.observableInstances

  override implicit def H: Monad[Task] = monix.eval.instances.CatsAsyncForTask

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
}

package otters

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink}
import cats.Monad
import org.scalatest.BeforeAndAfterAll
import otters.instances.akkastream.{Flw, Src}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

trait AkkaFlowBaseSuite[I] extends TestBase[Flw[I, ?], Future, Sink[I, ?], Flw, Sink] with BeforeAndAfterAll {
  implicit val as: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ex: ExecutionContext = as.dispatcher

  def input: Src[I]

  override implicit def F: EitherStream[Flw[I, ?], Future, Sink[I, ?], Flw, Sink] =
    otters.instances.akkastream.akkaFlowInstances[I]

  override def runStream[A](stream: Flw[I, A]): Seq[A] = waitFor(input.via(stream).toMat(mkSeqSink)(Keep.right).run())

  override def mkPipe[A, B](f: A => B): Flw[A, B] = Flow.fromFunction(f)

  override def materialize[A](i: Sink[I, A]): A = input.runWith(i)

  override def mkSeqSink[A]: Sink[A, Future[Seq[A]]] = Sink.seq

  override def waitFor[A](fut: Future[A]): A = Await.result(fut, Duration.Inf)

  override protected def afterAll(): Unit = {
    mat.shutdown()
    waitFor(as.terminate())
    super.afterAll()
  }

  override implicit def G: Monad[Future] = cats.instances.future.catsStdInstancesForFuture
}

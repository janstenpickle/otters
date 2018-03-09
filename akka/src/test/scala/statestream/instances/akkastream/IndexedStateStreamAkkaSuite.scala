package statestream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink => ASink}
import cats.Monad
import org.scalatest.BeforeAndAfterAll
import statestream.instances.akkastream.{runnableGraphSemigroupalFunctor, Src}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

trait IndexedStateStreamAkkaSuite[G[_]]
    extends IndexedStateStreamSuite[Src, G, Future, RunnableGraph]
    with BeforeAndAfterAll {
  implicit val as: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ex: ExecutionContext = as.dispatcher

  override implicit def F: TupleStream[Src, Future, RunnableGraph] =
    statestream.instances.akkastream.akkaInstances

  override implicit def H: Monad[Future] = cats.instances.future.catsStdInstancesForFuture

  override def runStream[A](stream: Src[A]): Seq[A] =
    waitFor(mkSeqSink(stream).run())

  override def mkPipe[A, B](f: A => B): Pipe[Src, A, B] = _.map(f)

  override def materialize[A](i: RunnableGraph[A]): A = i.run()

  override def mkSeqSink[A]: Sink[Src, RunnableGraph, A, Future[Seq[A]]] = _.toMat(ASink.seq)(Keep.right)

  override def waitFor[A](fut: Future[A]): A = Await.result(fut, Duration.Inf)

  override protected def afterAll(): Unit = {
    mat.shutdown()
    waitFor(as.terminate())
    super.afterAll()
  }
}

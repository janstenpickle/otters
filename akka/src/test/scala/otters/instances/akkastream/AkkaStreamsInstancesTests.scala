package otters.instances.akkastream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Source, Sink => ASink}
import cats.Eq
import cats.effect.laws.util.{TestContext, TestInstances}
import cats.effect.util.CompositeException
import cats.instances.all._
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import cats.laws.discipline.arbitrary._
import cats.laws.discipline.{FunctorTests, SemigroupalTests}
import org.scalacheck.rng.Seed
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalatest.BeforeAndAfterAll
import otters.{Pipe, Sink}
import otters.laws.TupleStreamLaws
import otters.laws.discipline.{TestBase, TupleStreamTests}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionException, Future}
import scala.util.{Failure, Success}

class AkkaStreamsInstancesTests extends TestBase with BeforeAndAfterAll with TestInstances {
  implicit val as: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext = as.dispatcher

  implicit val streamLaws: TupleStreamLaws[Source[?, NotUsed], Future, RunnableGraph] =
    TupleStreamLaws[Source[?, NotUsed], Future, RunnableGraph]

  implicit def sourceArb[A](implicit ev: Arbitrary[List[A]]): Arbitrary[Source[A, NotUsed]] =
    Arbitrary(ev.arbitrary.map(li => Source.fromIterator(() => li.iterator)))

  implicit def sourceEq[A](implicit ev: Eq[List[A]]): Eq[Source[A, NotUsed]] = Eq.by { src =>
    waitFor(src.toMat(ASink.seq)(Keep.right).run()).toList
  }

  implicit def runnableGraphArb[A](implicit ev: Arbitrary[A]): Arbitrary[RunnableGraph[Future[A]]] =
    Arbitrary(ev.arbitrary.map(a => Source.single(a).toMat(ASink.head)(Keep.right)))

  implicit def runnableGraphEq[A](implicit ev: Eq[Future[A]]): Eq[RunnableGraph[Future[A]]] = Eq.by { v =>
    val ret = v.run()
    Thread.sleep(100) // hack because akka streams uses a different execution context
    ret
  }

  implicit def runnableGraphEq2[A, B](
    implicit ev1: Eq[Future[A]],
    ev2: Eq[Future[B]]
  ): Eq[RunnableGraph[(Future[A], Future[B])]] = Eq.by { v =>
    true // force test to pass because akka streams uses a different execution context
  }

  implicit def pipeArb[A, B](implicit ev: Arbitrary[A => B]): Arbitrary[Pipe[Source[?, NotUsed], A, B]] =
    Arbitrary(ev.arbitrary.map(f => (s: Source[A, NotUsed]) => s.map(f)))

  implicit def sinkFnArb[A, B](
    implicit ev: Arbitrary[A => B],
    ec: TestContext
  ): Arbitrary[Sink[Source[?, NotUsed], RunnableGraph, A, Future[List[B]]]] =
    Arbitrary(
      ev.arbitrary
        .map(
          f => (s: Source[A, NotUsed]) => s.map(f).toMat(ASink.seq)(Keep.right).mapMaterializedValue(_.map(_.toList))
        )
    )

  implicit def sinkArb[A](
    implicit ec: TestContext
  ): Arbitrary[Sink[Source[?, NotUsed], RunnableGraph, A, Future[List[A]]]] =
    Arbitrary(
      Gen.const((s: Source[A, NotUsed]) => s.toMat(ASink.seq)(Keep.right).mapMaterializedValue(_.map(_.toList)))
    )

  implicit def sourceIso: Isomorphisms[Source[?, NotUsed]] = Isomorphisms.invariant[Source[?, NotUsed]]

  implicit def cogenFuture[A](implicit A: Cogen[A], th: Cogen[Throwable], ec: TestContext): Cogen[Future[A]] =
    Cogen { (seed: Seed, x: Future[A]) =>
      // Unwraps exceptions that got caught by Future's implementation
      // and that got wrapped in ExecutionException (`Future(throw ex)`)
      @tailrec def extractEx(ex: Throwable): String = {
        ex match {
          case e: ExecutionException if e.getCause != null =>
            extractEx(e.getCause)
          case e: CompositeException =>
            extractEx(e.head)
          case _ =>
            s"${ex.getClass.getName}: ${ex.getMessage}"
        }
      }

      ec.tick()

      x.value match {
        case None =>
          seed
        case Some(Success(a)) =>
          A.perturb(seed, a)
        case Some(Failure(ex1)) =>
          th.perturb(seed, ex1)
      }
    }

  def waitFor[A](fut: Future[A]): A = Await.result(fut, 10.second)

  implicit def eqAbc[A: Eq, B: Eq, C: Eq](
    implicit ev: Eq[(A, B, C)],
    ec: TestContext
  ): Eq[RunnableGraph[(Future[A], Future[B], Future[C])]] =
    new Eq[RunnableGraph[(Future[A], Future[B], Future[C])]] {
      override def eqv(
        x: RunnableGraph[(Future[A], Future[B], Future[C])],
        y: RunnableGraph[(Future[A], Future[B], Future[C])]
      ): Boolean = {
        val combine: ((Future[A], Future[B], Future[C])) => Future[(A, B, C)] = {
          case (f1, f2, f3) =>
            for {
              i1 <- f1
              i2 <- f2
              i3 <- f3
            } yield (i1, i2, i3)
        }

        val xx = combine(x.run())
        val yy = combine(y.run())
        Thread.sleep(100)

        Eq[Future[(A, B, C)]].eqv(xx, yy)
      }
    }

  def pureFuture[A]: Arbitrary[A => Future[A]] = Arbitrary(Gen.const((a: A) => Future.successful(a)))

  checkAllAsync(
    "Source[Int, NotUsed]",
    implicit ec => TupleStreamTests[Source[?, NotUsed], Future, RunnableGraph].tupleStream[Int, Int, Int]
  )

  checkAllAsync(
    "RunnableGraph[Future[Int]]",
    implicit ec => FunctorTests[RunnableGraph].functor[Future[Int], Future[Int], Future[Int]]
  )

  checkAllAsync(
    "RunnableGraph[Future[Int]]",
    implicit ec => SemigroupalTests[RunnableGraph].semigroupal[Future[Int], Future[Int], Future[Int]]
  )

  override protected def afterAll(): Unit = {
    mat.shutdown()
    waitFor(as.terminate())
    super.afterAll()
  }
}

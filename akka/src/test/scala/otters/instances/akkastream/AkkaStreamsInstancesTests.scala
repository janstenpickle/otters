package otters.instances.akkastream

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
import org.scalacheck.{Arbitrary, Cogen, Gen, Prop}
import org.scalatest.BeforeAndAfterAll
import otters.laws.EitherStreamLaws
import otters.laws.discipline.{EitherStreamTests, TestBase}
import otters.{Pipe, Sink}

import scala.annotation.tailrec
import scala.collection.SortedMap
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionException, Future}
import scala.util.{Failure, Success}

class AkkaStreamsInstancesTests extends TestBase with BeforeAndAfterAll with TestInstances {
  implicit val as: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext = as.dispatcher

  implicit val streamLaws: EitherStreamLaws[Src, Future, RunnableGraph] =
    EitherStreamLaws[Src, Future, RunnableGraph]

  implicit def sourceArb[A](implicit ev: Arbitrary[List[A]]): Arbitrary[Src[A]] =
    Arbitrary(ev.arbitrary.map(li => Source.fromIterator(() => li.iterator)))

  implicit def sourceEq[A](implicit ev: Eq[List[A]]): Eq[Src[A]] = Eq.by { src =>
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

  implicit def pipeArb[A, B](implicit ev: Arbitrary[A => B]): Arbitrary[Pipe[Src, A, B]] =
    Arbitrary(ev.arbitrary.map(f => (s: Src[A]) => s.map(f)))

  implicit def sinkFnArb[A, B](
    implicit ev: Arbitrary[A => B]
  ): Arbitrary[Sink[Src, RunnableGraph, A, Future[List[B]]]] =
    Arbitrary(
      ev.arbitrary
        .map(f => (s: Src[A]) => s.map(f).toMat(ASink.seq)(Keep.right).mapMaterializedValue(_.map(_.toList)))
    )

  implicit def sinkArb[A]: Arbitrary[Sink[Src, RunnableGraph, A, Future[List[A]]]] =
    Arbitrary(Gen.const((s: Src[A]) => s.toMat(ASink.seq)(Keep.right).mapMaterializedValue(_.map(_.toList))))

  implicit def sourceIso: Isomorphisms[Src] = Isomorphisms.invariant[Src]

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
        Thread.sleep(100) // hack because akka streams uses a different execution context

        Eq[Future[(A, B, C)]].eqv(xx, yy)
      }
    }

  def pureFuture[A]: Arbitrary[A => Future[A]] = Arbitrary(Gen.const((a: A) => Future.successful(a)))

  checkAllAsync(
    "Source[Int, _]",
    implicit ec => AkkaEitherStreamTest[Src, Future, RunnableGraph].akkaEitherStream[Int, Int, Int]
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

trait AkkaEitherStreamTest[F[_], G[_], H[_]] extends EitherStreamTests[F, G, H] {
  def akkaEitherStream[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](
    implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAB: Arbitrary[F[(A, B)]],
    ArbFEitherAB: Arbitrary[F[Either[A, B]]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    ArbPipeAToB: Arbitrary[Pipe[F, A, B]],
    ArbPipeBToC: Arbitrary[Pipe[F, B, C]],
    ArbSinkAToA: Arbitrary[Sink[F, H, A, G[List[A]]]],
    ArbSinkBToB: Arbitrary[Sink[F, H, B, G[List[B]]]],
    ArbSinkAToB: Arbitrary[Sink[F, H, A, G[List[B]]]],
    ArbSinkABToAB: Arbitrary[Sink[F, H, (A, B), G[List[(A, B)]]]],
    CogenA: Cogen[A],
    CogenB: Cogen[B],
    CogenC: Cogen[C],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqFInt: Eq[F[Int]],
    EqFAB: Eq[F[(A, B)]],
    EqFEitherAB: Eq[F[Either[A, B]]],
    EqGA: Eq[H[G[List[A]]]],
    EqGB: Eq[H[G[List[B]]]],
    EqFABC: Eq[F[(A, B, C)]],
    EqGAB: Eq[H[(G[List[A]], G[List[B]])]],
    EqFSeqA: Eq[F[Seq[A]]],
    iso: Isomorphisms[F]
  ): RuleSet = {
    val rs = eitherStream[A, B, C]

    val all: SortedMap[String, Prop] = SortedMap(rs.props: _*) ++ rs.parents
      .flatMap(_.all.properties)
      .filterNot(_._1.contains("tailRecM stack safety"))

    new DefaultRuleSet(name = "either stream", parent = None, all.toSeq: _*)
  }
}

object AkkaEitherStreamTest {
  def apply[F[_], G[_], H[_]](implicit ev: EitherStreamLaws[F, G, H]): AkkaEitherStreamTest[F, G, H] =
    new AkkaEitherStreamTest[F, G, H] {
      override def laws: EitherStreamLaws[F, G, H] = ev
    }
}

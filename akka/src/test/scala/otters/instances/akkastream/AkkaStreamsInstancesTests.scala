package otters.instances.akkastream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}
import cats.Eq
import cats.effect.laws.util.{TestContext, TestInstances}
import cats.effect.util.CompositeException
import cats.instances.all._
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import cats.laws.discipline.arbitrary._
import org.scalacheck.rng.Seed
import org.scalacheck.{Arbitrary, Cogen, Gen, Prop}
import org.scalatest.BeforeAndAfterAll
import otters.laws.StreamLaws
import otters.laws.discipline.{StreamTests, TestBase}

import scala.annotation.tailrec
import scala.collection.SortedMap
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionException, Future}
import scala.util.{Failure, Success}

class AkkaStreamsInstancesTests extends TestBase with BeforeAndAfterAll with TestInstances {
  implicit val as: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext = as.dispatcher

  implicit val streamLaws: StreamLaws[Src] =
    StreamLaws[Src]

  implicit def sourceArb[A](implicit ev: Arbitrary[List[A]]): Arbitrary[Src[A]] =
    Arbitrary(ev.arbitrary.map(li => Source.fromIterator(() => li.iterator)))

  implicit def sourceEq[A](implicit ev: Eq[List[A]]): Eq[Src[A]] = Eq.by { src =>
    waitFor(src.toMat(Sink.seq)(Keep.right).run()).toList
  }

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

  def pureFuture[A]: Arbitrary[A => Future[A]] = Arbitrary(Gen.const((a: A) => Future.successful(a)))

  checkAllAsync("Source[Int, _]", implicit ec => AkkaEitherStreamTest[Src].akkaStream[Int, Int, Int])

  override protected def afterAll(): Unit = {
    mat.shutdown()
    waitFor(as.terminate())
    super.afterAll()
  }
}

trait AkkaStreamTests[F[_]] extends StreamTests[F] {
  def akkaStream[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](
    implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    CogenA: Cogen[A],
    CogenB: Cogen[B],
    CogenC: Cogen[C],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqFInt: Eq[F[Int]],
    EqFABC: Eq[F[(A, B, C)]],
    EqFSeqA: Eq[F[Seq[A]]],
    iso: Isomorphisms[F]
  ): RuleSet = {
    val rs = stream[A, B, C]

    val all: SortedMap[String, Prop] = SortedMap(rs.props: _*) ++ rs.parents
      .flatMap(_.all.properties)
      .filterNot(_._1.contains("tailRecM stack safety"))

    new DefaultRuleSet(name = "async stream", parent = None, all.toSeq: _*)
  }
}

object AkkaEitherStreamTest {
  def apply[F[_]](implicit ev: StreamLaws[F]): AkkaStreamTests[F] =
    new AkkaStreamTests[F] {
      override def laws: StreamLaws[F] = ev
    }
}

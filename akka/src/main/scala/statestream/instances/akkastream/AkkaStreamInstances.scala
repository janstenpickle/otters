package statestream.instances.akkastream

import akka.NotUsed
import akka.stream.scaladsl.{GraphDSL, Keep, RunnableGraph, Source, Zip}
import akka.stream.{ClosedShape, SourceShape}
import cats.{Functor, Semigroupal}
import statestream.{Pipe, Sink, TupleStream}

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait AkkaStreamInstances {
  implicit val runnableGraphSemigroupalFunctor: Functor[RunnableGraph] with Semigroupal[RunnableGraph] =
    new Functor[RunnableGraph] with Semigroupal[RunnableGraph] {
      override def map[A, B](fa: RunnableGraph[A])(f: A => B): RunnableGraph[B] = fa.mapMaterializedValue(f)

      override def product[A, B](fa: RunnableGraph[A], fb: RunnableGraph[B]): RunnableGraph[(A, B)] =
        RunnableGraph.fromGraph(GraphDSL.create(fa, fb)(Keep.both) { implicit builder => (_, _) =>
          ClosedShape
        })
    }

  implicit def akkaInstances(
    implicit ev: Functor[RunnableGraph] with Semigroupal[RunnableGraph]
  ): TupleStream[Src, Future, RunnableGraph] =
    new TupleStream[Src, Future, RunnableGraph] {
      override implicit def H: Functor[RunnableGraph] with Semigroupal[RunnableGraph] = ev

      override def zip[A, B](fa: Src[A])(fb: Src[B]): Src[(A, B)] = joinSources(fa, fb)

      override def to[A, B](fa: Src[A])(sink: Sink[Src, RunnableGraph, A, B]): RunnableGraph[B] = sink(fa)

      override def via[A, B](fa: Source[A, NotUsed])(pipe: Pipe[Src, A, B]): Source[B, NotUsed] = pipe(fa)

      override def mapAsync[A, B](fa: Source[A, NotUsed])(f: A => Future[B]): Source[B, NotUsed] = mapAsyncN(fa)(1)(f)

      override def mapAsyncN[A, B](fa: Source[A, NotUsed])(parallelism: Int)(f: A => Future[B]): Source[B, NotUsed] =
        fa.mapAsync(parallelism)(f)

      override def grouped[A](fa: Source[A, NotUsed])(count: Int): Source[Seq[A], NotUsed] = fa.grouped(count)

      override def groupedWithin[A](
        fa: Source[A, NotUsed]
      )(count: Int, timespan: FiniteDuration): Source[Seq[A], NotUsed] =
        fa.groupedWithin(count, timespan)

      override def flatMap[A, B](fa: Source[A, NotUsed])(f: A => Source[B, NotUsed]): Source[B, NotUsed] =
        fa.flatMapConcat(f)

      override def mapConcat[A, B](fa: Source[A, NotUsed])(f: A => immutable.Iterable[B]): Source[B, NotUsed] =
        fa.mapConcat(f)

      override def pure[A](x: A): Source[A, NotUsed] = Source.single(x)

      override def fromIterator[A](iter: => Iterator[A]): Src[A] = Source.fromIterator(() => iter)

      override def fromSeq[A](seq: Seq[A]): Src[A] = fromIterator(seq.iterator)

      override def ap[A, B](ff: Source[A => B, NotUsed])(fa: Source[A, NotUsed]): Source[B, NotUsed] =
        ff.flatMapConcat(f => fa.map(f))

      private def joinSources[A, B](aSrc: Src[A], bSrc: Src[B]): Src[(A, B)] =
        Source.fromGraph(GraphDSL.create() { implicit builder =>
          import GraphDSL.Implicits._

          val zip = builder.add(Zip[A, B])

          aSrc ~> zip.in0
          bSrc ~> zip.in1

          SourceShape.of(zip.out)
        })
    }
}

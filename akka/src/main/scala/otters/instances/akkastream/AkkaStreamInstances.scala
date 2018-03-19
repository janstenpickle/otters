package otters.instances.akkastream

import akka.NotUsed
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source, Unzip, Zip}
import akka.stream.{ClosedShape, SourceShape}
import otters.EitherStream

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait AkkaStreamInstances {
  implicit val akkaInstances: EitherStream[Src, Future, RunnableGraph, Flw, Sink] =
    new EitherStream[Src, Future, RunnableGraph, Flw, Sink] {
      override def map[A, B](fa: Src[A])(f: A => B): Src[B] = fa.map(f)

      override def zip[A, B](fa: Src[A])(fb: Src[B]): Src[(A, B)] = joinSources(fa, fb)

      override def mapAsync[A, B](fa: Src[A])(f: A => Future[B]): Src[B] = mapAsyncN(fa)(1)(f)

      override def mapAsyncN[A, B](fa: Src[A])(parallelism: Int)(f: A => Future[B]): Src[B] =
        fa.mapAsync(parallelism)(f)

      override def grouped[A](fa: Src[A])(count: Int): Source[Seq[A], _] = fa.grouped(count)

      override def groupedWithin[A](fa: Src[A])(count: Int, timespan: FiniteDuration): Src[Seq[A]] =
        fa.groupedWithin(count, timespan)

      override def flatMap[A, B](fa: Src[A])(f: A => Src[B]): Src[B] =
        fa.flatMapConcat(f)

      override def mapConcat[A, B](fa: Src[A])(f: A => immutable.Iterable[B]): Src[B] =
        fa.mapConcat(f)

      override def pure[A](x: A): Src[A] = Source.single(x)

      override def fromIterator[A](iter: => Iterator[A]): Src[A] = Source.fromIterator(() => iter)

      override def fromSeq[A](seq: Seq[A]): Src[A] = fromIterator(seq.iterator)

      override def ap[A, B](ff: Src[A => B])(fa: Src[A]): Src[B] =
        ff.flatMapConcat(f => fa.map(f))

      override def collect[A, B](fa: Src[A])(pf: PartialFunction[A, B]): Src[B] = fa.collect(pf)

      override def tailRecM[A, B](a: A)(f: A => Src[Either[A, B]]): Src[B] = flatMap(f(a)) {
        case Left(a) => tailRecM(a)(f)
        case Right(b) => pure(b)
      }

      private def joinSources[A, B](aSrc: Src[A], bSrc: Src[B]): Src[(A, B)] =
        Source.fromGraph(GraphDSL.create() { implicit builder =>
          import GraphDSL.Implicits._

          val zip = builder.add(Zip[A, B])

          aSrc ~> zip.in0
          bSrc ~> zip.in1

          SourceShape.of(zip.out)
        })

      override def leftVia[A, B, C](fa: Src[Either[A, B]])(lPipe: Flw[A, C]): Src[Either[C, B]] =
        via[A, B, C, B](fa)(lPipe, Flow[B])

      override def rightVia[A, B, C](fa: Src[Either[A, B]])(rPipe: Flw[B, C]): Src[Either[A, C]] =
        via[A, B, A, C](fa)(Flow[A], rPipe)

      override def toSinks[A, B, C, D, E](
        fab: Src[(A, B)]
      )(lSink: Sink[A, C], rSink: Sink[B, D])(combine: (C, D) => E): RunnableGraph[E] =
        RunnableGraph.fromGraph(GraphDSL.create(fab, lSink, rSink)((_, c, d) => combine(c, d)) {
          implicit builder => (s, l, r) =>
            import GraphDSL.Implicits._

            val unzip = builder.add(Unzip[A, B])

            s ~> unzip.in
            unzip.out0 ~> l
            unzip.out1 ~> r

            ClosedShape
        })

      override def fanOutFanIn[A, B, C, D](fab: Src[(A, B)])(lPipe: Flw[A, C], rPipe: Flw[B, D]): Src[(C, D)] =
        Source.fromGraph(GraphDSL.create(fab, lPipe, rPipe)((_, _, _) => NotUsed) { implicit builder => (s, l, r) =>
          import GraphDSL.Implicits._

          val unzip = builder.add(Unzip[A, B])
          val zip = builder.add(Zip[C, D])

          s ~> unzip.in
          unzip.out0 ~> l.in
          unzip.out1 ~> r.in
          l.out ~> zip.in0
          r.out ~> zip.in1

          SourceShape(zip.out)
        })

      override def tupleLeftVia[A, B, C](fab: Src[(A, B)])(lPipe: Flw[A, C]): Src[(C, B)] =
        fanOutFanIn(fab)(lPipe, Flow[B])

      override def tupleRightVia[A, B, C](fab: Src[(A, B)])(rPipe: Flw[B, C]): Src[(A, C)] =
        fanOutFanIn(fab)(Flow[A], rPipe)

      override def via[A, B](fa: Src[A])(pipe: Flw[A, B]): Src[B] = fa.via(pipe)

      override def to[A, B](fa: Src[A])(sink: Sink[A, B]): RunnableGraph[B] = fa.toMat(sink)(Keep.right)

      override def toEitherSinks[A, B, C, D, E](
        fab: Src[Either[A, B]]
      )(lSink: Sink[A, C], rSink: Sink[B, D])(combine: (C, D) => E): RunnableGraph[E] =
        RunnableGraph.fromGraph(GraphDSL.create(fab, lSink, rSink)((_, c, d) => combine(c, d)) {
          implicit builder => (s, l, r) =>
            import GraphDSL.Implicits._

            val bcast = builder.add(Broadcast[Either[A, B]](2))
            val lFlow = builder.add(Flow[Either[A, B]].collect { case Left(a) => a })
            val rFlow = builder.add(Flow[Either[A, B]].collect { case Right(b) => b })

            s ~> bcast.in
            bcast.out(0) ~> lFlow.in
            bcast.out(1) ~> rFlow.in

            lFlow.out ~> l.in
            rFlow.out ~> r.in

            ClosedShape
        })
    }
}

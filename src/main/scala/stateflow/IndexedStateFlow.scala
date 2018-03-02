package stateflow

import akka.NotUsed
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Unzip}
import akka.stream.{FlowShape, OverflowStrategy, SinkShape}
import cats.data.{IndexedStateT, State, StateT}
import cats.instances.future._
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.{~>, Always, Applicative, Comonad, Eval, FlatMap, Functor, Monad, Monoid, Traverse}

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait IndexedStateFlowBase[F[_], SA, SB, A, B] {
  type Return[SC, SD, C] <: IndexedStateFlowBase[F, SC, SD, A, C]

  val flow: Flow[A, IndexedStateT[F, SA, SB, B], NotUsed]

  protected def apply[SC, SD, C](run: Flow[A, IndexedStateT[F, SC, SD, C], NotUsed]): Return[SC, SD, C]

  def map[C](f: B => C)(implicit F: Functor[F]): Return[SA, SB, C] = apply(flow.map(_.map(f)))

  def flatMap[C](f: B => StateT[F, SB, C])(implicit F: FlatMap[F]): Return[SA, SB, C] =
    apply(flow.map(_.flatMap(f)))

  def transform[C, SC](f: (SB, B) => (SC, C))(implicit F: Functor[F]): Return[SA, SC, C] =
    apply(flow.map(_.transform(f)))

  def modify[SC](f: SB => SC)(implicit F: Functor[F]): Return[SA, SC, B] =
    apply(flow.map(_.modify(f)))

  def async: Return[SA, SB, B] = apply(flow.async)

  def async(dispatcher: String): Return[SA, SB, B] = apply(flow.async(dispatcher))

  def async(dispatcher: String, inputBufferSize: Int): Return[SA, SB, B] =
    apply(flow.async(dispatcher, inputBufferSize))

  def buffer(size: Int, overflowStrategy: OverflowStrategy): Return[SA, SB, B] =
    apply(flow.buffer(size, overflowStrategy))
}

trait WithComonad[F[_]] {
  protected def CM: Comonad[F]
}

trait WithFunctorK[F[_]] {
  protected def FK: F ~> Future
}

trait IndexedFlowStateTupleBase[F[_], SA, SB, A, B] {
  def toTuple(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Flow[A, (SB, B), NotUsed]
  def toTuple(initial: SA)(implicit F: FlatMap[F]): Flow[A, (SB, B), NotUsed]
}

trait IndexedFlowStateTupleComonad[F[_], SA, SB, A, B]
    extends IndexedFlowStateTupleBase[F, SA, SB, A, B]
    with WithComonad[F] {
  self: IndexedStateFlowBase[F, SA, SB, A, B] =>

  def toTuple(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Flow[A, (SB, B), NotUsed] =
    flow.map(d => CM.extract(d.runEmpty))

  def toTuple(initial: SA)(implicit F: FlatMap[F]): Flow[A, (SB, B), NotUsed] =
    flow.map(d => CM.extract(d.run(initial)))
}

trait IndexedStateFlowTupleFunctorK[F[_], SA, SB, A, B]
    extends IndexedFlowStateTupleBase[F, SA, SB, A, B]
    with WithFunctorK[F] {
  self: IndexedStateFlowBase[F, SA, SB, A, B] =>

  def toTuple(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Flow[A, (SB, B), NotUsed] =
    flow.mapAsync(1)(d => FK(d.runEmpty))

  def toTuple(initial: SA)(implicit F: FlatMap[F]): Flow[A, (SB, B), NotUsed] =
    flow.mapAsync(1)(d => FK(d.run(initial)))
}

trait IndexedStateFlowSink[F[_], SA, SB, A, B] {
  self: IndexedStateFlowBase[F, SA, SB, A, B] with IndexedFlowStateTupleBase[F, SA, SB, A, B] =>

  def to(
    stateSink: Sink[SB, NotUsed],
    dataSink: Sink[B, NotUsed]
  )(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Sink[A, NotUsed] =
    toTuple.to(splitSink(stateSink, dataSink)(Keep.none))

  def to(
    initial: SA
  )(stateSink: Sink[SB, NotUsed], dataSink: Sink[B, NotUsed])(implicit F: FlatMap[F]): Sink[A, NotUsed] =
    toTuple(initial).to(splitSink(stateSink, dataSink)(Keep.none))

  def passThroughData(
    stateSink: Sink[SB, NotUsed]
  )(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Flow[A, B, NotUsed] =
    toTuple.via(passThroughDataSink(stateSink))

  def passThroughData(initial: SA)(stateSink: Sink[SB, NotUsed])(implicit F: FlatMap[F]): Flow[A, B, NotUsed] =
    toTuple(initial).via(passThroughDataSink(stateSink))

  def passThroughState(
    dataSink: Sink[B, NotUsed]
  )(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Flow[A, SB, NotUsed] =
    toTuple.via(passThroughStateSink(dataSink))

  def passThroughState(initial: SA)(dataSink: Sink[B, NotUsed])(implicit F: FlatMap[F]): Flow[A, SB, NotUsed] =
    toTuple(initial).via(passThroughStateSink(dataSink))

  def toMat[Mat2](stateSink: Sink[SB, NotUsed], dataSink: Sink[B, NotUsed])(
    combine: (NotUsed, NotUsed) => Mat2
  )(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Sink[A, Mat2] =
    toTuple.toMat(splitSink(stateSink, dataSink)(Keep.none))(combine)

  def toMat[Mat2, Mat3, Mat4, Mat5](stateSink: Sink[SB, Mat2], dataSink: Sink[B, Mat3])(
    combineSinks: (Mat2, Mat3) => Mat4,
    combine: (NotUsed, Mat4) => Mat5
  )(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Sink[A, Mat5] =
    toTuple.toMat[Mat4, Mat5](splitSink[Mat2, Mat3, Mat4](stateSink, dataSink)(combineSinks))(combine)

  def toMat[Mat2](initial: SA)(stateSink: Sink[SB, NotUsed], dataSink: Sink[B, NotUsed])(
    combine: (NotUsed, NotUsed) => Mat2
  )(implicit F: FlatMap[F]): Sink[A, Mat2] =
    toTuple(initial).toMat(splitSink(stateSink, dataSink)(Keep.none))(combine)

  def toMat[Mat2, Mat3, Mat4, Mat5](initial: SA)(
    stateSink: Sink[SB, Mat2],
    dataSink: Sink[B, Mat3]
  )(combineSinks: (Mat2, Mat3) => Mat4, combine: (NotUsed, Mat4) => Mat5)(implicit F: FlatMap[F]): Sink[A, Mat5] =
    toTuple(initial).toMat[Mat4, Mat5](splitSink[Mat2, Mat3, Mat4](stateSink, dataSink)(combineSinks))(combine)

  private def splitSink[Mat2, Mat3, Mat4](stateSink: Sink[SB, Mat2], dataSink: Sink[B, Mat3])(
    combine: (Mat2, Mat3) => Mat4
  ): Sink[(SB, B), Mat4] =
    Sink.fromGraph(GraphDSL.create(stateSink, dataSink)(combine) { implicit builder => (ss, ds) =>
      import GraphDSL.Implicits._

      val unzip = builder.add(Unzip[SB, B])
      val fl = builder.add(Flow[(SB, B)])

      fl.out ~> unzip.in

      unzip.out0 ~> ss.in
      unzip.out1 ~> ds.in

      SinkShape.of(fl.in)
    })

  private def passThroughStateSink[Mat2, Mat3](dataSink: Sink[B, Mat2]): Flow[(SB, B), SB, Mat2] =
    Flow.fromGraph(GraphDSL.create(dataSink) { implicit builder => ds =>
      import GraphDSL.Implicits._

      val unzip = builder.add(Unzip[SB, B])
      val fl = builder.add(Flow[(SB, B)])
      val outFlow = builder.add(Flow[SB])

      fl.out ~> unzip.in

      unzip.out0 ~> outFlow.in
      unzip.out1 ~> ds.in

      FlowShape(fl.in, outFlow.out)
    })

  private def passThroughDataSink[Mat2, Mat3](stateSink: Sink[SB, Mat2]): Flow[(SB, B), B, Mat2] =
    Flow.fromGraph(GraphDSL.create(stateSink) { implicit builder => ss =>
      import GraphDSL.Implicits._

      val unzip = builder.add(Unzip[SB, B])
      val fl = builder.add(Flow[(SB, B)])
      val outFlow = builder.add(Flow[B])

      fl.out ~> unzip.in

      unzip.out0 ~> ss.in
      unzip.out1 ~> outFlow.in

      FlowShape(fl.in, outFlow.out)
    })
}

trait SeqInstances {
  implicit val seqTraverse: Traverse[Seq] = new Traverse[Seq] {

    override def traverse[G[_], A, B](fa: Seq[A])(f: A => G[B])(implicit G: Applicative[G]): G[Seq[B]] =
      foldRight[A, G[Seq[B]]](fa, Always(G.pure(Seq.empty))) { (a, lglb) =>
        G.map2Eval(f(a), lglb)(_ +: _)
      }.value

    override def foldLeft[A, B](fa: Seq[A], b: B)(f: (B, A) => B): B = fa.foldLeft(b)(f)

    override def foldRight[A, B](fa: Seq[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = {
      def loop(as: Seq[A]): Eval[B] =
        as match {
          case Nil => lb
          case h :: t => f(h, Eval.defer(loop(t)))
        }
      Eval.defer(loop(fa))
    }
  }
}

trait IndexedFlowStateGrouped[F[_], SA, SB, A, B] extends SeqInstances { self: IndexedStateFlowBase[F, SA, SB, A, B] =>

  def groupedWithin(n: Int, d: FiniteDuration)(
    implicit F: Applicative[IndexedStateT[F, SA, SB, ?]]
  ): Return[SA, SB, Seq[B]] =
    apply(flow.groupedWithin(n, d).map(seqTraverse.sequence(_)))

  def group(n: Int)(implicit F: Applicative[IndexedStateT[F, SA, SB, ?]]): Return[SA, SB, Seq[B]] =
    apply(flow.grouped(n).map(seqTraverse.sequence(_)))
}

trait IndexedStateFlowAsync[F[_], SA, SB, A, B] { self: IndexedStateFlowBase[F, SA, SB, A, B] =>
  def mapAsync[C](parallelism: Int)(
    f: B => Future[C]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB], FK: F ~> Future, ec: ExecutionContext): Return[SB, SB, C] =
    asyncTransform(parallelism)(_.flatMapF(f))

  def flatMapAsync[SC, C](parallelism: Int)(
    f: B => IndexedStateT[Future, SB, SC, C]
  )(implicit F: Monad[F], FK: F ~> Future, SA: Monoid[SA], SC: Monoid[SC], ec: ExecutionContext): Return[SC, SC, C] =
    asyncTransform(parallelism)(_.flatMap(f))

  private def asyncTransform[SC, C](
    parallelism: Int
  )(f: IndexedStateT[Future, SA, SB, B] => IndexedStateT[Future, SA, SC, C])(
    implicit F: Applicative[F],
    SA: Monoid[SA],
    SC: Monoid[SC],
    FK: F ~> Future,
    ec: ExecutionContext
  ): Return[SC, SC, C] =
    apply(
      flow.mapAsync(parallelism)(
        x =>
          f(x.mapK[Future](FK)).runEmpty.map {
            case (s, b) => IndexedStateT.applyF[F, SC, SC, C](F.pure((ss: SC) => F.pure(SC.combine(ss, s), b)))
        }
      )
    )
}

trait IndexedFlowStateConcatOps {
  protected def makeSafe[C](cs: immutable.Iterable[C]): immutable.Iterable[Option[C]] =
    if (cs.isEmpty) Vector(None)
    else cs.map(Some(_))

  def head[F[_]: Applicative, S: Monoid, A]: ((S, immutable.Iterable[A])) => immutable.Iterable[StateT[F, S, A]] = {
    case (state, data) =>
      data match {
        case d: immutable.Seq[A] => headSeq[F, S, A](state, d)
        case d => headSeq[F, S, A](state, d.toVector)
      }
  }

  def tail[F[_]: Applicative, S: Monoid, A]: ((S, immutable.Iterable[A])) => immutable.Iterable[StateT[F, S, A]] = {
    case (state, data) =>
      data match {
        case d: immutable.Seq[A] => tailSeq[F, S, A](state, d)
        case d => tailSeq[F, S, A](state, d.toVector)
      }
  }

  def all[F[_]: Applicative, S: Monoid, A]: ((S, immutable.Iterable[A])) => immutable.Iterable[StateT[F, S, A]] = {
    case (state, data) =>
      data match {
        case d: immutable.Seq[A] => allSeq[F, S, A](state, d)
        case d => allSeq[F, S, A](state, d.toVector)
      }
  }

  def headSeq[F[_]: Applicative, S, A](state: S, as: immutable.Seq[A])(
    implicit S: Monoid[S]
  ): immutable.Seq[StateT[F, S, A]] =
    as match {
      case head +: tail =>
        StateT[F, S, A](s => (S.combine(state, s), head).pure) +: tail.map(a => StateT[F, S, A](s => (s, a).pure))
    }

  def tailSeq[F[_]: Applicative, S, A](state: S, as: immutable.Seq[A])(
    implicit S: Monoid[S]
  ): immutable.Seq[StateT[F, S, A]] =
    as match {
      case xs :+ last =>
        xs.map(a => StateT[F, S, A](s => (s, a).pure)) :+ StateT[F, S, A](s => (S.combine(state, s), last).pure)
    }

  def allSeq[F[_]: Applicative, S, A](state: S, as: immutable.Seq[A])(
    implicit S: Monoid[S]
  ): immutable.Seq[StateT[F, S, A]] = as.map(a => StateT[F, S, A](s => (s, a).pure))
}

trait IndexedFlowStateConcatBase[F[_], SA, SB, A, B] extends IndexedFlowStateConcatOps {
  self: IndexedStateFlowBase[F, SA, SB, A, B] =>

  def mapConcat[C](
    f: B => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[C])) => immutable.Iterable[StateT[F, SB, C]]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C]

  def safeMapConcat[C](
    f: B => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[Option[C]])) => immutable.Iterable[StateT[F, SB, Option[C]]]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]]

  def mapConcatHead[C](
    f: B => immutable.Iterable[C]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    mapConcat[C](f, head[F, SB, C])

  def safeMapConcatHead[C](
    f: B => immutable.Iterable[C],
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    safeMapConcat[C](f, head[F, SB, Option[C]])

  def mapConcatTail[C](
    f: B => immutable.Iterable[C]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    mapConcat[C](f, tail[F, SB, C])

  def safeMapConcatTail[C](
    f: B => immutable.Iterable[C],
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    safeMapConcat[C](f, tail[F, SB, Option[C]])

  def mapConcatAll[C](
    f: B => immutable.Iterable[C]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    mapConcat[C](f, all[F, SB, C])

  def safeMapConcatAll[C](
    f: B => immutable.Iterable[C],
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    safeMapConcat[C](f, all[F, SB, Option[C]])
}

trait IndexedFlowStateConcatComonad[F[_], SA, SB, A, B] extends IndexedFlowStateConcatBase[F, SA, SB, A, B] {
  self: IndexedStateFlowBase[F, SA, SB, A, B] with WithComonad[F] =>

  override def mapConcat[C](
    f: B => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[C])) => immutable.Iterable[StateT[F, SB, C]]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    apply(flow.mapConcat(x => CM.extract(x.map(f).runEmpty.map[immutable.Iterable[StateT[F, SB, C]]](f2))))

  override def safeMapConcat[C](
    f: B => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[Option[C]])) => immutable.Iterable[StateT[F, SB, Option[C]]]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    apply(
      flow.mapConcat(
        x =>
          CM.extract(
            x.map(b => makeSafe(f(b)))
              .runEmpty
              .map[immutable.Iterable[StateT[F, SB, Option[C]]]](f2)
        )
      )
    )
}

trait IndexedStateFlowConcatFunctorK[F[_], SA, SB, A, B] extends IndexedFlowStateConcatBase[F, SA, SB, A, B] {
  self: IndexedStateFlowBase[F, SA, SB, A, B] with WithFunctorK[F] =>

  def mapConcat[C](
    f: B => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[C])) => immutable.Iterable[StateT[F, SB, C]]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    apply(
      flow.mapAsync(1)(x => FK(x.map(f).runEmpty.map[immutable.Iterable[StateT[F, SB, C]]](f2))).mapConcat(identity)
    )

  def safeMapConcat[C](
    f: B => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[Option[C]])) => immutable.Iterable[StateT[F, SB, Option[C]]]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    apply(
      flow
        .mapAsync(1)(x => FK(x.map(b => makeSafe(f(b))).runEmpty.map[immutable.Iterable[StateT[F, SB, Option[C]]]](f2)))
        .mapConcat(identity)
    )
}

case class IndexedStateFlowComonad[F[_], SA, SB, A, B](flow: Flow[A, IndexedStateT[F, SA, SB, B], NotUsed])(
  override implicit protected val CM: Comonad[F]
) extends IndexedStateFlowBase[F, SA, SB, A, B]
    with IndexedStateFlowAsync[F, SA, SB, A, B]
    with IndexedFlowStateConcatComonad[F, SA, SB, A, B]
    with IndexedFlowStateTupleComonad[F, SA, SB, A, B]
    with IndexedStateFlowSink[F, SA, SB, A, B]
    with WithComonad[F] {

  override type Return[SC, SD, C] = IndexedStateFlowComonad[F, SC, SD, A, C]

  override protected def apply[SC, SD, C](
    fl: Flow[A, IndexedStateT[F, SC, SD, C], NotUsed]
  ): IndexedStateFlowComonad[F, SC, SD, A, C] =
    new IndexedStateFlowComonad[F, SC, SD, A, C](fl)
}

case class IndexedStateFlowFunctorK[F[_], SA, SB, A, B](flow: Flow[A, IndexedStateT[F, SA, SB, B], NotUsed])(
  override implicit protected val FK: F ~> Future
) extends IndexedStateFlowBase[F, SA, SB, A, B]
    with IndexedStateFlowAsync[F, SA, SB, A, B]
    with IndexedStateFlowConcatFunctorK[F, SA, SB, A, B]
    with IndexedStateFlowTupleFunctorK[F, SA, SB, A, B]
    with IndexedStateFlowSink[F, SA, SB, A, B]
    with WithFunctorK[F] {

  override type Return[SC, SD, C] = IndexedStateFlowFunctorK[F, SC, SD, A, C]

  override protected def apply[SC, SD, C](
    fl: Flow[A, IndexedStateT[F, SC, SD, C], NotUsed]
  ): IndexedStateFlowFunctorK[F, SC, SD, A, C] =
    new IndexedStateFlowFunctorK[F, SC, SD, A, C](fl)
}

object StateFlow {
  implicit val evalToFuture: Eval ~> Future = new (Eval ~> Future) {
    override def apply[A](fa: Eval[A]): Future[A] = Future.successful(fa.value)
  }

  def apply[A, B, S](underlying: Flow[A, B, NotUsed]): StateFlow[S, A, B] =
    new IndexedStateFlowComonad[Eval, S, S, A, B](underlying.map(b => State[S, B](s => (s, b))))

  def apply[A, B, S](underlying: Flow[A, (S, B), NotUsed])(implicit S: Monoid[S]): StateFlow[S, A, B] =
    new IndexedStateFlowComonad[Eval, S, S, A, B](underlying.map {
      case (state, b) => State[S, B](s => (S.combine(s, state), b))
    })

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val strMon: Monoid[String] = ???

  val x =
    apply[String, String, String](Flow[String]).map(???).flatMapAsync[String, String](1)(???).mapConcatTail[String](???)
}

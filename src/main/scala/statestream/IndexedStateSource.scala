package statestream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, RunnableGraph, Sink, Source, Unzip, Zip}
import akka.stream.{FlowShape, Materializer, OverflowStrategy, SinkShape}
import cats.data.{IndexedStateT, StateT}
import cats.instances.future._
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.{~>, Always, Applicative, Comonad, Eval, FlatMap, Functor, Monad, Monoid, Traverse}

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait IndexedStateSourceBase[F[_], SA, SB, A] {
  type Return[SC, SD, B] = IndexedStateSource[F, SC, SD, B]

  val stream: Source[IndexedStateT[F, SA, SB, A], NotUsed]

  protected def apply[SC, SD, C](run: Source[IndexedStateT[F, SC, SD, C], NotUsed]): Return[SC, SD, C]

  def map[C](f: A => C)(implicit F: Functor[F]): Return[SA, SB, C] = apply(stream.map(_.map(f)))

  def flatMap[C](f: A => StateT[F, SB, C])(implicit F: FlatMap[F]): Return[SA, SB, C] =
    apply(stream.map(_.flatMap(f)))

  def transform[C, SC](f: (SB, A) => (SC, C))(implicit F: Functor[F]): Return[SA, SC, C] =
    apply(stream.map(_.transform(f)))

  def modify[SC](f: SB => SC)(implicit F: Functor[F]): Return[SA, SC, A] =
    apply(stream.map(_.modify(f)))
}

trait WithComonad[F[_]] {
  protected def CF: Comonad[F]
}

trait WithNat[F[_]] {
  protected def nat: F ~> Future
}

trait IndexedStateTupleBase[F[_], SA, SB, A] {
  def toTuple(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Source[(SB, A), NotUsed]
  def toTuple(initial: SA)(implicit F: FlatMap[F]): Source[(SB, A), NotUsed]
}

trait IndexedFlowStateTupleComonad[F[_], SA, SB, A] extends IndexedStateTupleBase[F, SA, SB, A] with WithComonad[F] {
  self: IndexedStateSourceBase[F, SA, SB, A] =>

  def toTuple(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Source[(SB, A), NotUsed] =
    stream.map(d => CF.extract(d.runEmpty))

  def toTuple(initial: SA)(implicit F: FlatMap[F]): Source[(SB, A), NotUsed] =
    stream.map(d => CF.extract(d.run(initial)))
}

trait IndexedStateSourceTupleNat[F[_], SA, SB, A] extends IndexedStateTupleBase[F, SA, SB, A] with WithNat[F] {
  self: IndexedStateSourceBase[F, SA, SB, A] =>

  def toTuple(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Source[(SB, A), NotUsed] =
    stream.mapAsync(1)(d => nat(d.runEmpty))

  def toTuple(initial: SA)(implicit F: FlatMap[F]): Source[(SB, A), NotUsed] =
    stream.mapAsync(1)(d => nat(d.run(initial)))
}

trait IndexedStateSourceStreamOps[F[_], SA, SB, A] {
  self: IndexedStateSourceBase[F, SA, SB, A] with IndexedStateTupleBase[F, SA, SB, A] =>

  def async: Return[SA, SB, A] = apply(stream.async)

  def async(dispatcher: String): Return[SA, SB, A] = apply(stream.async(dispatcher))

  def async(dispatcher: String, inputBufferSize: Int): Return[SA, SB, A] =
    apply(stream.async(dispatcher, inputBufferSize))

  def buffer(size: Int, overflowStrategy: OverflowStrategy): Return[SA, SB, A] =
    apply(stream.buffer(size, overflowStrategy))

  def via[SC, B](
    stateFlow: Flow[SB, SC, NotUsed],
    dataFlow: Flow[A, B, NotUsed]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB], SC: Monoid[SC]): Return[SC, SC, B] =
    apply(toTuple.via(splitFlow(stateFlow, dataFlow)).map {
      case (state, data) =>
        StateT[F, SC, B](s => F.pure(SC.combine(s, state) -> data))
    })

  def stateVia[SC](
    stateFlow: Flow[SB, SC, NotUsed]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB], SC: Monoid[SC]): Return[SC, SC, A] =
    via(stateFlow, Flow[A])

  def dataVia[B](
    dataFlow: Flow[A, B, NotUsed]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, B] =
    via(Flow[SB], dataFlow)

  def to[Mat1, Mat2](
    stateSink: Sink[SB, Mat1],
    dataSink: Sink[A, Mat2]
  )(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): RunnableGraph[NotUsed] =
    toTuple.to(splitSink(stateSink, dataSink)(Keep.none))

  def to[Mat1, Mat2](
    initial: SA
  )(stateSink: Sink[SB, Mat1], dataSink: Sink[A, Mat2])(implicit F: FlatMap[F]): RunnableGraph[NotUsed] =
    toTuple(initial).to(splitSink(stateSink, dataSink)(Keep.none))

  def passThroughData(
    stateSink: Sink[SB, NotUsed]
  )(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Source[A, NotUsed] =
    toTuple.via(passThroughDataSink(stateSink))

  def passThroughData(initial: SA)(stateSink: Sink[SB, NotUsed])(implicit F: FlatMap[F]): Source[A, NotUsed] =
    toTuple(initial).via(passThroughDataSink(stateSink))

  def passThroughState(
    dataSink: Sink[A, NotUsed]
  )(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): Source[SB, NotUsed] =
    toTuple.via(passThroughStateSink(dataSink))

  def passThroughState(initial: SA)(dataSink: Sink[A, NotUsed])(implicit F: FlatMap[F]): Source[SB, NotUsed] =
    toTuple(initial).via(passThroughStateSink(dataSink))

  def toMat[Mat1, Mat2, Mat3](stateSink: Sink[SB, Mat1], dataSink: Sink[A, Mat2])(
    combine: (Mat1, Mat2) => Mat3,
  )(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): RunnableGraph[Mat3] =
    toTuple.toMat[Mat3, Mat3](splitSink[Mat1, Mat2, Mat3](stateSink, dataSink)(combine))(Keep.right)

  def toMat[Mat1, Mat2](
    stateSink: Sink[SB, Mat1],
    dataSink: Sink[A, Mat2]
  )(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB]): RunnableGraph[(Mat1, Mat2)] =
    toMat[Mat1, Mat2, (Mat1, Mat2)](stateSink, dataSink)(Keep.both)

  def toMat[Mat1, Mat2, Mat3](initial: SA)(stateSink: Sink[SB, Mat1], dataSink: Sink[A, Mat2])(
    combine: (Mat1, Mat2) => Mat3
  )(implicit F: FlatMap[F]): RunnableGraph[Mat3] =
    toTuple(initial).toMat[Mat3, Mat3](splitSink[Mat1, Mat2, Mat3](stateSink, dataSink)(combine))(Keep.right)

  def toMat[Mat1, Mat2](
    initial: SA
  )(stateSink: Sink[SB, Mat1], dataSink: Sink[A, Mat2])(implicit F: FlatMap[F]): RunnableGraph[(Mat1, Mat2)] =
    toMat[Mat1, Mat2, (Mat1, Mat2)](initial)(stateSink, dataSink)(Keep.both)

  def runWith[Mat1, Mat2](
    stateSink: Sink[SB, Mat1],
    dataSink: Sink[A, Mat2]
  )(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB], mat: Materializer): (Mat1, Mat2) =
    toMat[Mat1, Mat2](stateSink, dataSink).run()

  def runWith[Mat1, Mat2, Mat3](stateSink: Sink[SB, Mat1], dataSink: Sink[A, Mat2])(
    combine: (Mat1, Mat2) => Mat3
  )(implicit F: FlatMap[F], SA: Monoid[SA], SB: Monoid[SB], mat: Materializer): Mat3 =
    toMat[Mat1, Mat2, Mat3](stateSink, dataSink)(combine).run()

  def runWith[Mat1, Mat2](
    initial: SA
  )(stateSink: Sink[SB, Mat1], dataSink: Sink[A, Mat2])(implicit F: FlatMap[F], mat: Materializer): (Mat1, Mat2) =
    toMat[Mat1, Mat2](initial)(stateSink, dataSink).run()

  def runWith[Mat1, Mat2, Mat3](initial: SA)(stateSink: Sink[SB, Mat1], dataSink: Sink[A, Mat2])(
    combine: (Mat1, Mat2) => Mat3
  )(implicit F: FlatMap[F], mat: Materializer): Mat3 =
    toMat[Mat1, Mat2, Mat3](initial)(stateSink, dataSink)(combine).run()

  private def splitFlow[SC, B](
    stateFlow: Flow[SB, SC, NotUsed],
    dataFlow: Flow[A, B, NotUsed]
  ): Flow[(SB, A), (SC, B), NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val unzip = builder.add(Unzip[SB, A])
      val zip = builder.add(Zip[SC, B])
      val fl = builder.add(Flow[(SB, A)])

      fl.out ~> unzip.in

      unzip.out0 ~> stateFlow ~> zip.in0
      unzip.out1 ~> dataFlow ~> zip.in1

      FlowShape.of(fl.in, zip.out)
    })

  private def splitSink[Mat2, Mat3, Mat4](stateSink: Sink[SB, Mat2], dataSink: Sink[A, Mat3])(
    combine: (Mat2, Mat3) => Mat4
  ): Sink[(SB, A), Mat4] =
    Sink.fromGraph(GraphDSL.create(stateSink, dataSink)(combine) { implicit builder => (ss, ds) =>
      import GraphDSL.Implicits._

      val unzip = builder.add(Unzip[SB, A])
      val fl = builder.add(Flow[(SB, A)])

      fl.out ~> unzip.in

      unzip.out0 ~> ss.in
      unzip.out1 ~> ds.in

      SinkShape.of(fl.in)
    })

  private def passThroughStateSink[Mat2, Mat3](dataSink: Sink[A, Mat2]): Flow[(SB, A), SB, Mat2] =
    Flow.fromGraph(GraphDSL.create(dataSink) { implicit builder => ds =>
      import GraphDSL.Implicits._

      val unzip = builder.add(Unzip[SB, A])
      val fl = builder.add(Flow[(SB, A)])
      val outFlow = builder.add(Flow[SB])

      fl.out ~> unzip.in

      unzip.out0 ~> outFlow.in
      unzip.out1 ~> ds.in

      FlowShape(fl.in, outFlow.out)
    })

  private def passThroughDataSink[Mat2, Mat3](stateSink: Sink[SB, Mat2]): Flow[(SB, A), A, Mat2] =
    Flow.fromGraph(GraphDSL.create(stateSink) { implicit builder => ss =>
      import GraphDSL.Implicits._

      val unzip = builder.add(Unzip[SB, A])
      val fl = builder.add(Flow[(SB, A)])
      val outFlow = builder.add(Flow[A])

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
          case Seq() => lb
          case h +: t => f(h, Eval.defer(loop(t)))
        }
      Eval.defer(loop(fa))
    }
  }
}

trait IndexedStateSourceGrouped[F[_], SA, SB, A] extends SeqInstances { self: IndexedStateSourceBase[F, SA, SB, A] =>

  def groupedWithin(n: Int, d: FiniteDuration)(
    implicit F: Applicative[IndexedStateT[F, SA, SB, ?]]
  ): Return[SA, SB, Seq[A]] =
    apply(stream.groupedWithin(n, d).map(seqTraverse.sequence(_)))

  def group(n: Int)(implicit F: Applicative[IndexedStateT[F, SA, SB, ?]]): Return[SA, SB, Seq[A]] =
    apply(stream.grouped(n).map(seqTraverse.sequence(_)))
}

trait IndexedStateSourceAsync[F[_], SA, SB, A] { self: IndexedStateSourceBase[F, SA, SB, A] =>
  def mapAsync[C](parallelism: Int)(
    f: A => Future[C]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB], nat: F ~> Future, ec: ExecutionContext): Return[SB, SB, C] =
    asyncTransform(parallelism)(_.flatMapF(f))

  def flatMapAsync[SC, C](parallelism: Int)(
    f: A => IndexedStateT[Future, SB, SC, C]
  )(implicit F: Monad[F], nat: F ~> Future, SA: Monoid[SA], SC: Monoid[SC], ec: ExecutionContext): Return[SC, SC, C] =
    asyncTransform(parallelism)(_.flatMap(f))

  private def asyncTransform[SC, C](
    parallelism: Int
  )(f: IndexedStateT[Future, SA, SB, A] => IndexedStateT[Future, SA, SC, C])(
    implicit F: Applicative[F],
    SA: Monoid[SA],
    SC: Monoid[SC],
    nat: F ~> Future,
    ec: ExecutionContext
  ): Return[SC, SC, C] =
    apply(
      stream.mapAsync(parallelism)(
        x =>
          f(x.mapK[Future](nat)).runEmpty.map {
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
        case immutable.Seq() => immutable.Seq.empty
        case d: immutable.Seq[A] => allSeq[F, S, A](state, d)
        case d => allSeq[F, S, A](state, d.toVector)
      }
  }

  def headSeq[F[_]: Applicative, S, A](state: S, as: immutable.Seq[A])(
    implicit S: Monoid[S]
  ): immutable.Seq[StateT[F, S, A]] =
    as match {
      case immutable.Seq() => immutable.Seq.empty
      case head +: tail =>
        StateT[F, S, A](s => (S.combine(s, state), head).pure) +: tail.map(a => StateT[F, S, A](s => (s, a).pure))
    }

  def tailSeq[F[_]: Applicative, S, A](state: S, as: immutable.Seq[A])(
    implicit S: Monoid[S]
  ): immutable.Seq[StateT[F, S, A]] =
    as match {
      case immutable.Seq() => immutable.Seq.empty
      case xs :+ last =>
        xs.map(a => StateT[F, S, A](s => (s, a).pure)) :+ StateT[F, S, A](s => (S.combine(s, state), last).pure)
    }

  def allSeq[F[_]: Applicative, S, A](state: S, as: immutable.Seq[A])(
    implicit S: Monoid[S]
  ): immutable.Seq[StateT[F, S, A]] = as.map(a => StateT[F, S, A](s => (S.combine(s, state), a).pure))
}

trait IndexedStateSourceConcatBase[F[_], SA, SB, A] extends IndexedFlowStateConcatOps {
  self: IndexedStateSourceBase[F, SA, SB, A] =>

  def mapConcat[C](
    f: A => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[C])) => immutable.Iterable[StateT[F, SB, C]]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C]

  def safeMapConcat[C](
    f: A => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[Option[C]])) => immutable.Iterable[StateT[F, SB, Option[C]]]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]]

  def mapConcatHead[C](
    f: A => immutable.Iterable[C]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    mapConcat[C](f, head[F, SB, C])

  def safeMapConcatHead[C](
    f: A => immutable.Iterable[C],
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    safeMapConcat[C](f, head[F, SB, Option[C]])

  def mapConcatTail[C](
    f: A => immutable.Iterable[C]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    mapConcat[C](f, tail[F, SB, C])

  def safeMapConcatTail[C](
    f: A => immutable.Iterable[C],
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    safeMapConcat[C](f, tail[F, SB, Option[C]])

  def mapConcatAll[C](
    f: A => immutable.Iterable[C]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    mapConcat[C](f, all[F, SB, C])

  def safeMapConcatAll[C](
    f: A => immutable.Iterable[C],
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    safeMapConcat[C](f, all[F, SB, Option[C]])
}

trait IndexedFlowStateConcatComonad[F[_], SA, SB, A] extends IndexedStateSourceConcatBase[F, SA, SB, A] {
  self: IndexedStateSourceBase[F, SA, SB, A] with WithComonad[F] =>

  override def mapConcat[C](
    f: A => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[C])) => immutable.Iterable[StateT[F, SB, C]]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    apply(stream.mapConcat(x => CF.extract(x.map(f).runEmpty.map[immutable.Iterable[StateT[F, SB, C]]](f2))))

  override def safeMapConcat[C](
    f: A => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[Option[C]])) => immutable.Iterable[StateT[F, SB, Option[C]]]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    apply(
      stream.mapConcat(
        x =>
          CF.extract(
            x.map(b => makeSafe(f(b)))
              .runEmpty
              .map[immutable.Iterable[StateT[F, SB, Option[C]]]](f2)
        )
      )
    )
}

trait IndexedStateSourceConcatNat[F[_], SA, SB, A] extends IndexedStateSourceConcatBase[F, SA, SB, A] {
  self: IndexedStateSourceBase[F, SA, SB, A] with WithNat[F] =>

  def mapConcat[C](
    f: A => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[C])) => immutable.Iterable[StateT[F, SB, C]]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    apply(
      stream.mapAsync(1)(x => nat(x.map(f).runEmpty.map[immutable.Iterable[StateT[F, SB, C]]](f2))).mapConcat(identity)
    )

  def safeMapConcat[C](
    f: A => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[Option[C]])) => immutable.Iterable[StateT[F, SB, Option[C]]]
  )(implicit F: Monad[F], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    apply(
      stream
        .mapAsync(1)(
          x => nat(x.map(b => makeSafe(f(b))).runEmpty.map[immutable.Iterable[StateT[F, SB, Option[C]]]](f2))
        )
        .mapConcat(identity)
    )
}

trait IndexedStateSource[F[_], SA, SB, A]
    extends IndexedStateSourceBase[F, SA, SB, A]
    with IndexedStateSourceAsync[F, SA, SB, A]
    with IndexedStateSourceConcatBase[F, SA, SB, A]
    with IndexedStateTupleBase[F, SA, SB, A]
    with IndexedStateSourceStreamOps[F, SA, SB, A]
    with IndexedStateSourceGrouped[F, SA, SB, A]

case class IndexedStateSourceComonad[F[_], SA, SB, A](stream: Source[IndexedStateT[F, SA, SB, A], NotUsed])(
  override implicit protected val CF: Comonad[F]
) extends IndexedStateSource[F, SA, SB, A]
    with IndexedFlowStateConcatComonad[F, SA, SB, A]
    with IndexedFlowStateTupleComonad[F, SA, SB, A]
    with WithComonad[F] {

  override protected def apply[SC, SD, C](
    src: Source[IndexedStateT[F, SC, SD, C], NotUsed]
  ): IndexedStateSource[F, SC, SD, C] =
    new IndexedStateSourceComonad[F, SC, SD, C](src)
}

object IndexedStateSourceComonad {
  def apply[F[_], S, A](
    src: Source[A, NotUsed]
  )(implicit F: Applicative[F], CF: Comonad[F]): IndexedStateSourceComonad[F, S, S, A] =
    new IndexedStateSourceComonad[F, S, S, A](src.map(a => StateT[F, S, A](s => F.pure(s -> a))))

  def apply[F[_], S, A](
    src: Source[(S, A), NotUsed]
  )(implicit F: Applicative[F], CF: Comonad[F], S: Monoid[S]): IndexedStateSourceComonad[F, S, S, A] =
    new IndexedStateSourceComonad[F, S, S, A](src.map {
      case (state, a) => StateT[F, S, A](s => F.pure(S.combine(s, state) -> a))
    })

  implicit class IndexedStateSourceOps1[A](src: Source[A, NotUsed]) {
    def toStateSource[F[_], S](implicit F: Applicative[F], CF: Comonad[F]): IndexedStateSourceComonad[F, S, S, A] =
      IndexedStateSourceComonad[F, S, A](src)
  }

  implicit class IndexedStateSourceOps2[S, A](src: Source[(S, A), NotUsed])(implicit S: Monoid[S]) {
    def toStateSource[F[_]](
      implicit F: Applicative[F],
      CF: Comonad[F],
      S: Monoid[S]
    ): IndexedStateSourceComonad[F, S, S, A] =
      IndexedStateSourceComonad[F, S, A](src)
  }

  implicit class IndexedStateSourceOps3[F[_], SA, SB, A](src: Source[IndexedStateT[F, SA, SB, A], NotUsed]) {
    def toStateSource(implicit CF: Comonad[F]): IndexedStateSourceComonad[F, SA, SB, A] =
      new IndexedStateSourceComonad[F, SA, SB, A](src)
  }
}

case class IndexedStateSourceNat[F[_], SA, SB, A](stream: Source[IndexedStateT[F, SA, SB, A], NotUsed])(
  override implicit protected val nat: F ~> Future
) extends IndexedStateSource[F, SA, SB, A]
    with IndexedStateSourceConcatNat[F, SA, SB, A]
    with IndexedStateSourceTupleNat[F, SA, SB, A]
    with WithNat[F] {

  override protected def apply[SC, SD, C](
    src: Source[IndexedStateT[F, SC, SD, C], NotUsed]
  ): IndexedStateSource[F, SC, SD, C] =
    new IndexedStateSourceNat[F, SC, SD, C](src)
}

object IndexedStateSourceNat {
  def apply[F[_], S, A](
    src: Source[A, NotUsed]
  )(implicit F: Applicative[F], nat: F ~> Future): IndexedStateSourceNat[F, S, S, A] =
    new IndexedStateSourceNat[F, S, S, A](src.map(a => StateT[F, S, A](s => F.pure(s -> a))))

  def apply[F[_], S, A](
    src: Source[(S, A), NotUsed]
  )(implicit F: Applicative[F], S: Monoid[S], nat: F ~> Future): IndexedStateSourceNat[F, S, S, A] =
    new IndexedStateSourceNat[F, S, S, A](src.map {
      case (state, a) => StateT[F, S, A](s => F.pure(S.combine(s, state) -> a))
    })

  implicit class IndexedStateSourceOps1[A](src: Source[A, NotUsed]) {
    def toStateSource[F[_], S](implicit F: Applicative[F], nat: F ~> Future): IndexedStateSourceNat[F, S, S, A] =
      IndexedStateSourceNat[F, S, A](src)
  }

  implicit class IndexedStateSourceOps2[S, A](src: Source[(S, A), NotUsed])(implicit S: Monoid[S]) {
    def toStateSource[F[_]](
      implicit F: Applicative[F],
      S: Monoid[S],
      nat: F ~> Future
    ): IndexedStateSourceNat[F, S, S, A] =
      IndexedStateSourceNat[F, S, A](src)
  }

  implicit class IndexedStateSourceOps3[F[_], SA, SB, A](src: Source[IndexedStateT[F, SA, SB, A], NotUsed]) {
    def toStateSource(implicit nat: F ~> Future): IndexedStateSourceNat[F, SA, SB, A] =
      new IndexedStateSourceNat[F, SA, SB, A](src)
  }
}

object StateSource {
  implicit val evalToFutureNat: Eval ~> Future = new (Eval ~> Future) {
    override def apply[A](fa: Eval[A]): Future[A] = Future.successful(fa.value)
  }

  def apply[S, A](src: Source[A, NotUsed]): StateSource[S, A] =
    IndexedStateSourceComonad[Eval, S, A](src)

  def apply[S, A](src: Source[(S, A), NotUsed])(implicit S: Monoid[S]): StateSource[S, A] =
    IndexedStateSourceComonad[Eval, S, A](src)
}

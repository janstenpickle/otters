package otters

trait TupleStream[F[_], G[_], H[_], P[_, _], S[_, _]]
    extends StreamSink[F, H, S]
    with StreamPipe[F, P]
    with AsyncStream[F, G] {

  def toSinks[A, B, C, D, E](fab: F[(A, B)])(lSink: S[A, C], rSink: S[B, D])(combine: (C, D) => E): H[E]

  def toSinks[A, B, C, D](fab: F[(A, B)])(lSink: S[A, C], rSink: S[B, D]): H[(C, D)] =
    toSinks[A, B, C, D, (C, D)](fab)(lSink, rSink)(_ -> _)

  def fanOutFanIn[A, B, C, D](fab: F[(A, B)])(lPipe: P[A, C], rPipe: P[B, D]): F[(C, D)]

  def tupleLeftVia[A, B, C](fab: F[(A, B)])(lPipe: P[A, C]): F[(C, B)]

  def tupleRightVia[A, B, C](fab: F[(A, B)])(rPipe: P[B, C]): F[(A, C)]
}

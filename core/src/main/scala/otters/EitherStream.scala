package otters

trait EitherStream[F[_], G[_], H[_], P[_, _], S[_, _]] extends EitherStreamBase[F] with TupleStream[F, G, H, P, S] {
  def via[A, B, C, D](fa: F[Either[A, B]])(lPipe: P[A, C], rPipe: P[B, D]): F[Either[C, D]] =
    flatMap(fa) {
      case Left(a) => map(via(pure(a))(lPipe))(Left(_))
      case Right(b) => map(via(pure(b))(rPipe))(Right(_))
    }

  def leftVia[A, B, C](fa: F[Either[A, B]])(lPipe: P[A, C]): F[Either[C, B]]
  def rightVia[A, B, C](fa: F[Either[A, B]])(rPipe: P[B, C]): F[Either[A, C]]

  def toEitherSinks[A, B, C, D](fab: F[Either[A, B]])(lSink: S[A, C], rSink: S[B, D]): H[(C, D)] =
    toEitherSinks[A, B, C, D, (C, D)](fab)(lSink, rSink)(_ -> _)

  def toEitherSinks[A, B, C, D, E](fab: F[Either[A, B]])(lSink: S[A, C], rSink: S[B, D])(combine: (C, D) => E): H[E]

}

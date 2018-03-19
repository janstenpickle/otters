package otters

trait EitherStreamFunctionPipeSink[F[_], G[_], H[_]]
    extends EitherStream[F, G, H, FunctionPipe[F, ?, ?], FunctionSink[F, H, ?, ?]] {
  override def tupleLeftVia[A, B, C](fab: F[(A, B)])(lPipe: FunctionPipe[F, A, C]): F[(C, B)] =
    fanOutFanIn(fab)(lPipe, identity)

  override def tupleRightVia[A, B, C](fab: F[(A, B)])(rPipe: FunctionPipe[F, B, C]): F[(A, C)] =
    fanOutFanIn(fab)(identity, rPipe)

  override def leftVia[A, B, C](fa: F[Either[A, B]])(lPipe: FunctionPipe[F, A, C]): F[Either[C, B]] =
    via[A, B, C, B](fa)(lPipe, identity)

  override def rightVia[A, B, C](fa: F[Either[A, B]])(rPipe: FunctionPipe[F, B, C]): F[Either[A, C]] =
    via[A, B, A, C](fa)(identity, rPipe)

  override def to[A, B](fa: F[A])(sink: FunctionSink[F, H, A, B]): H[B] = sink(fa)

  override def via[A, B](fa: F[A])(pipe: FunctionPipe[F, A, B]): F[B] = pipe(fa)
}

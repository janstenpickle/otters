package otters

trait EitherStreamPipe[F[_, _], G[_], H[_], S[_, _], I] extends EitherStream[F[I, ?], G, H, F, S]

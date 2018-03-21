package object otters {
  type FunctionPipe[F[_], A, B] = F[A] => F[B]
  type FunctionSink[F[_], G[_], A, B] = F[A] => G[B]
}

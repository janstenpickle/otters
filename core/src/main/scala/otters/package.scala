package object otters {
  type Pipe[F[_], A, B] = F[A] => F[B]
  type Sink[F[_], G[_], A, B] = F[A] => G[B]
}

import cats.Eval

package object statestream {
  type StateSource[S, A] = IndexedStateSourceComonad[Eval, S, S, A]
}

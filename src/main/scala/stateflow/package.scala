import cats.Eval

package object stateflow {
  type StateFlow[S, A] = IndexedStateFlowComonad[Eval, S, S, A]
}

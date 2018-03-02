import cats.Eval

package object stateflow {
  type StateFlow[S, A, B] = IndexedStateFlowComonad[Eval, S, S, A, B]
}

package morphir.flowz

package object compat {
  type UStep[+A]            = Step[Any, Any, Nothing, A]
  type Step[-R, -P, +E, +A] = morphir.flowz.Act[Any, Any, R, P, E, A]
}

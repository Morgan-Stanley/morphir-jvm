package morphir.flowz

import morphir.flowz.FiberSyntax.FiberOutputChannelOps
import zio._

trait FiberSyntax {
  implicit def toFiberOutputChannelOps[State, Err, Output](
    fiber: Fiber[Err, OutputChannels[State, Err]]
  ): FiberOutputChannelOps[State, Err, Output] =
    new FiberOutputChannelOps[State, Err, Output](fiber)

}

object FiberSyntax {
  class FiberOutputChannelOps[+State, +Err, +Output](val self: Fiber[Err, OutputChannels[State, Err]]) extends {
    def joinFlow: SrcFlow[State, Err, Err] = Flow.join(self)
  }
}
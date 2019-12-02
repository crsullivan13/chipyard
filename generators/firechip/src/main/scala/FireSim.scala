//See LICENSE for license details.

package firesim.firesim

import chisel3._

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule}

import midas.widgets.{Bridge, PeekPokeBridge, RationalClockBridge}

import chipyard.{BuildTop}
import chipyard.iobinders.{IOBinders}

// Determines the number of times to instantiate the DUT in the harness.
// Subsumes legacy supernode support
case object NumNodes extends Field[Int](1)

class WithNumNodes(n: Int) extends Config((pname, site, here) => {
  case NumNodes => n
})

// Hacky: Set before each node is generated. Ideally we'd give IO binders
// accesses to the the Harness's parameters instance. We could then alter that.
object NodeIdx {
  private var idx = 0
  def increment(): Unit = {idx = idx + 1 }
  def apply(): Int = idx
}

class FireSim(implicit val p: Parameters) extends RawModule {
  val clockBridge = Module(new RationalClockBridge)
  val clock = clockBridge.io.clocks.head
  val reset = WireInit(false.B)
  withClockAndReset(clock, reset) {
    // Instantiate multiple instances of the DUT to implement supernode
    val targets = Seq.fill(p(NumNodes))(p(BuildTop)(p))
    val peekPokeBridge = PeekPokeBridge(clock, reset)
    // A Seq of partial functions that will instantiate the right bridge only
    // if that Mixin trait is present in the target's class instance
    //
    // Apply each partial function to each DUT instance
    for (target <- targets) {
      p(IOBinders).values.map(fn => fn(clock, reset.asBool, false.B, target))
      NodeIdx.increment()
    }
  }
}

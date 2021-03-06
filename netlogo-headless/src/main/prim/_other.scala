// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.prim

import org.nlogo.agent.{ AgentSet, AgentSetBuilder }
import org.nlogo.nvm.{ Context, Reporter }

class _other extends Reporter {

  override def report(context: Context): AgentSet =
    report_1(context, argEvalAgentSet(context, 0))

  def report_1(context: Context, sourceSet: AgentSet): AgentSet = {
    val builder = new AgentSetBuilder(sourceSet.kind, sourceSet.count)
    val it = sourceSet.iterator
    while(it.hasNext) {
      val otherAgent = it.next()
      if (context.agent ne otherAgent)
        builder.add(otherAgent)
    }
    builder.build()
  }

}

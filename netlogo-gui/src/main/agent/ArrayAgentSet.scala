// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.agent

import
  org.nlogo.{ api, core },
  org.nlogo.api.MersenneTwisterFast
import collection.JavaConverters._

// ArrayAgentSets are only used for agentsets which are never added to
// after they are initially created.  However note that turtles and
// links can die, so we may end up with an array containing some dead
// agents (agents with id -1). NP 2013-08-28.

class ArrayAgentSet(
  kind: core.AgentKind,
  printName: String,
  private val array: Array[Agent])
extends IndexedAgentSet(kind, printName) {

  private[this] val arraySize = array.size

  /// conversions

  override def toLogoList = {
    val freshArray =
      if (!kind.mortal)
        array.clone
      else {
        val buf = collection.mutable.ArrayBuffer[Agent]()
        val iter = iterator
        while (iter.hasNext)
          buf += iter.next()
        buf.toArray
      }
    java.util.Arrays.sort(freshArray.asInstanceOf[Array[AnyRef]])
    core.LogoList.fromIterator(freshArray.iterator)
  }

  /// counting

  override def isEmpty =
    if (!kind.mortal)
      array.isEmpty
    else
      !iterator.hasNext

  override def count =
    if (!kind.mortal)
      arraySize
    else {
      var result = 0
      val iter = iterator
      while(iter.hasNext) {
        iter.next()
        result += 1
      }
      result
    }

  /// equality

  // assumes we've already checked for equal counts - ST 7/6/06
  override def containsSameAgents(otherSet: api.AgentSet) = {
    val set = collection.mutable.HashSet[api.Agent]()
    val iter = iterator
    while (iter.hasNext)
      set += iter.next()
    otherSet.agents.asScala.forall(set.contains)
  }

  /// one-agent queries

  override def getByIndex(index: Int) = array(index)

  override def contains(agent: api.Agent): Boolean = {
    val iter = iterator
    while (iter.hasNext)
      if (iter.next() eq agent)
        return true
    false
  }

  /// random selection

  // the next few methods take precomputedCount as an argument since we want to avoid _randomoneof
  // and _randomnof resulting in more than one total call to count(), since count() can be O(n)
  // - ST 2/27/03

  // assume agentset is nonempty, since _randomoneof.java checks for that
  override def randomOne(precomputedCount: Int, rng: api.MersenneTwisterFast) = {
    val random = rng.nextInt(precomputedCount)
    if (!kind.mortal)
      array(random)
    else {
      val iter = iterator
      var i = 0
      while (i < random) {
        iter.next()
        i += 1
      }
      iter.next()
    }
  }

  // This is used to optimize the special case of randomSubset where size == 2
  override def randomTwo(precomputedCount: Int, rng: api.MersenneTwisterFast): Array[Agent] = {
    // we know precomputedCount, or this method would not have been called.
    // see randomSubset().
//    var smallRandom = rng.nextInt(precomputedCount)
//    var bigRandom = rng.nextInt(precomputedCount)
//    if (smallRandom > bigRandom) {
//      val temp = smallRandom
//      smallRandom = bigRandom
//      bigRandom = temp
//    }
//
//    if (!kind.mortal)
//      Array(
//        array(smallRandom),
//        array(bigRandom))
//    else {
//      val it = iterator
//      var i = 0
//      // skip to the first random place
//      while(i < smallRandom) {
//        it.next()
//        i += 1
//      }
//      val first = it.next()
//      i += 1
//      while (i < bigRandom) {
//        it.next()
//        i += 1
//      }
//      val second = it.next()
//      Array(first, second)
//    }
    randomSubsetGeneral(2, precomputedCount, rng)
  }

  override def randomSubsetGeneral(resultSize: Int, precomputedCount: Int, random: MersenneTwisterFast) = {
    val result = new Array[Agent](resultSize)
    if (precomputedCount == arraySize) {
      var i, j = 0
      while (j < resultSize) {
        if (random.nextInt(precomputedCount - i) < resultSize - j) {
          result(j) = array(i)
          j += 1
        }
        i += 1
      }
    } else {
      val iter = iterator
      var i, j = 0
      while (j < resultSize) {
        val next = iter.next()
        if (random.nextInt(precomputedCount - i) < resultSize - j) {
          result(j) = next
          j += 1
        }
        i += 1
      }
    }
    result
  }

  override def iterator: AgentIterator =
    if (!kind.mortal)
      new Iterator
    else
      new DeadSkippingIterator

  // shuffling iterator = shufflerator! (Google hits: 0)
  // Update: Now 5 Google hits, the first 4 of which are NetLogo related,
  // and the last one is a person named "SHUFFLER, Ator", which Google thought
  // was close enough!  ;-)  ~Forrest (10/3/2008)

  // shufflerator: 12 google hits - majority are NetLogo related ~Eric (2/9/2017)

  override def shufflerator(rng: MersenneTwisterFast): AgentIterator =
    // note it at the moment (and this should probably be fixed)
    // Job.runExclusive() counts on this making a copy of the
    // contents of the agentset - ST 12/15/05
    new Shufflerator(rng)

  /// iterator implementations

  private class Iterator extends AgentIterator {
    protected var index = 0
    override def hasNext = index < arraySize
    override def next() = {
      val result = array(index)
      index += 1
      result
    }
  }

  // extended to skip dead agents
  private class DeadSkippingIterator extends Iterator {
    var nextAgent: Agent = null

    // if hasNext returns false, then nextAgent must be null.
    override def hasNext: Boolean = {
      if (nextAgent != null && nextAgent.id != -1)
        true
      else {
        // if nextAgent is null or dead, attempt to find new one:
        while (index < array.size && array(index).id == -1)
          index += 1
        // replace null nextAgent if successfully found one:
        if (index < array.size) {
          nextAgent = array(index)
          true
        } else
          false
      }
    }

    override def next() = {
      if (hasNext) {
        val ret = nextAgent
        nextAgent = null
        index += 1
        ret
      } else
      // should always throw index out of bounds exception:
        array(index)
    }
  }

  private class Shufflerator(rng: MersenneTwisterFast) extends Iterator {
    private[this] var i = 0
    private[this] val copy = array.clone
    private[this] var nextOne: Agent = null
    while (i < copy.length && copy(i) == null)
      i += 1
    fetch()
    override def hasNext =
      nextOne != null
    override def next(): Agent = {
      val result = nextOne
      fetch()
      result
    }
    private def fetch() {
      if (i >= copy.length)
        nextOne = null
      else {
        if (i < copy.length - 1) {
          val r = i + rng.nextInt(copy.length - i)
          nextOne = copy(r)
          copy(r) = copy(i)
        }
        else
          nextOne = copy(i)
        i += 1
        // we could have a bunch of different Shufflerator subclasses
        // the same way we have Iterator subclasses in order to avoid
        // having to do both checks, but I'm not
        // sure it's really worth the effort - ST 3/15/06
        if (nextOne == null || nextOne.id == -1)
          fetch()
      }
    }
  }

}

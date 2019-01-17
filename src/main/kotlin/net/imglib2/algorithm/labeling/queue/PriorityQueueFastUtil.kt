package net.imglib2.algorithm.labeling.queue

import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue

import java.util.Comparator

/**
 *
 * @author Philipp Hanslovsky
 */
class PriorityQueueFastUtil : PriorityQueue {

    private val queue: ObjectHeapPriorityQueue<LongWithCost>

    class Factory : PriorityQueueFactory {

        override fun create(): PriorityQueue {
            return PriorityQueueFastUtil()
        }

    }

    private class LongWithCost(val longVal: Long, val cost: Double)

    private class IntWithCostComparator : Comparator<LongWithCost> {

        override fun compare(o1: LongWithCost, o2: LongWithCost): Int {
            return java.lang.Double.compare(o1.cost, o2.cost)
        }

    }

    init {
        this.queue = ObjectHeapPriorityQueue(IntWithCostComparator())
    }

    override fun pop(): Long {
        return this.queue.dequeue().longVal
    }

    override fun size(): Long {
        return this.queue.size().toLong()
    }

    override fun add(`val`: Long, cost: Double) {
        this.queue.enqueue(LongWithCost(`val`, cost))

    }

    companion object {

        val FACTORY = Factory()
    }

}

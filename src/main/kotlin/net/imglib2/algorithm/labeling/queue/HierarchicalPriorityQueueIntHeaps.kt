package net.imglib2.algorithm.labeling.queue

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue

/**
 *
 * @author Philipp Hanslovsky
 */
class HierarchicalPriorityQueueIntHeaps(private val nPriorityQueues: Int) : PriorityQueue {

    private var size: Long = 0

    private val queues: Array<LongArrayFIFOQueue>

    private var currentQueue: Int = 0

    private val nextPriority: Int
        get() {
            while (this.currentQueue < this.nPriorityQueues) {
                if (this.queues[this.currentQueue].size() > 0)
                    break
                ++this.currentQueue
            }
            return this.currentQueue
        }

    class Factory(private val nPriorityQueues: Int) : PriorityQueueFactory {

        override fun create(): PriorityQueue {
            return HierarchicalPriorityQueueIntHeaps(this.nPriorityQueues)
        }
    }

    init {
        this.size = 0
        this.queues = Array(nPriorityQueues, {LongArrayFIFOQueue()})
        this.currentQueue = 0

    }

    override fun add(`val`: Long, cost: Double) {
        val index = cost.toInt()

        this.queues[index].enqueue(`val`)
        this.currentQueue = Math.min(index, this.currentQueue)

        ++this.size
    }

    override fun pop(): Long {
        --this.size
        return this.queues[nextPriority].dequeueLong()
    }

    override fun size(): Long {
        return this.size
    }

    companion object {

        fun factory(nPriorityQueues: Int): PriorityQueueFactory {
            return Factory(nPriorityQueues)
        }
    }

}

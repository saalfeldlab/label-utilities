package net.imglib2.algorithm.labeling.queue

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue

/**
 *
 * @author Philipp Hanslovsky
 */
class HierarchicalPriorityQueueQuantized(private val nPriorityQueues: Int, private val min: Double, private val max: Double) : PriorityQueue {


    private var size: Long = 0

    private val queues: Array<LongArrayFIFOQueue>

    private var currentQueue: Int = 0

    private val range: Double

    private val maxQueueIndex: Int

    private val nextPriority: Int
        get() {
            while (currentQueue < nPriorityQueues) {
                if (queues[currentQueue].size() > 0)
                    break
                ++currentQueue
            }
            return currentQueue
        }

    class Factory(private val nPriorityQueues: Int, private val min: Double, private val max: Double) : PriorityQueueFactory {

        override fun create(): PriorityQueue {
            return HierarchicalPriorityQueueQuantized(nPriorityQueues, min, max)
        }
    }

    init {
        this.size = 0
        this.queues = Array(nPriorityQueues, {LongArrayFIFOQueue()})
        this.currentQueue = 0
        this.range = this.max - this.min
        this.maxQueueIndex = this.nPriorityQueues - 1

    }

    override fun add(`val`: Long, cost: Double) {
        val index = Math.max(Math.min((nPriorityQueues * (cost - min) / range).toInt(), maxQueueIndex), minQueueIndex)

        this.queues[index].enqueue(`val`)
        this.currentQueue = Math.min(index, currentQueue)

        ++size
    }

    override fun pop(): Long {
        --size
        return queues[nextPriority].dequeueLong()
    }

    override fun size(): Long {
        return size
    }

    companion object {

        private val minQueueIndex = 0

        fun factory(nPriorityQueues: Int, min: Double, max: Double): PriorityQueueFactory {
            return Factory(nPriorityQueues, min, max)
        }
    }

}

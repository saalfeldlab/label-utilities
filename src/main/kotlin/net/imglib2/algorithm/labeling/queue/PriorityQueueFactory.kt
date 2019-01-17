package net.imglib2.algorithm.labeling.queue

/**
 *
 * @author Philipp Hanslovsky
 */
interface PriorityQueueFactory {

    fun create(): PriorityQueue

}

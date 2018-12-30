package net.imglib2.algorithm.labeling.queue

/**
 *
 * @author Philipp Hanslovsky
 */
interface PriorityQueue {

    fun pop(): Long

    fun size(): Long

    fun add(`val`: Long, cost: Double)

}

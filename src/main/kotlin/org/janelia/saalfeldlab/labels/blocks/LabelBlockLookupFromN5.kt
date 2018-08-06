package org.janelia.saalfeldlab.labels.blocks

import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.n5.*
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.nio.ByteBuffer
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream

class LabelBlockLookupFromN5(
		private val n5: N5FSWriter,
		private val scaleDatasetPattern: String,
		private val exceptionHandler: Consumer<IOException> = Consumer { LOG.warn("Caught exception in read/write. Defaulting to no-op", it) }) : LabelBlockLookup {

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private const val SINGLE_ENTRY_BYTE_SIZE = 3 * 2 * java.lang.Long.BYTES

		private fun fromBytes(array: ByteArray): MutableMap<Long, Array<Interval>> {

			val map = mutableMapOf<Long, Array<Interval>>()

			val bb = ByteBuffer.wrap(array)
			while (bb.hasRemaining()) {
				val id = bb.long
				val numIntervals = bb.int

				val intervals = Stream
						.generate(
								({
									FinalInterval(
											longArrayOf(bb.long, bb.long, bb.long),
											longArrayOf(bb.long, bb.long, bb.long)
									) as Interval
								}))
						.limit(numIntervals.toLong())
						.collect(Collectors.toList())
						.toTypedArray()
				map.put(id, intervals)
			}

			return map
		}

		private fun toBytes(map: Map<Long, Array<Interval>>): ByteArray {
			val sizeInBytes = map.values.stream().mapToInt { java.lang.Long.BYTES + Integer.BYTES + SINGLE_ENTRY_BYTE_SIZE * it.size }.sum()
			val bytes = ByteArray(sizeInBytes)
			val bb = ByteBuffer.wrap(bytes)
			for (entry in map) {
				bb.putLong(entry.key)
				bb.putInt(entry.value.size)
				for (interval in entry.value) {
					bb.putLong(interval.min(0))
					bb.putLong(interval.min(1))
					bb.putLong(interval.min(2))
					bb.putLong(interval.max(0))
					bb.putLong(interval.max(1))
					bb.putLong(interval.max(2))
				}
			}
			return bytes
		}
	}

	private val attributes = mutableMapOf<Int, DatasetAttributes>()

	fun set(level: Int, ids: Map<Long, Array<Interval>>) {


		try {
			val dataset = String.format(scaleDatasetPattern, level)
			val attributes = this.attributes.getOrPut(level, { n5.getDatasetAttributes(dataset) })
			val stepSize = attributes.blockSize[0]

			val mapByBlock = mutableMapOf<Long, MutableMap<Long, Array<Interval>>>()

			for (entry in ids)
				mapByBlock.computeIfAbsent((entry.key / stepSize) * stepSize, { mutableMapOf() })[entry.key] = entry.value

			for (m in mapByBlock)
				writeMap(level, m.key, m.value)
		} catch (e: IOException) {
			exceptionHandler.accept(e)
		}

	}

	override fun read(level: Int, id: Long): Array<Interval> {
		try {
			val map = readMap(level, id) ?: mutableMapOf()
			return map.getOrElse(id, { emptyArray() })
		} catch (e: IOException) {
			exceptionHandler.accept(e)
			return emptyArray()
		}

	}

	override fun write(level: Int, id: Long, vararg intervals: Interval) {
		try {
			val map = readMap(level, id) ?: mutableMapOf()
			map[id] = arrayOf(*intervals)
			writeMap(level, id, map)
		} catch (e: IOException) {
			exceptionHandler.accept(e)
		}
	}

	@Throws(IOException::class)
	private fun readMap(level: Int, id: Long): MutableMap<Long, Array<Interval>>? {
		val dataset = "${String.format(scaleDatasetPattern, level)}"
		val attributes = this.attributes.getOrPut(level, { n5.getDatasetAttributes(dataset) })

		val blockSize = attributes.blockSize[0]
		val blockId = id / blockSize

		val block = n5.readBlock(dataset, attributes, longArrayOf(blockId)) as? ByteArrayDataBlock

		if (block == null) {
			LOG.warn("Did not find any data, returning empty array")
			return null
		}

		val map = fromBytes(block.data)

		return map
	}

	private fun writeMap(level: Int, id: Long, map: Map<Long, Array<Interval>>) {
		val dataset = "${String.format(scaleDatasetPattern, level)}"

		val attributes = this.attributes.getOrPut(level, { n5.getDatasetAttributes(dataset) })
		val size = intArrayOf(attributes.blockSize[0])

		val blockSize = attributes.blockSize[0]
		val blockId = id / blockSize

		val block = ByteArrayDataBlock(size, longArrayOf(blockId), toBytes(map))
		n5.writeBlock(dataset, attributes, block)
	}

}

fun main(args: Array<String>) {
	val level = 1
	val basePath = "bla-test"
	val pattern = "label-to-block-mapping/s%d"
	val writer = N5FSWriter(basePath)
	val lookup = LabelBlockLookupFromN5(writer, pattern)

	writer.createDataset(String.format(pattern, level), DatasetAttributes(longArrayOf(100), intArrayOf(3), DataType.INT8, GzipCompression()))

	val inMap = mapOf(Pair(1L, arrayOf(FinalInterval(longArrayOf(1, 2, 3), longArrayOf(3, 4, 5)) as Interval)))
	lookup.set(level, inMap)
	lookup.write(level, 10L, FinalInterval(longArrayOf(4, 5, 6), longArrayOf(7, 8, 9)) as Interval, FinalInterval(longArrayOf(10, 11, 12), longArrayOf(123, 123, 123)))
	lookup.write(level, 0L, FinalInterval(longArrayOf(1, 1, 1), longArrayOf(2, 2, 2)))

	for (i in 0L..11L)
		println(lookup.read(level, i).asList().stream().map { "(${Arrays.toString(Intervals.minAsLongArray(it))}-${Arrays.toString(Intervals.maxAsLongArray(it))})" }.collect(Collectors.toList()) as List<String>)


}

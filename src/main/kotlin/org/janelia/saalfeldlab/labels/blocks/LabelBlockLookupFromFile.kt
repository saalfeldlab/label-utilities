package org.janelia.saalfeldlab.labels.blocks

import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.cache.ref.SoftRefLoaderCache
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.util.HashWrapper
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.function.BiFunction
import java.util.function.Predicate

@LabelBlockLookup.LookupType("from-file")
class LabelBlockLookupFromFile(@LabelBlockLookup.Parameter private val pattern: String) : CachedLabelBlockLookup {

	private constructor(): this("")

	private val cache = SoftRefLoaderCache<LabelBlockLookupKey, Array<Interval>>()

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private val EMPTY_ARRAY = arrayOf<Interval>()

		// 3 : nDim
		// 2 : min and max
		private const val SINGLE_ENTRY_BYTE_SIZE = 3 * 2 * java.lang.Long.BYTES

		private fun fromPattern(pattern: String?): BiFunction<Int, Long, Path?> {
			if (pattern == null) {
				return BiFunction { _, _ -> null }
			} else {
				return BiFunction { level, id -> Paths.get(String.format(pattern, level, id)) }
			}
		}

		@JvmStatic
		@JvmOverloads
		fun patternFromBasePath(basePath: String?, levelPattern: String = "s%d", idPattern: String = "%d"): String? {
			return if (basePath == null) null else Paths.get(basePath, levelPattern, idPattern).toAbsolutePath().toString()
		}

		private fun getPath(level: Int, id: Long, pattern: String?): Path?
		{
			return if (pattern == null) null else Paths.get(String.format(pattern, level, id))
		}
	}

	override fun read(key: LabelBlockLookupKey): Array<Interval> {
		return cache.get(key, this::readFromFile)
	}

	override fun write(key: LabelBlockLookupKey, vararg intervals: Interval) {
		invalidate(key)
		writeToFile(key, *intervals)
	}

	override fun invalidate(key: LabelBlockLookupKey?) {
		cache.invalidate(key)
	}

	override fun invalidateAll(parallelismThreshold: Long) {
		cache.invalidateAll(parallelismThreshold)
	}

	override fun invalidateIf(parallelismThreshold: Long, condition: Predicate<LabelBlockLookupKey>?) {
		cache.invalidateIf(parallelismThreshold, condition)
	}

	private fun readFromFile(key: LabelBlockLookupKey): Array<Interval> {

		LOG.debug("Getting block list for id {} at level {}", key.id, key.level)
		val path: Path? = getPath(key.level, key.id, pattern)
		LOG.debug("File path for block list for id {} at level {} is {}", key.id, key.level, path)

		if (path == null) {
			LOG.debug("Invalid path, returning empty array: {}", path)
			return EMPTY_ARRAY
		}
		try {
			val bytes = Files.readAllBytes(path)
			if (!isValidByteSize(bytes.size)) {
				throw InvalidFileSize(bytes.size)
			}

			val intervals = HashSet<HashWrapper<Interval>>()

			val bb = ByteBuffer.wrap(bytes)

			while (bb.hasRemaining()) {
				intervals.add(HashWrapper.interval(FinalInterval(
						longArrayOf(bb.long, bb.long, bb.long),
						longArrayOf(bb.long, bb.long, bb.long)
				)))
			}

			return intervals
					.stream()
					.map<Interval> { it.data }
					.toArray { arrayOfNulls<Interval>(it) }

		} catch (e: Exception) {
			LOG.debug(
					"Unable to read data from file at {} for level and id {} -- returning empty array: " + "{}",
					path, key.level, key.id,
					e.message, e
			)
			return EMPTY_ARRAY
		}
	}

	private fun writeToFile(key: LabelBlockLookupKey, vararg intervals: Interval) {

		val path: Path? = getPath(key.level, key.id, pattern)

		if (path == null) {
			LOG.info("Path is null, cannot write!")
			return
		}

		val numBytes = intervals.size * SINGLE_ENTRY_BYTE_SIZE
		val data = ByteArray(numBytes)
		val bb = ByteBuffer.wrap(data)
		for (t in intervals) {
			val l1 = Intervals.minAsLongArray(t)
			val l2 = Intervals.maxAsLongArray(t)
			bb.putLong(l1[0])
			bb.putLong(l1[1])
			bb.putLong(l1[2])
			bb.putLong(l2[0])
			bb.putLong(l2[1])
			bb.putLong(l2[2])
		}
		Files.createDirectories(path.parent)
		Files.write(path, data)
	}

	private fun isValidByteSize(sizeInBytes: Int): Boolean {
		return sizeInBytes % SINGLE_ENTRY_BYTE_SIZE == 0
	}

	private class InvalidFileSize(sizeInBytes: Int) : Exception("Expected file size in bytes of integer multiple of " + SINGLE_ENTRY_BYTE_SIZE + " but got " +
			sizeInBytes) {
		companion object {

			/**
			 *
			 */
			private val serialVersionUID = 3063871520312958385L
		}

	}

}

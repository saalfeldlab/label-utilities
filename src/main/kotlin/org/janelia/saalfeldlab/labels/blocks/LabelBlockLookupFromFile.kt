package org.janelia.saalfeldlab.labels.blocks

import com.google.gson.annotations.Expose
import net.imglib2.FinalInterval
import net.imglib2.Interval
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

@LabelBlockLookup.LookupType("from-file")
class LabelBlockLookupFromFile(@Expose private val toFilePath: BiFunction<Int, Long, Path?>) : LabelBlockLookup {

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

	}

	constructor(pattern: String?) : this(fromPattern(pattern))

	override fun read(level: Int, id: Long): Array<Interval> {

		LOG.debug("Getting block list for id {} at level {}", id, level)
		val path: Path? = toFilePath.apply(level, id)
		LOG.debug("File path for block list for id {} at level {} is {}", id, level, path)

		if (path == null) {
			LOG.warn("Invalid path, returning empty array: {}", path)
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
			LOG.error(
					"Unable to read data from file at {} for level and id {} -- returning empty array: " + "{}",
					path, level, id,
					e.message, e
			)
			return EMPTY_ARRAY
		}

	}

	override fun write(level: Int, id: Long, vararg intervals: Interval) {

		val path: Path? = toFilePath.apply(level, id)

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

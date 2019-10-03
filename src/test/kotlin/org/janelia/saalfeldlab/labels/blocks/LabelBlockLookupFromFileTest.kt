package org.janelia.saalfeldlab.labels.blocks

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.imglib2.Interval
import net.imglib2.util.Intervals
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.invoke.MethodHandles

class LabelBlockLookupFromFileTest {

	@Test
	fun `test LabelBlockLookupFromFile equals`() {
		val pattern1 = "pattern1/s%d/%d"
		val pattern2 = "pattern2/s%d/%d"

		val lookup1 = LabelBlockLookupFromFile(pattern1)
		val lookup2 = LabelBlockLookupFromFile(pattern2)

		Assert.assertEquals(lookup1, LabelBlockLookupFromFile(pattern1))
		Assert.assertEquals(lookup2, LabelBlockLookupFromFile(pattern2))

		Assert.assertNotEquals(lookup1, lookup2)
		Assert.assertNotEquals(lookup2, lookup1)
	}

	@Test
	fun `test LabelBlockLookupFromFile serialization`() {
		Assert.assertEquals("from-file", LabelBlockLookupFromFile.LOOKUP_TYPE)

		val pattern = "/s%d/%d"
		val lookup = LabelBlockLookupFromFile(pattern)
		val gson = GsonBuilder()
				.registerTypeHierarchyAdapter(LabelBlockLookup::class.java, LabelBlockLookupAdapter.getJsonAdapter())
				.create()

		val serialized = gson.toJsonTree(lookup).assertJsonObject()

		val groundTruth = JsonObject()
				.also { it.addProperty("type", LabelBlockLookupFromFile.LOOKUP_TYPE) }
				.also { it.addProperty("pattern", pattern) }

		Assert.assertEquals(groundTruth, serialized)

		val deserialized = gson.fromJson(serialized, LabelBlockLookup::class.java)

		Assert.assertEquals(lookup, deserialized)
	}

	@Test
	fun `test LabelBlockLookupFromFile`() {
		val root = testDirectory.resolve("root").absolutePath
		val level = 1
		val pattern = "$root/label-to-block-mapping/s%d/%d"
		val lookup = LabelBlockLookupFromFile(pattern = pattern)

		val intervalsForId0 = arrayOf<Interval>(Intervals.createMinMax(1, 1, 1, 2, 2, 2))
		val intervalsForId1 = arrayOf<Interval>(Intervals.createMinMax(1, 2, 3, 3, 4, 5))
		val intervalsForId10 = arrayOf<Interval>(
				Intervals.createMinMax(4, 5, 6, 7, 8, 9),
				Intervals.createMinMax(10, 11, 12, 123, 123, 123))

		val groundTruthMap = mapOf(
				Pair(0L, intervalsForId0),
				Pair(1L, intervalsForId1),
				Pair(10L, intervalsForId10))

		lookup.write(LabelBlockLookupKey(level, 1L), *intervalsForId1)
		lookup.write(LabelBlockLookupKey(level, 10L), *intervalsForId10)
		lookup.write(LabelBlockLookupKey(level, 0L), *intervalsForId0)

		for (i in 0L..11L) {
			val intervals = lookup.read(LabelBlockLookupKey(level, i))
			val groundTruth = groundTruthMap[i] ?: arrayOf()
			LOG.debug("Block {}: Got intervals {} for root {}", i, intervals, root)
			Assert.assertEquals("Size Mismatch at index $i for root $root", groundTruth.size, intervals.size)
			(groundTruth zip intervals).forEachIndexed { idx, p ->
				Assert.assertThat(
						"Mismatch for interval $idx of entry $i for root $root ",
						p.second,
						IntervalMatcher(p.first))
			}
		}
	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private lateinit var _testDirectory: File

		private val testDirectory: File
			get() {
				if (!::_testDirectory.isInitialized)
					_testDirectory = tempDirectory()
				return _testDirectory
			}

		private val isDeleteOnExit: Boolean
			get() = !LOG.isDebugEnabled

		@AfterClass
		@JvmStatic
		fun deleteTestDirectory() {
			if (isDeleteOnExit && ::_testDirectory.isInitialized) {
				LOG.debug("Deleting directory {}", _testDirectory)
				testDirectory.deleteRecursively()
			}
		}

		private fun tempDirectory(
				prefix: String = "label-block-lookup-file-",
				suffix: String? = ".test") = createTempDir(prefix, suffix).also { LOG.debug("Created tmp directory {}", it) }

		private fun JsonElement.assertJsonObject() = this
				.also { Assert.assertTrue("Expected a JsonObject: $this", this.isJsonObject) }
				.let { it.asJsonObject }

	}

	private class IntervalMatcher(private val interval: Interval) : TypeSafeMatcher<Interval>() {

		override fun describeTo(description: Description?) = description?.appendValue(interval).let {}

		override fun matchesSafely(item: Interval?) = item?.let { Intervals.equals(interval, it) } ?: false

	}

}

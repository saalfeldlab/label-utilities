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
	fun `test LabelBlockLookupFromN5 equals`() {

		val root1 = testDirectory.resolve("equalsRoot1").absolutePath
		val root2 = testDirectory.resolve("equalsRoot2").absolutePath
		val root3: String? = null
		val pattern1 = "pattern1/s%d/%d"
		val pattern2 = "pattern2/s%d/%d"

		val lookups = mutableListOf<LabelBlockLookupFromFile>()

		for (root in arrayOf(root1, root2, root3)) {
			for (pattern in arrayOf(pattern1, pattern2)) {
				for (isRelative in booleanArrayOf(true, false)) {
					val lookup = LabelBlockLookupFromFile(pattern, root, isRelative)
					Assert.assertEquals(lookup, LabelBlockLookupFromFile(pattern, root, isRelative))
					lookups += lookup
				}
			}
		}

		for ((i1, l1) in lookups.withIndex()) {
			for ((i2, l2) in lookups.withIndex()) {
				if (i1 == i2)
					Assert.assertEquals(l1, l2)
				else
					Assert.assertNotEquals(l1, l2)
			}
		}
	}

	@Test
	fun `test LabelBlockLookupFromN5 serialization`() {
		val pattern = "label-to-block-mapping/s%d/%d"
		val root1 = testDirectory.resolve("serializationRelative").absolutePath
		val root2 = testDirectory.resolve("serializationAbsolute").absolutePath
		val lookup1 = LabelBlockLookupFromFile(pattern, root1, false)
		val lookup2 = LabelBlockLookupFromFile(pattern, root2, true)
		val lookup3 = LabelBlockLookupFromFile(pattern, null, false)
		val lookup4 = LabelBlockLookupFromFile(pattern, null, true)
		val gson = GsonBuilder()
				.registerTypeHierarchyAdapter(LabelBlockLookup::class.java, LabelBlockLookupAdapter.getJsonAdapter())
				.create()

		val serialized1 = gson.toJsonTree(lookup1).assertJsonObject()
		val serialized2 = gson.toJsonTree(lookup2).assertJsonObject()
		val serialized3 = gson.toJsonTree(lookup3).assertJsonObject()
		val serialized4 = gson.toJsonTree(lookup4).assertJsonObject()

		LOG.debug("Serialized lookup1 to {}", serialized1)
		LOG.debug("Serialized lookup2 to {}", serialized2)
		LOG.debug("Serialized lookup3 to {}", serialized3)
		LOG.debug("Serialized lookup4 to {}", serialized4)

		val groundTruth1 = JsonObject()
				.also { it.addProperty("type", LabelBlockLookupFromFile.TYPE_STRING) }
				.also { it.addProperty("pattern", pattern) }
				.also { it.addProperty("root", root1) }
				.also { it.addProperty("isRelative", false) }
		val groundTruth2 = JsonObject()
				.also { it.addProperty("type", LabelBlockLookupFromFile.TYPE_STRING) }
				.also { it.addProperty("pattern", pattern) }
				.also { it.addProperty("isRelative", true) }
		val groundTruth3 = JsonObject()
				.also { it.addProperty("type", LabelBlockLookupFromFile.TYPE_STRING) }
				.also { it.addProperty("pattern", pattern) }
				.also { it.addProperty("isRelative", false) }
		val groundTruth4 = JsonObject()
				.also { it.addProperty("type", LabelBlockLookupFromFile.TYPE_STRING) }
				.also { it.addProperty("pattern", pattern) }
				.also { it.addProperty("isRelative", true) }

		Assert.assertEquals(groundTruth1, serialized1)
		Assert.assertEquals(groundTruth2, serialized2)
		Assert.assertEquals(groundTruth3, serialized3)
		Assert.assertEquals(groundTruth4, serialized4)

		val deserialized1 = gson.fromJson(serialized1, LabelBlockLookupFromFile::class.java)
		val deserialized2 = gson.fromJson(serialized2, LabelBlockLookupFromFile::class.java)
		val deserialized3 = gson.fromJson(serialized3, LabelBlockLookupFromFile::class.java)
		val deserialized4 = gson.fromJson(serialized4, LabelBlockLookupFromFile::class.java)

		lookup2.setRelativeTo(null, null)

		Assert.assertEquals(lookup1, deserialized1)
		Assert.assertEquals(lookup2, deserialized2)
		Assert.assertEquals(lookup3, deserialized3)
		Assert.assertEquals(lookup4, deserialized4)
	}

	@Test
	fun `test LabelBlockLookupFromN5`() {
		val root1 = testDirectory.resolve("root1").absolutePath
		val root2 = testDirectory.resolve("root2").absolutePath
		LOG.debug("root1 = {}", root1)
		LOG.debug("root2 = {}", root2)
		val roots = arrayOf(root1, root2)
		val level = 1
		val pattern = "label-to-block-mapping/s%d/%d"
		val lookup = LabelBlockLookupFromFile(pattern = pattern, isRelative = true)


		val intervalsForId0 = arrayOf<Interval>(Intervals.createMinMax(1, 1, 1, 2, 2, 2))
		val intervalsForId1 = arrayOf<Interval>(Intervals.createMinMax(1, 2, 3, 3, 4, 5))
		val intervalsForId10 = arrayOf<Interval>(
				Intervals.createMinMax(4, 5, 6, 7, 8, 9),
				Intervals.createMinMax(10, 11, 12, 123, 123, 123))

		val groundTruthMap = mapOf(
				Pair(0L, intervalsForId0),
				Pair(1L, intervalsForId1),
				Pair(10L, intervalsForId10))

		Assert.assertTrue(lookup.isRelative)

		for (root in roots) {

			lookup.setRelativeTo(root, null)

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

package org.janelia.saalfeldlab.labels

import org.junit.Assert
import org.junit.Test
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class LabelTest
{

	companion object {
	    private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
	}

	@Test
	fun testReservedValue()
	{
//		for (name in arrayOf("background", "TRANSPARENT", "InVaLId", "OUTSIDE", "MAX_ID"))
//		{
//			println("$name -- ${Label.ReservedValue.fromString(name)}")
//		}
		for (rv in Label.ReservedValue.values())
		{
			LOG.debug("Testing {}", rv)
			Assert.assertEquals(rv, Label.ReservedValue.fromString(rv.name))
			Assert.assertEquals(rv, Label.ReservedValue.fromString(rv.name.toLowerCase()))
			Assert.assertEquals(rv, Label.ReservedValue.fromString(rv.name.toLowerCase().capitalize()))
			Assert.assertEquals(rv, Label.ReservedValue.fromId(rv.id))
		}

		Assert.assertEquals(Label.BACKGROUND, Label.ReservedValue.BACKGROUND.id)
		Assert.assertEquals(Label.TRANSPARENT, Label.ReservedValue.TRANSPARENT.id)
		Assert.assertEquals(Label.INVALID, Label.ReservedValue.INVALID.id)
		Assert.assertEquals(Label.OUTSIDE, Label.ReservedValue.OUTSIDE.id)
		Assert.assertEquals(Label.MAX_ID, Label.ReservedValue.MAX_ID.id)

		Assert.assertNull(Label.ReservedValue.fromId(1))
		Assert.assertNull(Label.ReservedValue.fromId(Label.ReservedValue.MAX_ID.id - 1))

		Assert.assertNull(Label.ReservedValue.fromString("1"))
		Assert.assertNull(Label.ReservedValue.fromString("nonsense"))

	}
}

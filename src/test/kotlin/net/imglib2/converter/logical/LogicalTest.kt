package net.imglib2.converter.logical

import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.logic.BoolType
import net.imglib2.util.ConstantUtils
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.junit.Assert
import org.junit.Test
import java.util.*


class LogicalTest {

	private val dim = longArrayOf(10, 20, 30)
	private val numEntities = Math.ceil(Intervals.numElements(*dim) / 64.0).toInt()
	private val rng = Random(100)
	private val data = randomLongs(numEntities, rng)
	private val mask = ArrayImgs.bits(net.imglib2.img.basictypeaccess.array.LongArray(data), *dim)

	@Test
	fun complementReadOnly() {
		val converted = Logical.complementReadOnly(mask)
		Assert.assertArrayEquals(dim, Intervals.dimensionsAsLongArray(converted))
		for (p in Views.interval(Views.pair(mask, converted), mask))
			Assert.assertNotEquals(p.a.get(), p.b.get())
	}

	@Test
	fun complement() {
		val dataCopy = data.clone()
		val converted = Logical.complement(ArrayImgs.bits(net.imglib2.img.basictypeaccess.array.LongArray(dataCopy), *dim))
		Assert.assertArrayEquals(dim, Intervals.dimensionsAsLongArray(converted))

		for (p in Views.interval(Views.pair(mask, converted), mask))
			Assert.assertNotEquals(p.a.get(), p.b.get())

		Views.iterable(converted).forEach { it.not() }

		for (p in Views.interval(Views.pair(mask, converted), mask))
			Assert.assertEquals(p.a.get(), p.b.get())
	}

	@Test
	fun and() {
		val constantTrue = ConstantUtils.constantRandomAccessibleInterval(BoolType(true), dim.size, mask)
		val constantFalse = ConstantUtils.constantRandomAccessibleInterval(BoolType(false), dim.size, mask)
		val complement = Logical.complementReadOnly(mask)
		val otherRandomData = randomLongs(numEntities, rng)
		val otherRandomMask = ArrayImgs.bits(net.imglib2.img.basictypeaccess.array.LongArray(otherRandomData), *dim)

		Views.iterable(Logical.and(mask, constantFalse)).forEach { Assert.assertFalse(it.get()) }
		Views.iterable(Logical.and(mask, complement)).forEach { Assert.assertFalse(it.get()) }
		Views.interval(Views.pair(mask, Logical.and(mask, constantTrue)), mask).forEach { Assert.assertEquals(it.a.get(), it.b.get()) }
		Views.interval(Views.pair(Views.pair(mask, otherRandomMask), Logical.and(mask, otherRandomMask)), mask).forEach { Assert.assertEquals(it.a.a.get() and it.a.b.get(), it.b.get()) }
	}

	@Test
	fun andComposite() {
		val collapsed = Views.collapse(mask)
		val size = dim[dim.size - 1]
		val and = Logical.and(collapsed, size)
		Views.interval(Views.pair(collapsed, and), collapsed).forEach {
			var actual = true
			for (index in 0 until size)
			{
				if (!it.a[index].get())
				{
					actual = false
					break
				}
			}
			Assert.assertEquals(actual, it.b.get())
		}
	}

	@Test
	fun or() {
		val constantTrue = ConstantUtils.constantRandomAccessibleInterval(BoolType(true), dim.size, mask)
		val constantFalse = ConstantUtils.constantRandomAccessibleInterval(BoolType(false), dim.size, mask)
		val complement = Logical.complementReadOnly(mask)
		val otherRandomData = randomLongs(numEntities, rng)
		val otherRandomMask = ArrayImgs.bits(net.imglib2.img.basictypeaccess.array.LongArray(otherRandomData), *dim)

		Views.iterable(Logical.or(mask, constantTrue)).forEach { Assert.assertTrue(it.get()) }
		Views.iterable(Logical.or(mask, complement)).forEach { Assert.assertTrue(it.get()) }
		Views.interval(Views.pair(mask, Logical.or(mask, constantFalse)), mask).forEach { Assert.assertEquals(it.a.get(), it.b.get()) }
		Views.interval(Views.pair(Views.pair(mask, otherRandomMask), Logical.or(mask, otherRandomMask)), mask).forEach { Assert.assertEquals(it.a.a.get() or it.a.b.get(), it.b.get()) }
	}

	@Test
	fun orComposite() {
		val collapsed = Views.collapse(mask)
		val size = dim[dim.size - 1]
		val and = Logical.or(collapsed, size)
		Views.interval(Views.pair(collapsed, and), collapsed).forEach {
			var actual = false
			for (index in 0 until size)
			{
				if (it.a[index].get())
				{
					actual = true
					break
				}
			}
			Assert.assertEquals(actual, it.b.get())
		}
	}

	private fun randomLongs(n: Int, rng: Random): LongArray {
		val data = LongArray(n)
		for (i in 0 until data.size)
			data[i] = rng.nextLong()
		return data
	}
}

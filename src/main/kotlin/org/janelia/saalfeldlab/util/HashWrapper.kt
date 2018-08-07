package org.janelia.saalfeldlab.util

import net.imglib2.Interval
import net.imglib2.util.Intervals
import java.io.Serializable
import java.util.*
import java.util.function.BiPredicate
import java.util.function.Function
import java.util.function.ToIntFunction

class HashWrapper<T : Any> @JvmOverloads constructor(
		val data: T,
		private val hash: ToIntFunction<T>,
		private val equals: BiPredicate<T, T>,
		private val toString: Function<T, String> = Function { it.toString() }) : Serializable {

	private var hashValue: Int = 0

	init {
		updateHashValue()
	}

	fun updateHashValue() {
		this.hashValue = hash.applyAsInt(this.data)
	}

	override fun hashCode(): Int {
		return hashValue
	}

	override fun equals(o: Any?): Boolean {
		if (o != null && o is HashWrapper<*>) {
			val hw = o as HashWrapper<*>
			val obj = hw.data
			if (this.data::class.java.isInstance(obj)) {
				val that = hw as HashWrapper<T>
				return this.dataEquals(that)
			}
		}
		return false
	}

	fun dataEquals(that: HashWrapper<T>): Boolean {
		return equals.test(this.data, that.data)
	}

	class LongArrayHash : ToIntFunction<LongArray> {

		override fun applyAsInt(arr: LongArray): Int {
			return Arrays.hashCode(arr)
		}

	}

	class LongArrayEquals : BiPredicate<LongArray, LongArray> {

		override fun test(t: LongArray, u: LongArray): Boolean {
			return Arrays.equals(t, u)
		}

	}

	override fun toString(): String {
		return "{HashWrapper: " + this.toString.apply(this.data) + "}"
	}

	companion object {

		/**
		 *
		 */
		private const val serialVersionUID = -2571523935606311437L

		@JvmStatic
		fun longArray(vararg array: Long): HashWrapper<LongArray> {
			return HashWrapper(array, LongArrayHash(), LongArrayEquals())
		}

		@JvmStatic
		fun interval(interval: Interval): HashWrapper<Interval> {
			return interval(
					interval,
					Function { i -> Arrays.toString(Intervals.minAsLongArray(i)) + " " + Arrays.toString(Intervals.maxAsLongArray(i)) }
			)

		}

		@JvmStatic
		fun interval(interval: Interval, toString: Function<Interval, String>): HashWrapper<Interval> {
			val hash = LongArrayHash()
			return HashWrapper(
					interval,
					ToIntFunction { i -> 31 * hash.applyAsInt(Intervals.minAsLongArray(i)) + hash.applyAsInt(Intervals.maxAsLongArray(i)) },
					BiPredicate { i1, i2 -> Intervals.contains(i1, i2) && Intervals.contains(i2, i1) },
					toString
			)
		}
	}

}


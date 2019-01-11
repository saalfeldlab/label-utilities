package net.imglib2.algorithm.labeling.affinities

import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.view.Views
import org.junit.Assert
import org.junit.Test
import java.util.function.BiPredicate
import java.util.function.Predicate

class AffinityWatersheds2Test {

	@Test
	fun testFindParents() {
		val affinitiesStore = doubleArrayOf(
				Double.NaN, 0.1, 0.8, 0.9,
				Double.NaN, 0.1, 0.7, 0.6,
				Double.NaN, 0.9, 0.85, 0.85,

				Double.NaN, Double.NaN, Double.NaN, Double.NaN,
				1.0, 0.8, 1.0, 0.9,
				0.95, 0.15, 0.01, 0.02,


				1.0, 0.8, 1.0, 0.9,
				0.95, 0.15, 0.01, 0.02,
				Double.NaN, Double.NaN, Double.NaN, Double.NaN,

				0.1, 0.8, 0.9,  Double.NaN,
				0.1, 0.7, 0.6,  Double.NaN,
				0.9, 0.85, 0.85, Double.NaN
		)

		val affinities = Views.collapseReal(ArrayImgs.doubles(affinitiesStore, 4, 3, 4))
		val offsets = arrayOf(longArrayOf(-1, 0), longArrayOf(0, -1), longArrayOf(0, 1), longArrayOf(1, 0))

		val labels = LongArray(12)
		AffinityWatersheds2.findParents(
				affinities,
				labels,
				Predicate { !it.realDouble.isNaN() },
				BiPredicate { v1, v2 -> v1.realDouble > v2.realDouble },
				DoubleType(Double.NEGATIVE_INFINITY),
				bitMask = longArrayOf(1, 2, 4, 8),
				nEdges = 4)
		Assert.assertArrayEquals(longArrayOf(4, 12, 4, 5, 2, 2, 2, 2, 2, 1, 9, 1), labels)
	}

}

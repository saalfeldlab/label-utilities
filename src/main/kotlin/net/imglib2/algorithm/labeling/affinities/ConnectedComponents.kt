package net.imglib2.algorithm.labeling.affinities

import net.imglib2.Localizable
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.util.unionfind.IntArrayUnionFind
import net.imglib2.algorithm.util.unionfind.UnionFind
import net.imglib2.type.BooleanType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import net.imglib2.view.composite.Composite
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.Arrays
import java.util.function.LongUnaryOperator

class ConnectedComponents {

	@FunctionalInterface
	interface ToIndex {

		fun toIndex(position: Localizable, flatIndex: Long): Long

	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private fun getDefaultToIndex(): ToIndex {
			return object : ToIndex {
				override fun toIndex(position: Localizable, flatIndex: Long): Long {
					return flatIndex
				}
			}
		}

		@JvmStatic
		@JvmOverloads
		fun <B: BooleanType<B>, U: BooleanType<U>, R: RealType<R>, C: Composite<R>, L: IntegerType<L>> fromSymmetricAffinities(
				foreground: RandomAccessible<B>,
				affinities: RandomAccessible<C>,
				labels: RandomAccessibleInterval<L>,
				unionMask: RandomAccessible<U>,
				threshold: Double,
				vararg steps: Long,
				indexToId: LongUnaryOperator = LongUnaryOperator { it + 1 },
				unionFind: UnionFind = IntArrayUnionFind(Intervals.numElements(labels).toInt()),
				toIndex: ToIndex = getDefaultToIndex()
		): Long {

			val longArraySteps = steps.mapIndexed { index, l -> LongArray(foreground.numDimensions(), {if (it == index) l else 0}) }.toTypedArray()

			return fromSymmetricAffinities(
					foreground = foreground,
					affinities = affinities,
					labels = labels,
					unionMask = unionMask,
					threshold = threshold,
					steps = *longArraySteps,
					indexToId = indexToId,
					unionFind = unionFind,
					toIndex = toIndex
			);
		}

		@JvmStatic
		@JvmOverloads
		fun <B: BooleanType<B>, U: BooleanType<U>, R: RealType<R>, C: Composite<R>, L: IntegerType<L>> fromSymmetricAffinities(
				foreground: RandomAccessible<B>,
				affinities: RandomAccessible<C>,
				labels: RandomAccessibleInterval<L>,
				unionMask: RandomAccessible<U>,
				threshold: Double,
				vararg steps: LongArray,
				indexToId: LongUnaryOperator = LongUnaryOperator { it + 1 },
				toIndex: ToIndex = getDefaultToIndex(),
				unionFind: UnionFind = IntArrayUnionFind(Intervals.numElements(labels).toInt())
		): Long {

			if (!Views.isZeroMin(labels))
				return fromSymmetricAffinities(
						Views.translate(foreground, *Intervals.minAsLongArray(labels).invertValues()),
						Views.translate(affinities, *Intervals.minAsLongArray(labels).invertValues()),
						Views.zeroMin(labels),
						Views.translate(unionMask, *Intervals.minAsLongArray(labels).invertValues()),
						threshold,
						*steps,
						indexToId = indexToId,
						unionFind = unionFind,
						toIndex = toIndex)

			unionFindFromSymmetricAffinities(foreground, Views.interval(affinities, labels), unionMask, unionFind, threshold, *steps, toIndex = toIndex)
			return relabel(Views.interval(foreground, labels), labels, Views.interval(unionMask, labels), unionFind, toIndex, indexToId)
		}

		@JvmStatic
		fun <B: BooleanType<B>, U: BooleanType<U>, L: IntegerType<L>> relabel(
				mask: RandomAccessibleInterval<B>,
				labels: RandomAccessibleInterval<L>,
				unionMask: RandomAccessibleInterval<U>,
				unionFind: UnionFind,
				toIndex: ToIndex,
				indexToId: LongUnaryOperator)
		: Long {
			val c = Views.flatIterable(labels).cursor()
			val b = Views.flatIterable(mask).cursor()
			val u = Views.flatIterable(unionMask).cursor()
			var maxId = Long.MIN_VALUE
			var index = -1L
			while (c.hasNext()) {
				val p = c.next()
				++index
				b.fwd()
				u.fwd()
				if (b.get().get() && u.get().get()) {
					val id = indexToId.applyAsLong(unionFind.findRoot(toIndex.toIndex(c, index)))
					p.setInteger(id)
					if (id > maxId)
						maxId = id
				}
			}
			return maxId
		}

		@JvmStatic
		fun <B: BooleanType<B>, U: BooleanType<U>, R: RealType<R>, C: Composite<R>> unionFindFromSymmetricAffinities(
				foreground: RandomAccessible<B>,
				affinities: RandomAccessibleInterval<C>,
				unionMask: RandomAccessible<U>,
				unionFind: UnionFind,
				threshold: Double,
				vararg steps: LongArray,
				toIndex: ToIndex
		) {

			for (step in steps)
				if (step.size != affinities.numDimensions())
					throw IllegalArgumentException("Need on step size for each dimension but got steps ${Arrays.toString(steps)} " +
							"and dimensions ${Arrays.toString(Intervals.dimensionsAsLongArray(affinities))}")

			val imgStrides = LongArray(affinities.numDimensions(), {1})
			(1 until imgStrides.size).forEach { imgStrides[it] = imgStrides[it - 1] * affinities.dimension(it - 1) }
			val flatIndexStrides = steps.map { it.zip(imgStrides).map { it.first * it.second }.sum() }
			LOG.debug("Dimensions {} and strides {} and flat index strides {}", Intervals.dimensionsAsLongArray(affinities), imgStrides, flatIndexStrides)


			for (stepIndex in steps.indices) {

				val currentPixelMask = Views.interval(foreground, affinities)
				val shiftedPixelMask = Views.interval(foreground, Views.translate(affinities, *steps[stepIndex]))

				val currentUnionFindMask = Views.interval(unionMask, affinities)
				val shiftedUnionFindMask = Views.interval(unionMask, Views.translate(affinities, *steps[stepIndex]))

				val cCursor = Views.flatIterable(currentPixelMask).cursor()
				val sCursor = Views.flatIterable(shiftedPixelMask).cursor()
				val aCursor = Views.flatIterable(affinities).cursor()

				val cuCursor = Views.flatIterable(currentUnionFindMask).cursor()
				val suCursor = Views.flatIterable(shiftedUnionFindMask).cursor()

				val stepIndexLong = stepIndex.toLong()
				var cFlatIndex = -1L
				var sFlatIndex = cFlatIndex + flatIndexStrides[stepIndex]

				while (aCursor.hasNext()) {
					cCursor.fwd()
					sCursor.fwd()
					aCursor.fwd()
					cuCursor.fwd()
					suCursor.fwd()
					++cFlatIndex
					++sFlatIndex

					val c = cCursor.get().get()
					if (!c)
						continue

					val s = sCursor.get().get()
					if (!s)
						continue

					val a = aCursor.get().get(stepIndexLong).realDouble
					if (a.isNaN() || a < threshold)
						continue

					val r1 = unionFind.findRoot(toIndex.toIndex(cCursor, cFlatIndex))
					val r2 = unionFind.findRoot(toIndex.toIndex(sCursor, sFlatIndex))

					if (r1 != r2) {
						unionFind.join(r1, r2)
						cuCursor.get().set(true)
						suCursor.get().set(true)
					}

				}

			}

		}

		private fun LongArray.invertValues(max: Long = 0): LongArray {
			return LongArray(this.size, {max-this[it]})
		}

	}

}

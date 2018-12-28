package net.imglib2.algorithm.labeling.affinities

import net.imglib2.Localizable
import net.imglib2.Point
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.util.unionfind.IntArrayUnionFind
import net.imglib2.algorithm.util.unionfind.UnionFind
import net.imglib2.type.BooleanType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.util.IntervalIndexer
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import net.imglib2.view.composite.Composite
import java.util.Arrays

class ConnectedComponents {

	companion object {

		@JvmStatic
		fun <B: BooleanType<B>, R: RealType<R>, C: Composite<R>, L: IntegerType<L>> fromSymmetricAffinities(
				foreground: RandomAccessible<B>,
				affinities: RandomAccessible<C>,
				labels: RandomAccessibleInterval<L>,
				threshold: Double,
				vararg steps: Long,
				unionFind: UnionFind = IntArrayUnionFind(Intervals.numElements(labels).toInt()),
				toIndex: (Localizable) -> Long = { IntervalIndexer.positionToIndex(it, labels) }
		) {

			if (!Views.isZeroMin(labels))
				return fromSymmetricAffinities(
						Views.translate(foreground, *Intervals.minAsLongArray(labels).invertValues()),
						Views.translate(affinities, *Intervals.minAsLongArray(labels).invertValues()),
						labels,
						threshold,
						*steps,
						unionFind = unionFind,
						toIndex = toIndex)

			unionFindFromSymmetricAffinities(foreground, Views.interval(affinities, labels), unionFind, threshold, *steps, toIndex = toIndex)
			relabel(Views.interval(foreground, labels), labels, unionFind, toIndex)
		}

		@JvmStatic
		fun <B: BooleanType<B>, R: RealType<R>, C: Composite<R>, L: IntegerType<L>> fromSymmetricAffinities(
				foreground: RandomAccessible<B>,
				affinities: RandomAccessible<C>,
				labels: RandomAccessibleInterval<L>,
				threshold: Double,
				vararg steps: LongArray,
				unionFind: UnionFind = IntArrayUnionFind(Intervals.numElements(labels).toInt()),
				toIndex: (Localizable) -> Long = { IntervalIndexer.positionToIndex(it, labels) }
		): Long {

			if (!Views.isZeroMin(labels))
				return fromSymmetricAffinities(
						Views.translate(foreground, *Intervals.minAsLongArray(labels).invertValues()),
						Views.translate(affinities, *Intervals.minAsLongArray(labels).invertValues()),
						labels,
						threshold,
						*steps,
						unionFind = unionFind,
						toIndex = toIndex)

			unionFindFromSymmetricAffinities(foreground, Views.interval(affinities, labels), unionFind, threshold, *steps, toIndex = toIndex)
			return relabel(Views.interval(foreground, labels), labels, unionFind, toIndex)
		}

		@JvmStatic
		fun <B: BooleanType<B>, L: IntegerType<L>> relabel(
				mask: RandomAccessibleInterval<B>,
				labels: RandomAccessibleInterval<L>,
				unionFind: UnionFind,
				toIndex: (Localizable) -> Long,
				indexToId: (Long) -> Long = {it+1})
		: Long {
			val c = Views.flatIterable(labels).cursor()
			val b = Views.flatIterable(mask).cursor()
			var maxId = Long.MIN_VALUE
			while (c.hasNext()) {
				val p = c.next()
				if (b.next().get()) {
					val id = indexToId(unionFind.findRoot(toIndex(c)))
					p.setInteger(id)
					if (id > maxId)
						maxId = id
				}
			}
			return maxId
		}

		@JvmStatic
		fun <B: BooleanType<B>, R: RealType<R>, C: Composite<R>> unionFindFromSymmetricAffinities(
				foreground: RandomAccessible<B>,
				affinities: RandomAccessibleInterval<C>,
				unionFind: UnionFind,
				threshold: Double,
				vararg steps: Long,
				toIndex: (Localizable) -> Long
		) {

			if (steps.size != affinities.numDimensions())
				throw IllegalArgumentException("Need on step size for each dimension but got steps ${Arrays.toString(steps)} " +
						"and dimensions ${Arrays.toString(Intervals.dimensionsAsLongArray(affinities))}")

			for (dim in steps.indices) {
				val currentPixelMask = Views.interval(foreground, affinities)
				val shiftedPixelMask = Views.interval(foreground, Intervals.translate(affinities, steps[dim], dim))
				val cCursor = Views.flatIterable(currentPixelMask).cursor()
				val sCursor = Views.flatIterable(shiftedPixelMask).cursor()
				val aCursor = Views.flatIterable(affinities).cursor()

				val dimAslong = dim.toLong()

				while (aCursor.hasNext()) {
					cCursor.fwd()
					sCursor.fwd()
					aCursor.fwd()

					val c = cCursor.get().get()
					if (!c)
						continue

					val s = sCursor.get().get()
					if (!s)
						continue

					val a = aCursor.get().get(dimAslong).realDouble
					if (a.isNaN() || a < threshold)
						continue

					val r1 = unionFind.findRoot(toIndex(cCursor))
					val r2 = unionFind.findRoot(toIndex(sCursor))

					if (r1 != r2)
						unionFind.join(r1, r2)

				}

			}

		}

		@JvmStatic
		fun <B: BooleanType<B>, R: RealType<R>, C: Composite<R>> unionFindFromSymmetricAffinities(
				foreground: RandomAccessible<B>,
				affinities: RandomAccessibleInterval<C>,
				unionFind: UnionFind,
				threshold: Double,
				vararg steps: LongArray,
				toIndex: (Localizable) -> Long
		) {

			for (step in steps)
				if (step.size != affinities.numDimensions())
					throw IllegalArgumentException("Need on step size for each dimension but got steps ${Arrays.toString(steps)} " +
							"and dimensions ${Arrays.toString(Intervals.dimensionsAsLongArray(affinities))}")

			for (stepIndex in steps.indices) {
				val currentPixelMask = Views.interval(foreground, affinities)
				val shiftedPixelMask = Views.interval(foreground, Views.translate(affinities, *steps[stepIndex]))
				val cCursor = Views.flatIterable(currentPixelMask).cursor()
				val sCursor = Views.flatIterable(shiftedPixelMask).cursor()
				val aCursor = Views.flatIterable(affinities).cursor()

				val stepIndexLong = stepIndex.toLong()

				while (aCursor.hasNext()) {
					cCursor.fwd()
					sCursor.fwd()
					aCursor.fwd()

					val c = cCursor.get().get()
					if (!c)
						continue

					val s = sCursor.get().get()
					if (!s)
						continue

					val a = aCursor.get().get(stepIndexLong).realDouble
					if (a.isNaN() || a < threshold)
						continue

					val r1 = unionFind.findRoot(toIndex(cCursor))
					val r2 = unionFind.findRoot(toIndex(sCursor))

					if (r1 != r2)
						unionFind.join(r1, r2)

				}

			}

		}

		private fun LongArray.invertValues(): LongArray {
			return LongArray(this.size, {-this[it]})
		}

	}

}

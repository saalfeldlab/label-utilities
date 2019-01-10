package net.imglib2.algorithm.labeling.affinities

import gnu.trove.list.array.TIntArrayList
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
//import org.apache.commons.math3.analysis.differentiation.SparseGradient.createVariable
import gnu.trove.list.array.TLongArrayList
import it.unimi.dsi.fastutil.ints.IntComparators
import net.imglib2.*
import net.imglib2.algorithm.neighborhood.Neighborhood
import net.imglib2.algorithm.neighborhood.Shape
import net.imglib2.algorithm.util.unionfind.IntArrayUnionFind
import net.imglib2.algorithm.util.unionfind.UnionFind
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.Type
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.integer.IntType
import net.imglib2.type.numeric.integer.LongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.util.IntervalIndexer
import net.imglib2.util.Intervals
import net.imglib2.util.Pair;
import net.imglib2.view.Views
import net.imglib2.view.composite.Composite
import java.util.*
import java.util.function.BiPredicate
import java.util.function.Predicate
import java.util.stream.Stream


class Rain {

	companion object {
//		interface Checks<T : Type<T>, U : IntegerType<U>> {
//
//			fun isPlateau(position: Long, u: U, neighbors: Neighborhood<Pair<T, U>>): Boolean {
//				return position == u.getIntegerLong()
//			}
//
//			fun isSamePlateau(t1: T, u1: U, t2: T, u2: U): Boolean {
//				return t1.valueEquals(t2)
//			}
//
//			fun isBoundary(t: T, u: U, neighbors: Neighborhood<Pair<T, U>>): Boolean {
//				val n = neighbors.cursor()
//				while (n.hasNext())
//					if (n.next().getA().valueEquals(t)) {
//						u.set(n.get().getB())
//						return true
//					}
//				return false
//			}
//
//		}

		// TODO still need to resolve plateaus
		@JvmStatic
		fun <T : Type<T>, C : Composite<T>> watershed(
				img: RandomAccessibleInterval<C>,
				isValid: Predicate<T>,
				compare: BiPredicate<T, T>,
				worstVal: T,
				vararg offsets: LongArray): kotlin.Pair<IntArray, TIntArrayList> {

			if (Intervals.numElements(img) > Integer.MAX_VALUE)
				throw IllegalArgumentException("" +
						"Only images smaller than max array size allowed. " +
						"Got ${Intervals.numElements(img)} (${Arrays.toString(Intervals.dimensionsAsLongArray(img))})")

			if (!Views.isZeroMin(img))
				return watershed(Views.zeroMin(img), isValid, compare, worstVal)

			val steps = offsetsToSteps(dimensionStrides(img), *offsets)

			val parentStore = IntArray(Intervals.numElements(img).toInt())

			findParents(img, parentStore, isValid, compare, worstVal, *steps.map { it.toInt() }.toIntArray())

			val roots = findRoots(parentStore)

			for (index in parentStore.indices) {
				val root = find(parentStore, index)
				parentStore[index] = root
			}

			return kotlin.Pair(parentStore, roots)
		}


		private fun <T: Type<T>, C: Composite<T>> findParents(
				img: RandomAccessibleInterval<C>,
				parentStore: IntArray,
				isValid: Predicate<T>,
				compare: BiPredicate<T, T>, // first better than second?
				worstVal: T,
				vararg offsets: Int) {

			val nDim = img.numDimensions()
			val maxDim = nDim - 1

			// TODO only need size here, remove strides
			val strides = LongArray(nDim)
			strides[0] = 1
			for (d in 1 until strides.size)
				strides[d] = strides[d - 1] * img.dimension(d - 1)
//			val size = strides[nDim - 1] * img.dimension(nDim - 1)
			val size = Intervals.numElements(img).toInt()

			val currentBest = img.randomAccess().get().get(0).createVariable()
			var currentArgBest: Int

			val cursor = Views.flatIterable(img).cursor()

			for (pos in 0 until size) {
				val current = cursor.next()
				currentBest.set(worstVal)
				currentArgBest = pos

				offsets.forEachIndexed { i, offset ->
					val affinity = current.get(i.toLong())
					if (isValid.test(affinity)) {
						if (compare.test(affinity, currentBest)) {
							currentBest.set(affinity)
							currentArgBest = pos + offset
						}
					}

				}

				parentStore[pos] = currentArgBest

			}

		}

		private fun findRoots(
				labels: IntArray): TIntArrayList {

			val roots = TIntArrayList()

			labels.forEachIndexed { index, current ->
				val other = labels[current]
				if (other == index) {
					labels[index] = index
					roots.add(index)
				}
			}

			return roots
		}

//		private fun <T : Type<T>, U : IntegerType<U>> findNonMinimumPlateauBoundaries(
//				img: RandomAccessible<T>,
//				labels: RandomAccessibleInterval<U>,
//				shape: Shape,
//				compare: BiPredicate<T, T>,
//				plateauBoundaryCheck: Checks<T, U>): LongArrayFIFOQueue {
//			val queue = LongArrayFIFOQueue()
//			val currentIndex = labels.randomAccess().get().createVariable()
//
//			val imgAndLabelsAccessible = Views.pair(img, Views.extendBorder(labels))
//			val imgAndLabelsNeighborhood = shape.neighborhoodsRandomAccessible(imgAndLabelsAccessible)
//			val neighborhoodAndLabels = Views.pair(imgAndLabelsNeighborhood, Views.pair(img, labels))
//
//
//			val cursor = Views.interval(neighborhoodAndLabels, labels).cursor()
//			while (cursor.hasNext()) {
//				val pair = cursor.next()
//				val currentPair = pair.getB()
//				val ref = currentPair.getA()
//				val refIndex = currentPair.getB()
//				val index = IntervalIndexer.positionToIndex(cursor, labels)
//				currentIndex.setInteger(index)
//
//				val neighbors = pair.getA()
//
//				if (plateauBoundaryCheck.isBoundary(ref, refIndex, neighbors)) {
//					val isBoundaryPixel = plateauBoundaryCheck.isBoundary(ref, refIndex, neighbors)
//
//					if (isBoundaryPixel) {
//						val neighborCursor = pair.getA().cursor()
//						while (neighborCursor.hasNext()) {
//							val p = neighborCursor.next()
//							if (p.getA().valueEquals(ref))
//								queue.enqueue(IntervalIndexer.positionToIndex(neighborCursor, labels))
//						}
//					}
//				}
//			}
//
//			return queue
//		}

//		private fun <T : Type<T>, U : IntegerType<U>> fillNonMinimumPlateaus(
//				img: RandomAccessible<T>,
//				labels: RandomAccessibleInterval<U>,
//				shape: Shape,
//				compare: BiPredicate<T, T>,
//				checks: Checks<T, U>,
//				queue: LongArrayFIFOQueue) {
//
//			val access = Views.pair(img, labels).randomAccess()
//			val neighborhoodsAccess = shape.neighborhoodsRandomAccessible(Views.pair(img, labels)).randomAccess()
//
//			while (!queue.isEmpty) {
//				val index = queue.dequeueLong()
//				IntervalIndexer.indexToPosition(index, labels, access)
//				IntervalIndexer.indexToPosition(index, labels, neighborhoodsAccess)
//				val ref = access.get()
//
//				val neighbors = neighborhoodsAccess.get()
//
//				val nCursor = neighbors.cursor()
//				while (nCursor.hasNext()) {
//					val n = nCursor.next()
//					if (checks.isSamePlateau(ref.getA(), ref.getB(), n.getA(), n.getB())) {
//						n.getB().setInteger(index)
//						queue.enqueue(IntervalIndexer.positionToIndex(nCursor, labels))
//					}
//				}
//
//			}
//		}

		// TODO replace this with union find
		private fun find(parentStore: IntArray, index: Int): Int {
			var returnVal = index

			while (true) {
				val currentVal = parentStore[returnVal]
				if (currentVal == returnVal)
					break
				returnVal = currentVal
			}

			var w = index
			while (w != returnVal) {
				val t = parentStore[w]
				val tmp = t
				parentStore[w] = returnVal
				w = tmp
			}

			return returnVal
		}

		private fun dimensionStrides(dims: Dimensions): LongArray {
			val strides = LongArray(dims.numDimensions())
			strides[0] = 1
			for (d in 1 until strides.size)
			strides[d] = strides[d - 1] * dims.dimension(d - 1)
			return strides
		}

		private fun offsetsToSteps(strides: LongArray, vararg offsets: LongArray): LongArray {
			return Stream.of(*offsets).mapToLong { offsetToStep(strides, *it) }.toArray()
		}

		private fun offsetToStep(strides: LongArray, vararg offsets: Long): Long {
			return Stream.of(*offsets.indices.toList().toTypedArray()).mapToLong {strides[it] * offsets[it]}.sum()
		}
	}


}

fun main(args: Array<String>) {

	val affinitiesStore = doubleArrayOf(
			Double.NaN, Double.NaN, 0.8, 0.9,
			Double.NaN, Double.NaN, 0.7, 0.6,
			Double.NaN, 0.9, 0.85, 0.9,

			Double.NaN, Double.NaN, Double.NaN, Double.NaN,
			1.0, 0.8, 1.0, 0.9,
			0.95, 0.15, 0.01, 0.02
	)

	val affinities = Views.collapseReal(ArrayImgs.doubles(affinitiesStore, 4, 3, 2))
	val offsets = arrayOf(longArrayOf(-1, 0), longArrayOf(0, -1))

	val (parents, roots) = Rain.watershed(
			affinities,
			Predicate { !it.realDouble.isNaN() },
			BiPredicate { v1, v2 -> v1.realDouble > v2.realDouble },
			DoubleType(Double.NEGATIVE_INFINITY),
			*offsets)

	println("${Arrays.toString(parents)} $roots")


}

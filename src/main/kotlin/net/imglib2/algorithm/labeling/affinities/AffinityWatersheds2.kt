import gnu.trove.list.array.TIntArrayList
import gnu.trove.list.array.TLongArrayList
import net.imglib2.Dimensions
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import net.imglib2.view.composite.RealComposite
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

import java.util.ArrayList
import java.util.Arrays
import java.util.concurrent.Callable
import java.util.function.BiPredicate
import java.util.function.Predicate

object AffinityWatersheds2 {

	// TODO decide what to do about symmetry!

	private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

	fun generateStride(i: Dimensions): LongArray {
		val nDim = i.numDimensions()
		val strides = LongArray(nDim)

		strides[0] = 1
		for (d in 1 until nDim)
			strides[d] = strides[d - 1] * i.dimension(d - 1)

		return strides
	}

	fun generateSteps(strides: LongArray, offsets: Array<out LongArray>): LongArray {
		val steps = LongArray(offsets.size)
		for (d in steps.indices) {
			var step: Long = 0
			for (k in 0 until offsets[d].size)
				step += strides[k] * offsets[d][k]
			steps[d] = step
		}
		return steps
	}

	fun generateDirectionBitmask(nStrides: Int): LongArray {

		require(nStrides <= 30)

		val bitmask = LongArray(nStrides)
		for (d in bitmask.indices)
			bitmask[d] = (1 shl d).toLong()
		return bitmask
	}

	fun generateInverseDirectionBitmask(bitmask: LongArray): LongArray {
		val inverseBitmask = LongArray(bitmask.size)
		for (d in bitmask.indices)
			inverseBitmask[d] = bitmask[bitmask.size - 1 - d]
		return inverseBitmask
	}

	@JvmStatic
	fun <T : RealType<T>> letItRain(
			source: RandomAccessibleInterval<RealComposite<T>>,
			isValid: Predicate<T>,
			isBetter: BiPredicate<T, T>,
			worst: T,
			vararg offsets: LongArray): Pair<LongArray, LongArray> {

		checkOffsets(*offsets)

		if (!Views.isZeroMin(source))
			return letItRain(Views.zeroMin(source), isValid, isBetter, worst)

		assert(Intervals.numElements(source) <= Integer.MAX_VALUE)

		val size = Intervals.numElements(source).toInt()

		val labels = LongArray(size)

		val highBit = 1L shl 63
		val secondHighBit = 1L shl 62

		val nDim = source.numDimensions()
		val strides = generateStride(source)
		val steps = Arrays.stream(generateSteps(strides, offsets)).mapToInt { l -> l.toInt() }.toArray()
		val bitmask = generateDirectionBitmask(offsets.size)
		val inverseBitmask = generateInverseDirectionBitmask(bitmask)

		LOG.debug("Steps:           {}", steps)
		LOG.debug("Bitmask:         {}", bitmask)
		LOG.debug("Inverse bitmask: {}", inverseBitmask)

		findParents(source, labels, isValid, isBetter, worst, bitmask, offsets.size)
		LOG.debug("Parent labels: {}", labels)
		val plateauCorners = findPlateauCorners(labels, steps, bitmask, inverseBitmask, secondHighBit)
		LOG.debug("Plateau corners: {}", plateauCorners)
		removePlateaus(plateauCorners, labels, steps, bitmask, inverseBitmask, highBit, secondHighBit)
		LOG.debug("Labels after plateau removal: {}", labels)
		val counts = fillFromRoots(labels, steps, bitmask, inverseBitmask, highBit)

		return Pair(labels, counts)

	}

	private fun checkOffsets(vararg offsets: LongArray) {

		fun LongArray.invert(): LongArray {
			return LongArray(this.size, { -this[it] } )
		}
		require(offsets.size % 2 == 0) {"Expecting symmetric affinities but got odd number of offsets: ${offsets.map { Arrays.toString(it) }}"}
		require(offsets.size <= 30) {"Number of offsets cannot be greater than 30 but got: ${offsets.map { Arrays.toString(it) }}"}

		val halfSize = offsets.size / 2
		for (index in 0 until halfSize) {
			val otherIndex = offsets.size - 1 - index
			require(Arrays.equals(offsets[index], offsets[otherIndex].invert()))
			{ "${index}th offset is not symmetric (require offsets[i] == -offsets[offsets.size - 1 -i]): ${Arrays.toString(offsets[index])} != - ${Arrays.toString(offsets[otherIndex])} (${offsets.map { Arrays.toString(it) } })" }
		}

	}

	fun <T : RealType<T>> findParents(
			source: RandomAccessibleInterval<RealComposite<T>>,
			labels: LongArray,
			isValid: Predicate<T>,
			isBetter: BiPredicate<T, T>,
			worst: T,
			bitMask: LongArray,
			nEdges: Int) {
		val size = labels.size
		val cursor = Views.flatIterable(source).cursor()
		val currentBest = worst.createVariable()

		for (start in 0 until size) {
			val edgeWeights = cursor.next()
			var label: Long = 0
			currentBest.set(worst)

			for (edgeIndex in 0 until nEdges) {
				val currentWeight = edgeWeights.get(edgeIndex.toLong())
				if (isValid.test(currentWeight) && isBetter.test(currentWeight, currentBest))
					currentBest.set(currentWeight)
			}

			if (!currentBest.valueEquals(worst))
				for (i in 0 until nEdges)
					if (edgeWeights.get(i.toLong()).valueEquals(currentBest))
						label = label or bitMask[i]

			labels[start] = label
		}

	}

	private fun findPlateauCorners(
			labels: LongArray,
			steps: IntArray,
			bitmask: LongArray,
			inverseBitmask: LongArray,
			plateauCornerMask: Long): TIntArrayList {
		val nEdges = steps.size
		val size = labels.size


		val plateauCornerIndices = TIntArrayList()

		for (index in 0 until size) {

			val label = labels[index]
			for (i in 0 until nEdges) {
				if (label and bitmask[i] != 0L) {
					val otherIndex = index + steps[i]
					if (labels[otherIndex] and inverseBitmask[i] == 0L) {
						labels[index] = label or plateauCornerMask
						plateauCornerIndices.add(index)
						break
					}
				}
			}

		}

		return plateauCornerIndices
	}

	private fun removePlateaus(
			queue: TIntArrayList,
			labels: LongArray,
			steps: IntArray,
			bitMask: LongArray,
			inverseBitmask: LongArray,
			highBit: Long,
			secondHighBit: Long) {

		// This is the same as example in paper, if traversal order of queue is
		// reversed.

		// helpers
		val nEdges = steps.size

		var queueIndex = 0
		while(queueIndex < queue.size()) {
			val index = queue.get(queueIndex)
			var parent: Long = 0
			val label = labels[index]
			for (d in 0 until nEdges)
				if (label and bitMask[d] != 0L) {
					val otherIndex = index + steps[d]
//					run {
					val otherLabel = labels[otherIndex]
					if (otherLabel and inverseBitmask[d] != 0L && otherLabel and secondHighBit == 0L) {
						queue.add(otherIndex)
						labels[otherIndex] = otherLabel or secondHighBit
					} else if (parent == 0L)
						parent = bitMask[d]
//					}
				}

			labels[index] = parent
			++queueIndex
		}
	}

	private fun fillFromRoots(
			labels: LongArray,
			steps: IntArray,
			bitmask: LongArray,
			inverseBitmask: LongArray,
			visitedMask: Long): LongArray {

		val size = labels.size
		val nEdges = steps.size


		var backgroundCount = 0L

		val roots = TIntArrayList()

		for (index in 0 until size) {
			run {
				var isChild = false
				var hasChild = false

				val label = labels[index]

				var i = 0
				while (i < nEdges && !isChild && !hasChild) {
					if (label and bitmask[i] != 0L) {
						isChild = true
						val otherIndex = index + steps[i]
						if (labels[otherIndex] and inverseBitmask[i] != 0L && index < otherIndex)
							hasChild = true

					}
					++i
				}
				if (hasChild)
					roots.add(index)
				else if (!isChild)
					++backgroundCount
				else Unit
			}
		}

		LOG.debug("Found roots {}", roots)
		LOG.debug("Parent labels after root relabeling {}", labels)

		val counts = LongArray(roots.size() + 1)
		counts[0] = backgroundCount

		for (id in 1 until counts.size) {
			val queue = TIntArrayList()
			run {
				queue.add(roots.get(id - 1))
				val regionLabel = id.toLong() or visitedMask
				var startIndex = 0
				while (startIndex < queue.size()) {
					val index = queue.get(startIndex)
					for (d in 0 until nEdges) {
						val otherIndex = index + steps[d]
						if (otherIndex >= 0 && otherIndex < size) {
							val otherLabel = labels[otherIndex]
							if (otherLabel and visitedMask == 0L && otherLabel and inverseBitmask[d] != 0L)
								queue.add(otherIndex)
						}
					}
					labels[index] = regionLabel
					++counts[id]
					++startIndex
				}
				queue.clear()
			}
		}

		// should this happen outside?
		val activeBits = visitedMask.inv()
		for (start in 0 until size)
			labels[start] = labels[start] and activeBits
		return counts
	}

}

fun main(args: Array<String>) {

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

	val (labels, counts) = AffinityWatersheds2.letItRain(
			affinities,
			Predicate { !it.realDouble.isNaN() },
			BiPredicate { v1, v2 -> v1.realDouble > v2.realDouble },
			DoubleType(Double.NEGATIVE_INFINITY),
			*offsets)

//	for (index in labels.indices)
//		labels[index] = labels[index] and (1 shl 31).inv()
	println("${Arrays.toString(labels)} ${Arrays.toString(counts)}")


}

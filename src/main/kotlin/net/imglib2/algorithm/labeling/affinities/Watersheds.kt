package net.imglib2.algorithm.labeling.affinities

import net.imglib2.Dimensions
import net.imglib2.FinalDimensions
import net.imglib2.Interval
import net.imglib2.Point
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.labeling.queue.PriorityQueue
import net.imglib2.algorithm.labeling.queue.PriorityQueueFactory
import net.imglib2.algorithm.labeling.queue.PriorityQueueFastUtil
import net.imglib2.algorithm.util.unionfind.IntArrayUnionFind
import net.imglib2.img.Img
import net.imglib2.img.ImgFactory
import net.imglib2.loops.LoopBuilder
import net.imglib2.type.BooleanType
import net.imglib2.type.Type
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.LongType
import net.imglib2.util.IntervalIndexer
import net.imglib2.util.Intervals
import net.imglib2.util.Util
import net.imglib2.view.Views
import net.imglib2.view.composite.Composite
import net.imglib2.view.composite.RealComposite
import org.janelia.saalfeldlab.labels.Label
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.Arrays
import java.util.function.BiConsumer
import java.util.function.BiPredicate
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.LongStream
import java.util.stream.Stream

class Watersheds {

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private const val STORE_ID_INDEX = 1L

		private const val STORE_AFF_INDEX = 0L

		@JvmStatic
		@JvmOverloads
		fun <A: RealType<A>> constructAffinities(
				affinities: RandomAccessibleInterval<A>,
				vararg offsets: LongArray,
				factory: ImgFactory<A>? = null): RandomAccessibleInterval<A> {
			return if (factory == null) constructAffinitiesWithViews(affinities, *offsets) else constructAffinitiesWithCopy(affinities, factory, *offsets)
		}

		private fun <A: RealType<A>> constructAffinitiesWithCopy(
				affinities: RandomAccessibleInterval<A>,
				factory: ImgFactory<A>,
				vararg offsets: LongArray): RandomAccessibleInterval<A> {
			val dims = Intervals.dimensionsAsLongArray(affinities)
			dims[dims.size - 1] = dims[dims.size - 1] * 2
			val symmetricAffinities = factory.create(*dims)

			LoopBuilder
					.setImages(affinities, Views.interval(symmetricAffinities, Views.zeroMin(affinities)))
					.forEachPixel(BiConsumer { t, u ->  u.set(t)})

			val nanExtension = Util.getTypeFromInterval(affinities).createVariable()
			nanExtension.setReal(Double.NaN)

			val zeroMinAffinities = if (Views.isZeroMin(affinities)) affinities else Views.zeroMin(affinities)

			for (offsetIndex in 0 until offsets.size) {
				val targetSlice = Views.hyperSlice(symmetricAffinities, dims.size - 1, offsets.size + offsetIndex.toLong())
				val sourceSlice = Views.interval(Views.translate(
						Views.extendValue(Views.hyperSlice(zeroMinAffinities, dims.size - 1, offsetIndex.toLong()), nanExtension),
						*offsets[offsetIndex]), targetSlice)
				LoopBuilder
						.setImages(sourceSlice, targetSlice)
						.forEachPixel(BiConsumer { t, u -> u.set(t) })
			}

			return if (Views.isZeroMin(affinities)) symmetricAffinities else Views.translate(symmetricAffinities, *Intervals.minAsLongArray(affinities))
		}

		private fun <A: RealType<A>> constructAffinitiesWithViews(
				affinities: RandomAccessibleInterval<A>,
				vararg offsets: LongArray): RandomAccessibleInterval<A> {
			TODO("View-based symmetric affinities not yet implemented. Use factory instead.")
		}

		@JvmStatic
		fun symmetricOffsets(vararg offsets: LongArray): Array<LongArray> {
			return arrayOf(*offsets) + Stream.of(*offsets).map { it.invertValues() }.collect(Collectors.toList()).toTypedArray()
		}

		@JvmStatic
		@JvmOverloads
		fun <A: RealType<A>, L: IntegerType<L>, C: Composite<A>> seededFromAffinities(
				affinities: RandomAccessible<C>,
				labels: RandomAccessibleInterval<L>,
				seeds: List<Point>,
				vararg offsets: LongArray,
				priorityQueueFactory: PriorityQueueFactory = PriorityQueueFastUtil.FACTORY,
				labelsAndCostStoreFactory: (Dimensions) -> Img<LongType> = {Util.getSuitableImgFactory(it, LongType()).create(it)},
				notSetLabel: L = withInvalid { Util.getTypeFromInterval(labels).createVariable() }) {
			seededFromAffinities(
					affinities,
					Views.extendValue(labels, notSetLabel.copy()),
					seeds,
					listOf(*offsets),
					priorityQueue = priorityQueueFactory.create(),
					labelsAndCostStore = createStore(labelsAndCostStoreFactory, labels),
					notSetLabel = notSetLabel)
		}

		@JvmStatic
		fun <T: RealType<T>> letItRainRealType(
				affinities: RandomAccessibleInterval<out Composite<T>>,
				isValid: Predicate<T> = Predicate { !it.realDouble.isNaN() },
				isBetter: BiPredicate<T, T> = BiPredicate { t, u -> t.realDouble > u.realDouble },
				worst: T,
				vararg offsets: LongArray
		): Pair<LongArray, LongArray> {
			return letItRain(affinities, isValid, isBetter, worst, *offsets)
		}

		@JvmStatic
		fun <T: Type<T>> letItRain(
				affinities: RandomAccessibleInterval<out Composite<T>>,
				isValid: Predicate<T>,
				isBetter: BiPredicate<T, T>,
				worst: T,
				vararg offsets: LongArray
		): Pair<LongArray, LongArray> {
			return Rain.letItRain(affinities, isValid, isBetter, worst, *offsets)
		}

		private fun <A: RealType<A>, L: IntegerType<L>, C: Composite<A>> seededFromAffinities(
				affinities: RandomAccessible<C>,
				labels: RandomAccessible<L>,
				seeds: List<Point>,
				offsets: List<LongArray>,
				priorityQueue: PriorityQueue,
				labelsAndCostStore: RandomAccessibleInterval<RealComposite<LongType>>,
				notSetLabel: L) {

			val affinitiesAccess = affinities.randomAccess()
			val labelsAccess = labels.randomAccess()
			val labelsAndCostStoreAccess = labelsAndCostStore.randomAccess()
			val inverseOffsets = offsets.stream().map { it.invertValues() }.collect(Collectors.toList())
			val zippedOffsets = offsets.zip(inverseOffsets).toTypedArray()

			LOG.debug("Running watersheds with {} seeds.", seeds.size)

			for (seed in seeds) {
				LOG.trace("Adding seed {}", seed)
				labelsAccess.setPosition(seed)
				val label = labelsAccess.get()

				if (notSetLabel.valueEquals(label))
					continue

				val labelPrimitive = label.integerLong

				affinitiesAccess.setPosition(seed)
				labelsAndCostStoreAccess.setPosition(seed)

				val affinity = affinitiesAccess.get()

				zippedOffsets.forEachIndexed { index, (fwd, bck)->
					val aff = affinity.get(index.toLong()).realDouble

					if (!aff.isNaN()) {
						labelsAndCostStoreAccess.move(fwd)
						val lac = labelsAndCostStoreAccess.get()
						val a = lac[STORE_AFF_INDEX]
						if (aff > ltd(a.integerLong)) {
							a.setInteger(dtl(aff))
							lac[STORE_ID_INDEX].setInteger(labelPrimitive)
						}

						priorityQueue.add(IntervalIndexer.positionToIndexForInterval(labelsAndCostStoreAccess, labelsAndCostStore), aff)

						labelsAndCostStoreAccess.move(bck)
					}
				}

			}

			LOG.debug("Starting with queue size {}.", priorityQueue.size())

			while (priorityQueue.size() > 0) {
				val seedLinear = priorityQueue.pop()
				IntervalIndexer.indexToPositionForInterval(seedLinear, labelsAndCostStore, labelsAccess)
				val label = labelsAccess.get()

				if (!notSetLabel.valueEquals(label))
					continue

				affinitiesAccess.setPosition(labelsAccess)
				labelsAndCostStoreAccess.setPosition(labelsAccess)

				val labelPrimitive = labelsAndCostStoreAccess.get()[STORE_ID_INDEX].integerLong
				label.setInteger(labelPrimitive)

				val affinity = affinitiesAccess.get()

				zippedOffsets.forEachIndexed { index, (fwd, bck)->
					val aff = affinity.get(index.toLong()).realDouble

					if (!aff.isNaN()) {
						labelsAndCostStoreAccess.move(fwd)
						val lac = labelsAndCostStoreAccess.get()
						val a = lac[STORE_AFF_INDEX]
						if (aff > ltd(a.integerLong)) {
							a.setInteger(dtl(aff))
							lac[STORE_ID_INDEX].setInteger(labelPrimitive)
						}

						priorityQueue.add(IntervalIndexer.positionToIndexForInterval(labelsAndCostStoreAccess, labelsAndCostStore), aff)

						labelsAndCostStoreAccess.move(bck)
					}
				}

			}

		}

		private fun createStore(
				factory: (Dimensions) -> Img<LongType>,
				interval: Interval): RandomAccessibleInterval<RealComposite<LongType>> {
			val store = factory(FinalDimensions(*(Intervals.dimensionsAsLongArray(interval) + longArrayOf(2))))
			val translatedStore = Views.translate<RealComposite<LongType>>(Views.collapseReal(store), *Intervals.minAsLongArray(interval))
			for (c in Views.iterable(translatedStore))
				c.get(1).setInteger(dtl(java.lang.Double.NEGATIVE_INFINITY))
			return translatedStore
		}

		private fun ltd(l: Long): Double {
			return java.lang.Double.longBitsToDouble(l)
		}

		private fun dtl(d: Double): Long {
			return java.lang.Double.doubleToRawLongBits(d)
		}

		@JvmStatic
		fun collectSeeds(seedMask: RandomAccessibleInterval<out BooleanType<*>>): List<Point> {
			val s = Views.iterable(seedMask).cursor()
			val seeds = mutableListOf<Point>()
			while (s.hasNext())
				if (s.next().get())
					seeds.add(Point(s))
			return seeds
		}

		@JvmStatic
		fun <B: BooleanType<B>, S: BooleanType<S>> seedsFromMask(mask: RandomAccessible<B>, seedMask: RandomAccessibleInterval<S>, vararg offsets: LongArray) {
			for (offset in offsets) {
				val seedMaskCursor = Views.flatIterable(seedMask).cursor()
				val maskCursor = Views.flatIterable(Views.interval(mask, seedMask)).cursor()
				val maskAtOffsetCursor = Views.flatIterable(Views.interval(mask, Views.translate(seedMask, *offset))).cursor()

				while (seedMaskCursor.hasNext()) {
					seedMaskCursor.fwd()
					maskCursor.fwd()
					maskAtOffsetCursor.fwd()
					if (maskCursor.get().get() && !maskAtOffsetCursor.get().get())
						seedMaskCursor.get().set(true)
				}

			}
		}

		private fun LongArray.invertValues(max: Long = 0): LongArray {
			return LongArray(this.size, {max-this[it]})
		}

		fun <T: IntegerType<T>> withInvalid(supplier: () -> T): T {
			val t = supplier()
			t.setInteger(Label.INVALID)
			return t
		}

	}

}

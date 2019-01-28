package net.imglib2.algorithm.labeling

import net.imglib2.*
import net.imglib2.algorithm.labeling.queue.PriorityQueue
import net.imglib2.algorithm.labeling.queue.PriorityQueueFactory
import net.imglib2.algorithm.labeling.queue.PriorityQueueFastUtil
import net.imglib2.algorithm.neighborhood.DiamondShape
import net.imglib2.algorithm.neighborhood.Shape
import net.imglib2.img.ImgFactory
import net.imglib2.img.array.ArrayImgFactory
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.NumericType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.LongType
import net.imglib2.util.IntervalIndexer
import net.imglib2.util.Intervals
import net.imglib2.util.Pair
import net.imglib2.util.Util
import net.imglib2.view.Views
import net.imglib2.view.composite.Composite
import net.imglib2.view.composite.RealComposite
import java.util.*
import java.util.function.ToDoubleBiFunction
import java.util.stream.LongStream

class Watersheds {

	companion object {

		@JvmStatic
		@JvmOverloads
		fun <T : RealType<T>, U : IntegerType<U>> seededRealType(
				relief: RandomAccessible<T>,
				basins: RandomAccessibleInterval<U>,
				seeds: List<Localizable>,
				distance: ToDoubleBiFunction<T, T> = ToDoubleBiFunction { value, _ -> value.realDouble },
				shape: Shape = DiamondShape(1),
				notSetLabel: U = zero(basins),
				queueFactory: PriorityQueueFactory = PriorityQueueFastUtil.FACTORY,
				labelsAndcostToreFactory: ImgFactory<LongType> = ArrayImgFactory(LongType())) {
			seeded(relief, basins, seeds, distance, notSetLabel, shape, labelsAndcostToreFactory, queueFactory)
		}

		@JvmStatic
		@JvmOverloads
		fun <T, U : IntegerType<U>> seeded(
				relief: RandomAccessible<T>,
				basins: RandomAccessibleInterval<U>,
				seeds: List<Localizable>,
				distance: ToDoubleBiFunction<T, T>,
				notSetLabel: U,
				shape: Shape = DiamondShape(1),
				labelsAndCostStoreFactory: ImgFactory<LongType> = ArrayImgFactory(LongType()),
				queueFactory: PriorityQueueFactory = PriorityQueueFastUtil.FACTORY) {
			val queue = queueFactory.create()
			val interval = FinalInterval(basins)
			val minCostAndAssociatedLabel = createStore(labelsAndCostStoreFactory, interval)
			initializeQueue(relief, Views.extendZero(basins), minCostAndAssociatedLabel, interval, seeds, shape, distance, notSetLabel, queue)

			seeded(
					relief,
					Views.extendZero(basins),
					minCostAndAssociatedLabel,
					interval,
					shape,
					queue,
					notSetLabel,
					distance)
		}

		private fun <T, U : IntegerType<U>, C : Composite<LongType>> seeded(
				img: RandomAccessible<T>,
				labels: RandomAccessible<U>,
				helpers: RandomAccessible<C>,
				interval: Interval,
				shape: Shape,
				queue: PriorityQueue,
				nonSeedValue: U,
				distance: ToDoubleBiFunction<T, T>) {

			val imgAndLabels = Views.pair(img, labels)
			val imgAndLabelsNeighborhood = shape.neighborhoodsRandomAccessible(imgAndLabels)
			val costNeighborhood = shape.neighborhoodsRandomAccessible(helpers)

			val imgAndLabelsNeighborhoodAccess = imgAndLabelsNeighborhood.randomAccess()
			val labelsAccess = labels.randomAccess()
			val costsAccess = helpers.randomAccess()
			val costNeighborhoodAccess = costNeighborhood.randomAccess()
			val imgAccess = img.randomAccess()

			while (queue.size() > 0) {
				val candidatePos = queue.pop()
				IntervalIndexer.indexToPositionForInterval(candidatePos, interval, imgAndLabelsNeighborhoodAccess)
				labelsAccess.setPosition(imgAndLabelsNeighborhoodAccess)
				costsAccess.setPosition(imgAndLabelsNeighborhoodAccess)
				costNeighborhoodAccess.setPosition(imgAndLabelsNeighborhoodAccess)
				imgAccess.setPosition(imgAndLabelsNeighborhoodAccess)

				val id = costsAccess.get().get(0).get()

				labelsAccess.get().setInteger(id)

				checkAndAdd(
						imgAndLabelsNeighborhoodAccess.get().cursor(),
						costNeighborhoodAccess.get().cursor(),
						interval,
						distance,
						imgAccess.get(),
						nonSeedValue,
						id,
						queue)

			}
		}

		private fun createStore(
				factory: ImgFactory<LongType>,
				interval: Interval): RandomAccessibleInterval<out Composite<LongType>> {
			val store = factory.create(*LongStream.concat(Arrays.stream(Intervals.dimensionsAsLongArray(interval)), LongStream.of(2)).toArray())
			val translatedStore = Views.translate<RealComposite<LongType>>(
					Views.collapseReal(store),
					*Intervals.minAsLongArray(interval))
			for (c in Views.iterable(translatedStore))
				c.get(1).setInteger(dtl(java.lang.Double.POSITIVE_INFINITY))
			return translatedStore
		}

		private fun <T, U : IntegerType<U>, C : Composite<LongType>> initializeQueue(
				img: RandomAccessible<T>,
				labels: RandomAccessible<U>,
				minCostWithAssociatedLabel: RandomAccessible<C>,
				interval: Interval,
				seeds: List<Localizable>,
				shape: Shape,
				distance: ToDoubleBiFunction<T, T>,
				nonSetLabel: U,
				queue: PriorityQueue) {

			val imgAndLabels = Views.pair(img, labels)
			val imgAndLabelsNeighborhood = shape.neighborhoodsRandomAccessible(imgAndLabels)
			val costNeighborhood = shape.neighborhoodsRandomAccessible(minCostWithAssociatedLabel)

			val imgAccess = img.randomAccess()
			val lblAccess = labels.randomAccess()
			val costNeighborhoodAccess = costNeighborhood.randomAccess()
			val imgAndLabelsNeighborhoodAccess = imgAndLabelsNeighborhood.randomAccess()

			for (seed in seeds) {
				imgAndLabelsNeighborhoodAccess.setPosition(seed)
				costNeighborhoodAccess.setPosition(seed)
				lblAccess.setPosition(seed)
				imgAccess.setPosition(seed)

				val id = lblAccess.get()
				assert(!id.valueEquals(nonSetLabel))

				checkAndAdd(
						imgAndLabelsNeighborhoodAccess.get().cursor(),
						costNeighborhoodAccess.get().cursor(),
						interval,
						distance,
						imgAccess.get(),
						nonSetLabel,
						id.integerLong,
						queue)

			}
		}

		private fun <T, U : IntegerType<U>> checkAndAdd(
				valuesAndLabels: Cursor<Pair<T, U>>,
				costs: Cursor<out Composite<LongType>>,
				interval: Interval,
				distance: ToDoubleBiFunction<T, T>,
				seedVal: T,
				notSetLabel: U,
				id: Long,
				queue: PriorityQueue) {

			while (costs.hasNext()) {
				val valueAndLabel = valuesAndLabels.next()
				costs.fwd()

				if (notSetLabel.valueEquals(valueAndLabel.b)) {
					val value = valueAndLabel.a
					val cost = costs.get()
					val currentCost = distance.applyAsDouble(value, seedVal)
					if (java.lang.Double.isFinite(currentCost)) {
						val currentBest = ltd(cost.get(1).get())
						if (currentCost < currentBest) {
							cost.get(1).set(dtl(currentCost))
							cost.get(0).set(id)
							queue.add(IntervalIndexer.positionToIndexForInterval(costs, interval), currentCost)
						}
					}
				}
			}
		}

		private fun ltd(l: Long): Double {
			return java.lang.Double.longBitsToDouble(l)
		}

		private fun dtl(d: Double): Long {
			return java.lang.Double.doubleToRawLongBits(d)
		}

		private fun <T: NumericType<T>> zero(rai: RandomAccessibleInterval<T>): T {
			val zero = Util.getTypeFromInterval(rai).createVariable()
			zero.setZero()
			return zero
		}

	}

}

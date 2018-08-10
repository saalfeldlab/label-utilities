package org.janelia.saalfeldlab.labels

import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.set.hash.TLongHashSet
import net.imglib2.Dimensions
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.converter.Converters
import net.imglib2.converter.logical.Logical
import net.imglib2.img.ImgFactory
import net.imglib2.img.array.ArrayImgs
import net.imglib2.loops.LoopBuilder
import net.imglib2.type.BooleanType
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.util.Util
import net.imglib2.view.Views
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

class InterpolateBetweenSections {
	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private fun <B : BooleanType<B>, T : RealType<T>> signedDistanceTransform(
				mask: RandomAccessibleInterval<B>,
				distanceOutside: RandomAccessibleInterval<T>,
				distanceInside: RandomAccessibleInterval<T>,
				distanceCombined: RandomAccessibleInterval<T>,
				combine: (T, T) -> Double,
				vararg weights: Double = doubleArrayOf(1.0),
				distanceType: DistanceTransform.DISTANCE_TYPE = DistanceTransform.DISTANCE_TYPE.EUCLIDIAN
		) {
			LOG.debug("Got type {}", Util.getTypeFromInterval(distanceOutside).javaClass.simpleName)
			DistanceTransform.binaryTransform(mask, distanceOutside, distanceOutside, distanceType, *weights)
			DistanceTransform.binaryTransform(Logical.complement(mask), distanceInside, distanceInside, distanceType, *weights)
			LoopBuilder.setImages(distanceOutside, distanceInside, distanceCombined).forEachPixel(LoopBuilder.TriConsumer { o, i, c -> c.setReal(combine(o, i)) })
		}

		@JvmStatic
		fun makeFillers(
				numFillers: Long,
				vararg dim: Long
		): Array<RandomAccessibleInterval<UnsignedLongType>> {
			return Stream
					.generate { ArrayImgs.unsignedLongs(*dim) }
					.limit(numFillers)
					.collect(Collectors.toList<RandomAccessibleInterval<UnsignedLongType>>())
					.toTypedArray()
		}

		@JvmStatic
		fun <I : IntegerType<I>, R : RealType<R>> interpolateBetweenSectionsWithSignedDistanceTransform(
				section1: RandomAccessibleInterval<I>,
				section2: RandomAccessibleInterval<I>,
				distanceFactory: ImgFactory<R>,
				vararg fillers: RandomAccessibleInterval<I>,
				distanceType: DistanceTransform.DISTANCE_TYPE = DistanceTransform.DISTANCE_TYPE.EUCLIDIAN,
				transformWeights: DoubleArray = doubleArrayOf(1.0),
				background: Long = 0
		) {
			interpolateBetweenSectionsWithSignedDistanceTransform(
					section1,
					section2,
					distanceFactory.create(section1),
					distanceFactory.create(section1),
					distanceFactory.create(section2),
					distanceFactory.create(section2),
					*fillers,
					distanceType = distanceType,
					transformWeights = transformWeights,
					background = background
			)
		}

		@JvmStatic
		fun <I : IntegerType<I>, R : RealType<R>> interpolateBetweenSectionsWithSignedDistanceTransform(
				section1: RandomAccessibleInterval<I>,
				section2: RandomAccessibleInterval<I>,
				distance11: RandomAccessibleInterval<R>,
				distance12: RandomAccessibleInterval<R>,
				distance21: RandomAccessibleInterval<R>,
				distance22: RandomAccessibleInterval<R>,
				vararg fillers: RandomAccessibleInterval<I>,
				distanceType: DistanceTransform.DISTANCE_TYPE = DistanceTransform.DISTANCE_TYPE.EUCLIDIAN,
				transformWeights: DoubleArray = doubleArrayOf(1.0),
				background: Long = 0
		) {

			checkNotNull(section1)
			checkNotNull(section2)
			checkNotNull(fillers)
			fillers.forEach { checkNotNull(it) }
			checkNotNull(distanceType)

			val postCalc: (R, R) -> Double = when (distanceType) {
				DistanceTransform.DISTANCE_TYPE.EUCLIDIAN -> { x, y -> Math.sqrt(x.realDouble) - Math.sqrt(y.realDouble) }
				else -> { x, y -> x.realDouble - y.realDouble }
			}


			// include section1 and section2
			val numFillers = fillers.size
			val numSections = numFillers + 2

			LOG.debug("Got {} fillers and {} sections", numFillers, numSections)

			// nothing to do
			if (numFillers < 1)
				return

			val (labelSet, bboxes) = unique(section1, section2)
			labelSet.remove(background)
			val labels = longArrayOf(background) + labelSet.toArray()
			LOG.debug("Interpolating between labels {}", labels)


			val maxValForR = Util.getTypeFromInterval(distance11).createVariable()
			maxValForR.setReal(maxValForR.maxValue)

			LOG.debug("Processing {} labels", labels.size)

			for (label in labels) {
				if (label == background) continue
				LOG.debug("Processing label={}", label)
				val mask1 = Converters.convert(section1, { s, t -> t.set(s.integerLong == label) }, BoolType())
				val mask2 = Converters.convert(section2, { s, t -> t.set(s.integerLong == label) }, BoolType())
				signedDistanceTransform(mask1, distance11, distance12, distance11, postCalc, distanceType = distanceType, weights = *transformWeights)
				signedDistanceTransform(mask2, distance21, distance22, distance21, postCalc, distanceType = distanceType, weights = *transformWeights)

				val bbox = bboxes.get(label)
				val nDim = mask1.numDimensions()
				val min = LongArray(nDim)
				val max = LongArray(nDim)
				Arrays.setAll(min, {bbox[it]})
				Arrays.setAll(max, {bbox[it+nDim]})

				LOG.debug("Got bbox={} min={} max={} for label {}", bbox, min, max, label)

				for (i in 1..numFillers) {

					val w1 = i.toDouble() / (numSections - 1).toDouble()
					val w2 = 1.0 - w1
					LOG.debug("Weights {} and {} for filler {} and label {} and type {}", w1, w2, i, label, Util.getTypeFromInterval(distance11).javaClass.simpleName)

					val d1 = Views.flatIterable(Views.interval(distance11, min, max)).cursor()
					val d2 = Views.flatIterable(Views.interval(distance21, min, max)).cursor()
					val f = Views.flatIterable(Views.interval(fillers[i - 1], min, max)).cursor()
					while (d1.hasNext()) {
						val d = w2 * d1.next().realDouble + w1 * d2.next().realDouble
						val o = f.next()
						if (d <= 0) {
							o.setInteger(label)
						}
					}
				}

			}


		}

		/**
		 * filler distances should be initialized to infinity
		 */
		@JvmStatic
		fun <I : IntegerType<I>, R : RealType<R>> interpolateBetweenSections(
				section1: RandomAccessibleInterval<I>,
				section2: RandomAccessibleInterval<I>,
				imgFactory: ImgFactory<R>,
				vararg fillers: RandomAccessibleInterval<I>,
				distanceType: DistanceTransform.DISTANCE_TYPE = DistanceTransform.DISTANCE_TYPE.EUCLIDIAN,
				transformWeights: DoubleArray = doubleArrayOf(1.0),
				background: Long = 0
		) {

			checkNotNull(section1)
			checkNotNull(section2)
			checkNotNull(imgFactory)
			checkNotNull(fillers)

			val postCalc = when (distanceType) {
				DistanceTransform.DISTANCE_TYPE.EUCLIDIAN -> { x: R -> x.setReal(Math.sqrt(x.realDouble)) }
				else -> { _: R -> }
			}

			// include section1 and section2
			val numFillers = fillers.size
			val numSections = numFillers + 2

			LOG.debug("Got {} fillers and {} sections", numFillers, numSections)

			// nothing to do
			if (numFillers < 1)
				return

			val (labelSet, bboxes) = unique(section1, section2)
			labelSet.remove(background)
			val labels = longArrayOf(background) + labelSet.toArray()


			val distances = imgFactory.create(section1)
			val maxValForR = Util.getTypeFromInterval(distances).createVariable()
			maxValForR.setReal(maxValForR.maxValue)
			val fillersDistances = Stream
					.generate { createAndInitializeWithValue(imgFactory, section1, maxValForR) }
					.limit(fillers.size.toLong())
					.collect(Collectors.toList())

			val dt1 = imgFactory.create(section1)
			val dt2 = imgFactory.create(section2)

			for (label in labels) {
				val mask1 = Converters.convert(section1, { s, t -> t.set(s.integerLong == label) }, BoolType())
				val mask2 = Converters.convert(section2, { s, t -> t.set(s.integerLong == label) }, BoolType())
				DistanceTransform.binaryTransform(mask1, dt1, distanceType, *transformWeights)
				DistanceTransform.binaryTransform(mask2, dt2, distanceType, *transformWeights)
				dt1.forEach(postCalc)
				dt2.forEach(postCalc)

				for (i in 1..numFillers) {

					val w1 = i.toDouble() / (numSections - 1).toDouble()
					val w2 = 1.0 - w1
					LOG.debug("Weights {} and {} for filler {}", w1, w2, i)

					val d1 = Views.flatIterable(dt1).cursor()
					val d2 = Views.flatIterable(dt2).cursor()
					val f = Views.flatIterable(fillers[i - 1]).cursor()
					val fd = Views.flatIterable(fillersDistances[i - 1]).cursor()
					while (d1.hasNext()) {
						val d = w2 * d1.next().realDouble + w1 * d2.next().realDouble
						val md = fd.next()
						val abc = f.next()
						if (md.realDouble > d) {
							abc.setInteger(label)
							md.setReal(d)
						}
					}
				}

			}


		}

		private fun <I : IntegerType<I>> unique(vararg data: RandomAccessibleInterval<I>): Pair<TLongHashSet, TLongObjectHashMap<LongArray>> {
			val set = TLongHashSet()
			val map = TLongObjectHashMap<LongArray>()

			for (d in data) {
				val c = Views.iterable(d).localizingCursor()
				val nDim = d.numDimensions()
				while (c.hasNext()) {
					val i = c.next().integerLong
					set.add(i)
					val bbox = map.get(i)
					if (bbox == null) {
						val newBox = LongArray(2 * nDim)
						for (k in 0 until nDim)
						{
							val p = c.getLongPosition(k)
							newBox[k] = p
							newBox[k+nDim] = p
						}
						map.put(i, newBox)
					}
					else
					{
						for (k in 0 until nDim)
						{
							val p = c.getLongPosition(k)
							if (p < bbox[k]) bbox[k] = p
							if (p > bbox[k+nDim]) bbox[k+nDim] = p
						}
					}
				}
			}
			return Pair(set, map)
		}

		private fun <R : RealType<R>> createAndInitializeWithValue(
				factory: ImgFactory<R>,
				dims: Dimensions,
				initialValue: R
		): RandomAccessibleInterval<R> {
			val img = factory.create(dims)
			img.forEach({ it.set(initialValue) })
			return img
		}
	}
}

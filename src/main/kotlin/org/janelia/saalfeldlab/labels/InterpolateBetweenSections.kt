package org.janelia.saalfeldlab.labels

import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import net.imglib2.Dimensions
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.converter.Converters
import net.imglib2.img.ImgFactory
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.util.Util
import net.imglib2.view.Views
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.stream.Collectors
import java.util.stream.Stream

class InterpolateBetweenSections {
	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

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

			val postCalc = when(distanceType) {
				DistanceTransform.DISTANCE_TYPE.EUCLIDIAN -> { x: R -> x.setReal(Math.sqrt(x.realDouble)); x }
				else -> { x: R -> x }
			}

			// include section1 and section2
			val numFillers = fillers.size
			val numSections = numFillers + 2

			LOG.debug("Got {} fillers and {} sections", numFillers, numSections)

			// nothing to do
			if (numFillers < 1)
				return;

			val weightDelta = 1.0 / numSections
			val weights = 1.rangeTo(numFillers + 1)

			val labelSet = unique(Views.iterable(section1), Views.iterable(section2))
			labelSet.remove(background)
			val labels = longArrayOf(background) + labelSet.toArray()


			val distances = imgFactory.create(section1)
			val maxValForR = Util.getTypeFromInterval(distances).createVariable()
			maxValForR.setReal(maxValForR.maxValue)
			val fillersDistances = Stream
					.generate({ createAndInitializeWithValue(imgFactory, section1, maxValForR) })
					.limit(fillers.size.toLong())
					.collect(Collectors.toList())

			val dt1 = imgFactory.create(section1)
			val dt2 = imgFactory.create(section2)

			for(label in labels) {
				val mask1 = Converters.convert(section1, { s, t -> t.set(s.integerLong == label) }, BoolType())
				val mask2 = Converters.convert(section2, { s, t -> t.set(s.integerLong == label) }, BoolType())
				DistanceTransform.binaryTransform(mask1, dt1, distanceType, *transformWeights)
				DistanceTransform.binaryTransform(mask2, dt2, distanceType, *transformWeights)

				for (i in 1..numFillers) {

					val w1 = i.toDouble() / (numSections - 1).toDouble()
					val w2 = 1.0 - w1
					LOG.debug("Weights {} and {} for filler {}", w1, w2, i)

					val d1 = Views.flatIterable(dt1).cursor()
					val d2 = Views.flatIterable(dt2).cursor()
					val f = Views.flatIterable(fillers[i - 1]).cursor()
					val fd = Views.flatIterable(fillersDistances[i - 1]).cursor()
					while (d1.hasNext()) {
						val d = w2 * postCalc(d1.next()).realDouble + w1 * postCalc(d2.next()).realDouble
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

		private fun <I : IntegerType<I>> unique(vararg data: Iterable<I>): TLongSet {
			val set = TLongHashSet()

			for (d in data)
				for (i in d)
					set.add(i.integerLong)
			return set
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

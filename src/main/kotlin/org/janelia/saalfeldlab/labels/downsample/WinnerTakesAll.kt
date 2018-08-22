package org.janelia.saalfeldlab.labels.downsample

import gnu.trove.map.hash.TLongLongHashMap
import net.imglib2.*
import net.imglib2.algorithm.util.Grids
import net.imglib2.type.numeric.IntegerType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.labels.Label
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.function.Consumer

class WinnerTakesAll {
	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		@JvmStatic
		fun <I : IntegerType<I>, O : IntegerType<O>> downsample(
				input: RandomAccessible<I>,
				output: RandomAccessibleInterval<O>,
				vararg steps: Int) {
			assert(input.numDimensions() == output.numDimensions())
			assert(input.numDimensions() == steps.size)
			assert(Arrays.stream(steps).allMatch { it > 0 })
			val nDim = input.numDimensions()

			val inputAccess = input.randomAccess()
			val outputAccess = output.randomAccess()
			val inputPos = LongArray(nDim)
			for (dim in 0 until nDim) inputPos[dim] = outputAccess.getLongPosition(dim) * steps[dim];
			val initialInputPos = inputPos.clone()

			val min = Intervals.minAsLongArray(output)
			val max = Intervals.maxAsLongArray(output)
			outputAccess.setPosition(min)

			val stepsMinusOne = steps.clone()
			Arrays.setAll(stepsMinusOne) { stepsMinusOne[it] - 1 }

			val inputMax = inputPos.clone()
			Arrays.setAll(inputMax) { inputMax[it] + stepsMinusOne[it] }

			val ones = ones(nDim)

			val counts = TLongLongHashMap()
			val getCounts = Consumer<I> { addIfValid(it.integerLong, counts) }

			LOG.debug("min={} max={}", min, max)

			var d = 0
			while (d < nDim) {

				counts.clear()
				forAllIntervals(inputPos, inputMax, ones, getCounts, inputAccess)
				outputAccess.get().setInteger(argMax(counts))

				d = 0
				while (d < nDim) {
					outputAccess.move(1L, d)
					inputPos[d] = steps[d] + inputPos[d]
					inputMax[d] = steps[d] + inputMax[d]
					if (outputAccess.getLongPosition(d) <= max[d])
						break
					else {
						outputAccess.setPosition(min[d], d)
						inputPos[d] = initialInputPos[d]
						inputMax[d] = initialInputPos[d] + stepsMinusOne[d]
					}
					++d
				}
			}

		}

		private fun <T, S> forAllIntervals(min: LongArray, max: LongArray, blockSize: IntArray, action: Consumer<T>, s: S)
				where S : Sampler<T>, S : Positionable, S : Localizable {
			Grids.forEachOffset(
					min,
					max,
					blockSize,
					Grids.SetForDimension { to, dim -> s.setPosition(to, dim) },
					Grids.GetForDimension { s.getLongPosition(it) },
					Grids.MoveForDimension { step, dim -> s.move(step, dim) },
					Runnable { action.accept(s.get()) })
		}

		private fun newArray(size: Int, value: Int): IntArray {
			val array = IntArray(size)
			if (value != 0)
				Arrays.fill(array, value)
			return array
		}

		private fun ones(size: Int): IntArray {
			return newArray(size, 1)
		}

		private fun addIfValid(v: Long, counts: TLongLongHashMap)
		{
			if (v != Label.INVALID)
				counts.put(v, counts.get(v) + 1)
		}

		private fun argMax(counts: TLongLongHashMap): Long
		{
			var max = Label.INVALID
			var argMax = Long.MIN_VALUE
			val it = counts.iterator()
			while (it.hasNext())
			{
				it.advance()
				val count = it.value()
				if (count > max)
				{
					max = count
					argMax = it.key()
				}
			}
			return argMax
		}
	}
}

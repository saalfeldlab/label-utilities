package net.imglib2.algorithm.labeling.affinities

import net.imglib2.Point
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.type.BooleanType
import net.imglib2.view.Views

class Watersheds {

	companion object {

		fun seededFromAffinities() {

		}

		fun collectSeeds(seedMask: RandomAccessibleInterval<out BooleanType<*>>): List<Point> {
			val s = Views.iterable(seedMask).cursor()
			val seeds = mutableListOf<Point>()
			while (s.hasNext())
				if (s.next().get())
					seeds.add(Point(s))
			return seeds
		}

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

	}

}

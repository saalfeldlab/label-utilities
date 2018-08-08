package net.imglib2.converter.logical

import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.converter.Converters
import net.imglib2.img.basictypeaccess.LongAccess
import net.imglib2.type.BooleanType
import net.imglib2.type.logic.BitType
import net.imglib2.util.Pair
import net.imglib2.util.Util
import net.imglib2.view.Views
import net.imglib2.view.composite.Composite
import java.util.function.Function
import java.util.function.Supplier

class Logical private constructor() {
	companion object {
		@JvmStatic
		fun <B : BooleanType<B>> complementReadOnly(input: RandomAccessibleInterval<B>): RandomAccessibleInterval<B> {
			return Converters.convert(input, Complement.readOnly(), Util.getTypeFromInterval(input))!!
		}

		// complement
		@JvmStatic
		fun <B1 : BooleanType<B1>, B2: BooleanType<B2>> complementReadOnly(input: RandomAccessibleInterval<B1>, typeFactory: Supplier<B2>): RandomAccessibleInterval<B2> {
			return Converters.convert(input, Complement.readOnly(), typeFactory.get())
		}

		@JvmStatic
		fun <B1 : BooleanType<B1>, B2 : BooleanType<B2>> complement(
				input: RandomAccessibleInterval<B1>,
				typeFactory: Function<LongAccess, B2>): RandomAccessibleInterval<B2> {
			return Converters.convert(input, Complement.readWrite(typeFactory))!!
		}

		@JvmStatic
		fun <B: BooleanType<B>> complement(input: RandomAccessibleInterval<B>): RandomAccessibleInterval<BitType> {
			return Converters.convert(input, Complement.readWrite())!!
		}

		// and pair
		@JvmStatic
		fun <B1: BooleanType<B1>, B2: BooleanType<B2>> and(input1: RandomAccessibleInterval<B1>, input2: RandomAccessible<B2>): RandomAccessibleInterval<B1>
		{
			return and(Views.interval(Views.pair(input1, input2), input1))
		}

		@JvmStatic
		fun <B1: BooleanType<B1>, B2: BooleanType<B2>, B3: BooleanType<B3>> and(input1: RandomAccessibleInterval<B1>, input2: RandomAccessible<B2>, typeFactory: Supplier<B3>): RandomAccessibleInterval<B3>
		{
			return and(Views.interval(Views.pair(input1, input2), input1), typeFactory)
		}

		@JvmStatic
		fun <B1: BooleanType<B1>, B2: BooleanType<B2>> and(input: RandomAccessibleInterval<Pair<B1, B2>>): RandomAccessibleInterval<B1>
		{
			return and(input, Supplier{Util.getTypeFromInterval(input).a.createVariable()})
		}

		@JvmStatic
		fun <B1: BooleanType<B1>, B2: BooleanType<B2>, B3: BooleanType<B3>> and(input: RandomAccessibleInterval<Pair<B1, B2>>, typeFactory: Supplier<B3>): RandomAccessibleInterval<B3>
		{
			return Converters.convert(input, And.pair(), typeFactory.get())
		}

		// and composite
		@JvmStatic
		fun <B: BooleanType<B>, C: Composite<B>> and(input: RandomAccessibleInterval<C>, size: Long): RandomAccessibleInterval<B>
		{
			return and(input, 0, size)
		}

		@JvmStatic
		fun <B: BooleanType<B>, C: Composite<B>> and(input: RandomAccessibleInterval<C>, start: Long, stop: Long): RandomAccessibleInterval<B>
		{
			return and(input, Supplier{Util.getTypeFromInterval(input).get(start).createVariable()}, start, stop)
		}

		@JvmStatic
		fun <B1: BooleanType<B1>, B2: BooleanType<B2>, C: Composite<B1>> and(input: RandomAccessibleInterval<C>, typeFactory: Supplier<B2>, size: Long): RandomAccessibleInterval<B2>
		{
			return and(input, typeFactory, 0, size)
		}

		@JvmStatic
		fun <B1: BooleanType<B1>, B2: BooleanType<B2>, C: Composite<B1>> and(input: RandomAccessibleInterval<C>, typeFactory: Supplier<B2>, start: Long, stop: Long): RandomAccessibleInterval<B2>
		{
			return Converters.convert(input, And.composite(start, stop), typeFactory.get())
		}

		// or pair
		@JvmStatic
		fun <B1: BooleanType<B1>, B2: BooleanType<B2>> or(input1: RandomAccessibleInterval<B1>, input2: RandomAccessible<B2>): RandomAccessibleInterval<B1>
		{
			return or(Views.interval(Views.pair(input1, input2), input1))
		}

		@JvmStatic
		fun <B1: BooleanType<B1>, B2: BooleanType<B2>, B3: BooleanType<B3>> or(input1: RandomAccessibleInterval<B1>, input2: RandomAccessible<B2>, typeFactory: Supplier<B3>): RandomAccessibleInterval<B3>
		{
			return or(Views.interval(Views.pair(input1, input2), input1), typeFactory)
		}

		@JvmStatic
		fun <B1: BooleanType<B1>, B2: BooleanType<B2>> or(input: RandomAccessibleInterval<Pair<B1, B2>>): RandomAccessibleInterval<B1>
		{
			return or(input, Supplier{Util.getTypeFromInterval(input).a.createVariable()})
		}

		@JvmStatic
		fun <B1: BooleanType<B1>, B2: BooleanType<B2>, B3: BooleanType<B3>> or(input: RandomAccessibleInterval<Pair<B1, B2>>, typeFactory: Supplier<B3>): RandomAccessibleInterval<B3>
		{
			return Converters.convert(input, Or.pair(), typeFactory.get())
		}

		// or composite
		@JvmStatic
		fun <B: BooleanType<B>, C: Composite<B>> or(input: RandomAccessibleInterval<C>, size: Long): RandomAccessibleInterval<B>
		{
			return or(input, 0, size)
		}

		@JvmStatic
		fun <B: BooleanType<B>, C: Composite<B>> or(input: RandomAccessibleInterval<C>, start: Long, stop: Long): RandomAccessibleInterval<B>
		{
			return or(input, Supplier{Util.getTypeFromInterval(input).get(start).createVariable()}, start, stop)
		}

		@JvmStatic
		fun <B1: BooleanType<B1>, B2: BooleanType<B2>, C: Composite<B1>> or(input: RandomAccessibleInterval<C>, typeFactory: Supplier<B2>, size: Long): RandomAccessibleInterval<B2>
		{
			return or(input, typeFactory, 0, size)
		}

		@JvmStatic
		fun <B1: BooleanType<B1>, B2: BooleanType<B2>, C: Composite<B1>> or(input: RandomAccessibleInterval<C>, typeFactory: Supplier<B2>, start: Long, stop: Long): RandomAccessibleInterval<B2>
		{
			return Converters.convert(input, Or.composite(start, stop), typeFactory.get())
		}
	}
}

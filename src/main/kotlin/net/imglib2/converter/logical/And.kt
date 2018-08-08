package net.imglib2.converter.logical

import net.imglib2.converter.Converter
import net.imglib2.type.BooleanType
import net.imglib2.util.Pair
import net.imglib2.view.composite.Composite

class And private constructor() {
	companion object {

		@JvmStatic
		fun <B1 : BooleanType<B1>, B2 : BooleanType<B2>, B : BooleanType<B>> pair(): Converter<Pair<B1, B2>, B> {
			return PairAnd()
		}

		@JvmStatic
		fun <B1 : BooleanType<B1>, B2 : BooleanType<B2>, C : Composite<B1>> composite(size: Long): Converter<C, B1> {
			return CompositeAnd(size)
		}

		@JvmStatic
		fun <B1 : BooleanType<B1>, B2 : BooleanType<B2>, C : Composite<B1>> composite(start: Long, stop: Long): Converter<C, B2> {
			return CompositeAnd(start, stop)
		}

		private fun <B1 : BooleanType<B1>, C : Composite<B1>> compositeAllTrue(composite: C, start: Long, stop: Long): Boolean {
			for (index in start until stop)
				if (!composite[index].get())
					return false
			return true
		}

	}

	private class PairAnd<B1 : BooleanType<B1>, B2 : BooleanType<B2>, B : BooleanType<B>> : Converter<Pair<B1, B2>, B> {
		override fun convert(input: Pair<B1, B2>, output: B) {
			output.set(input.a.get() and input.b.get())
		}
	}

	private class CompositeAnd<B1 : BooleanType<B1>, B2 : BooleanType<B2>, C : Composite<B1>>(val start: Long, val stop: Long) : Converter<C, B2> {

		constructor(size: Long) : this(0, size)

		override fun convert(input: C, output: B2) {
			output.set(compositeAllTrue(input, start, stop))
		}

	}
}

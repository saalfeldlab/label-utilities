package net.imglib2.converter.logical

import net.imglib2.Sampler
import net.imglib2.converter.Converter
import net.imglib2.converter.readwrite.SamplerConverter
import net.imglib2.img.basictypeaccess.LongAccess
import net.imglib2.type.BooleanType
import net.imglib2.type.logic.BitType
import java.util.function.Function

class Complement private constructor() {

	companion object {

		@JvmStatic
		fun <B1 : BooleanType<B1>, B2 : BooleanType<B2>> readOnly(): Converter<B1, B2> {
			return ReadOnlyConverter()
		}

		@JvmStatic
		fun <B : BooleanType<B>> readWrite(): SamplerConverter<B, BitType> {
			return readWrite(Function { BitType(it) })
		}

		@JvmStatic
		fun <B1 : BooleanType<B1>, B2 : BooleanType<B2>> readWrite(typeFactory: Function<LongAccess, B2>): SamplerConverter<B1, B2> {
			return ReadWriteConverter(typeFactory)
		}

	}

	private class ReadOnlyConverter<B1 : BooleanType<B1>, B2 : BooleanType<B2>> : Converter<B1, B2> {
		override fun convert(input: B1, output: B2) {
			output.set(!input.get())
		}
	}

	private class ReadWriteConverter<B1 : BooleanType<B1>, B2 : BooleanType<B2>>(private val typeFactory: Function<LongAccess, B2>) : SamplerConverter<B1, B2> {
		override fun convert(sampler: Sampler<out B1>): B2 {
			val access = ComplementAccess(sampler)
			return typeFactory.apply(access)
		}

		private class ComplementAccess<B : BooleanType<B>>(private val sampler: Sampler<out B>) : LongAccess {
			override fun setValue(index: Int, value: Long) {
				sampler.get().set(value == 0L)
			}

			override fun getValue(index: Int): Long {
				return if (sampler.get().get()) 0L else 1L
			}

		}

	}

}

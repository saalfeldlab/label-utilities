package org.janelia.saalfeldlab.labels

class Label private constructor() {

	companion object {

		@JvmStatic
		val BACKGROUND = 0L

		@JvmStatic
		val TRANSPARENT = -0x1L // -1L or uint64.MAX_VALUE

		@JvmStatic
		val INVALID = -0x2L // -2L or uint64.MAX_VALUE - 1

		@JvmStatic
		val OUTSIDE = -0x3L // -3L or uint64.MAX_VALUE - 2

		@JvmStatic
		val MAX_ID = -0x4L // -4L or uint64.MAX_VALUE - 3

		@JvmStatic
		fun regular(id: Long): Boolean {
			return max(id, Label.MAX_ID) == Label.MAX_ID
		}

		/**
		 * Max of two uint64 passed as long.
		 *
		 * @param a
		 * @param b
		 * @return
		 */
		@JvmStatic
		fun max(a: Long, b: Long): Long {
			return if (a + java.lang.Long.MIN_VALUE > b + java.lang.Long.MIN_VALUE) a else b
		}
	}

    enum class ReservedValue constructor(val id: Long) {
        BACKGROUND(Label.BACKGROUND),
        TRANSPARENT(Label.TRANSPARENT),
        INVALID(Label.INVALID),
        OUTSIDE(Label.OUTSIDE),
        MAX_ID(Label.MAX_ID);

		override fun toString(): String
		{
			return String.format("%s(%d)", name, id)
		}

		companion object {

			@JvmStatic
			fun fromString(str: String): ReservedValue? {
				try {
					return ReservedValue.valueOf(str.toUpperCase())
				} catch (e: IllegalArgumentException) {
					return null
				} catch (e: NullPointerException) {
					return null
				}

			}

			@JvmStatic
			fun fromId(id: Long): ReservedValue? {
				for (rv in ReservedValue.values())
					if (rv.id == id)
						return rv
				return null
			}

		}
    }
}

fun main(args: Array<String>) {
	for (name in arrayOf("background", "TRANSPARENT", "InVaLId", "OUTSIDE", "MAX_ID"))
	{
		println("$name -- ${Label.ReservedValue.fromString(name)}")
	}
}

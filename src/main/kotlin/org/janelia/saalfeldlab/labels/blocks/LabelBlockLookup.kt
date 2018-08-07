package org.janelia.saalfeldlab.labels.blocks

import net.imglib2.Interval
import net.imglib2.loops.LoopBuilder
import org.scijava.annotations.Indexable
import java.lang.annotation.*
import java.lang.annotation.Retention
import java.lang.annotation.Target
import java.util.function.BiFunction

interface LabelBlockLookup {

	fun read(level: Int, id: Long): Array<Interval>

	fun write(level: Int, id: Long, vararg intervals: Interval)

	/**
	 * Annotation for runtime discovery of compression schemes.
	 *
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@Target(ElementType.TYPE)
	@Indexable
	annotation class LookupType(val value: String)

	/**
	 * Annotation for runtime discovery of compression schemes.
	 *
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@Target(ElementType.FIELD)
	annotation class Parameter

	fun getType(): String? {
		val compressionType = javaClass.getAnnotation(LookupType::class.java)
		return compressionType?.value
	}
}

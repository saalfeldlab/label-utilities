package org.janelia.saalfeldlab.labels.blocks

import net.imglib2.Interval
import org.scijava.annotations.Indexable
import java.io.IOException
import java.lang.annotation.Inherited

interface LabelBlockLookup {

	@Throws(IOException::class)
	fun read(key: LabelBlockLookupKey): Array<Interval>

	@Throws(IOException::class)
	fun write(key: LabelBlockLookupKey, vararg intervals: Interval)

	/**
	 * Annotation for runtime discovery of compression schemes.
	 *
	 */
	@Retention(AnnotationRetention.RUNTIME)
	@Target(AnnotationTarget.CLASS)
	@Indexable
	@Inherited
	annotation class LookupType(val value: String)

	/**
	 * Annotation for runtime discovery of compression schemes.
	 *
	 */
	@Retention(AnnotationRetention.RUNTIME)
	@Target(AnnotationTarget.FIELD)
	@Inherited
	annotation class Parameter

	fun getType(): String {
		val compressionType = javaClass.getAnnotation(LookupType::class.java)
		return compressionType!!.value
	}
}

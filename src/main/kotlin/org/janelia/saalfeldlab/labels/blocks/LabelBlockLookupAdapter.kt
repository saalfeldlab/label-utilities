package org.janelia.saalfeldlab.labels.blocks

import com.google.gson.*
import org.scijava.annotations.Index
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Type
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

class LabelBlockLookupAdapter() : JsonSerializer<LabelBlockLookup>, JsonDeserializer<LabelBlockLookup> {

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private val instance = LabelBlockLookupAdapter()

		private val lookupConstructors = mutableMapOf<String, Constructor<out LabelBlockLookup>>()

		private val lookupParameters = mutableMapOf<String, MutableMap<String, Class<*>>>()


		private fun getDeclaredFields(clazz: Class<*>?, fields: MutableList<Field> = mutableListOf()): MutableList<Field> {
			if (clazz != null) {
				fields.addAll(listOf(*clazz!!.declaredFields))
				getDeclaredFields(clazz?.superclass, fields)
			}
			return fields
		}

		private fun update() {
			lookupConstructors.clear()
			lookupParameters.clear()
			val classLoader = Thread.currentThread().contextClassLoader
			val annotationIndex = Index.load(LabelBlockLookup.LookupType::class.java, classLoader)
			LOG.debug("Got annotation index {} with {} entries", annotationIndex, annotationIndex.iterator().asSequence().toList().size)

			for (item in annotationIndex) {
				LOG.debug("Looking at item {}", item.className())

				try {
					val clazz = Class.forName(item.className())
					val klazz = clazz.kotlin
					val type = (clazz.getAnnotation(LabelBlockLookup.LookupType::class.java)).value
					val constructor = clazz.getDeclaredConstructor()
					val parameters = mutableMapOf<String, Class<*>>()
					val fields = getDeclaredFields(clazz)
					LOG.debug("Got fields {} for type {}", fields, type)

					for (field in fields) {
						LOG.debug("Checking if field {} has correct annotation {}", field, field.annotations)
						if (field.getAnnotation(LabelBlockLookup.Parameter::class.java) != null) {
							parameters.put(field.name, field.type)
						}
					}

					LOG.debug("Got members {} for type {}", klazz.members, type)
					for (property in klazz.memberProperties)
					{
						LOG.debug("Checking if field {} has correct annotation {}", property, property.annotations)
						if (property.findAnnotation<LabelBlockLookup.Parameter>() != null) {
							parameters.put(property.name, property.javaField!!.type)
						}
					}

					lookupConstructors.put(type, constructor as Constructor<out LabelBlockLookup>)
					lookupParameters.put(type, parameters)
				} catch (var11: NoSuchMethodException) {
					var11.printStackTrace()
				} catch (var11: ClassCastException) {
					var11.printStackTrace()
				} catch (var11: ClassNotFoundException) {
					var11.printStackTrace()
				}

			}

			LOG.debug("Updated lookupConstructors {}", lookupConstructors)
			LOG.debug("Updated lookupParameters {}", lookupParameters)

		}

		@JvmStatic
		fun getJsonAdapter(): LabelBlockLookupAdapter {
			if (lookupParameters.size == 0) {
				update()
			}

			return instance
		}
	}


	override fun serialize(lookup: LabelBlockLookup, typeOfSrc: Type, context: JsonSerializationContext): JsonElement? {
		val type = lookup.getType()
		val clazz = lookup.javaClass
		val json = JsonObject()
		json.addProperty("type", type)
		val parameterTypes = lookupParameters.get(type)!!

		LOG.debug("Got parameter types {}", parameterTypes)

		try {
			for ((name, _) in parameterTypes.entries) {
				val field = clazz.getDeclaredField(name)
				val isAccessible = field.isAccessible
				field.isAccessible = true
				val value = field.get(lookup)
				field.isAccessible = isAccessible
				json.add(name, context.serialize(value))
			}

			LOG.debug("Serialized lookup to {}", json);
			return json
		} catch (e: SecurityException) {
			e.printStackTrace(System.err)
			return null
		} catch (e: IllegalArgumentException) {
			e.printStackTrace(System.err)
			return null
		} catch (e: IllegalAccessException) {
			e.printStackTrace(System.err)
			return null
		} catch (e: NoSuchFieldException) {
			e.printStackTrace(System.err)
			return null
		}

	}

	@Throws(JsonParseException::class)
	override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LabelBlockLookup? {
		val jsonObject = json.asJsonObject
		val jsonType = jsonObject.get("type")
		if (jsonType == null)
			return null


		val type = jsonType!!.asString
		val constructor = lookupConstructors.get(type) as Constructor<*>
		val isConstructorAccessible = constructor.isAccessible
		constructor.isAccessible = true

		try {
			val lookup = constructor.newInstance() as LabelBlockLookup
			val clazz = lookup.javaClass
			val parameterTypes = lookupParameters.get(type)!!
			val modifiersField = Field::class.java.getDeclaredField("modifiers")
			val isModifiersAccessible = modifiersField.isAccessible
			modifiersField.isAccessible = true

			for ((name, ev) in parameterTypes.entries) {
				LOG.debug("Getting name {} from object {} and type {}", name, jsonObject, ev)
				jsonObject.get(name)
				val parameter = context.deserialize<Any>(jsonObject.get(name), ev as Type)
				val field = clazz.getDeclaredField(name)
				val isAccessible = field.isAccessible
				field.isAccessible = true
				val modifiers = field.modifiers
				modifiersField.setInt(field, modifiers and -17)
				field.set(lookup, parameter)
				modifiersField.setInt(field, modifiers)
				field.isAccessible = isAccessible
			}

			modifiersField.isAccessible = isModifiersAccessible
			return lookup
		} catch (e: IllegalAccessException) {
			e.printStackTrace(System.err)
			return null
		} catch (e: IllegalArgumentException) {
			e.printStackTrace(System.err)
			return null
		} catch (e: InvocationTargetException) {
			e.printStackTrace(System.err)
			return null
		} catch (e: SecurityException) {
			e.printStackTrace(System.err)
			return null
		} catch (e: NoSuchFieldException) {
			e.printStackTrace(System.err)
			return null
		} catch (e: InstantiationException) {
			e.printStackTrace(System.err)
			return null
		}
		finally {
			constructor.isAccessible = false
		}

	}

}

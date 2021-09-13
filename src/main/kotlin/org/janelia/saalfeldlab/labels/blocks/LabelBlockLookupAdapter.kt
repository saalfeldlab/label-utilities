package org.janelia.saalfeldlab.labels.blocks

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
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
			clazz?.let {
				fields.addAll(listOf(*clazz.declaredFields))
				getDeclaredFields(clazz.superclass, fields)
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
					for (property in klazz.memberProperties) {
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
		val json = JsonObject()
		json.addProperty("type", type)
		val parameterTypes = lookupParameters[type]!!

		LOG.debug("Got parameter types {}", parameterTypes)

		try {
			parameterTypes.forEach { (paramName, _) ->
				lookup.javaClass.getDeclaredField(paramName).let { field ->
					val originalFieldAccess = field.isAccessible
					field.isAccessible = true

					val value = field[lookup]
					json.add(paramName, context.serialize(value))

					field.isAccessible = originalFieldAccess
				}
			}
			LOG.debug("Serialized lookup to {}", json)
			return json
		} catch (ex: Exception) {
			when (ex) {
				is SecurityException,
				is IllegalArgumentException,
				is IllegalAccessException,
				is NoSuchFieldError,
				-> {
					ex.printStackTrace(System.err)
					return null
				}
				else -> throw ex
			}
		}
	}

	@Throws(JsonParseException::class)
	override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LabelBlockLookup? {
		val jsonObject = json.asJsonObject
		val jsonType = jsonObject.get("type") ?: return null


		val type = jsonType.asString
		val constructor = lookupConstructors[type] as Constructor<*>

		val isConstructorAccessible = constructor.isAccessible
		constructor.isAccessible = true
		try {
			val lookup = constructor.newInstance() as LabelBlockLookup
			val parameterTypes = lookupParameters[type]

			parameterTypes?.forEach { (name, ev) ->
				LOG.debug("Getting name {} from object {} and type {}", name, jsonObject, ev)
				val parameter = context.deserialize<Any>(jsonObject.get(name), ev as Type)

				val field = lookup.javaClass.getDeclaredField(name)
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
		} catch (ex: Exception) {
			when (ex) {
				is IllegalAccessException,
				is IllegalArgumentException,
				is InvocationTargetException,
				is SecurityException,
				is NoSuchFieldException,
				is InstantiationException,
				-> {
					ex.printStackTrace(System.err)
					return null
				}
				else -> throw ex
			}
		} finally {
			constructor.isAccessible = false
		}
	}
}

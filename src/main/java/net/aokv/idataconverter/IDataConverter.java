package net.aokv.idataconverter;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFormatter;
import com.wm.data.IDataUtil;

public class IDataConverter extends Converter
{
	@SuppressWarnings(
	{ "unchecked", "rawtypes" })
	public <T> T convertToObject(final Object iData, final Class<T> objectType)
			throws IDataConversionException
	{
		if (iData == null)
		{
			return null;
		}
		if (isPrimitiveType(objectType))
		{
			if (objectType.equals(iData.getClass()))
			{
				return (T) iData;
			}
			return convertToPrimitiveType(iData, objectType);
		}
		if (objectType.isEnum())
		{
			return convertEnum(iData.toString(), (Class<? extends Enum>) objectType);
		}

		try
		{
			final Optional<CustomConverter<?>> customConverter = findCustomConverter(objectType, objectType);
			if (objectType.isArray())
			{
				return convertArray(iData, objectType, customConverter);
			}
			if (customConverter.isPresent())
			{
				return (T) customConverter.get().convertToObject((IData) iData);
			}
			return convertObject((IData) iData, objectType);
		}
		catch (final Exception e)
		{
			throw createException(iData, objectType, e);
		}
	}

	public <T> T convertToObject(
			final IData iData, final String fieldName, final Class<T> fieldType, final Class<?> elementType)
			throws IDataConversionException
	{
		if (iData == null)
		{
			return null;
		}
		if (!iData.getCursor().hasMoreData())
		{
			return null;
		}

		checkWhetherFieldExists(iData, fieldName);
		return convertFieldOrCollection(iData, fieldName, fieldType, elementType);
	}

	public <T> T convertToObject(final IData iData, final String fieldName, final Class<T> fieldType)
			throws IDataConversionException
	{
		return convertToObject(iData, fieldName, fieldType, null);
	}

	@SuppressWarnings(
	{ "unchecked", "rawtypes" })
	public <T> T convertToObject(final Object[] objects, final Class<T> collectionType, final Class<?> elementType)
			throws IDataConversionException
	{
		if (objects == null)
		{
			return null;
		}
		final Collection collection;
		if (collectionType.equals(List.class))
		{
			collection = new ArrayList<>();
		}
		else
		{
			try
			{
				collection = (Collection) collectionType.newInstance();
			}
			catch (final InstantiationException | IllegalAccessException e)
			{
				throw new IDataConversionException(e.getMessage(), e);
			}
		}
		for (final Object object : objects)
		{
			collection.add(convertToObject(object, elementType));
		}
		return (T) collection;
	}

	@SuppressWarnings("unchecked")
	private <T> T convertToPrimitiveType(final Object iData, final Class<T> objectType)
			throws IDataConversionException
	{
		if (iData.getClass().equals(objectType))
		{
			return (T) iData;
		}
		final String object = iData.toString();
		if (objectType.equals(byte.class) || objectType.equals(Byte.class))
		{
			return (T) convertToPrimitiveType(object, Byte::valueOf);
		}
		if (objectType.equals(short.class) || objectType.equals(Short.class))
		{
			return (T) convertToPrimitiveType(object, Short::valueOf);
		}
		if (objectType.equals(int.class) || objectType.equals(Integer.class))
		{
			return (T) convertToPrimitiveType(object, Integer::valueOf);
		}
		if (objectType.equals(long.class) || objectType.equals(Long.class))
		{
			return (T) convertToPrimitiveType(object, Long::valueOf);
		}
		if (objectType.equals(float.class) || objectType.equals(Float.class))
		{
			return (T) convertToPrimitiveType(object, Float::valueOf);
		}
		if (objectType.equals(double.class) || objectType.equals(Double.class))
		{
			return (T) convertToPrimitiveType(object, Double::valueOf);
		}
		if (objectType.equals(char.class) || objectType.equals(Character.class))
		{
			return (T) convertToPrimitiveType(object, IDataConverter::stringToCharacter);
		}
		if (objectType.equals(boolean.class) || objectType.equals(Boolean.class))
		{
			return (T) convertToPrimitiveType(object, Boolean::valueOf);
		}
		if (objectType.equals(Object.class))
		{
			return (T) iData;
		}
		throw new IDataConversionException(
				String.format("Could not convert <%s> to type <%s>.", iData, objectType));
	}

	private <T> T convertToPrimitiveType(final String iData, final Function<String, T> converter)
	{
		return converter.apply(iData);
	}

	private static Character stringToCharacter(final String string)
	{
		return string.charAt(0);
	}

	private <T> IDataConversionException createException(
			final Object iData, final Class<T> objectType, final Exception exception)
	{
		String object = iData.toString();
		if (iData instanceof IData)
		{
			object = new IDataFormatter().format((IData) iData);
		}
		return new IDataConversionException(
				String.format("IData could not be converted to object of class <%s>:%nError message: %s%n%s",
						objectType, exception.getMessage(), object),
				exception);
	}

	@SuppressWarnings("unchecked")
	private <T> T convertField(final IData iData, final String fieldName, final Class<T> fieldType,
			final Class<?> elementType, final Optional<CustomConverter<?>> customConverter)
			throws IDataConversionException
	{
		final IDataCursor idc = iData.getCursor();
		if (fieldType.isArray())
		{
			final Object fieldValue = IDataUtil.get(idc, fieldName);
			return convertArray(fieldValue, fieldType, customConverter);
		}
		if (customConverter.isPresent())
		{
			final CustomConverter<?> cc = customConverter.get();
			final IData fieldValue = (IData) IDataUtil.get(idc, fieldName);
			if (fieldValue == null)
			{
				return null;
			}
			return (T) cc.convertToObject(fieldValue);
		}
		return convertFieldOrCollection(iData, fieldName, fieldType, elementType);
	}

	private <T> T convertFieldOrCollection(
			final IData iData, final String fieldName, final Class<T> fieldType, final Class<?> elementType)
			throws IDataConversionException
	{
		final IDataCursor idc = iData.getCursor();
		if (elementType != null && Collection.class.isAssignableFrom(fieldType))
		{
			final Object[] field = IDataUtil.getObjectArray(idc, fieldName);
			return convertToObject(field, fieldType, elementType);
		}
		else
		{
			final Object field = IDataUtil.get(idc, fieldName);
			return convertToObject(field, fieldType);
		}
	}

	private void checkWhetherFieldExists(final IData iData, final String fieldName)
			throws IDataConversionException
	{
		final IDataCursor idc = iData.getCursor();
		while (idc.hasMoreData())
		{
			idc.next();
			if (idc.getKey().equals(fieldName))
			{
				return;
			}
		}
		throw new IDataConversionException(
				String.format("Field <%s> does not exist in IData: %s",
						fieldName, new IDataFormatter().format(iData)));
	}

	@SuppressWarnings(
	{ "rawtypes", "unchecked" })
	private <T> T convertEnum(final String enumValue, final Class<? extends Enum> enumType)
			throws IDataConversionException
	{
		return (T) Enum.valueOf(enumType, enumValue);
	}

	@SuppressWarnings("unchecked")
	private <T> T convertArray(
			final Object object, final Class<T> objectType, final Optional<CustomConverter<?>> customConverter)
			throws IDataConversionException
	{
		if (object == null)
		{
			return null;
		}
		final int length = Array.getLength(object);
		final Class<?> componentType = objectType.getComponentType();
		final Object array = Array.newInstance(componentType, length);
		for (int i = 0; i < length; i++)
		{
			Object value = null;
			if (customConverter.isPresent())
			{
				value = customConverter.get().convertToObject((IData) Array.get(object, i));
			}
			else
			{
				value = convertToObject(Array.get(object, i), componentType);
			}
			Array.set(array, i, value);
		}
		return (T) array;
	}

	private <T> T convertObject(final IData iData, final Class<T> objectType)
			throws IDataConversionException
	{
		try
		{
			final T instance = objectType.newInstance();
			convertFields(iData, objectType, instance);
			convertSetters(iData, objectType, instance);
			return instance;
		}
		catch (InstantiationException | IllegalAccessException e)
		{
			throw new IDataConversionException(e.getMessage(), e);
		}
	}

	private <T> void convertFields(final IData iData, final Class<T> objectType, final T instance)
			throws IDataConversionException
	{
		try
		{
			for (final Field field : objectType.getFields())
			{
				final String fieldName = generateFieldName(field, field.getName());
				final Class<?> fieldType = field.getType();
				final Class<?> elementType = findElementType(field, fieldType);
				final Optional<CustomConverter<?>> customConverter = findCustomConverter(field, fieldType);
				field.set(instance, convertField(iData, fieldName, fieldType, elementType, customConverter));
			}
		}
		catch (final Exception e)
		{
			throw new IDataConversionException(e.getMessage(), e);
		}
	}

	private Class<?> findElementType(final Field field, final Class<?> fieldType)
	{
		Class<?> elementType = null;
		if (Collection.class.isAssignableFrom(fieldType))
		{
			final ParameterizedType listType = (ParameterizedType) field.getGenericType();
			elementType = (Class<?>) listType.getActualTypeArguments()[0];
		}
		return elementType;
	}

	private <T> void convertSetters(final IData iData, final Class<T> objectType, final T instance)
			throws IDataConversionException
	{
		final Method[] methods = findMethodsStartingWith(getMethods(objectType), "set");
		try
		{
			for (final Method method : methods)
			{
				final Field field = findField(method, objectType);
				final String fieldName = generateFieldName(field, method.getName().substring(3));
				final Class<?> fieldType = field.getType();
				final Class<?> elementType = findElementType(field, fieldType);
				final Object fieldValue = convertToObject(iData, fieldName, fieldType, elementType);
				method.invoke(instance, fieldValue);
			}
		}
		catch (final NoSuchFieldException | IllegalAccessException | InvocationTargetException e)
		{
			throw new IDataConversionException(e.getMessage(), e);
		}
	}

}

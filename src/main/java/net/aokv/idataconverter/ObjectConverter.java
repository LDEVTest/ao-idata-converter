package net.aokv.idataconverter;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;

public class ObjectConverter extends Converter
{
	public IData convertToIData(final String key, final Object value, final Class<?> elementType)
			throws ObjectConversionException
	{
		final Object iDataValue = convertObject(value, elementType);
		return wrapInIData(key, iDataValue);
	}

	public IData convertToIData(final String key, final Object value)
			throws ObjectConversionException
	{
		return convertToIData(key, value, null);
	}

	public IData convertToIData(final Object value)
			throws ObjectConversionException
	{
		return (IData) convertObject(value);
	}

	private IData wrapInIData(final String key, final Object value)
	{
		final IData output = IDataFactory.create();
		final IDataCursor idc = output.getCursor();
		idc.insertAfter(key, value);
		idc.destroy();
		return output;
	}

	private Object convertObject(final Object object, final Class<?> elementType)
			throws ObjectConversionException
	{
		if (object == null)
		{
			return null;
		}

		final Class<?> objectType = object.getClass();

		if (isPrimitiveType(objectType))
		{
			return object;
		}
		if (objectType.isEnum())
		{
			return object.toString();
		}
		if (objectType.isArray())
		{
			return convertArray((Object[]) object);
		}
		if (Collection.class.isAssignableFrom(objectType))
		{
			return convertCollection((Collection<?>) object, elementType);
		}

		return convertClass(object);
	}

	private Object convertObject(final Object object)
			throws ObjectConversionException
	{
		return convertObject(object, null);
	}

	private Object convertArray(final Object[] object)
			throws ObjectConversionException
	{
		final int length = Array.getLength(object);
		final IData[] elements = new IData[length];
		for (int i = 0; i < length; i++)
		{
			elements[i] = convertToIData(Array.get(object, i));
		}
		return elements;
	}

	private Object convertCollection(final Collection<?> collection, final Class<?> elementType)
			throws ObjectConversionException
	{
		final Object[] array = collection.toArray(new Object[collection.size()]);
		if (array.length == 0 || isPrimitiveType(array[0].getClass()))
		{
			return convertCollectionToArray(collection, elementType);
		}
		return convertArray(array);
	}

	@SuppressWarnings("unchecked")
	private <T> T[] convertCollectionToArray(final Collection<T> collection, Class<?> elementType)
	{
		if (elementType == null)
		{
			if (collection.iterator().hasNext())
			{
				final T firstElement = collection.iterator().next();
				elementType = firstElement.getClass();
			}
			else
			{
				return (T[]) collection.toArray();
			}
		}

		final T[] array = (T[]) Array.newInstance(elementType, collection.size());
		return collection.toArray(array);
	}

	private Object convertClass(final Object object)
			throws ObjectConversionException
	{
		final IData output = IDataFactory.create();
		final IDataCursor idc = output.getCursor();

		convertFields(object, idc);
		convertGetters(object, idc);

		idc.destroy();
		return output;
	}

	private void convertFields(final Object object, final IDataCursor idc)
			throws ObjectConversionException
	{
		final Class<?> objectType = object.getClass();
		final Field[] fields = objectType.getFields();
		sortFields(fields);
		try
		{
			for (final Field field : fields)
			{
				final String fieldName = generateFieldName(field, field.getName());
				final Object fieldValue = field.get(object);
				final Class<?> elementType = getParameterType(field);
				final Object fieldData = convertObject(fieldValue, elementType);
				idc.insertAfter(fieldName, fieldData);
			}
		}
		catch (IllegalArgumentException | IllegalAccessException e)
		{
			throw new ObjectConversionException(e.getMessage(), e);
		}
	}

	protected Class<?> getParameterType(final Field field)
	{
		Class<?> elementType = null;
		final Type type = field.getGenericType();
		if (type instanceof ParameterizedType)
		{
			final ParameterizedType pType = (ParameterizedType) type;
			final Type parameterType = pType.getActualTypeArguments()[0];
			elementType = (Class<?>) parameterType;
		}
		return elementType;
	}

	private void convertGetters(final Object object, final IDataCursor idc)
			throws ObjectConversionException
	{
		final Class<?> objectType = object.getClass();
		final Method[] methods = findMethodsStartingWith(getMethods(objectType), "get");
		try
		{
			for (final Method method : methods)
			{
				final Field field = findField(method, objectType);
				final String fieldName = generateFieldName(field, method.getName().substring(3));
				final Object fieldValue = method.invoke(object);
				final Object fieldData = convertObject(fieldValue);
				idc.insertAfter(fieldName, fieldData);
			}
		}
		catch (IllegalAccessException | NoSuchFieldException | InvocationTargetException e)
		{
			throw new ObjectConversionException(e.getMessage(), e);
		}
	}

}
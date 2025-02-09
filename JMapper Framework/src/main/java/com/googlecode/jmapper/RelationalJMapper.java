/**
 * Copyright (C) 2012 - 2016 Alessandro Vurro.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.jmapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.googlecode.jmapper.annotations.Annotation;
import com.googlecode.jmapper.annotations.JGlobalMap;
import com.googlecode.jmapper.annotations.JMap;
import com.googlecode.jmapper.api.IRelationalJMapper;
import com.googlecode.jmapper.api.JMapperAPI;
import com.googlecode.jmapper.api.enums.MappingType;
import com.googlecode.jmapper.api.enums.NullPointerControl;
import com.googlecode.jmapper.config.Error;
import com.googlecode.jmapper.config.JmapperLog;
import com.googlecode.jmapper.enums.ChooseConfig;
import com.googlecode.jmapper.exceptions.ClassNotMappedException;
import com.googlecode.jmapper.exceptions.MappingErrorException;
import com.googlecode.jmapper.xml.Attribute;
import com.googlecode.jmapper.xml.Global;
import com.googlecode.jmapper.xml.SimplyAttribute;
import com.googlecode.jmapper.xml.XML;

import static com.googlecode.jmapper.util.ClassesManager.*;
import static com.googlecode.jmapper.util.GeneralUtility.*;

/**
 * RelationalJMapper takes as input one configured Class.<br>
 * For configured Class, we mean a Class that contains fields configured with annotation or XML.<br>
 * It is mandatory that all fields have defined classes.<br>
 * For example:
 * <pre><code>
 * class Destination {
 *
 *  &#64;JMap(  attributes={"field1Class1","field1Class2","field1Class3"},
 *   	  classes={Class1.class,Class2.class,Class3.class})
 *	 private String field1;
 *  &#64;JMap(  attributes={"field2Class1","field2Class2","field2Class3"},
 *   	  classes={Class1.class,Class2.class,Class3.class})
 *	 private String field2;
 *  &#64;JMap(  attributes={"field3Class1","field3Class2","field3Class3"},
 *   	  classes={Class1.class,Class2.class,Class3.class})
 *	 private String field3;
 *
 *  // getter and setter
 * }
 * </code></pre>
 * then invoke the methods manyToOne or oneToMany.<br><br>
 * With manyToOne method, the mapped classes are the source and the configured Class is the destination.<br>
 * manyToOne example:
 * <pre><code>
 *         AnnotatedClass manyToOne = null;
 *	Class1 class1 = new Class1("field1Class1", "field2Class1", "field3Class1");
 *	Class2 class2 = new Class2("field1Class2", "field2Class2", "field3Class2");
 *	Class3 class3 = new Class3("field1Class3", "field2Class3", "field3Class3");
 *
 *	RelationalJMapper&lt;AnnotatedClass&gt; rm = new RelationalJMapper&lt;AnnotatedClass&gt;(AnnotatedClass.class);
 *
 *	manyToOne = rm.manyToOne(class1);
 *	manyToOne = rm.manyToOne(class2);
 *	manyToOne = rm.manyToOne(class3);
 *	</code></pre>
 * With oneToMany method, the mapped classes are the destination and the configured Class is the source.<br>
 * oneToMany example:
 * <pre><code>
 *         AnnotatedClass annotatedClass = new AnnotatedClass("field1", "field2", "field3");
 *
 *	RelationalJMapper&lt;AnnotatedClass&gt; rm = new RelationalJMapper&lt;AnnotatedClass&gt;(AnnotatedClass.class);
 *
 *	Class1 class1 = rm.setDestinationClass(Class1.class).oneToMany(annotatedClass);
 *	Class2 class2 = rm.setDestinationClass(Class2.class).oneToMany(annotatedClass);
 *	Class3 class3 = rm.setDestinationClass(Class3.class).oneToMany(annotatedClass);
 *  </code></pre>
 * For more information see {@link RelationalJMapper#manyToOne manyToOne} and {@link RelationalJMapper#oneToMany oneToMany} Methods<br>
 *
 * @author Alessandro Vurro
 *
 * @param <T> Type of Configured Class
 */
@SuppressWarnings({"rawtypes","unchecked"})
public final class RelationalJMapper<T> implements IRelationalJMapper<T>{

	/** Configured Class*/
	private Class<T> configuredClass;

	/** map that has the target class names as keys and relative JMapper as values */
	private final HashMap<String,JMapper> relationalOneToManyMapper = new HashMap<String, JMapper>();

	/** map that has the target class names as keys and relative JMapper as values */
	private final HashMap<String,JMapper> relationalManyToOneMapper = new HashMap<String, JMapper>();;

	/**
	 * Takes in input only the annotated Class
	 * @param configuredClass configured class
	 */
	public RelationalJMapper(final Class<T> configuredClass){
		this.configuredClass = configuredClass;
		try{
			init();
		}catch(ClassNotMappedException e){
			JmapperLog.error(e);
		}catch(MappingErrorException e){
			JmapperLog.error(e);
		}
	}

	/**
	 * Takes in input the configured Class and the configuration in API format.
	 * @param configuredClass configured class
	 * @param jmapperAPI the configuration
	 */
	public RelationalJMapper(final Class<T> configuredClass, JMapperAPI jmapperAPI){
		this(configuredClass, jmapperAPI.toXStream().toString());
	}

	/**
	 * Takes in input only the configured Class and the xml mapping path or the xml as String format.
	 * @param configuredClass configured class
	 * @param xmlPath XML path or xml as String
	 */
	public RelationalJMapper(final Class<T> configuredClass, String xmlPath){
		this.configuredClass = configuredClass;

		try {
			init(xmlPath);
		} catch (MalformedURLException e) {
			JmapperLog.error(e);
		} catch (IOException e) {
			JmapperLog.error(e);
		}catch(ClassNotMappedException e){
			JmapperLog.error(e);
		}catch(MappingErrorException e){
			JmapperLog.error(e);
		}
	}

	/**
	 * This method initializes relational maps starting from XML.
	 * @param xmlPath xml path
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	private void init(String xmlPath) throws MalformedURLException, IOException{

		XML xml = new XML(true, xmlPath);
		if(!xml.isInheritedMapped(configuredClass))
			Error.classNotMapped(configuredClass);

		for (Class<?> classe :getClasses(xml)){
			relationalManyToOneMapper.put(classe.getName(), new JMapper(configuredClass, classe,ChooseConfig.DESTINATION, xmlPath));
			relationalOneToManyMapper.put(classe.getName(), new JMapper(classe, configuredClass,ChooseConfig.SOURCE, xmlPath));
		}
	}

	/**
	 * Returns all Target Classes contained in the XML.
	 * @param xml xml to analyze
	 * @return target classes
	 */
	private Set<Class<?>> getClasses(XML xml){
		HashSet<Class<?>> result = new HashSet<Class<?>>();

		// in case of override only the last global configuration must be analyzed
		Global global = null;

		for (Class<?> clazz : getAllsuperClasses(configuredClass)) {
			// only if global configuration is null will be searched global configuration on super classes
			if(isNull(global)){

				global = xml.loadGlobals().get(clazz.getName());
				if(!isNull(global)){
					addClasses(global.getClasses(),result);
					if(global.getExcluded()!=null)
						for (Attribute attribute : xml.loadAttributes().get(clazz.getName()))
							for (String fieldName : global.getExcluded())
								if(attribute.getName().equals(fieldName))
									addClasses(attribute.getClasses(),result,attribute.getName());
				}
			}

			List<Attribute> attributes = xml.loadAttributes().get(clazz.getName());

			if(!isNull(attributes))
				for (Attribute attribute : attributes)
					if(    isNull(global)
						|| isPresent(global.getExcluded(), attribute.getName())
					    || ( !isEmpty(global.getAttributes())
						     && !isPresent(global.getAttributes(), new SimplyAttribute(attribute.getName()))
						   )
					   )
					addClasses(attribute.getClasses(),result,attribute.getName());
		}

		return result;
	}

	/**
	 * This method initializes relational maps
	 */
	private void init(){

		if(!Annotation.isInheritedMapped(configuredClass))
			Error.classNotMapped(configuredClass);

		for (Class<?> classe :getClasses()){
			relationalManyToOneMapper.put(classe.getName(), new JMapper(configuredClass, classe,ChooseConfig.DESTINATION));
			relationalOneToManyMapper.put(classe.getName(), new JMapper(classe, configuredClass,ChooseConfig.SOURCE));
		}
	}

	/**
	 * Returns all Target Classes
	 * @return a List of Target Classes
	 */
	private Set<Class<?>> getClasses() {
		HashSet<Class<?>> result = new HashSet<Class<?>>();

		// in case of override only the last global configuration must be analyzed
		JGlobalMap jGlobalMap = null;

		for (Class<?> clazz : getAllsuperClasses(configuredClass)) {

			// only if global configuration is null will be searched global configuration on super classes
			if(isNull(jGlobalMap)){

				jGlobalMap = clazz.getAnnotation(JGlobalMap.class);
				//if the field configuration is defined in the global map
				if(!isNull(jGlobalMap)){
					addClasses(jGlobalMap.classes(),result);
//					if(!isNull(jGlobalMap.excluded()))
//						for (Field field : getListOfFields(configuredClass)){
//							JMap jMap = field.getAnnotation(JMap.class);
//							if(!isNull(jMap))
//								for (String fieldName : jGlobalMap.excluded())
//									if(field.getName().equals(fieldName))
//										addClasses(jMap.classes(),result,field.getName());
//						}
//
//					return result;
				}
			}
		}

		for (Field field : getListOfFields(configuredClass)){
			JMap jMap = field.getAnnotation(JMap.class);
			if(!isNull(jMap)) addClasses(jMap.classes(),result,field.getName());
		}

		return result;
	}

	/**
	 * Adds to the result parameter all classes that aren't present in it
	 * @param classes classes to control
	 * @param result List to enrich
	 * @param fieldName name of file, only for control purpose
	 */
	private void addClasses(Class<?>[] classes, HashSet<Class<?>> result, String fieldName){

		if(classes == null || classes.length==0)
			Error.classesAbsent(fieldName, configuredClass);

		for (Class<?> classe : classes)	result.add(classe);
	}

	/**
	 * Adds to the result parameter all classes that aren't present in it
	 * @param classes classes to control
	 * @param result List to enrich
	 */
	private void addClasses(Class<?>[] classes, HashSet<Class<?>> result){

		if(isNull(classes) || classes.length==0)
			Error.globalClassesAbsent(configuredClass);

		for (Class<?> classe : classes)	result.add(classe);
	}
	/**
	 * Returns a new instance of Class given as input, managing also the exception.
	 * @param exception exception to handle
	 * @param clazz class to instantiate
	 * @return a new instance of Class given as input
	 */
	private <I> I logAndReturnNull(Exception exception){
		JmapperLog.error(exception);
		return null;
	}

	/**
	 * This method verifies that the destinationClass exists.
	 * @param exception exception to handle
	 * @param clazz class to check
	 * @return a new instance of Class given as input
	 */
	private <I> I destinationClassControl(Exception exception, Class<I> clazz){
		try{
			if(clazz == null)throw new IllegalArgumentException("it's mandatory define the destination class");
		}catch (Exception e) {JmapperLog.error(e);return null;}
		return logAndReturnNull(exception);
	}
	/**
	 * Returns the desired JMapper instance.
	 * @param map the map of relationships
	 * @param source key of the map
	 * @return the instance of JMapper
	 */
	private <D,S> JMapper<D,S> getJMapper(HashMap<String,JMapper> map,Object source){
		Class<?> sourceClass = source instanceof Class?((Class<?>)source):source.getClass();
		JMapper<D,S> jMapper = map.get(sourceClass.getName());
		if(jMapper == null) Error.classNotMapped(source, configuredClass);
		return jMapper;
	}

	/**
	 * This method returns a new instance of Configured Class with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td><code>SOURCE</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td><code>ALL_FIELDS</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td><code>ALL_FIELDS</code></td>
	 * </tr>
	 * </table>
	 * @param source instance of Target Class type that contains the data
	 * @return new instance of Configured Class
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <S> T manyToOne(final S source) {
		try{ return this.<T,S>getJMapper(relationalManyToOneMapper,source).getDestination(source); }
		catch (Exception e) { return logAndReturnNull(e);}
	}

	/**
	 * This method returns a new instance of Configured Class with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td><code>NOT_ANY</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td><code>ALL_FIELDS</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td><code>ALL_FIELDS</code></td>
	 * </tr>
	 * </table>
	 * @param source instance of Target Class type that contains the data
	 * @return new instance of Configured Class
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <S> T manyToOneWithoutControl(final S source) {
		try{ return this.<T,S>getJMapper(relationalManyToOneMapper,source).getDestinationWithoutControl(source); }
		catch (Exception e) { return logAndReturnNull(e); }
	}

	/**
	 * This Method returns the configured instance given in input enriched with data contained in source given in input<br>
	 * with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td><code>ALL</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td><code>ALL_FIELDS</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td><code>ALL_FIELDS</code></td>
	 * </tr>
	 * </table>
	 * @param destination instance of Configured Class type to enrich
	 * @param source instance of Target Class type that contains the data
	 * @return destination enriched
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <S> T manyToOne(T destination,final S source) {
		try{ return this.<T,S>getJMapper(relationalManyToOneMapper,source).getDestination(destination,source); }
		catch (Exception e) { return logAndReturnNull(e); }
	}

	/**
	 * This Method returns the configured instance given in input enriched with data contained in source given in input<br>
	 * with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td><code>NOT_ANY</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td><code>ALL_FIELDS</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td><code>ALL_FIELDS</code></td>
	 * </tr>
	 * </table>
	 * @param destination instance of Configured Class type to enrich
	 * @param source instance of Target Class type that contains the data
	 * @return destination enriched
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <S> T manyToOneWithoutControl(T destination,final S source) {
		try{ return this.<T,S>getJMapper(relationalManyToOneMapper,source).getDestinationWithoutControl(destination,source); }
		catch (Exception e) { return logAndReturnNull(e); }
	}

	/**
	 * This method returns a new instance of Configured Class with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td><code>SOURCE</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td><code>ALL_FIELDS</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td>mtSource</td>
	 * </tr>
	 * </table>
	 * @param source instance of Target Class type that contains the data
	 * @param mtSource type of mapping of source instance
	 * @return new instance of Configured Class
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <S> T manyToOne(final S source,final MappingType mtSource) {
		try{ return this.<T,S>getJMapper(relationalManyToOneMapper,source).getDestination(source,mtSource); }
		catch (Exception e) { return logAndReturnNull(e); }
	}

	/**
	 * This method returns a new instance of Configured Class with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td>nullPointerControl</td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td><code>ALL_FIELDS</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td>mtSource</td>
	 * </tr>
	 * </table>
	 * @param source instance of Target Class type that contains the data
	 * @param nullPointerControl type of null pointer control
	 * @param mtSource type of mapping of source instance
	 * @return new instance of Configured Class
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <S> T manyToOne(final S source,final NullPointerControl nullPointerControl,final MappingType mtSource) {
		try{ return this.<T,S>getJMapper(relationalManyToOneMapper,source).getDestination(source,nullPointerControl,mtSource); }
		catch (Exception e) { return logAndReturnNull(e); }
	}

	/**
	 * This Method returns the configured instance given in input enriched with data contained in source given in input<br>
	 * with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td><code>ALL</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td>mtDestination</td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td>mtSource</td>
	 * </tr>
	 * </table>
	 * @param destination instance of Configured Class type to enrich
	 * @param source instance of Target Class type that contains the data
	 * @param mtDestination type of mapping of destination instance
	 * @param mtSource type of mapping of source instance
	 * @return destination enriched
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <S> T manyToOne(T destination,final S source,final MappingType mtDestination,final MappingType mtSource) {
		try{ return this.<T,S>getJMapper(relationalManyToOneMapper,source).getDestination(destination, source, mtDestination,mtSource); }
		catch (Exception e) { return logAndReturnNull(e); }
	}

	/**
	 * This Method returns the configured instance given in input enriched with data contained in source given in input<br>
	 * with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td>nullPointerControl</td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td>mtDestination</td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td>mtSource</td>
	 * </tr>
	 * </table>
	 * @param destination instance of Configured Class type to enrich
	 * @param source instance of Target Class type that contains the data
	 * @param nullPointerControl type of null pointer control
	 * @param mtDestination type of mapping of destination instance
	 * @param mtSource type of mapping of source instance
	 * @return destination enriched
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <S> T manyToOne(T destination,final S source,final NullPointerControl nullPointerControl,final MappingType mtDestination,final MappingType mtSource) {
		try{ return this.<T,S>getJMapper(relationalManyToOneMapper,source).getDestination(destination, source, nullPointerControl, mtDestination, mtSource); }
		catch (Exception e) { return logAndReturnNull(e); }
	}

	/**
	 * This method returns a new instance of Target Class with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td><code>SOURCE</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td><code>ALL_FIELDS</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td><code>ALL_FIELDS</code></td>
	 * </tr>
	 * </table>
	 * @param destinationClass class to create
	 * @param source instance of Configured Class that contains the data
	 * @return new instance of Target Class
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <D> D oneToMany(Class<D> destinationClass, final T source) {
		try{ return this.<D,T>getJMapper(relationalOneToManyMapper,destinationClass).getDestination(source); }
		catch (Exception e) { return (D) this.destinationClassControl(e,destinationClass); }
	}


	/**
	 * This method returns a new instance of Target Class with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td><code>NOT_ANY</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td><code>ALL_FIELDS</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td><code>ALL_FIELDS</code></td>
	 * </tr>
	 * </table>
	 * @param source instance of Configured Class that contains the data
	 * @return new instance of Target Class
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <D> D oneToManyWithoutControl(Class<D> destinationClass, final T source) {
		try{ return this.<D,T>getJMapper(relationalOneToManyMapper,destinationClass).getDestinationWithoutControl(source); }
		catch (Exception e) { return (D) this.destinationClassControl(e,destinationClass); }
	}

	/**
	 * This Method returns the destination given in input enriched with data contained in source given in input<br>
	 * with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td><code>ALL</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td><code>ALL_FIELDS</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td><code>ALL_FIELDS</code></td>
	 * </tr>
	 * </table>
	 * @param destination instance of Target Class to enrich
	 * @param source instance of Configured Class that contains the data
	 * @return destination enriched
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <D> D oneToMany(D destination,final T source) {
		try{ return this.<D,T>getJMapper(relationalOneToManyMapper,destination.getClass()).getDestination(destination,source); }
		catch (Exception e) { return this.logAndReturnNull(e); }
	}

	/**
	 * This Method returns the destination given in input enriched with data contained in source given in input<br>
	 * with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td><code>NOT_ANY</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td><code>ALL_FIELDS</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td><code>ALL_FIELDS</code></td>
	 * </tr>
	 * </table>
	 * @param destination instance of Target Class to enrich
	 * @param source instance of Configured Class that contains the data
	 * @return destination enriched
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <D> D oneToManyWithoutControl(D destination,final T source) {
		try{ return this.<D,T>getJMapper(relationalOneToManyMapper,destination.getClass()).getDestinationWithoutControl(destination,source); }
		catch (Exception e) { return this.logAndReturnNull(e); }
	}

	/**
	 * This method returns a new instance of Target Class with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td><code>SOURCE</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td><code>ALL_FIELDS</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td>mtSource</td>
	 * </tr>
	 * </table>
	 * @param destinationClass class to create
	 * @param source instance of Configured Class that contains the data
	 * @param mtSource type of mapping of source instance
	 * @return new instance of Target Class
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <D> D oneToMany(Class<D> destinationClass, final T source,final MappingType mtSource) {
		try{ return this.<D,T>getJMapper(relationalOneToManyMapper,destinationClass).getDestination(source,mtSource); }
		catch (Exception e) { return (D) this.destinationClassControl(e,destinationClass); }
	}

	/**
	 * This method returns a new instance of Target Class with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td>nullPointerControl</td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td><code>ALL_FIELDS</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td>mtSource</td>
	 * </tr>
	 * </table>
	 * @param source instance of Configured Class that contains the data
	 * @param nullPointerControl type of control
	 * @param mtSource type of mapping of source instance
	 * @return new instance of Target Class
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <D> D oneToMany(Class<D> destinationClass, final T source,final NullPointerControl nullPointerControl,final MappingType mtSource) {
		try{ return this.<D,T>getJMapper(relationalOneToManyMapper,destinationClass).getDestination(source,nullPointerControl,mtSource); }
		catch (Exception e) { return (D) this.destinationClassControl(e,destinationClass); }
	}

	/**
	 * This Method returns the destination given in input enriched with data contained in source given in input<br>
	 * with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td><code>ALL</code></td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td>mtDestination</td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td>mtSource</td>
	 * </tr>
	 * </table>
	 * @param destination instance of Target Class to enrich
	 * @param source instance of Configured Class that contains the data
	 * @param mtDestination type of mapping of destination instance
	 * @param mtSource type of mapping of source instance
	 * @return destination enriched
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <D> D oneToMany(D destination,final T source,final MappingType mtDestination,final MappingType mtSource) {
		try{ return this.<D,T>getJMapper(relationalOneToManyMapper,destination.getClass()).getDestination(destination, source, mtDestination,mtSource); }
		catch (Exception e) { return this.logAndReturnNull(e); }
	}

	/**
	 * This Method returns the destination given in input enriched with data contained in source given in input<br>
	 * with this setting:
	 * <table summary ="">
	 * <tr>
	 * <td><code>NullPointerControl</code></td><td>nullPointerControl</td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Destination</td><td>mtDestination</td>
	 * </tr><tr>
	 * <td><code>MappingType</code> of Source</td><td>mtSource</td>
	 * </tr>
	 * </table>
	 * @param destination instance of Target Class to enrich
	 * @param source instance of Configured Class that contains the data
	 * @param nullPointerControl type of null pointer control
	 * @param mtDestination type of mapping of destination instance
	 * @param mtSource type of mapping of source instance
	 * @return destination enriched
	 * @see NullPointerControl
	 * @see MappingType
	 */
	public <D> D oneToMany(D destination,final T source,final NullPointerControl nullPointerControl,final MappingType mtDestination,final MappingType mtSource) {
		try{ return this.<D,T>getJMapper(relationalOneToManyMapper,destination.getClass()).getDestination(destination, source, nullPointerControl, mtDestination, mtSource);}
		catch (Exception e) { return this.logAndReturnNull(e); }
	}
}

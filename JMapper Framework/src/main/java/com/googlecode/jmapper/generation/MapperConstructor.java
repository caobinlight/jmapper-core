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

package com.googlecode.jmapper.generation;

import static com.googlecode.jmapper.api.enums.MappingType.ALL_FIELDS;
import static com.googlecode.jmapper.api.enums.MappingType.ONLY_NULL_FIELDS;
import static com.googlecode.jmapper.api.enums.MappingType.ONLY_VALUED_FIELDS;
import static com.googlecode.jmapper.api.enums.NullPointerControl.ALL;
import static com.googlecode.jmapper.api.enums.NullPointerControl.DESTINATION;
import static com.googlecode.jmapper.api.enums.NullPointerControl.NOT_ANY;
import static com.googlecode.jmapper.api.enums.NullPointerControl.SOURCE;
import static com.googlecode.jmapper.util.GeneralUtility.newLine;
import static com.googlecode.jmapper.util.GeneralUtility.write;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.googlecode.jmapper.IMapper;
import com.googlecode.jmapper.api.enums.MappingType;
import com.googlecode.jmapper.api.enums.NullPointerControl;
import com.googlecode.jmapper.config.Error;
import com.googlecode.jmapper.enums.ChooseConfig;
import com.googlecode.jmapper.generation.beans.Method;
import com.googlecode.jmapper.operations.AGeneralOperation;
import com.googlecode.jmapper.operations.OperationHandler;
import com.googlecode.jmapper.operations.complex.AComplexOperation;
import com.googlecode.jmapper.operations.simple.ASimpleOperation;
import com.googlecode.jmapper.xml.XML;

/**
 * MapperConstructor builds all combinations of possible mappings between classes Source and Destination.<br>
 *
 * @author Alessandro Vurro
 *
 */
public class MapperConstructor extends MapperConstructorAccessor{

	/**
	 * Returns a Map where the keys are the mappings names and relative values are the mappings.
	 * @return a Map with all mapping combinations
	 */
	public Map<String,String> getMappings(){

		HashMap<String, String> mappings      = new HashMap<String, String> ();
		HashMap<String, Boolean> destInstance = new HashMap<String, Boolean>();

		String s = "V";

		destInstance.put("null", true  );
		destInstance.put("v"   , false );

		HashMap<String, NullPointerControl> nullPointer = new HashMap<String, NullPointerControl>();

		nullPointer.put("Not", NOT_ANY     );
		nullPointer.put("All", ALL         );
		nullPointer.put("Des", DESTINATION );
		nullPointer.put("Sou", SOURCE      );

		HashMap<String, MappingType> mapping = new HashMap<String, MappingType>();

		mapping.put("All"   , ALL_FIELDS         );
		mapping.put("Valued", ONLY_VALUED_FIELDS );
		mapping.put("Null"  , ONLY_NULL_FIELDS   );

		java.lang.reflect.Method[] methods = IMapper.class.getDeclaredMethods();

		for (Entry<String, Boolean> d : destInstance.entrySet())
			for (Entry<String, NullPointerControl> npc : nullPointer.entrySet())
				for (Entry<String, MappingType> mtd : mapping.entrySet())
					for (Entry<String, MappingType> mts : mapping.entrySet()) {
						String methodName = d.getKey()+s+npc.getKey()+mtd.getKey()+mts.getKey();
						for (java.lang.reflect.Method method : methods)
							if(method.getName().equals(methodName))
								mappings.put(methodName, wrappedMapping(d.getValue(),npc.getValue(),mtd.getValue(),mts.getValue()));}

		mappings.put("get", "return null;"+newLine);
		return mappings;
	}

	/**
	 * This method adds the Null Pointer Control to mapping created by the mapping method.
	 * wrapMapping is used to wrap the mapping returned by mapping method.
	 *
	 * @param makeDest true if destination is a new instance, false otherwise
	 * @param npc a NullPointerControl chosen
	 * @param mtd Mapping Type of destination
	 * @param mts Mapping Type of source
	 * @return a String that contains the mapping
	 * @see MapperConstructor#write
	 * @see NullPointerControl
	 * @see MappingType
	 */
	private String wrappedMapping(boolean makeDest,NullPointerControl npc,MappingType mtd,MappingType mts){
		String sClass = source.getName();
		String dClass = destination.getName();

		String str = (makeDest?"   "+sClass+" "+stringOfGetSource     +" = ("+sClass+") $1;"
				              :"   "+dClass+" "+stringOfGetDestination+" = ("+dClass+") $1;"+newLine+
				               "   "+sClass+" "+stringOfGetSource     +" = ("+sClass+") $2;")+newLine;

		switch(npc){
		  case SOURCE: 	    str +="if("+stringOfGetSource+"!=null){"     +newLine; break;
		  case DESTINATION: str +="if("+stringOfGetDestination+"!=null){"+newLine; break;
		  case ALL:	        str +="if("+stringOfGetSource+"!=null && "+stringOfGetDestination+"!=null){"+newLine;break;
		  default: break;
		}

		str +=     mapping(makeDest,mtd,mts)          + newLine
			+  "   return "+stringOfSetDestination+";"+ newLine;

		return (npc != NOT_ANY) ? str +=  "}"             + newLine
				                      +   " return null;" + newLine
				                : str;
	}

	private String newInstance(Class<?> destinationClass, String destinationField){

		String destinationClassName = destinationClass.getName();
		String emptyConstructor = "";

		try{
			destinationClass.newInstance();
			emptyConstructor =
			   write("else{",newLine,
					 "   ",destinationField," = new ",destinationClassName,"();",newLine,
			         "   }",newLine);
		}catch(Exception e){
			emptyConstructor =
			   write("else{",newLine,
			         "   com.googlecode.jmapper.config.Error#absentFactoryAndEmptyConstructor(\"",destinationClass.getSimpleName(),"\");",newLine,
					 "   }",newLine);
		}
		//TODO incaso di classi anidate non va!
	    return write("   ",destinationClassName," ",destinationField," = null;",newLine,
				     "   if(super.getDestinationFactory()!=null){",newLine,
				     "   ", destinationField," = (",destinationClassName,") super.getDestinationFactory().make();",newLine,
				     "   }",emptyConstructor);
	}
	/**
	 * This method writes the mapping based on the value of the three MappingType taken in input.
	 *
	 * @param makeDest true if destination is a new instance, false otherwise
	 * @param mtd mapping type of destination
	 * @param mts mapping type of source
	 * @return a String that contains the mapping
	 */
	public StringBuilder mapping(boolean makeDest,MappingType mtd,MappingType mts){

		StringBuilder sb = new StringBuilder();

		if(isNullSetting(makeDest, mtd, mts, sb)) return sb;

		if(makeDest)
			sb.append(newInstance(destination, stringOfSetDestination));

		for (ASimpleOperation simpleOperation : simpleOperations)
			sb.append(setOperation(simpleOperation,mtd,mts).write());

		for (AComplexOperation complexOperation : complexOperations)
			sb.append(setOperation(complexOperation,mtd,mts).write(makeDest));

		return sb;
	}

	/**
	 * Setting common to all operations.
	 *
	 * @param operation operation to configure
	 * @param mtd mapping type of destination
	 * @param mts mapping type of source
	 * @return operation configured
	 */
	private <T extends AGeneralOperation>T setOperation(T operation,MappingType mtd,MappingType mts){
		operation.setMtd(mtd).setMts(mts)
		         .initialDSetPath(stringOfSetDestination)
			     .initialDGetPath(stringOfGetDestination)
			     .initialSGetPath(stringOfGetSource);

		return operation;
	}

	/**
	 * if it is a null setting returns the null mapping
	 * @param makeDest true if destination is a new instance
	 * @param mtd mapping type of destination
	 * @param mts mapping type of source
	 * @param result StringBuilder used for mapping
	 * @return true if operation is a null setting, false otherwise
	 */
	private boolean isNullSetting(boolean makeDest,MappingType mtd,MappingType mts,StringBuilder result){
		if( 	makeDest
			&& (mtd == ALL_FIELDS||mtd == ONLY_VALUED_FIELDS)
			&&	mts == ONLY_NULL_FIELDS){
			result.append("   "+stringOfSetDestination+"(null);"+newLine);
			return true;
		}
		return false;
	}

   /**
	 * MapperConstructor takes in input all informations that need for to write the mappings.
	 *
	 * @param aDestination Type of Destination
	 * @param aSource Type of Source
	 * @param aStringOfSetDestination set destination String
	 * @param aStringOfGetDestination get destination String
	 * @param aStringOfGetSource get source String
	 * @param cc config choosen
	 * @param xml xml object
	 * @param dynamicMethodsToWrite dynamic methods to write (if defined)
	 */
	public MapperConstructor(Class<?> aDestination, Class<?> aSource,String aStringOfSetDestination,String aStringOfGetDestination,String aStringOfGetSource,ChooseConfig cc, XML xml, Set<Method>	dynamicMethodsToWrite) {
    		this(aDestination,aSource,cc,xml,dynamicMethodsToWrite);
    		stringOfSetDestination = aStringOfSetDestination;
    		stringOfGetDestination = aStringOfGetDestination;
    		stringOfGetSource = aStringOfGetSource;
    }

    /**
	 * MapperConstructor takes in input all informations that need to write the mappings.
	 *
	 * @param aDestination Type of Destination
	 * @param aSource Type of Source
	 * @param configChoice configuration chosen from user
	 * @param xml xml object
	 * @param dynamicMethodsToWrite dynamic methods to write (if defined)
	 */
    public MapperConstructor(Class<?> aDestination, Class<?> aSource,ChooseConfig configChoice, XML xml,Set<Method> dynamicMethodsToWrite){

    	// definition of the instance variables
    	destination = aDestination;
    	source = aSource;

    	// configuration chosen, null if not declared
    	ChooseConfig configObtained = searchConfig(configChoice, xml);

    	// if the configuration was not found
    	if(notFound(configObtained)){

    		Class<?> clazz = configChoice == ChooseConfig.SOURCE?source:destination;

    		// and the developer doesn't declared the configuration
    		if(notDeclared(configChoice))

    			// not found any configuration in the xml file declared
	    		if(xml.exists()) Error.configNotPresent(destination, source ,xml);
	    	    // xml configuration file doesn't exist
	    		else   			 Error.classesNotConfigured(destination, source);

	    	// or the chosen configuration was not found
	    	if(isDeclared(configChoice))

	    		// not found the clazz configuration in the xml file declared
	    		if(xml.exists()) Error.configNotPresent(clazz, xml);
	    		// not found the clazz configuration
	    		else             Error.classNotConfigured(clazz);
    	}

    	// creation of the operations handler
		OperationHandler operationHandler = new OperationHandler(destination, source, configObtained, xml);

		// Loading structures (simpleOperations and complexOperations) that will be used for writing mapping
		operationHandler.loadStructures(dynamicMethodsToWrite);

		simpleOperations = operationHandler.getSimpleOperations();
		complexOperations = operationHandler.getComplexOperations();
	}

	/**
	 * The algorithm is recursive, the setting of the name needs to be done externally.
	 * @param name the mapper class name
	 * @return this instance
	 * */
	public MapperConstructor setMapperName(String name){
		mapperName = name;
		return this;
	}
}

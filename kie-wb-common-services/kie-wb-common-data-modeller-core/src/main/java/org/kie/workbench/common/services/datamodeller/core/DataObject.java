/**
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.workbench.common.services.datamodeller.core;

import java.util.List;
import java.util.Map;

public interface DataObject extends HasName, HasPackageName, HasClassName, HasAnnotations {

    List<String> getImports();

    boolean hasSuperClass();

    String getSuperClassName();

    void setSuperClassName(String superClass);

    Map<String, ObjectProperty> getProperties();
    
    ObjectProperty addProperty(String name, String className);

    ObjectProperty addProperty(String name, String className, int modifiers);

    ObjectProperty addProperty(String name, String className, boolean multiple);

    ObjectProperty addProperty(String name, String className, boolean multiple, int modifiers);

    ObjectProperty addProperty(String name, String className, boolean multiple, String bag);

    ObjectProperty addProperty(String name, String className, boolean multiple, String bag, int modifiers);

    ObjectProperty addProperty( ObjectProperty property );

    ObjectProperty removeProperty(String name);

    boolean isInterface();

    boolean isAbstract();

    boolean isFinal();

}

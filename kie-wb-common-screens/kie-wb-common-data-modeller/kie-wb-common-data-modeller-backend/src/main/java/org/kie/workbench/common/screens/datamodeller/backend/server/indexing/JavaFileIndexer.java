/*
 * Copyright 2014 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.workbench.common.screens.datamodeller.backend.server.indexing;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.drools.core.base.ClassTypeResolver;
import org.guvnor.common.services.project.model.Package;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.Type;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.kie.api.builder.KieModule;
import org.kie.scanner.KieModuleMetaData;
import org.kie.workbench.common.screens.datamodeller.model.index.FieldName;
import org.kie.workbench.common.screens.datamodeller.model.index.FieldType;
import org.kie.workbench.common.screens.datamodeller.model.index.JavaType;
import org.kie.workbench.common.screens.datamodeller.model.index.JavaTypeInterface;
import org.kie.workbench.common.screens.datamodeller.model.index.JavaTypeName;
import org.kie.workbench.common.screens.datamodeller.model.index.JavaTypeParent;
import org.kie.workbench.common.screens.datamodeller.model.index.terms.JavaTypeIndexTerm;
import org.kie.workbench.common.screens.datamodeller.model.index.terms.valueterms.ValueFieldNameIndexTerm;
import org.kie.workbench.common.screens.datamodeller.model.index.terms.valueterms.ValueFieldTypeIndexTerm;
import org.kie.workbench.common.screens.datamodeller.model.index.terms.valueterms.ValueJavaTypeIndexTerm;
import org.kie.workbench.common.screens.datamodeller.model.index.terms.valueterms.ValueJavaTypeInterfaceIndexTerm;
import org.kie.workbench.common.screens.datamodeller.model.index.terms.valueterms.ValueJavaTypeNameIndexTerm;
import org.kie.workbench.common.screens.datamodeller.model.index.terms.valueterms.ValueJavaTypeParentIndexTerm;
import org.kie.workbench.common.screens.javaeditor.type.JavaResourceTypeDefinition;
import org.kie.workbench.common.services.backend.builder.LRUBuilderCache;
import org.kie.workbench.common.services.datamodeller.util.DriverUtils;
import org.kie.workbench.common.services.refactoring.backend.server.indexing.DefaultIndexBuilder;
import org.kie.workbench.common.services.refactoring.backend.server.util.KObjectUtil;
import org.kie.workbench.common.services.refactoring.model.index.terms.valueterms.ValueTypeIndexTerm;
import org.kie.workbench.common.services.shared.project.KieProject;
import org.kie.workbench.common.services.shared.project.KieProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uberfire.backend.server.util.Paths;
import org.uberfire.ext.metadata.engine.Indexer;
import org.uberfire.ext.metadata.model.KObject;
import org.uberfire.ext.metadata.model.KObjectKey;
import org.uberfire.io.IOService;
import org.uberfire.java.nio.file.Path;

/**
 * The following information is being indexed for java files.
 * <p/>
 * Java type definition minimal information:
 * <p/>
 * (java_type, {class|enum|interface|annotation})
 * (java_type_name, qualifiedClassName)
 * <p/>
 * Java type definition inheritance information:
 * <p/>
 * (java_type_parent, superClassQualifiedName)
 * (java_type_interface, implementedInterface1QualifiedName)
 * (java_type_interface, implementedInterface2QualifiedName)
 * <p/>
 * Java type definition fields information:
 * <p/>
 * (field_name, theField1Name)
 * (field_type:theField1Name, field1TypeQualifiedName)
 * <p/>
 * (field_name, theField2Name)
 * (field_type:theField2Name, field2TypeQualifiedName)
 * <p/>
 * References to types used by this .java class definition, uses the Type references standard used by the other assets:
 * <p/>
 * (type_name, superClassQualifiedName)
 * (type_name, implementedInterface1)
 * (type_name, implementedInterface2)
 * (type_name, field1TypeQualifiedName)
 * (type_name, field2TypeQualifiedName)
 */
@ApplicationScoped
public class JavaFileIndexer implements Indexer {

    private static final Logger logger = LoggerFactory.getLogger( JavaFileIndexer.class );

    @Inject
    @Named("ioStrategy")
    protected IOService ioService;

    @Inject
    protected KieProjectService projectService;

    @Inject
    private LRUBuilderCache builderCache;

    @Inject
    protected JavaResourceTypeDefinition javaResourceTypeDefinition;

    @Override
    public boolean supportsPath( final Path path ) {
        return javaResourceTypeDefinition.accept( Paths.convert( path ) );
    }

    @Override
    public KObject toKObject( final Path path ) {
        KObject index = null;

        try {
            final String javaSource = ioService.readAllString( path );
            final KieProject project = getProject( path );

            if ( project == null ) {
                logger.error( "Unable to index: " + path.toUri().toString() + ", project could not be calculated." );
                return null;
            }

            final Package pkg = getPackage( path );

            if ( pkg == null ) {
                logger.error( "Unable to index: " + path.toUri().toString() + ", package could not be calculated." );
                return null;
            }

            JavaTypeIndexTerm.JAVA_TYPE javaTypeKind = null;
            String javaTypeName;
            DefaultIndexBuilder builder = new DefaultIndexBuilder( project,
                                                                   pkg );

            org.jboss.forge.roaster.model.JavaType<?> javaType = Roaster.parse( javaSource );
            if ( javaType.getSyntaxErrors() == null || javaType.getSyntaxErrors().isEmpty() ) {

                javaTypeName = javaType.getQualifiedName();
                if ( javaType.isAnnotation() ) {
                    javaTypeKind = JavaTypeIndexTerm.JAVA_TYPE.ANNOTATION;
                } else if ( javaType.isInterface() ) {
                    javaTypeKind = JavaTypeIndexTerm.JAVA_TYPE.INTERFACE;
                } else if ( javaType.isEnum() ) {
                    javaTypeKind = JavaTypeIndexTerm.JAVA_TYPE.ENUM;
                } else {
                    javaTypeKind = JavaTypeIndexTerm.JAVA_TYPE.CLASS;
                    //complete class fields processing.
                    addJavaTypeTerms( (JavaClassSource) javaType, builder, getProjectClassLoader( project ) );
                }

                builder.addGenerator( new JavaType( new ValueJavaTypeIndexTerm( javaTypeKind ) ) );
                builder.addGenerator( new JavaTypeName( new ValueJavaTypeNameIndexTerm( javaTypeName ) ) );

                index = KObjectUtil.toKObject( path,
                                               builder.build() );

            }

        } catch ( Exception e ) {
            //Unexpected parsing or processing error
            logger.error( "Unable to index '" + path.toUri().toString() + "'.",
                          e.getMessage() );
        }

        return index;
    }

    private void addJavaTypeTerms( final JavaClassSource javaClassSource,
                                   final DefaultIndexBuilder builder,
                                   final ClassLoader classLoader ) {

        ClassTypeResolver classTypeResolver = DriverUtils.getInstance().createClassTypeResolver( javaClassSource, classLoader );
        DriverUtils driverUtils = DriverUtils.getInstance();
        String superClass = null;
        Set<String> referencedTypes = new HashSet<String>();

        if ( javaClassSource.getSuperType() != null ) {
            try {
                superClass = classTypeResolver.getFullTypeName( javaClassSource.getSuperType() );
                referencedTypes.add( superClass );
                builder.addGenerator( new JavaTypeParent( new ValueJavaTypeParentIndexTerm( superClass ) ) );
            } catch ( ClassNotFoundException e ) {
                logger.error( "Unable to index super class name for class: " + javaClassSource.getQualifiedName() + ", superClass: " + superClass, e );
            }
        }

        List<String> implementedInterfaces = javaClassSource.getInterfaces();
        if ( implementedInterfaces != null ) {
            for ( String implementedInterface : implementedInterfaces ) {
                try {
                    implementedInterface = classTypeResolver.getFullTypeName( implementedInterface );
                    referencedTypes.add( implementedInterface );
                    builder.addGenerator( new JavaTypeInterface( new ValueJavaTypeInterfaceIndexTerm( implementedInterface ) ) );
                } catch ( ClassNotFoundException e ) {
                    logger.error( "Unable to index implemented interface qualified name for class: " + javaClassSource.getQualifiedName() + ", interface: " + implementedInterface, e );
                }
            }
        }

        List<FieldSource<JavaClassSource>> fields = javaClassSource.getFields();
        if ( fields != null ) {
            String fieldName;
            Type fieldType;
            String fieldClassName;

            for ( FieldSource<JavaClassSource> field : fields ) {
                fieldName = field.getName();
                fieldType = field.getType();
                try {
                    if ( driverUtils.isManagedType( fieldType, classTypeResolver ) ) {
                        if ( fieldType.isPrimitive() ) {
                            fieldClassName = fieldType.getName();
                        } else if ( driverUtils.isSimpleClass( fieldType ) ) {
                            fieldClassName = classTypeResolver.getFullTypeName( fieldType.getName() );
                        } else {
                            //if this point was reached, we know it's a Collection.
                            // Managed type check was done previous.
                            Type elementsType = ( (List<Type>) fieldType.getTypeArguments() ).get( 0 );
                            fieldClassName = classTypeResolver.getFullTypeName( elementsType.getName() );
                        }

                        referencedTypes.add( fieldClassName );
                        builder.addGenerator( new FieldName( new ValueFieldNameIndexTerm( fieldName ) ) );
                        builder.addGenerator( new FieldType( new ValueFieldNameIndexTerm( fieldName ), new ValueFieldTypeIndexTerm( fieldClassName ) ) );
                    }
                } catch ( Exception e ) {
                    logger.error( "Unable to index java class field for class: " + javaClassSource.getQualifiedName() + ", fieldName: " + fieldName + " fieldType: " + fieldType );
                }
            }
        }
        for ( String referencedType : referencedTypes ) {
            builder.addGenerator( new org.kie.workbench.common.services.refactoring.model.index.Type( new ValueTypeIndexTerm( referencedType ) ) );
        }
    }

    @Override
    public KObjectKey toKObjectKey( final Path path ) {
        return KObjectUtil.toKObjectKey( path );
    }

    protected KieProject getProject( final Path path ) {
        return projectService.resolveProject( Paths.convert( path ) );
    }

    protected Package getPackage( final Path path ) {
        return projectService.resolvePackage( Paths.convert( path ) );
    }

    protected ClassLoader getProjectClassLoader( final KieProject project ) {
        final KieModule module = builderCache.assertBuilder( project ).getKieModuleIgnoringErrors();
        final ClassLoader classLoader = KieModuleMetaData.Factory.newKieModuleMetaData( module ).getClassLoader();
        return classLoader;
    }
}

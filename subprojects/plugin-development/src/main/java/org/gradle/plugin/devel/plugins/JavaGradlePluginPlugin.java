/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.devel.plugins;

import org.gradle.api.*;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.plugins.PluginDescriptor;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata;

import javax.inject.Inject;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_API;

/**
 * A plugin for validating java gradle plugins during the jar task. Emits warnings for common error conditions.
 * <p>
 * Provides a direct integration with TestKit by declaring the {@code gradleTestKit()} dependency for the test
 * compile configuration and a dependency on the plugin classpath manifest generation task for the test runtime
 * configuration. Default conventions can be customized with the help of {@link GradlePluginDevelopmentExtension}.
 */
@Incubating
public class JavaGradlePluginPlugin implements Plugin<Project> {
    private static final Logger LOGGER = Logging.getLogger(JavaGradlePluginPlugin.class);
    private final ClassPathRegistry classPathRegistry;
    static final String COMPILE_CONFIGURATION = "compile";
    static final String JAR_TASK = "jar";
    static final String GRADLE_PLUGINS = "gradle-plugins";
    static final String PLUGIN_DESCRIPTOR_PATTERN = "META-INF/" + GRADLE_PLUGINS + "/*.properties";
    static final String CLASSES_PATTERN = "**/*.class";
    static final String BAD_IMPL_CLASS_WARNING_MESSAGE = "A valid plugin descriptor was found for %s but the implementation class %s was not found in the jar.";
    static final String INVALID_DESCRIPTOR_WARNING_MESSAGE = "A plugin descriptor was found for %s but it was invalid.";
    static final String NO_DESCRIPTOR_WARNING_MESSAGE = "No valid plugin descriptors were found in META-INF/" + GRADLE_PLUGINS + "";
    static final String EXTENSION_NAME = "gradlePlugin";
    static final String PLUGIN_UNDER_TEST_METADATA_TASK_NAME = "pluginUnderTestMetadata";

    @Inject
    public JavaGradlePluginPlugin(ClassPathRegistry classPathRegistry) {
        this.classPathRegistry = classPathRegistry;
    }

    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        applyDependencies(project);
        configureJarTask(project);
        GradlePluginDevelopmentExtension extension = createExtension(project);
        configureTestKit(project, extension);
    }

    private void applyDependencies(Project project) {
        DependencyHandler dependencies = project.getDependencies();
        dependencies.add(COMPILE_CONFIGURATION, dependencies.gradleApi());
    }

    private void configureJarTask(Project project) {
        Jar jarTask = (Jar) project.getTasks().findByName(JAR_TASK);
        List<PluginDescriptor> descriptors = new ArrayList<PluginDescriptor>();
        Set<String> classList = new HashSet<String>();
        PluginDescriptorCollectorAction pluginDescriptorCollector = new PluginDescriptorCollectorAction(descriptors);
        ClassManifestCollectorAction classManifestCollector = new ClassManifestCollectorAction(classList);
        PluginValidationAction pluginValidationAction = new PluginValidationAction(descriptors, classList);

        jarTask.filesMatching(PLUGIN_DESCRIPTOR_PATTERN, pluginDescriptorCollector);
        jarTask.filesMatching(CLASSES_PATTERN, classManifestCollector);
        jarTask.appendParallelSafeAction(pluginValidationAction);
    }

    private GradlePluginDevelopmentExtension createExtension(Project project) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        SourceSet defaultPluginSourceSet = javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet defaultTestSourceSet = javaConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        return project.getExtensions().create(EXTENSION_NAME, GradlePluginDevelopmentExtension.class, defaultPluginSourceSet, defaultTestSourceSet);
    }

    private void configureTestKit(Project project, GradlePluginDevelopmentExtension extension) {
        PluginUnderTestMetadata pluginUnderTestMetadataTask = createAndConfigurePluginUnderTestMetadataTask(project, extension);
        establishTestKitAndPluginClasspathDependencies(project, extension, pluginUnderTestMetadataTask);
    }

    private PluginUnderTestMetadata createAndConfigurePluginUnderTestMetadataTask(final Project project, final GradlePluginDevelopmentExtension extension) {
        final PluginUnderTestMetadata pluginUnderTestMetadataTask = project.getTasks().create(PLUGIN_UNDER_TEST_METADATA_TASK_NAME, PluginUnderTestMetadata.class);

        ConventionMapping conventionMapping = new DslObject(pluginUnderTestMetadataTask).getConventionMapping();
        conventionMapping.map("pluginClasspath", new Callable<Object>() {
            public Object call() throws Exception {
                FileCollection gradleApi = project.files(classPathRegistry.getClassPath(GRADLE_API.name()).getAsFiles());
                return extension.getPluginSourceSet().getRuntimeClasspath().minus(gradleApi);
            }
        });
        conventionMapping.map("outputDirectory", new Callable<Object>() {
            public Object call() throws Exception {
                return new File(project.getBuildDir(), pluginUnderTestMetadataTask.getName());
            }
        });

        return pluginUnderTestMetadataTask;
    }

    private void establishTestKitAndPluginClasspathDependencies(Project project, GradlePluginDevelopmentExtension extension, PluginUnderTestMetadata pluginClasspathTask) {
        project.afterEvaluate(new TestKitAndPluginClasspathDependenciesAction(extension, pluginClasspathTask));
    }

    /**
     * Implements plugin validation tasks to validate that a proper plugin jar is produced.
     */
    static class PluginValidationAction implements Action<Task> {
        Collection<PluginDescriptor> descriptors;
        Set<String> classes;

        PluginValidationAction(Collection<PluginDescriptor> descriptors, Set<String> classes) {
            this.descriptors = descriptors;
            this.classes = classes;
        }

        public void execute(Task task) {
            if (descriptors == null || descriptors.isEmpty()) {
                LOGGER.warn(NO_DESCRIPTOR_WARNING_MESSAGE);
            } else {
                for (PluginDescriptor descriptor : descriptors) {
                    URI descriptorURI = null;
                    try {
                        descriptorURI = descriptor.getPropertiesFileUrl().toURI();
                    } catch (URISyntaxException e) {
                        // Do nothing since the only side effect is that we wouldn't
                        // be able to log the plugin descriptor file name.  Shouldn't
                        // be a reasonable scenario where this occurs since these
                        // descriptors should be generated from real files.
                    }
                    String pluginFileName = descriptorURI != null ? new File(descriptorURI).getName() : "UNKNOWN";
                    String pluginImplementation = descriptor.getImplementationClassName();
                    if (pluginImplementation.length() == 0) {
                        LOGGER.warn(String.format(INVALID_DESCRIPTOR_WARNING_MESSAGE, pluginFileName));
                    } else if (!hasFullyQualifiedClass(pluginImplementation)) {
                        LOGGER.warn(String.format(BAD_IMPL_CLASS_WARNING_MESSAGE, pluginFileName, pluginImplementation));
                    }
                }
            }
        }

        boolean hasFullyQualifiedClass(String fqClass) {
            return classes.contains(fqClass.replaceAll("\\.", "/") + ".class");
        }
    }

    /**
     * A file copy action that collects plugin descriptors as they are added to the jar.
     */
    static class PluginDescriptorCollectorAction implements Action<FileCopyDetails> {
        List<PluginDescriptor> descriptors;

        PluginDescriptorCollectorAction(List<PluginDescriptor> descriptors) {
            this.descriptors = descriptors;
        }

        public void execute(FileCopyDetails fileCopyDetails) {
            PluginDescriptor descriptor;
            try {
                descriptor = new PluginDescriptor(fileCopyDetails.getFile().toURI().toURL());
            } catch (MalformedURLException e) {
                // Not sure under what scenario (if any) this would occur,
                // but there's no sense in collecting the descriptor if it does.
                return;
            }
            if (descriptor.getImplementationClassName() != null) {
                descriptors.add(descriptor);
            }
        }
    }

    /**
     * A file copy action that collects class file paths as they are added to the jar.
     */
    static class ClassManifestCollectorAction implements Action<FileCopyDetails> {
        Set<String> classList;

        ClassManifestCollectorAction(Set<String> classList) {
            this.classList = classList;
        }

        public void execute(FileCopyDetails fileCopyDetails) {
            classList.add(fileCopyDetails.getRelativePath().toString());
        }
    }

    /**
     * An action that automatically declares the TestKit dependency for the test compile configuration and a dependency
     * on the plugin classpath manifest generation task for the test runtime configuration.
     */
    private static class TestKitAndPluginClasspathDependenciesAction implements Action<Project> {
        private final GradlePluginDevelopmentExtension extension;
        private final PluginUnderTestMetadata pluginClasspathTask;

        private TestKitAndPluginClasspathDependenciesAction(GradlePluginDevelopmentExtension extension, PluginUnderTestMetadata pluginClasspathTask) {
            this.extension = extension;
            this.pluginClasspathTask = pluginClasspathTask;
        }

        @Override
        public void execute(Project project) {
            DependencyHandler dependencies = project.getDependencies();
            Set<SourceSet> testSourceSets = extension.getTestSourceSets();

            for (SourceSet testSourceSet : testSourceSets) {
                String compileConfigurationName = testSourceSet.getCompileConfigurationName();
                dependencies.add(compileConfigurationName, dependencies.gradleTestKit());
                String runtimeConfigurationName = testSourceSet.getRuntimeConfigurationName();
                dependencies.add(runtimeConfigurationName, project.files(pluginClasspathTask));
            }
        }
    }
}

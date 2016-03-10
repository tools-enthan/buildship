package org.eclipse.buildship.core.workspace.internal

import com.google.common.base.Predicate
import com.google.common.collect.FluentIterable

import com.gradleware.tooling.toolingclient.GradleDistribution

import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.JavaCore

import org.eclipse.buildship.core.test.fixtures.ProjectSynchronizationSpecification;

class ReexportedDependencySpecification extends ProjectSynchronizationSpecification {

    def "Transitive dependencies are acessible from local project classpath when using Gradle 2.5+"(GradleDistribution distribution) {
        setup:
        File location = multiProjectWithSpringTransitiveDependency()

        when:
        importAndWait(location, distribution)

        then:
        def moduleA = findProject('moduleA')
        def moduleB = findProject('moduleB')
        resolvedClasspath(moduleA).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'spring-beans-1.2.8.jar' }
        resolvedClasspath(moduleA).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'moduleB' && entry.entryKind == IClasspathEntry.CPE_PROJECT && !entry.isExported() }
        resolvedClasspath(moduleB).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'spring-beans-1.2.8.jar' && !entry.isExported() }

        where:
        distribution << [ GradleDistribution.forVersion('2.5'), GradleDistribution.forVersion('2.6') ]
    }

    def "Transitive dependencies are acessible via exports from dependent projects when using Gradle <2.5"(GradleDistribution distribution) {
        setup:
        File location = multiProjectWithSpringTransitiveDependency()

        when:
        importAndWait(location, distribution)

        then:
        def moduleA = findProject('moduleA')
        def moduleB = findProject('moduleB')
        !resolvedClasspath(moduleA).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'spring-beans-1.2.8.jar' }
        resolvedClasspath(moduleA).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'moduleB' && entry.entryKind == IClasspathEntry.CPE_PROJECT && entry.isExported() }
        resolvedClasspath(moduleB).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'spring-beans-1.2.8.jar' && entry.isExported() }

        where:
        distribution << [ GradleDistribution.forVersion('2.3'), GradleDistribution.forVersion('2.4') ]
    }

    def "Excluded dependencies (incorrectly) resolved from dependent projects when using Gradle <2.5"(GradleDistribution distribution) {
        setup:
        File location = springExampleProjectFromBug473348()

        when:
        importAndWait(location, distribution)

        then:
        def moduleA = findProject('moduleA')
        def moduleB = findProject('moduleB')

        resolvedClasspath(moduleA).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'spring-core-3.1.4.RELEASE.jar' }
        resolvedClasspath(moduleA).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'moduleB' && entry.entryKind == IClasspathEntry.CPE_PROJECT && entry.isExported() }
        resolvedClasspath(moduleB).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'spring-core-1.2.8.jar' && entry.isExported() }

        where:
        distribution << [ GradleDistribution.forVersion('2.3'), GradleDistribution.forVersion('2.4') ]
    }

    def "Excluded dependencies are not resolved when using Gradle 2.5+"(GradleDistribution distribution) {
        setup:
        File location = springExampleProjectFromBug473348()

        when:
        importAndWait(location, distribution)

        then:
        def moduleA = findProject('moduleA')
        def moduleB = findProject('moduleB')

        resolvedClasspath(moduleA).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'spring-core-3.1.4.RELEASE.jar' }
        !resolvedClasspath(moduleA).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'spring-core-1.2.8.jar' }
        resolvedClasspath(moduleA).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'moduleB' && entry.entryKind == IClasspathEntry.CPE_PROJECT && !entry.isExported() }
        resolvedClasspath(moduleB).any{ IClasspathEntry entry -> entry.path.lastSegment() == 'spring-core-1.2.8.jar' && !entry.isExported() }

        where:
        distribution << [ GradleDistribution.forVersion('2.5'), GradleDistribution.forVersion('2.6') ]
    }

    def "Sample with transitive dependency exclusion should compile when imported by Buildship"(GradleDistribution distribution) {
        setup:
        File location = springExampleProjectFromBug473348()

        when:
        importAndWait(location, distribution)
        waitForBuild()

        then:
        !projectContainsErrorMarkers('moduleA', 'moduleB');

        where:
        distribution << [ GradleDistribution.forVersion('2.1'), GradleDistribution.forVersion('2.6') ]
    }

    private def projectContainsErrorMarkers(String... projectNames) {
        projectNames.find { projectName ->
            IMarker[] markers = findProject(projectName).findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE)
            markers.length >= 0 && markers.find { it.getAttribute(IMarker.SEVERITY) == IMarker.SEVERITY_ERROR }
        }
    }

    private def waitForBuild() {
        workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)
    }

    private File multiProjectWithSpringTransitiveDependency() {
        dir('spring-example') {
            file 'build.gradle', '''
                allprojects {
                    repositories { mavenCentral() }
                    apply plugin: 'java'
                }
            '''
            file 'settings.gradle', '''
                include "moduleA"
                include "moduleB"
            '''

            moduleA {
                dir 'src/main/java'
                file 'build.gradle', '''
                    dependencies {
                        compile (project(":moduleB"))
                    }
                '''
            }
            moduleB {
                dir 'src/main/java'
                file 'build.gradle', '''
                    dependencies {
                        compile "org.springframework:spring-beans:1.2.8"
                    }
                '''
            }
        }
    }

    private File springExampleProjectFromBug473348() {
        dir('Bug473348') {
            file 'build.gradle', '''
                allprojects {
                   repositories { mavenCentral() }
                   apply plugin: 'java'
                }
            '''
            file 'settings.gradle', '''
                include "moduleA"
                include "moduleB"
            '''
            moduleA {
                file 'build.gradle', '''
                    dependencies {
                        compile "org.springframework:spring-beans:3.1.4.RELEASE"
                        compile (project(":moduleB")) {
                            exclude group: "org.springframework"
                        }
                    }
                '''
                dir ('src/main/java') {
                    file 'ApplicationA.java', '''
                        import org.springframework.beans.BeansException;
                        import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
                        import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
                        import java.beans.PropertyEditor;

                        public class ApplicationA implements BeanFactoryPostProcessor {
                            @Override
                            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                                try {
                                    Class<?> classA = Class.forName("any");
                                    beanFactory.registerCustomEditor(classA, classA.asSubclass(PropertyEditor.class));
                                } catch (ClassNotFoundException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    '''
                }

            }

            moduleB {
                file 'build.gradle', '''
                    dependencies {
                        compile "org.springframework:spring-beans:1.2.8"
                    }
                '''
                dir("src/main/java") {
                    file 'ApplicationB.java', '''
                        import org.springframework.beans.factory.FactoryBean;
                        public class ApplicationB {
                            public void methodA(){
                                FactoryBean factoryBean;
                            }
                        }
                    '''
                }

            }
        }
    }

    private def resolvedClasspath(IProject project) {
        JavaCore.create(project).getResolvedClasspath(false)
    }

    private def rawClasspath(IProject project) {
        JavaCore.create(project).rawClasspath
    }
}

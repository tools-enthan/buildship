/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.eclipse.buildship.core.workspace;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

import org.eclipse.core.resources.IProject;

import org.eclipse.buildship.core.event.Event;

/**
 * Event announcing that the Gradle nature is added to a set of projects.
 *
 * @author Donat Csikos
 */
public final class GradleNatureAddedEvent implements Event {

    private final Set<IProject> projects;

    public GradleNatureAddedEvent(Set<IProject> projects) {
        this.projects = ImmutableSet.copyOf(projects);
    }

    public Set<IProject> getProjects() {
        return this.projects;
    }
}

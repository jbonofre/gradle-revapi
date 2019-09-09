/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.revapi;

import java.util.function.Supplier;
import org.gradle.api.Project;

final class PreviousVersionResolutionHelpers {
    private PreviousVersionResolutionHelpers() { }

    private static final class GroupThreadLocal extends ThreadLocal<Object> {
        private final Object defaultGroup;

        GroupThreadLocal(Object defaultGroup) {
            this.defaultGroup = defaultGroup;
        }

        @Override
        protected Object initialValue() {
            return defaultGroup;
        }
    }

    private static final class ThreadLocalGroup {
        private final GroupThreadLocal group;

        private ThreadLocalGroup(Object defaultGroup, String newGroupName) {
            this.group = new GroupThreadLocal(defaultGroup);
            this.group.set(newGroupName);
        }

        @Override
        public boolean equals(Object obj) {
            return group.get().equals(obj);
        }

        @Override
        public int hashCode() {
            return group.get().hashCode();
        }

        @Override
        public String toString() {
            return group.get().toString();
        }
    }

    public static <T> T withRenamedGroupForCurrentThread(Project project, Supplier<T> action) {
        Object group = project.getGroup();
        project.setGroup(new ThreadLocalGroup(group, "revapi.changed.group." + group));
        T result = action.get();
        project.setGroup(group);
        return result;
    }
}
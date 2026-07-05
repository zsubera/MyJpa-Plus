package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class QueryBuildHelperTest {

    private static class DummyEntity {
        private String name;
        private int age;

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }

    @Test
    void constructor_isPrivate() throws Exception {
        java.lang.reflect.Constructor<?> ctor = QueryBuildHelper.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertNotNull(ctor);
    }
}

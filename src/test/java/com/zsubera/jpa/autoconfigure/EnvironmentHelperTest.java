package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EnvironmentHelperTest {

    @Test
    void getEnvOrPropertyReturnsEnvFirst() {
        String result = EnvironmentHelper.getEnvOrProperty("PATH", "nonexistent.property");
        assertNotNull(result);
    }

    @Test
    void getEnvOrPropertyReturnsNullWhenNeitherExists() {
        String result = EnvironmentHelper.getEnvOrProperty("NONEXISTENT_ENV_VAR_12345", "nonexistent.property.12345");
        assertNull(result);
    }
}

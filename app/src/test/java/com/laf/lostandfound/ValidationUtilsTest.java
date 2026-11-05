package com.laf.lostandfound;

import com.laf.lostandfound.utils.ValidationUtils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ValidationUtilsTest {

    @Test
    public void testValidEmail() {
        assertTrue(ValidationUtils.isValidEmail("student@university.edu"));
        assertTrue(ValidationUtils.isValidEmail("john.doe@gmail.com"));
        assertFalse(ValidationUtils.isValidEmail("invalid-email"));
        assertFalse(ValidationUtils.isValidEmail("user@"));
        assertFalse(ValidationUtils.isValidEmail("@domain.com"));
        assertFalse(ValidationUtils.isValidEmail(""));
        assertFalse(ValidationUtils.isValidEmail(null));
    }

    @Test
    public void testValidPassword() {
        assertTrue(ValidationUtils.isValidPassword("secret123"));
        assertTrue(ValidationUtils.isValidPassword("123456"));
        assertFalse(ValidationUtils.isValidPassword("12345"));
        assertFalse(ValidationUtils.isValidPassword(""));
        assertFalse(ValidationUtils.isValidPassword(null));
    }

    @Test
    public void testValidPhone() {
        assertTrue(ValidationUtils.isValidPhone("+1234567890"));
        assertTrue(ValidationUtils.isValidPhone("9876543210"));
        assertFalse(ValidationUtils.isValidPhone("123"));
        assertFalse(ValidationUtils.isValidPhone(""));
        assertFalse(ValidationUtils.isValidPhone(null));
    }

    @Test
    public void testValidTitleAndDescription() {
        assertTrue(ValidationUtils.isValidTitle("Keys"));
        assertFalse(ValidationUtils.isValidTitle("Hi"));
        assertTrue(ValidationUtils.isValidDescription("Found near cafe"));
        assertFalse(ValidationUtils.isValidDescription("Tiny"));
    }
}

package com.smarthealthdog.backend.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts the Permission enum to its 'name' string for the database
 * and back again, ensuring the database stores "can_login" instead of "CAN_LOGIN".
 */
@Converter(autoApply = true)
public class PermissionConverter implements AttributeConverter<PermissionEnum, String> {

    @Override
    public String convertToDatabaseColumn(PermissionEnum permission) {
        if (permission == null) {
            return null;
        }

        // Use the custom 'name' field defined in the enum
        return permission.getName();
    }

    @Override
    public PermissionEnum convertToEntityAttribute(String name) {
        if (name == null) {
            return null;
        }

        // Find the enum constant that matches the database 'name' string
        return PermissionEnum.getPermission(name);
    }
}
package com.femstep.store.domain;

public enum RoleName {
    ROLE_ADMIN("Administrator"),
    ROLE_CUSTOMER("Customer");

    private final String description;

    RoleName(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

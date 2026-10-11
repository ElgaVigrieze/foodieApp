package com.foodie.model;

import com.foodie.model.db.AppUser;
import com.foodie.model.db.Household;

/**
 * Interface for entities that have ownership (owner + household).
 * Used for access control validation.
 */
public interface OwnedEntity {
    AppUser getOwner();
    Household getHousehold();
}
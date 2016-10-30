package com.muciaccia.plugins.backend.pojo;

/**
 * Wrapper class for the user name
 */
public class User {
    private final String userName;

    public User(final String userName) {
        this.userName = userName;
    }
    public String getName() {
        return userName;
    }


    /** Custom implementations of equals, hashCode and toString */

    @Override
    public boolean equals(Object obj) {
        if(null == obj || !(obj instanceof User)) {
            return false;
        }
        return userName.equals(((User)obj).getName());
    }

    @Override
    public int hashCode() {
        return userName.hashCode();
    }

    @Override
    public String toString() {
        return userName;
    }
}

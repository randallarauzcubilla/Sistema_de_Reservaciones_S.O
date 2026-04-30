package Core;

/**
 * @class User
 * Represents a system user with a specific role.
 */
public class User {

    public enum Role {
        STUDENT, TEACHER, DEANERY
    }

    private final String name;
    private final Role role;

    /**
     * @param name the name of the user
     * @param role the role assigned to the user
     */
    public User(String name, Role role) {
        this.name = name;
        this.role = role;
    }

    /**
     * @return the user's name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the user's role
     */
    public Role getRole() {
        return role;
    }

    @Override
    public String toString() {
        return name + " [" + role + "]";
    }
}
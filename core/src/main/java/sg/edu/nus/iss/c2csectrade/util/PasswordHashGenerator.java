package sg.edu.nus.iss.c2csectrade.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility that generates BCrypt password hashes
 * Run this class to generate hashes, then update them in the database
 */
public class PasswordHashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // Generate the administrator password hash
        String adminPassword = "admin123";
        String adminHash = encoder.encode(adminPassword);
        System.out.println("Hash for the administrator password (admin123):");
        System.out.println(adminHash);
        System.out.println();

        // Generate the test user password hash
        String userPassword = "password1";
        String userHash = encoder.encode(userPassword);
        System.out.println("Hash for the test user password (password1):");
        System.out.println(userHash);
        System.out.println();

        // Check the hashes match
        System.out.println("Verify admin123: " + encoder.matches(adminPassword, adminHash));
        System.out.println("Verify password1: " + encoder.matches(userPassword, userHash));
    }
}


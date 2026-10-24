package com.rapidswing.security;

/**
 * Changes a user's password. Implement it next to your {@link Authenticator};
 * {@link SimpleAuthenticator} does. Called on a background thread, so it may block.
 * <ul>
 *   <li>Throw {@link AuthenticationException} if the current password is wrong.</li>
 *   <li>Throw {@link IllegalArgumentException} with a user-readable message if the new password breaks a rule of
 *       yours (the dialog already requires a minimum length and a different password).</li>
 *   <li>Any other exception means the change is unavailable and is reported as such.</li>
 *   <li>Do not keep the arrays: the dialog clears them as soon as this method returns.</li>
 * </ul>
 */
@FunctionalInterface
public interface PasswordChanger {

    void changePassword(String username, char[] currentPassword, char[] newPassword) throws Exception;
}

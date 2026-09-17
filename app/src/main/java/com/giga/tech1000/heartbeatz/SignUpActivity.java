package com.giga.tech1000.heartbeatz;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.media3.common.util.UnstableApi;

@OptIn(markerClass = UnstableApi.class)
public class SignUpActivity extends AppCompatActivity {

    // UI References
    private TextInputLayout nameInputLayout;
    private TextInputEditText nameInput;
    private TextInputLayout emailInputLayout;
    private TextInputEditText emailInput;
    private TextInputLayout passwordInputLayout;
    private TextInputEditText passwordInput;
    private TextInputLayout confirmPasswordInputLayout;
    private TextInputEditText confirmPasswordInput;
    private MaterialButton signUpButton;
    private MaterialButton googleSignInButton;
    private View loginLink;

    // Credential Manager
    private CredentialManager credentialManager;

    // Firebase Auth
    private FirebaseAuth mAuth;
    private GetGoogleIdOption googleIdOption;
    private GetCredentialRequest request;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.activity.EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content),
                (v, insets) -> {
                    androidx.core.graphics.Insets bars =
                            insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                    androidx.core.graphics.Insets ime =
                            insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                    return androidx.core.view.WindowInsetsCompat.CONSUMED;
                });

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        // Initialize Credential Manager
        credentialManager = CredentialManager.create(this);

        // Initialize UI
        initViews();

        // Set up click listeners
        setUpClickListeners();

        googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build();

        request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();
    }

    private void initViews() {
        nameInputLayout = findViewById(R.id.name_input_layout);
        nameInput = findViewById(R.id.name_input);
        emailInputLayout = findViewById(R.id.email_input_layout);
        emailInput = findViewById(R.id.email_input);
        passwordInputLayout = findViewById(R.id.password_input_layout);
        passwordInput = findViewById(R.id.password_input);
        confirmPasswordInputLayout = findViewById(R.id.confirm_password_input_layout);
        confirmPasswordInput = findViewById(R.id.confirm_password_input);
        signUpButton = findViewById(R.id.sign_up_button);
        googleSignInButton = findViewById(R.id.google_sign_in_button);
        loginLink = findViewById(R.id.login_link);
    }

    private void setUpClickListeners() {
        // Sign up button click
        signUpButton.setOnClickListener(v -> {
            if (validateForm()) {
                String email = emailInput.getText().toString().trim();
                String password = passwordInput.getText().toString();
                String displayName = nameInput.getText() != null
                        ? nameInput.getText().toString().trim() : "";
                setAuthUiEnabled(false);
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            if (task.isSuccessful()) {
                                FirebaseUser user = mAuth.getCurrentUser();
                                if (user != null && !TextUtils.isEmpty(displayName)) {
                                    user.updateProfile(new UserProfileChangeRequest.Builder()
                                                    .setDisplayName(displayName)
                                                    .build())
                                            .addOnCompleteListener(profileTask -> finishSignUp(user));
                                } else {
                                    finishSignUp(user);
                                }
                            } else {
                                String msg = task.getException() != null
                                        ? task.getException().getLocalizedMessage()
                                        : "Sign-up failed.";
                                Toast.makeText(SignUpActivity.this, msg, Toast.LENGTH_LONG).show();
                                updateUI(null);
                            }
                        });
            }
        });

        // Google sign-in button click using Credential Manager
        googleSignInButton.setOnClickListener(v -> {
            credentialManager.getCredentialAsync(
                    // Use activity background executor
                    this,
                    request,
                    null,
                    getMainExecutor(),
                    getCredentialCallback());
        });

        // Login link click
        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private CredentialManagerCallback<GetCredentialResponse, GetCredentialException> getCredentialCallback() {
        return new CredentialManagerCallback<>() {
            @Override
            public void onResult(@NonNull GetCredentialResponse result) {
                // Handle the successfully returned credential.
                Credential credential = result.getCredential();
                if (credential instanceof CustomCredential) {
                    if (credential.getType().equals("com.google.android.gms.auth.api.signin.GOOGLE_ID_TOKEN_KEY")) {
                        try {
                            GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
                            String idToken = googleIdTokenCredential.getIdToken();
                            firebaseAuthWithGoogle(idToken);
                        } catch (Exception e) {
                            Log.e("SignUpActivity", "Failed to parse Google ID token", e);
                            Toast.makeText(SignUpActivity.this, "Failed to process Google sign-in", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w("SignUpActivity", "Unexpected credential type: " + credential.getType());
                        Toast.makeText(SignUpActivity.this, "Invalid credential type", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.w("SignUpActivity", "Unexpected credential type: " + (credential != null ? credential.getClass().getSimpleName() : "null"));
                    Toast.makeText(SignUpActivity.this, "Invalid credential type", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(@NonNull GetCredentialException e) {
                Log.e("SignUpActivity", "Credential retrieval failed", e);
                Toast.makeText(SignUpActivity.this, "Google sign-in failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        };
    }

    private boolean validateForm() {
        boolean valid = true;

        // Validate name
        String name = nameInput.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            nameInputLayout.setError("Name is required");
            valid = false;
        } else {
            nameInputLayout.setError(null);
        }

        // Validate email
        String email = emailInput.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            emailInputLayout.setError("Email is required");
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.setError("Enter a valid email");
            valid = false;
        } else {
            emailInputLayout.setError(null);
        }

        // Validate password
        String password = passwordInput.getText().toString();
        if (TextUtils.isEmpty(password)) {
            passwordInputLayout.setError("Password is required");
            valid = false;
        } else if (password.length() < 6) {
            passwordInputLayout.setError("Password must be at least 6 characters");
            valid = false;
        } else {
            passwordInputLayout.setError(null);
        }

        // Validate confirm password
        String confirmPassword = confirmPasswordInput.getText().toString();
        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordInputLayout.setError("Please confirm your password");
            valid = false;
        } else if (!confirmPassword.equals(password)) {
            confirmPasswordInputLayout.setError("Passwords do not match");
            valid = false;
        } else {
            confirmPasswordInputLayout.setError(null);
        }

        return valid;
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success
                        FirebaseUser user = mAuth.getCurrentUser();
                        updateUI(user);
                        startActivity(new Intent(SignUpActivity.this, MainActivity.class));
                        finish();
                    } else {
                        // If sign in fails, display a message to the user.
                        Toast.makeText(SignUpActivity.this, "Firebase Authentication failed.",
                                Toast.LENGTH_SHORT).show();
                        updateUI(null);
                    }
                });
    }

    private void finishSignUp(FirebaseUser user) {
        updateUI(user);
        startActivity(new Intent(SignUpActivity.this, MainActivity.class));
        finish();
    }

    private void setAuthUiEnabled(boolean enabled) {
        if (signUpButton != null) {
            signUpButton.setEnabled(enabled);
            signUpButton.setText(enabled ? "Sign up" : "Please wait…");
        }
        if (googleSignInButton != null) googleSignInButton.setEnabled(enabled);
        if (loginLink != null) loginLink.setEnabled(enabled);
        if (nameInput != null) nameInput.setEnabled(enabled);
        if (emailInput != null) emailInput.setEnabled(enabled);
        if (passwordInput != null) passwordInput.setEnabled(enabled);
        if (confirmPasswordInput != null) confirmPasswordInput.setEnabled(enabled);
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            setAuthUiEnabled(false);
            if (emailInputLayout != null) emailInputLayout.setError(null);
            if (passwordInputLayout != null) passwordInputLayout.setError(null);
            if (confirmPasswordInputLayout != null) confirmPasswordInputLayout.setError(null);
            String name = user.getDisplayName();
            if (TextUtils.isEmpty(name)) name = user.getEmail();
            Toast.makeText(this, "Account created" + (name != null ? ": " + name : ""), Toast.LENGTH_SHORT).show();
        } else {
            setAuthUiEnabled(true);
            if (emailInputLayout != null) {
                emailInputLayout.setError("Could not create account. Try a different email.");
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser current = mAuth.getCurrentUser();
        if (current != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clear UI references
        nameInputLayout = null;
        nameInput = null;
        emailInputLayout = null;
        emailInput = null;
        passwordInputLayout = null;
        passwordInput = null;
        confirmPasswordInputLayout = null;
        confirmPasswordInput = null;
        signUpButton = null;
        googleSignInButton = null;
        loginLink = null;
    }
}
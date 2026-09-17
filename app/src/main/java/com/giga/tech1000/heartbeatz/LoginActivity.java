package com.giga.tech1000.heartbeatz;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;


import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

@OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class LoginActivity extends AppCompatActivity {

    // UI References
    private TextInputLayout emailInputLayout;
    private TextInputEditText emailInput;
    private TextInputLayout passwordInputLayout;
    private TextInputEditText passwordInput;
    private MaterialButton loginButton;
    private MaterialButton googleSignInButton;
    private View forgotPasswordLink;
    private View signUpLink;

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
        setContentView(R.layout.activity_login);

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
        emailInputLayout = findViewById(R.id.email_input_layout);
        emailInput = findViewById(R.id.email_input);
        passwordInputLayout = findViewById(R.id.password_input_layout);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_button);
        googleSignInButton = findViewById(R.id.google_sign_in_button);
        forgotPasswordLink = findViewById(R.id.forgot_password_link);
        signUpLink = findViewById(R.id.sign_up_link);
    }


    private void setUpClickListeners() {
        // Login button click
        loginButton.setOnClickListener(v -> {
            if (validateForm()) {
                String email = emailInput.getText().toString().trim();
                String password = passwordInput.getText().toString();
                setAuthUiEnabled(false);
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            if (task.isSuccessful()) {
                                FirebaseUser user = mAuth.getCurrentUser();
                                updateUI(user);
                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                finish();
                            } else {
                                String msg = task.getException() != null
                                        ? task.getException().getLocalizedMessage()
                                        : "Authentication failed.";
                                Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                                updateUI(null);
                            }
                        });
            }
        });

        // Forgot password link click
        forgotPasswordLink.setOnClickListener(v -> showForgotPasswordDialog());

        // Sign up link click
        signUpLink.setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
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

    }

    private boolean validateForm() {
        boolean valid = true;

        // Validate email
        String email = emailInput.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            emailInputLayout.setError("Email is required");
            valid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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

        return valid;
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
                            GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.getData());
                            String idToken = googleIdTokenCredential.getIdToken();
                            firebaseAuthWithGoogle(idToken);
                        } catch (Exception e) {
                            Log.e("LoginActivity", "Failed to parse Google ID token", e);
                            Toast.makeText(LoginActivity.this, "Failed to process Google sign-in", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w("LoginActivity", "Unexpected credential type: " + credential.getType());
                        Toast.makeText(LoginActivity.this, "Invalid credential type", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.w("LoginActivity", "Unexpected credential type: " + (credential != null ? credential.getClass().getSimpleName() : "null"));
                }
            }

            @Override
            public void onError(@NonNull GetCredentialException e) {
                Log.e("LoginActivity", "Credential retrieval failed", e);
                Toast.makeText(LoginActivity.this, "Google sign-in failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        };
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success
                        FirebaseUser user = mAuth.getCurrentUser();
                        updateUI(user);
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        // If sign in fails, display a message to the user.
                        Toast.makeText(LoginActivity.this, "Authentication failed.",
                                Toast.LENGTH_SHORT).show();
                        updateUI(null);
                    }
                });
    }

    /**
     * Production forgot-password flow via Firebase Auth email reset.
     */
    private void showForgotPasswordDialog() {
        final TextInputEditText emailField = new TextInputEditText(this);
        emailField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailField.setHint("Email address");
        String prefill = emailInput != null && emailInput.getText() != null
                ? emailInput.getText().toString().trim() : "";
        if (!TextUtils.isEmpty(prefill)) {
            emailField.setText(prefill);
        }

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = pad;
        params.rightMargin = pad;
        emailField.setLayoutParams(params);
        container.addView(emailField);

        new AlertDialog.Builder(this)
                .setTitle("Reset password")
                .setMessage("We will send a password reset link to your email.")
                .setView(container)
                .setPositiveButton("Send", (dialog, which) -> {
                    String email = emailField.getText() != null
                            ? emailField.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    setAuthUiEnabled(false);
                    mAuth.sendPasswordResetEmail(email)
                            .addOnCompleteListener(task -> {
                                setAuthUiEnabled(true);
                                if (task.isSuccessful()) {
                                    Toast.makeText(this,
                                            "Reset email sent. Check your inbox.",
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    String msg = task.getException() != null
                                            ? task.getException().getLocalizedMessage()
                                            : "Could not send reset email";
                                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setAuthUiEnabled(boolean enabled) {
        if (loginButton != null) loginButton.setEnabled(enabled);
        if (googleSignInButton != null) googleSignInButton.setEnabled(enabled);
        if (forgotPasswordLink != null) forgotPasswordLink.setEnabled(enabled);
        if (signUpLink != null) signUpLink.setEnabled(enabled);
        if (emailInput != null) emailInput.setEnabled(enabled);
        if (passwordInput != null) passwordInput.setEnabled(enabled);
        if (loginButton != null) {
            loginButton.setText(enabled ? "Log in" : "Please wait…");
        }
    }

    /**
     * Reflect auth outcome in the form (loading, errors, success path is handled by callers).
     */
    private void updateUI(FirebaseUser user) {
        if (user != null) {
            setAuthUiEnabled(false);
            if (emailInputLayout != null) emailInputLayout.setError(null);
            if (passwordInputLayout != null) passwordInputLayout.setError(null);
            String name = user.getDisplayName();
            if (TextUtils.isEmpty(name)) name = user.getEmail();
            Toast.makeText(this, "Welcome" + (name != null ? ", " + name : ""), Toast.LENGTH_SHORT).show();
        } else {
            setAuthUiEnabled(true);
            if (passwordInputLayout != null) {
                passwordInputLayout.setError("Sign-in failed. Check email and password.");
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Already signed in → skip login
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
        emailInputLayout = null;
        emailInput = null;
        passwordInputLayout = null;
        passwordInput = null;
        loginButton = null;
        googleSignInButton = null;
        forgotPasswordLink = null;
        signUpLink = null;
    }

}

package com.giga.tech1000.heartbeatz;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

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
                // Perform login (email/password)
                String email = emailInput.getText().toString().trim();
                String password = passwordInput.getText().toString();

                mAuth.signInWithEmailAndPassword(email, password)
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
        });

        // Forgot password link click
        forgotPasswordLink.setOnClickListener(v -> {
            // TODO: Implement forgot password flow
            Toast.makeText(this, "Forgot password clicked", Toast.LENGTH_SHORT).show();
        });

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

    private void updateUI(FirebaseUser user) {
        // TODO: Handle UI updates based on user state
        // For now, we'll just proceed to MainActivity if user is not null
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

package com.uottawa.servicenovigrad.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.uottawa.servicenovigrad.CurrentUser;
import com.uottawa.servicenovigrad.R;
import com.uottawa.servicenovigrad.errors.LoginError;
import com.uottawa.servicenovigrad.utils.Utils;

public class LoginActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        getSupportActionBar().hide();
    }

    @Override
    protected void onStart() {
        super.onStart();
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    @Override
    public void onBackPressed() {
        //Prevent user from going back because it could go back to the splash page
    }

    /**
     * Method to open the sign up activity.
     * @param view The current view.
     */
    public void createNewAccount(View view) {
        //Navigate to Sign Up Activity
        Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
        startActivity(intent);
    }

    /**
     * The method that will be called when the login button is pressed. Logs user into the app.
     * @param view The current view.
     */
    public void onLoginButtonPressed(View view) {
        //stores the editText from login page in variables
        final EditText login_emailEntry = (EditText) findViewById(R.id.login_emailEntry);
        final EditText login_passwordEntry = (EditText) findViewById(R.id.login_passwordEntry);
        //Get values of email and password variables
        final String email = (login_emailEntry.getText().toString());
        final String password = (login_passwordEntry.getText().toString());

        //Checks if user is trying to log in as admin
        final boolean admin = email.compareTo("admin") == 0 && password.compareTo("admin") == 0;
        //If logging in as admin
        if(admin) {
            //Stores information in the CurrentUser static object.
            CurrentUser.setInfo("admin", "admin", "admin", "admin");
            //Show message to user to notify that they are logging into the admin account.
            Toast.makeText(LoginActivity.this, "Logging in as admin", Toast.LENGTH_SHORT).show();
            //Navigate to Main Activity
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            //Signs into firebase anonymously, so that the admin can write to database
            auth.signInAnonymously();
        } else {
            //Validates input and gets error message
            final LoginError loginError = validateInput(email, password);

            //If there is an error
            if(loginError != LoginError.None) {
                //Show a snackbar with the error message
                Snackbar mySnackbar = Snackbar.make(findViewById(R.id.login_page), errorMessage(loginError), BaseTransientBottomBar.LENGTH_SHORT);
                //Add close button
                mySnackbar.setAction("CLOSE", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                    }
                });
                //Clear text when snackbar is closed
                mySnackbar.addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar snackbar, int event) {
                        switch(loginError) {
                            case FieldsEmpty:
                                break;
                            case EmailInvalid:
                                //Clears only the email entry
                                login_emailEntry.getText().clear();
                                break;
                            case PasswordTooShort:
                                //Clears only the password entry
                                login_passwordEntry.getText().clear();
                                break;
                        }
                    }
                });
                //Show snackbar
                mySnackbar.show();
            } else {
                //Sign into firebase
                auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        //Successful login

                        //Retrieve user info from firestore
                        firestore.collection("users")
                        .document(auth.getCurrentUser().getUid()) //Gets the document with the user UID, where the data should be stored.
                        .get()
                        .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                            @Override
                            public void onSuccess(DocumentSnapshot documentSnapshot) {
                                //Gets the name and role from the document snapshot
                                String n = (String) documentSnapshot.getData().get("name");
                                String r = (String) documentSnapshot.getData().get("role");
                                //Add info to CurrentUser
                                CurrentUser.setInfo(n, email, r, auth.getCurrentUser().getUid());

                                //Navigate to Main Activity when successful
                                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                                startActivity(intent);
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                //Show failed error
                                Snackbar snackbar = Snackbar.make(findViewById(R.id.login_page), "Failed to get user details from database!", BaseTransientBottomBar.LENGTH_SHORT);
                                snackbar.setAction("CLOSE", new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {}
                                });
                                snackbar.show();
                            }
                        });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        //Get firebase auth error message
                        String errorMessage = "Log In Error: " + ((FirebaseAuthException) e).getErrorCode().replace("ERROR_", "");

                        //Show failed error
                        Snackbar snackbar = Snackbar.make(getCurrentFocus(), errorMessage, BaseTransientBottomBar.LENGTH_SHORT);
                        //Add close button that does nothing
                        snackbar.setAction("CLOSE", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {}
                        });
                        snackbar.show();
                    }
                });
            }
        }
    }

    /**
     * Validates the inputs of login page
     * @param email the email to validate
     * @param password the password to validate
     * @return the LoginError value for the given inputs.
     */
    private LoginError validateInput(String email, String password) {
        //Checks if any field is empty
        if(email.isEmpty() || password.isEmpty()) {
            return LoginError.FieldsEmpty;
        }
        //Validates Email
        boolean validEmail = Utils.isEmailValid(email);
        if(!validEmail) {
            return LoginError.EmailInvalid;
        }
        //Checks if password is long enough
        if(password.length() < 6) {
            return LoginError.PasswordTooShort;
        }
        //Returns no error message if inputs are valid.
        return LoginError.None;
    }

    /**
     * Returns a string representation of the login error
     * @param error the login error
     * @return the string representation of the login error.
     */
    private String errorMessage(LoginError error) {
        switch(error) {
            case FieldsEmpty:
                return "One or more required fields are empty. ";
            case EmailInvalid:
                return "Email is invalid.";
            case PasswordTooShort:
                return "Password is too short.";
            default:
                return "";
        }
    }
}
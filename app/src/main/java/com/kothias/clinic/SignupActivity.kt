package com.kothias.clinic

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SignupActivity : AppCompatActivity() {
    private lateinit var mAuth: FirebaseAuth
    private lateinit var usernameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var dobEditText: EditText
    private lateinit var signupButton: Button
    private lateinit var verificationCodeEditText: EditText
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        mAuth = FirebaseAuth.getInstance()
        usernameEditText = findViewById(R.id.username)
        emailEditText = findViewById(R.id.email)
        phoneEditText = findViewById(R.id.phone)
        passwordEditText = findViewById(R.id.password)
        dobEditText = findViewById(R.id.dob)
        signupButton = findViewById(R.id.signup)
        verificationCodeEditText = findViewById(R.id.verification_code)

        // Set onClickListener for data input field
        dobEditText.setOnClickListener {
            // Get the current date
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            // Create and show the DatePickerDialog
            val datePickerDialog = DatePickerDialog(
                this, { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                    // Format and display the selected date
                    dobEditText.setText("$selectedDayOfMonth/${selectedMonth + 1}/$selectedYear")
                }, year, month, day
            )
            datePickerDialog.show()
        }

        // Set onClickListener for signup button
        signupButton.setOnClickListener {
            signUpUser()
            Toast.makeText(this, "Your request is being processed. Please wait...", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to sign up a new user
    private fun signUpUser() {
        val username = usernameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val phone = phoneEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val dob = dobEditText.text.toString().trim()

        // Validate the input fields
        if (TextUtils.isEmpty(username)) {
            usernameEditText.error = "Username is required."
            return
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.error = "Password is required."
            return
        }

        if (TextUtils.isEmpty(phone) && TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Please provide either a phone number or an email address.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!TextUtils.isEmpty(phone)) {
            // Verify by phone number
            verifyPhoneNumber(phone, username, email, password, dob)
        } else {
            // Verify by email
            verifyByEmail(username, email, password, dob)
        }
    }

    private fun verifyPhoneNumber(phone: String, username: String, email: String, password: String, dob: String) {
        var formattedPhone = phone
        if (!phone.startsWith("+91")) {
            formattedPhone = "+91$phone"
        }
        // Use Firebase Phone Authentication for verifying the phone number
        val options = PhoneAuthOptions.newBuilder(mAuth)
            .setPhoneNumber(formattedPhone)       // Phone number to verify
            .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
            .setActivity(this)                 // Activity (for callback binding)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Auto-retrieval or instant validation succeeded
                    signInWithPhoneAuthCredential(credential, username, email, dob, phone)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    // Handle error
                    Toast.makeText(this@SignupActivity, "Verification failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    // Save the verification ID for later use
                    this@SignupActivity.verificationId = verificationId

                    // Hide all fields except verification code and signup button
                    usernameEditText.visibility = View.GONE
                    emailEditText.visibility = View.GONE
                    phoneEditText.visibility = View.GONE
                    passwordEditText.visibility = View.GONE
                    dobEditText.visibility = View.GONE

                    // Show the verification code input field
                    verificationCodeEditText.visibility = View.VISIBLE

                    // Change signup button text to "Verify"
                    signupButton.visibility = View.VISIBLE
                    signupButton.text = "Verify"
                    signupButton.setOnClickListener {
                        val code = verificationCodeEditText.text.toString().trim()
                        if (code.isNotEmpty()) {
                            val credential = PhoneAuthProvider.getCredential(verificationId, code)
                            signInWithPhoneAuthCredential(credential, username, email, dob, phone)
                        } else {
                            Toast.makeText(this@SignupActivity, "Please enter the verification code.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential, username: String, email: String, dob: String, phone: String) {
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    saveUserToFirestore(username, email, dob, phone)
                    // Start HomepageActivity
                    val intent = Intent(this, HomePageActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Phone verification failed. Please try again later.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun verifyByEmail(username: String, email: String, password: String, dob: String) {
        // Create a new user with email and password
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Send email verification to the user
                    val user = mAuth.currentUser
                    user?.sendEmailVerification()
                        ?.addOnCompleteListener { verificationTask ->
                            if (verificationTask.isSuccessful) {
                                saveUserToFirestore(username, email, dob, "")
                                // Start LoginActivity
                                val intent = Intent(this, LoginActivity::class.java)
                                startActivity(intent)
                            } else {
                                Toast.makeText(this, "Failed to send verification email: ${verificationTask.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUserToFirestore(username: String, email: String, dob: String, phone: String) {
        val userId = mAuth.currentUser?.uid
        if (userId != null) {
            val userMap = hashMapOf(
                "username" to username,
                "email" to email,
                "dob" to dob,
                "phone" to phone
            )
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .set(userMap)
                .addOnSuccessListener {
                     Log.d("SignupActivity", "User details saved to Firestore: $userMap")
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to save user details: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
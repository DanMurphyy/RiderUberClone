package com.danmurphyy.rideruberclone

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.danmurphyy.rideruberclone.databinding.ActivitySplashScreenBinding
import com.danmurphyy.rideruberclone.model.RiderModel
import com.danmurphyy.rideruberclone.utils.Constants
import com.danmurphyy.rideruberclone.utils.UserUtils
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

@SuppressLint("CustomSplashScreen")
class SplashScreenActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashScreenBinding

    private lateinit var providers: List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener
    private lateinit var database: FirebaseDatabase
    private lateinit var riderInfoRef: DatabaseReference

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val response = IdpResponse.fromResultIntent(result.data)
        if (result.resultCode == RESULT_OK) {
            val user = FirebaseAuth.getInstance().currentUser
            // Handle successful sign-in
        } else {
            val error = response?.error?.message ?: "Unknown error"
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        delaySplashScreen()
    }

    @SuppressLint("CheckResult")
    private fun delaySplashScreen() {
        Completable.timer(1, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .subscribe {
                firebaseAuth.addAuthStateListener(listener)
            }
    }

    override fun onStop() {
        firebaseAuth.removeAuthStateListener(listener)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        init()
    }

    private fun init() {
        database = FirebaseDatabase.getInstance()
        riderInfoRef = database.getReference(Constants.RIDER_INFO_REFERENCE)

        providers = listOf(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )
        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener { myFirebaseAuth ->
            val user = myFirebaseAuth.currentUser
            if (user != null) {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    Log.d("Token", token)
                    UserUtils.updateToken(this, token)
                }.addOnFailureListener { e ->
                    Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                }
                checkUserFromFireBase()
            } else {
                showLoginLayout()
            }
        }
    }

    private fun checkUserFromFireBase() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            riderInfoRef.child(currentUser.uid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(this@SplashScreenActivity, error.message, Toast.LENGTH_SHORT)
                            .show()
                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        if (dataSnapshot.exists()) {
                            val model = dataSnapshot.getValue(RiderModel::class.java)
                            goToHomeActivity(model)

                        } else {
                            showRegisterLayout()
                        }
                    }

                })
        } else {
            showLoginLayout()
        }
    }

    private fun showRegisterLayout() {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.layout_register, null)
        builder.setView(dialogView)

        val alertDialog = builder.create()
        alertDialog.show()

        val edtFirstName = dialogView.findViewById<EditText>(R.id.edt_first_name)
        val edtLastName = dialogView.findViewById<EditText>(R.id.edt_last_name)
        val edtPhoneNumber = dialogView.findViewById<EditText>(R.id.edt_phone_number)
        val btnContinue = dialogView.findViewById<Button>(R.id.btn_continue)

        if (FirebaseAuth.getInstance().currentUser!!.phoneNumber != null) {
            edtPhoneNumber.setText(FirebaseAuth.getInstance().currentUser!!.phoneNumber)
        }

        btnContinue.setOnClickListener {
            val firstName = edtFirstName.text.toString()
            val lastName = edtLastName.text.toString()
            val phoneNumber = edtPhoneNumber.text.toString()

            if (firstName.isNotEmpty() && lastName.isNotEmpty() && phoneNumber.isNotEmpty()) {
                val model = RiderModel()
                model.firstName = edtFirstName.text.toString()
                model.lastName = edtLastName.text.toString()
                model.phoneNumber = edtPhoneNumber.text.toString()

                riderInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid).setValue(model)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Registration Successful!", Toast.LENGTH_LONG).show()
                        goToHomeActivity(model)
                        alertDialog.dismiss()
                        binding.progressBar.visibility = View.GONE

                    }
                    .addOnFailureListener {
                        Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                        alertDialog.dismiss()
                        binding.progressBar.visibility = View.GONE
                    }
            } else {
                Toast.makeText(this, "Please fill in all the details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.btn_phone_sign_in)
            .setGoogleButtonId(R.id.btn_google_sign_in)
            .build()

        signInLauncher.launch(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setTheme(R.style.LoginTheme)
                .setAvailableProviders(providers)
                .setIsSmartLockEnabled(false)
                .build()
        )
    }

    private fun goToHomeActivity(model: RiderModel?) {
        Constants.currentRider = model
        startActivity(Intent(this, RiderHomeActivity::class.java))
        finish()
    }

}
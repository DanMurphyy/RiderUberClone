package com.danmurphyy.rideruberclone

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.bumptech.glide.Glide
import com.danmurphyy.rideruberclone.databinding.ActivityRiderHomeBinding
import com.danmurphyy.rideruberclone.utils.Constants
import com.danmurphyy.rideruberclone.utils.UserUtils
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class RiderHomeActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityRiderHomeBinding
    private lateinit var navView: NavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController
    private lateinit var imgAvatar: ImageView
    private lateinit var imageUri: Uri
    private lateinit var waitingDialog: AlertDialog
    private lateinit var storageReference: StorageReference

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) showImageChooserPermissionDeniedDialog()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRiderHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarRiderHome.toolbar)
        drawerLayout = binding.drawerLayout
        navView = binding.navView
        navController = findNavController(R.id.nav_host_fragment_content_rider_home)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        init()
    }

    private fun init() {
        storageReference = FirebaseStorage.getInstance().reference

        waitingDialog = AlertDialog.Builder(this)
            .setMessage("Waiting...")
            .setCancelable(false).create()

        navView.setNavigationItemSelectedListener {
            if (it.itemId == R.id.nav_sign_out) {
                val builder = AlertDialog.Builder(this)
                builder.apply {
                    setTitle("Sign out")
                    setMessage("Do you want to sign out?")
                    setNegativeButton("CANCEL") { dialogInterface, _ -> dialogInterface.dismiss() }
                    setPositiveButton("SIGN OUT") { _, _ ->
                        FirebaseAuth.getInstance().signOut()
                        val intent =
                            Intent(this@RiderHomeActivity, SplashScreenActivity::class.java)
                        intent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }.setCancelable(false)
                    val dialog = builder.create()
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            .setTextColor(
                                ContextCompat.getColor(
                                    context, android.R.color.holo_red_dark
                                )
                            )
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                            .setTextColor(ContextCompat.getColor(context, R.color.colorAccent))
                    }
                    dialog.show()
                }
            }
            true
        }
        val headerView = navView.getHeaderView(0)
        val txtName = headerView.findViewById<View>(R.id.txt_name) as TextView
        val txtPhone = headerView.findViewById<View>(R.id.txt_phone) as TextView
        imgAvatar = headerView.findViewById<View>(R.id.img_avatar) as ImageView
        txtName.text = Constants.buildWelcomeMessage()
        txtPhone.text = Constants.currentRider?.phoneNumber?:""

        if (Constants.currentRider != null && !TextUtils.isEmpty(Constants.currentRider!!.avatar)) {
            Glide.with(this)
                .load(Constants.currentRider!!.avatar)
                .into(imgAvatar)
        }

        imgAvatar.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
                getContent.launch(Intent.createChooser(intent, "Select Picture"))
            } else {
                permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

    }

    private val getContent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                if (data != null && data.data != null) {
                    imageUri = data.data!!
                    imgAvatar.setImageURI(imageUri)
                    showDialogUpload()
                }
            }
        }

    private fun showDialogUpload() {
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setTitle("Change avatar")
            setMessage("Do you want to change avatar")
            setNegativeButton("CANCEL") { dialogInterface, _ -> dialogInterface.dismiss() }
            setPositiveButton("CHANGE") { _, _ ->
                waitingDialog.show()
                val avatarFolder =
                    storageReference.child("avatar/" + FirebaseAuth.getInstance().currentUser!!.uid)
                avatarFolder.putFile(imageUri)
                    .addOnFailureListener { error ->
                        Snackbar.make(drawerLayout, error.message!!, Snackbar.LENGTH_LONG).show()
                        waitingDialog.dismiss()
                    }
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            avatarFolder.downloadUrl.addOnSuccessListener { uri ->
                                val updateData = HashMap<String, Any>()
                                updateData["avatar"] = uri.toString()
                                UserUtils.updateUse(this@RiderHomeActivity, updateData)
                            }
                        }
                        waitingDialog.dismiss()
                    }
                    .addOnProgressListener {
                        val progress = (100.0 * it.bytesTransferred / it.totalByteCount)
                        waitingDialog.setMessage(
                            StringBuilder("Uploading: ").append(progress).append("%")
                        )
                    }
            }.setCancelable(false)
            val dialog = builder.create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(context.getColor(android.R.color.holo_red_dark))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(context.getColor(R.color.colorAccent))
            }
            dialog.show()
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.rider_home, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_rider_home)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun showImageChooserPermissionDeniedDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(getString(R.string.denied_permissions))
        builder.setPositiveButton(getString(R.string.go_to_settings)) { _, _ ->
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", this.packageName, null)
                intent.data = uri
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }
        builder.setNegativeButton(getString(R.string.close_dialog_perm)) { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }
}
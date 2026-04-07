package com.example.companion

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.core.AdbManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import androidx.core.content.edit

class SetupActivity : AppCompatActivity() {
    private lateinit var etIp: EditText
    private lateinit var etAdbPort: EditText
    private lateinit var btnEnable: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var tvStatus: TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_setup)

        etIp      = findViewById(R.id.etIp)
        etAdbPort = findViewById(R.id.etAdbPort)
        btnEnable = findViewById(R.id.btnEnable)
        progress  = findViewById(R.id.progress)
        tvStatus  = findViewById(R.id.tvStatus)

        etAdbPort.setText("5555")

        btnEnable.setOnClickListener {

            val ip = etIp.text.toString().trim()

            if(ip.isBlank()){
                tvStatus.text = "Please enter the FireStick IP address."
                return@setOnClickListener
            }

            val adbPort=etAdbPort.text.toString().toIntOrNull() ?: 5555

            setLoading(true)
            tvStatus.text="Connecting to FireStick..."

            lifecycleScope.launch{
                val result= AdbManager.grantNotificationListener(applicationContext,ip,adbPort)
                setLoading(false)


                tvStatus.text = if (result.isSuccess) {
                    "✅ Success! TRP is ready to track media."
                } else {
                    "❌ Failed: ${result.exceptionOrNull()?.message}"
                }

                if(result.isSuccess){
                    // Save so TV app knows permission is granted
                    getSharedPreferences("trp_prefs", MODE_PRIVATE)
                        .edit {
                            putBoolean("permission_granted", true)
                        }

                    btnEnable.isEnabled = false
                    btnEnable.text = "Permission Enabled ✓"
                }

            }

        }
    }

    private fun setLoading(loading: Boolean) {

        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnEnable.isEnabled = !loading
    }
}
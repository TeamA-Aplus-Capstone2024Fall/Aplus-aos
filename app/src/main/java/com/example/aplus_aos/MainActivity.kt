package com.example.aplus_aos

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var photoUri: Uri
    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>

    private val REQUEST_CODE_PERMISSIONS = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                view?.loadUrl(url)
                return true // true를 반환하면 새 창이 열리지 않음
            }
        }
        webView.loadUrl("https://skku-aplus-web.vercel.app/")
//        webView.loadUrl("http://192.168.1.113:3000")

        takePictureLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    uploadImage(photoUri)
                }
            }
    }

    private fun openCamera() = runOnUiThread {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        if (intent.resolveActivity(packageManager) != null) {
            externalCacheDir?.let { cacheDir ->
                val photoFile = File.createTempFile("photo_", ".jpg", cacheDir)
                photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                takePictureLauncher.launch(intent)
            }
        } else {
            Toast.makeText(this, "카메라를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions(){
        val cameraPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
        //val storagePermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
//        val imagePermission = ContextCompat.checkSelfPermission(this,android.Manifest.permission.READ_MEDIA_IMAGES)
        val imagePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            PackageManager.PERMISSION_GRANTED
        }

        if(cameraPermission!= PackageManager.PERMISSION_GRANTED ||
            imagePermission != PackageManager.PERMISSION_GRANTED) {
            Log.d("permission","request_permission");
            // 권한이 부여되지 않은 경우 요청
            val permissions = mutableListOf(android.Manifest.permission.CAMERA)
            if (imagePermission != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.READ_MEDIA_IMAGES),REQUEST_CODE_PERMISSIONS)
        }
        else{
            // 권한이 이미 부여된 경우 카메라 열기
            Log.d("permission","already_permitted");
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode==REQUEST_CODE_PERMISSIONS){
            if(grantResults.isNotEmpty() && grantResults.all {it==PackageManager.PERMISSION_GRANTED}){
                Log.d("permssion","check_permission")
                openCamera()
            }
            else{
                Toast.makeText(this,"카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadImage(uri: Uri){
        val client = OkHttpClient()

        val inputStream = contentResolver.openInputStream(uri)
        val file = File.createTempFile("uploaded_",".jpg",externalCacheDir)

        inputStream?.use { input ->
            FileOutputStream(file).use { output->
                input.copyTo(output)
            }
        }

        val mediaType = "image/jpeg".toMediaType()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file",file.name, file.asRequestBody(mediaType))
            .build()

        val request = Request.Builder()
            .url("https://1269kdr6v9.execute-api.ap-northeast-2.amazonaws.com/dev/scan")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                val responseBody = e.message
                println("Response: $responseBody")

                runOnUiThread {
                    webView.evaluateJavascript(
                        "javascript:handleProductInfoError('$responseBody');",
                        null
                    )
                }
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    println("Response: $responseBody")

                    //JSON 파싱하기
                    responseBody?.let {
                        val jsonObject = JSONObject(it)
                        val productName = jsonObject.getString("product_name")
                        val expirationDate = jsonObject.getString("expiration_date")
                        val storageOptions = listOf("상온", "실온", "냉장", "냉동")
                        val storageMethod = storageOptions.find { expirationDate.contains(it)} ?: ""
                        println("Product Name: $productName")

                        runOnUiThread {
                            webView.evaluateJavascript(
                                "javascript:handleProductInfo('$productName', '$storageMethod');",
                                null
                            )
                        }
                    }
                }
                else{
                    // 에러 응답 본문 읽기
                    val responseBody = response.body?.string()
                    println("Response: $responseBody")

                    responseBody?.let {
                        val jsonObject = JSONObject(it)
                        val errorMessage = jsonObject.getString("error")

                        runOnUiThread {
                            webView.evaluateJavascript(
                                "javascript:handleProductInfoError('$errorMessage');",
                                null
                            )
                        }
                    }
                }
            }
        })
    }

    class WebAppInterface(private val activity: MainActivity) {
        @JavascriptInterface
        fun openCameraWithQuery(query: String) {
            activity.checkPermissions() // 권한 확인 후 카메라 열기
        }
    }
}

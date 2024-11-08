package com.example.cameraapplication

import android.app.Activity
import android.app.Application
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException



class App: Application(){
    override fun onCreate() {
        super.onCreate()
        val dexOutputDirectory: File = codeCacheDir
        dexOutputDirectory.setReadOnly()
    }
}


class MainActivity : AppCompatActivity() {

    private lateinit var photoUri: Uri
    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>

    private val REQUEST_CODE_PERMISSIONS = 1001



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("start", "app_start")

        // ActivityResultLauncher 초기화
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
            if (result.resultCode == Activity.RESULT_OK){
                uploadImage(photoUri)
            }
        }
        Log.d("check", "permission")
        checkPermissions()
    }

    private fun openCamera(){
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        if(intent.resolveActivity(packageManager)!=null){
            //사진 저장 파일
            Log.d("PhotoSave", "Start")
            externalCacheDir?.let { cacheDir->
                //임시 파일 생성
                val photoFile = File.createTempFile("photo_",".jpg", cacheDir)
                Log.d("PhotoFile","File created at : ${photoFile.absolutePath}")

                //FileProvider를 통해 URI 생성
                photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider",photoFile)
                intent.putExtra(MediaStore.EXTRA_OUTPUT,photoUri)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                takePictureLauncher.launch(intent)

            } ?:run {
                Log.d("PhotoSave", "External cache directory is not available")
            }

            /*val photoFile= File.createTempFile("photo_",".jpg", externalCacheDir)
            Log.d("PhotoFile", "File created at: ${photoFile.absolutePath}")
            photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider",photoFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)

            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            takePictureLauncher.launch(intent)
            */

        }
        else {
            Toast.makeText(this, "카메라를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }

    }

    private fun checkPermissions(){
        val cameraPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
        //val storagePermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val imagePermission = ContextCompat.checkSelfPermission(this,android.Manifest.permission.READ_MEDIA_IMAGES)
        if(cameraPermission!=PackageManager.PERMISSION_GRANTED ||
            imagePermission != PackageManager.PERMISSION_GRANTED) {
            Log.d("permission","request_permission");
            // 권한이 부여되지 않은 경우 요청
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
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode==REQUEST_CODE_PERMISSIONS){
            if(grantResults.isNotEmpty() && grantResults.all {it==PackageManager.PERMISSION_GRANTED}){
                Log.d("permssion","check_permission")
                openCamera()
            }
            else{
                Toast.makeText(this,"카메라 권한이 필요합니다",Toast.LENGTH_SHORT).show()
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
                        println("Product Name: $productName")
                    }
                }
                else{
                    println("Error: ${response.code}")
                }
            }
        })
    }

    companion object{
        private const val REQUEST_IMAGE_CAPTURE = 1
    }
}
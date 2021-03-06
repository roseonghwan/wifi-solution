package com.CVproject.wifi_solution

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import kotlinx.android.synthetic.main.activity_fragment_scan.*
import kotlinx.android.synthetic.main.activity_fragment_scan.view.*

class Fragment_scan : Fragment(){

    lateinit var mAdView: AdView
    var imageCapture : ImageCapture? = null
    var lineText: String = ""
    lateinit var wifiManager : WifiManager
    lateinit var wifiList: MutableList<String>
    var database = FirebaseDatabase.getInstance()
    var myRef = database.reference

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.activity_fragment_scan, container, false)

        mAdView = view.findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)

        var e = object : ValueEventListener{
            override fun onCancelled(error: DatabaseError) {
            }

            override fun onDataChange(snapshot: DataSnapshot) {
                wifiList = mutableListOf()

                for(data in snapshot.children){
                    if(data.key.equals("wifiList")) {
                       for(d in data.children){
                           wifiList.add(d.value.toString())
                       }

                    }
                }
            }
        }
        myRef.addValueEventListener(e)

        view.checkbtn.setOnClickListener {

            wifiManager = view.context?.getSystemService(Context.WIFI_SERVICE) as WifiManager

            for (data in wifiList){
                if (data == null || data=="")continue
                else {
                    NetworkConnector(wifiManager, context).connectWifi(data, textView.text.toString())
                    if(NetworkConnector(wifiManager, context).isWifiConnected(context)){
                        NetworkConnector(wifiManager, context).getCurSSID()
                        break
                    }
                }
            }
            Toast.makeText(context,"비밀번호를 다시 확인해주세요.", Toast.LENGTH_SHORT).show() // add Toast
        }
        bindCameraUseCase(view)
        view.imageButton.setOnClickListener {
            takePhoto(view)
        }
        return view
    }


    fun takePhoto(view: View){
        imageCapture?.takePicture(ContextCompat.getMainExecutor(view.context), object : ImageCapture.OnImageCapturedCallback(){
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                textRecognize(bitmap)
                super.onCaptureSuccess(image)
            }
        })
    }
    fun textRecognize(bitmap: Bitmap){
        FirebaseVision.getInstance().onDeviceTextRecognizer.processImage(FirebaseVisionImage.fromBitmap(bitmap))
            .addOnSuccessListener {firebaseVisionText ->
                for(block in firebaseVisionText.textBlocks){

                    lineText = block.text
                    view?.textView?.setText(lineText)
                    showPasswordPopup()
                    break

                }
            }
    }


    private fun showPasswordPopup() {
     val inflater = view?.context?.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.password_popup, null)
        var password: TextView = view.findViewById(R.id.editText)
        password.text = lineText

        val alertDialog = AlertDialog.Builder(view.context)
            .setTitle("비밀번호 확인")
            .setPositiveButton("확인"){ dialog, which ->
                textView.text = "${password.text}"
                Toast.makeText(context,"확인 버튼을 누르고, 연결될 때까지 잠시만 기다려주세요.", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("취소",null)
            .create()

        alertDialog.setView(view)
        alertDialog.show()
    }




    fun imageProxyToBitmap(imageProxy: ImageProxy) : Bitmap{
        val buffer = imageProxy.planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Rotate bitmap
        val matrix = Matrix()
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

        return Bitmap.createBitmap(bitmap,0,0,bitmap.width, bitmap.height,matrix,true)
    }

    fun bindCameraUseCase(view: View){
        val rotation = 0
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(view.context)
        cameraProviderFuture?.addListener(Runnable {

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .build()

            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(960,1280))
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(rotation)
                .build()

            cameraProvider.unbindAll()

            val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            preview.setSurfaceProvider(viewFinder.createSurfaceProvider(camera.cameraInfo))

        },ContextCompat.getMainExecutor(view.context))
    }


}
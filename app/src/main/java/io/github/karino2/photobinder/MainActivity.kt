package io.github.karino2.photobinder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var overlayView: OverlayView
    private lateinit var btnCorrectShare: MaterialButton
    private lateinit var btnTakePhoto: MaterialButton
    private lateinit var btnRotate: MaterialButton
    private lateinit var buttonContainer: View
    private var photoUri: Uri? = null
    private var originalBitmap: Bitmap? = null
    private var rotatedBitmap: Bitmap? = null
    
    private fun getResultsDir() = File(cacheDir, "results").apply {
        if (!exists()) mkdirs()
    }

    private fun getResultFiles() = getResultsDir().listFiles()?.filter { it.name.startsWith("result_") }?.sortedBy { it.name } ?: emptyList()

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        println("deb callback")
        if (success) {
            println("success")
            photoUri?.let { uri ->
                println("loadBitmap")
                loadBitmap(uri)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCamera() {
        preparePhotoUri()
        photoUri?.let { takePictureLauncher.launch(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Clear previous results on startup so each session starts fresh
        if (savedInstanceState == null) {
            clearOldResults()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        imageView = findViewById(R.id.imageView)
        overlayView = findViewById(R.id.overlayView)
        btnCorrectShare = findViewById(R.id.btnCorrectShare)
        buttonContainer = findViewById(R.id.buttonContainer)
        btnRotate = findViewById(R.id.btnRotate)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)

        savedInstanceState?.getParcelable<Uri>("photoUri")?.let {
            photoUri = it
        }

        btnTakePhoto.setOnClickListener {
            if (overlayView.points.size == 4) {
                processAndAdd()
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnRotate.setOnClickListener {
            rotateImage()
        }

        btnCorrectShare.setOnClickListener {
            if (overlayView.points.size == 4) {
                processAndAdd()
                shareAllResults()
            } else {
                Toast.makeText(this, "Select 4 corners first", Toast.LENGTH_SHORT).show()
            }
        }

        overlayView.onPointsChanged = { size ->
            if (size == 4) {
                btnCorrectShare.visibility = View.VISIBLE
                btnRotate.visibility = View.VISIBLE
            } else {
                btnCorrectShare.visibility = View.INVISIBLE
                btnRotate.visibility = View.INVISIBLE
            }
            updateTakePhotoButton()
        }
        
        updateTakePhotoButton()
    }

    private fun updateTakePhotoButton() {
        if (overlayView.points.size == 4) {
            btnTakePhoto.text = "More"
            btnTakePhoto.setIconResource(R.drawable.ic_add)
        } else {
            val files = getResultFiles()
            if (files.isEmpty()) {
                btnTakePhoto.text = "Take Photo"
                btnTakePhoto.setIconResource(R.drawable.ic_camera)
            } else {
                btnTakePhoto.text = "More"
                btnTakePhoto.setIconResource(R.drawable.ic_add)
            }
        }
    }

    private fun clearOldResults() {
        getResultFiles().forEach { it.delete() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("photoUri", photoUri)
    }

    private fun preparePhotoUri() {
        val imagesDir = File(cacheDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        val photoFile = File(imagesDir, "photo.jpg")
        photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
    }

    private fun loadBitmap(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                originalBitmap = bitmap
                rotatedBitmap = bitmap
                imageView.setImageBitmap(bitmap)
                println("setImageBitmap")
                
                initOverlayPoints(bitmap)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun rotateImage() {
        val bitmap = rotatedBitmap ?: return
        val matrix = Matrix()
        matrix.postRotate(90f)
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        rotatedBitmap = rotated
        imageView.setImageBitmap(rotated)
        initOverlayPoints(rotated)
    }

    private fun initOverlayPoints(bitmap: Bitmap) {
        val runInit = {
            val viewWidth = overlayView.width.toFloat()
            val viewHeight = overlayView.height.toFloat()
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()

            val scale = minOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
            val dx = (viewWidth - bitmapWidth * scale) / 2
            val dy = (viewHeight - bitmapHeight * scale) / 2

            val initPoints = listOf(
                PointF(dx, dy), // TL
                PointF(dx + bitmapWidth * scale, dy), // TR
                PointF(dx + bitmapWidth * scale, dy + bitmapHeight * scale), // BR
                PointF(dx, dy + bitmapHeight * scale) // BL
            )
            overlayView.initializePoints(initPoints)
        }

        if (overlayView.width > 0 && overlayView.height > 0) {
            runInit()
        } else {
            overlayView.post {
                runInit()
            }
        }
    }

    private fun processAndAdd() {
        val bitmap = rotatedBitmap ?: return

        val viewWidth = overlayView.width.toFloat()
        val viewHeight = overlayView.height.toFloat()
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        val scale = minOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        val dx = (viewWidth - bitmapWidth * scale) / 2
        val dy = (viewHeight - bitmapHeight * scale) / 2

        val srcPoints = FloatArray(8)
        for (i in 0 until 4) {
            srcPoints[i * 2] = (overlayView.points[i].x - dx) / scale
            srcPoints[i * 2 + 1] = (overlayView.points[i].y - dy) / scale
        }

        // Target: A4 aspect ratio (approx 1:1.414)
        val resultWidth = 1000
        val resultHeight = 1414

        val dstPoints = floatArrayOf(
            0f, 0f,
            resultWidth.toFloat(), 0f,
            resultWidth.toFloat(), resultHeight.toFloat(),
            0f, resultHeight.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val resultBitmap = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))

        val resultFile = File(getResultsDir(), "result_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(resultFile).use { out ->
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        println("Saved result to ${resultFile.absolutePath}")
    }

    private fun shareAllResults() {
        val resultFiles = getResultFiles()
        if (resultFiles.isEmpty()) return

        println("deb2 result size=${resultFiles.size}")
        val uris = ArrayList<Uri>()
        try {
            for (file in resultFiles) {
                uris.add(FileProvider.getUriForFile(this, "${packageName}.fileprovider", file))
            }

            println("uris.size = ${uris.size}")
            val intent = if (uris.size == 1) {
                println("send single")
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
            } else {
                println("send multi")
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/jpeg"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Share image(s)"))
            
            // Clear UI for next time. 
            // We no longer delete files immediately here because the receiving app 
            // needs time to read them. System will clean up cacheDir when needed.
            btnCorrectShare.visibility = View.INVISIBLE
            btnRotate.visibility = View.INVISIBLE
            imageView.setImageBitmap(null)
            overlayView.clearPoints()
            updateTakePhotoButton()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

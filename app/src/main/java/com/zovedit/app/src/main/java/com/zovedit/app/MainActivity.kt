package com.zovedit.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*
import java.io.OutputStream

class MainActivity : Activity() {

    private lateinit var photoView: MaskableImageView
    private lateinit var status: TextView

    private var original: Bitmap? = null
    private var brightness = 0f
    private var contrast = 1f
    private var saturation = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(Color.rgb(18, 20, 24))
        }

        val title = TextView(this).apply {
            text = "ZOV EDIT"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        root.addView(title)

        photoView = MaskableImageView(this).apply {
            setBackgroundColor(Color.rgb(35, 38, 44))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        root.addView(photoView,
            LinearLayout.LayoutParams(
                -1, 0, 1f
            )
        )

        status = TextView(this).apply {
            text = "Open a photo to start editing"
            setTextColor(Color.WHITE)
        }
        root.addView(status)

        val buttons = LinearLayout(this)

        buttons.addView(button("Open Photo") {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, 101)
        })

        buttons.addView(button("Brush Mask") {
            photoView.brushMode = !photoView.brushMode
            status.text = "Brush: ${photoView.brushMode}"
        })

        buttons.addView(button("Clear Mask") {
            photoView.clearMask()
        })

        root.addView(buttons)

        slider(root, "Brightness", -100, 100, 0) {
            brightness = it / 100f
            updatePreview()
        }

        slider(root, "Contrast", 50, 200, 100) {
            contrast = it / 100f
            updatePreview()
        }

        slider(root, "Saturation", 0, 200, 100) {
            saturation = it / 100f
            updatePreview()
        }

        root.addView(button("Save Photo") {
            savePhoto()
        })

        setContentView(root)
    }

    private fun button(
        name: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = name
            setOnClickListener { action() }
        }
    }

    private fun slider(
        root: LinearLayout,
        name: String,
        min: Int,
        max: Int,
        initial: Int,
        change: (Int) -> Unit
    ) {
        val label = TextView(this).apply {
            text = name
            setTextColor(Color.WHITE)
        }

        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = initial - min

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        bar: SeekBar?,
                        value: Int,
                        user: Boolean
                    ) {
                        change(value + min)
                    }

                    override fun onStartTrackingTouch(
                        bar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        bar: SeekBar?
                    ) {}
                }
            )
        }

        root.addView(label)
        root.addView(seek)
    }

    @Deprecated("Legacy image picker")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return

            contentResolver.openInputStream(uri)?.use {
                original = BitmapFactory.decodeStream(it)
                photoView.setImageBitmap(original)
                updatePreview()
                status.text = "Photo loaded"
            }
        }
    }

    private fun updatePreview() {
        if (original == null) return

        val matrix = ColorMatrix().apply {
            setSaturation(saturation)
        }

        val values = floatArrayOf(
            contrast, 0f, 0f, 0f, brightness * 255,
            0f, contrast, 0f, 0f, brightness * 255,
            0f, 0f, contrast, 0f, brightness * 255,
            0f, 0f, 0f, 1f, 0f
        )

        matrix.postConcat(ColorMatrix(values))
        photoView.colorFilter = ColorMatrixColorFilter(matrix)
        photoView.invalidate()
    }

    private fun savePhoto() {
        val bitmap = original ?: return

        val output = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(output)

        val paint = Paint().apply {
            colorFilter = photoView.colorFilter
        }

        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "ZOV_EDIT_${System.currentTimeMillis()}.jpg"
            )
            put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
            )
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/ZOV EDIT"
            )
        }

        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return

        contentResolver.openOutputStream(uri)?.use {
            output.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }

        Toast.makeText(
            this,
            "Photo Saved!",
            Toast.LENGTH_SHORT
        ).show()
    }

    class MaskableImageView(context: android.content.Context) :
        ImageView(context) {

        var brushMode = false

        private val paths = mutableListOf<Path>()
        private var active: Path? = null

        private val brush = Paint().apply {
            color = Color.argb(150, 0, 220, 255)
            style = Paint.Style.STROKE
            strokeWidth = 28f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        fun clearMask() {
            paths.clear()
            active = null
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            paths.forEach {
                canvas.drawPath(it, brush)
            }

            active?.let {
                canvas.drawPath(it, brush)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!brushMode) return super.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    active = Path().apply {
                        moveTo(event.x, event.y)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    active?.lineTo(event.x, event.y)
                }

                MotionEvent.ACTION_UP -> {
                    active?.let { paths.add(it) }
                    active = null
                }
            }

            invalidate()
            return true
        }
    }
}

package com.nashi.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/** 物品照片的本地管理: 拍照后压缩摆正、删除时清理文件, 避免无限增长。 */
object PhotoStore {
    private const val MAX_SIDE = 1600
    private const val QUALITY = 85

    fun compressInPlace(file: File) {
        if (!file.isFile) return
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return
        val rotated = rotateByExif(file, decoded)
        runCatching {
            FileOutputStream(file).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
        }
        if (rotated !== decoded) decoded.recycle()
        rotated.recycle()
    }

    /** 只删自己照片目录里的文件, 备份导入等外部路径不动。 */
    fun delete(path: String?) {
        val file = path?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        if (file.isFile && file.parentFile?.name == "photos") file.delete()
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_SIDE || height / sample > MAX_SIDE) sample *= 2
        return sample
    }

    private fun rotateByExif(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

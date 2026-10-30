package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "pixelloop_prefs")

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

data class ThemePreferences(
    val useDynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

class GifRepository(
    private val context: Context,
    private val gifDao: GifDao
) {
    private val dynamicColorKey = booleanPreferencesKey("use_dynamic_color")
    private val themeModeKey = stringPreferencesKey("theme_mode_v2")

    val allCreations: Flow<List<GifCreationEntity>> = gifDao.observeAllCreations()

    val themePreferences: Flow<ThemePreferences> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[themeModeKey] ?: ThemeMode.SYSTEM.name
        val mode = runCatching { ThemeMode.valueOf(modeStr) }.getOrDefault(ThemeMode.SYSTEM)
        ThemePreferences(
            useDynamicColor = prefs[dynamicColorKey] ?: true,
            themeMode = mode
        )
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun saveCreation(entity: GifCreationEntity): Long {
        return gifDao.insertCreation(entity)
    }

    suspend fun deleteCreation(entity: GifCreationEntity) = withContext(Dispatchers.IO) {
        runCatching {
            val gifFile = File(entity.filePath)
            if (gifFile.exists()) gifFile.delete()
            val thumbFile = File(entity.thumbnailPath)
            if (thumbFile.exists()) thumbFile.delete()
        }
        gifDao.deleteById(entity.id)
    }

    suspend fun exportGifToSystemGallery(entity: GifCreationEntity): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceFile = File(entity.filePath)
                require(sourceFile.exists()) { "GIF 源文件不存在" }

                val resolver = context.contentResolver
                val displayName = "${entity.title.replace(" ", "_")}_${System.currentTimeMillis()}.gif"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                    put(MediaStore.Images.Media.WIDTH, entity.width)
                    put(MediaStore.Images.Media.HEIGHT, entity.height)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/PixelLoop"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val itemUri = resolver.insert(collection, contentValues)
                    ?: error("无法创建系统相册条目")

                resolver.openOutputStream(itemUri)?.use { outStream ->
                    sourceFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                } ?: error("无法写入相册输出流")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)
                }

                "Pictures/PixelLoop/$displayName"
            }
        }

    fun shareGifToSocial(entity: GifCreationEntity): Result<Unit> {
        return runCatching {
            val file = File(entity.filePath)
            require(file.exists()) { "GIF 文件不存在" }
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/gif"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(
                shareIntent,
                context.getString(R.string.share_chooser_title)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}

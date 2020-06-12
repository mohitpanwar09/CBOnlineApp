package com.codingblocks.cbonlineapp.workers

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.codingblocks.cbonlineapp.R
import com.codingblocks.cbonlineapp.database.ContentDao
import com.codingblocks.cbonlineapp.database.SectionWithContentsDao
import com.codingblocks.cbonlineapp.database.models.SectionContentHolder
import com.codingblocks.cbonlineapp.util.DOWNLOAD_CHANNEL_ID
import com.codingblocks.cbonlineapp.util.RUN_ATTEMPT_ID
import com.codingblocks.cbonlineapp.util.SECTION_ID
import com.codingblocks.onlineapi.CBOnlineLib
import com.google.gson.JsonObject
import com.vdocipher.aegis.media.ErrorDescription
import com.vdocipher.aegis.offline.DownloadOptions
import com.vdocipher.aegis.offline.DownloadRequest
import com.vdocipher.aegis.offline.DownloadSelections
import com.vdocipher.aegis.offline.DownloadStatus
import com.vdocipher.aegis.offline.OptionsDownloader
import com.vdocipher.aegis.offline.VdoDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import retrofit2.Response
import java.io.File

/**
 * A Foreground Service to download files
 */

class SectionService : Service(), VdoDownloadManager.EventListener {

    companion object {
        fun startService(context: Context, sectionId: String, attemptId: String) {
            val startIntent = Intent(context, SectionService::class.java)
            startIntent.putExtra(SECTION_ID, sectionId)
            startIntent.putExtra(RUN_ATTEMPT_ID, attemptId)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, SectionService::class.java)
            context.stopService(stopIntent)
        }

        const val NOTIFICATION_ID = 10
        const val ACTION_STOP = "ACTION_STOP_FOREGROUND_SERVICE"
        private val downloadList = hashMapOf<String, SectionContentHolder.DownloadableContent>()
    }

    private val notificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private lateinit var notification: NotificationCompat.Builder

    private val contentDao: ContentDao by inject()
    private val sectionWithContentsDao: SectionWithContentsDao by inject()

    var sectionId: String? = null
    var attemptId: String? = null
    var sectionName: String? = null
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == ACTION_STOP) {
            stopForeground(true)
            stopSelf()
            notificationManager.cancel(NOTIFICATION_ID)
        } else {
            downloadList.clear()
            attemptId = intent.getStringExtra(RUN_ATTEMPT_ID)
            sectionId = intent.getStringExtra(SECTION_ID)
            GlobalScope.launch {
                createNotification(withContext(Dispatchers.IO) { sectionWithContentsDao.getVideoIdsWithSectionId(sectionId!!, attemptId!!) })
            }
        }
        return START_NOT_STICKY
    }


    private suspend fun createNotification(sectionList: List<SectionContentHolder.DownloadableContent>) {
        if (sectionList.isNotEmpty()) {
            sectionName = sectionList.first().name
            notification = NotificationCompat.Builder(applicationContext, DOWNLOAD_CHANNEL_ID).apply {
                setSmallIcon(R.drawable.ic_file_download)
                setContentTitle("Downloading $sectionName")
                setOnlyAlertOnce(true)
                setLargeIcon(BitmapFactory.decodeResource(applicationContext.resources, R.mipmap.ic_launcher))
                setStyle(NotificationCompat.BigTextStyle().bigText("0 out of ${sectionList.size} downloaded"))
                setProgress(sectionList.size, 0, false)
                setOngoing(true)
            }
            startDownload(sectionList)
            val stopSelf = Intent(this, SectionService::class.java)
            stopSelf.action = ACTION_STOP
            val pStopSelf = PendingIntent.getService(this, 0, stopSelf, /*Stop Service*/PendingIntent.FLAG_CANCEL_CURRENT)
            notification.addAction(R.drawable.ic_pause_white_24dp, "Cancel", pStopSelf)
            startForeground(1, notification.build())

        }
    }

    private suspend fun startDownload(list: List<SectionContentHolder.DownloadableContent>) {
        list.forEach { content ->
            val response: Response<JsonObject> = withContext(Dispatchers.IO) {
                CBOnlineLib.api.getOtp(content.videoId, content.sectionId, attemptId!!, true)
            }
            if (response.isSuccessful) {
                response.body()?.let {
                    downloadList[content.videoId] = (content)
                    val mOtp = it.get("otp").asString
                    val mPlaybackInfo = it.get("playbackInfo").asString
                    initializeDownload(mOtp, mPlaybackInfo, content.videoId)
                }
            }
        }
    }

    private fun initializeDownload(mOtp: String, mPlaybackInfo: String, videoId: String) {
        val optionsDownloader = OptionsDownloader()
        // assuming we have otp and playbackInfo
        optionsDownloader.downloadOptionsWithOtp(
            mOtp,
            mPlaybackInfo,
            object : OptionsDownloader.Callback {
                override fun onOptionsReceived(options: DownloadOptions) {
                    // we have received the available download options
                    val selectionIndices = intArrayOf(0, 1)
                    val downloadSelections = DownloadSelections(options, selectionIndices)
                    val file =
                        applicationContext.getExternalFilesDir(Environment.getDataDirectory().absolutePath)
                    val folderFile = File(file, "/$videoId")
                    if (!folderFile.exists()) {
                        folderFile.mkdir()
                    }
                    val request =
                        DownloadRequest.Builder(downloadSelections, folderFile.absolutePath).build()
                    val vdoDownloadManager = VdoDownloadManager.getInstance(applicationContext)
                    // enqueue request to VdoDownloadManager for download
                    try {
                        vdoDownloadManager.enqueue(request)
                        vdoDownloadManager.addEventListener(this@SectionService)
                    } catch (e: IllegalArgumentException) {
                    } catch (e: IllegalStateException) {
                    }
                }

                override fun onOptionsNotReceived(errDesc: ErrorDescription) {
                    // there was an error downloading the available options
                    Log.e("Service Error", "onOptionsNotReceived : $errDesc")
                }
            })
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onChanged(p0: String?, p1: DownloadStatus?) {
        notification.apply {
            setStyle(NotificationCompat.BigTextStyle().bigText("${downloadList.filterValues { it.isDownloaded }.size} out of ${downloadList.size} downloaded( Current ${p1?.downloadPercent}% )"))
        }
        notificationManager.notify(1, notification.build())
    }

    override fun onDeleted(p0: String?) {
    }

    override fun onFailed(videoId: String, p1: DownloadStatus?) {
        downloadList.remove(videoId)
    }

    override fun onQueued(p0: String?, p1: DownloadStatus?) {
    }

    override fun onCompleted(videoId: String, p1: DownloadStatus?) {
        val data = downloadList[videoId]
        GlobalScope.launch(Dispatchers.IO) {
            downloadList[videoId]?.isDownloaded = true
            contentDao.updateContent(data?.contentId ?: "", 1)
        }
        notification.apply {
            setProgress(downloadList.size, downloadList.filterValues { it.isDownloaded }.size, false)
            setStyle(NotificationCompat.BigTextStyle().bigText("${downloadList.filterValues { it.isDownloaded }.size} out of ${downloadList.size} downloaded"))
        }
        notificationManager.notify(1, notification.build())
        if (downloadList.filterValues { !it.isDownloaded }.isEmpty()) {
            createCompletionNotification()
            stopForeground(true)
            stopSelf()
        }
    }

    private fun createCompletionNotification() {
        notification = NotificationCompat.Builder(applicationContext, DOWNLOAD_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_file_download)
            setContentTitle("Downloaded $sectionName")
            setOnlyAlertOnce(true)
            setLargeIcon(BitmapFactory.decodeResource(applicationContext.resources, R.mipmap.ic_launcher))
            setStyle(NotificationCompat.BigTextStyle().bigText("${downloadList.filterValues { it.isDownloaded }.size} out of ${downloadList.size} downloaded"))
        }
        notificationManager.notify(2, notification.build())
    }

}
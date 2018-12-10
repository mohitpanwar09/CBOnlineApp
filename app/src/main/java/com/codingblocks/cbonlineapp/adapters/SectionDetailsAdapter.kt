package com.codingblocks.cbonlineapp.adapters

import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.codingblocks.cbonlineapp.DownloadStarter
import com.codingblocks.cbonlineapp.R
import com.codingblocks.cbonlineapp.activities.PdfActivity
import com.codingblocks.cbonlineapp.activities.VideoPlayerActivity
import com.codingblocks.cbonlineapp.activities.YoutubePlayerActivity
import com.codingblocks.cbonlineapp.database.AppDatabase
import com.codingblocks.cbonlineapp.database.ContentDao
import com.codingblocks.cbonlineapp.database.CourseContent
import com.codingblocks.cbonlineapp.database.CourseSection
import kotlinx.android.synthetic.main.item_section.view.*
import okhttp3.ResponseBody
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.singleTop
import java.io.*


class SectionDetailsAdapter(private var sectionData: ArrayList<CourseSection>?, private var activity: LifecycleOwner, private var starter: DownloadStarter) : RecyclerView.Adapter<SectionDetailsAdapter.CourseViewHolder>(), AnkoLogger {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var contentDao: ContentDao
    lateinit var arrowAnimation: RotateAnimation


    fun setData(sectionData: ArrayList<CourseSection>) {
        this.sectionData = sectionData
        info { sectionData.size }
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        holder.bindView(sectionData!![position], starter)
    }


    override fun getItemCount(): Int {

        return sectionData!!.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        context = parent.context
        database = AppDatabase.getInstance(context)

        contentDao = database.contentDao()


        return CourseViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_section, parent, false))
    }

    inner class CourseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private var starter: DownloadStarter? = null


        fun bindView(data: CourseSection, starter: DownloadStarter) {

            itemView.title.text = data.name
            this.starter = starter

            contentDao.getCourseSectionContents(data.attempt_id, data.id).observe(activity, Observer<List<CourseContent>> { it ->
                val ll = itemView.findViewById<LinearLayout>(R.id.sectionContents)
                ll.removeAllViews()
                ll.orientation = LinearLayout.VERTICAL
                ll.visibility = View.GONE
                itemView.lectures.text = "${it.size} Lectures"
                var duration: Long = 0
                for (content in it) {
                    if (content.contentable == "lecture")
                        duration += content.contentLecture.lectureDuration
                    else if (content.contentable == "video") {
                        duration += content.contentVideo.videoDuration
                    }
                    val hour = duration / (1000 * 60 * 60) % 24
                    val minute = duration / (1000 * 60) % 60
                    info { "hour$hour   minute$minute" }

                    if (minute >= 1 && hour == 0L)
                        itemView.lectureTime.text = ("$minute Min")
                    else if (hour >= 1) {
                        itemView.lectureTime.text = ("$hour Hours")
                    } else
                        itemView.lectureTime.text = ("---")

                    val factory = LayoutInflater.from(context)
                    val inflatedView = factory.inflate(R.layout.item_section_detailed_info, ll, false)
                    val subTitle = inflatedView.findViewById(R.id.textView15) as TextView
                    val downloadBtn = inflatedView.findViewById(R.id.downloadBtn) as ImageView

                    subTitle.text = content.title
                    when {
                        content.contentable == "lecture" -> {
                            val url = content.contentLecture.lectureUrl.substring(38, (content.contentLecture.lectureUrl.length - 11))
                            ll.addView(inflatedView)
                            if (!content.contentLecture.isDownloaded) {
                                downloadBtn.setOnClickListener {
                                    starter.startDownload(url,data.id, content.contentLecture.lectureContentId)
                                    downloadBtn.isEnabled = false
                                    (downloadBtn.background as AnimationDrawable).start()
//                                    var downloadCount = 0
//                                    // download lecture index.m3u8,video.key and video.m3u8
//                                    //TODO : Error handling
//                                    //No need to nest every call within one another, we can start the larger downloads sequentially once the smaller
//                                    //downloads (m3u8 and key) have been completed
//
//                                    Clients.initiateDownload(url, "index.m3u8").enqueue(retrofitCallback { _, response ->
//                                        response?.body()?.let { indexResponse ->
//                                            writeResponseBodyToDisk(indexResponse, url, "index.m3u8")
//                                        }
//                                    })
//
//                                    Clients.initiateDownload(url, "video.key").enqueue(retrofitCallback { throwable, response ->
//                                        response?.body()?.let { videoResponse ->
//                                            writeResponseBodyToDisk(videoResponse, url, "video.key")
//                                        }
//                                    })
//
//                                    Clients.initiateDownload(url, "video.m3u8").enqueue(retrofitCallback { throwable, response ->
//                                        response?.body()?.let { keyResponse ->
//                                            writeResponseBodyToDisk(keyResponse, url, "video.m3u8")
//                                            val videoChunks = MediaUtils.getCourseDownloadUrls(url, context)
//                                            videoChunks.forEach { videoName: String ->
//                                                Clients.initiateDownload(url, videoName).enqueue(retrofitCallback { throwable, response ->
//                                                    val isDownloaded = writeResponseBodyToDisk(response?.body()!!, url, videoName)
//                                                    if (isDownloaded) {
//                                                        downloadCount++
//                                                    }
//                                                    if (downloadCount == videoChunks.size) {
//                                                        thread {
//                                                            contentDao.updateContent(data.id, content.contentLecture.lectureContentId)
//                                                        }
//
//
//                                                    }
//                                                })
//                                            }
//                                        }
//                                    })
                                }
                            } else {
                                downloadBtn.setImageDrawable(context.getDrawable(R.drawable.ic_lecture))
                                downloadBtn.background = null
                                if (content.progress == "DONE") {
                                    downloadBtn.setImageDrawable(context.getDrawable(R.drawable.ic_status_done))
                                }
                                inflatedView.setOnClickListener {
                                    it.context.startActivity(it.context.intentFor<VideoPlayerActivity>("FOLDER_NAME" to url).singleTop())
                                }
                            }

                        }
                        content.contentable == "document" -> {
                            downloadBtn.setImageDrawable(context.getDrawable(R.drawable.ic_document))
                            downloadBtn.background = null
                            if (content.progress == "DONE") {
                                downloadBtn.setImageDrawable(context.getDrawable(R.drawable.ic_status_done))
                            }
                            ll.addView(inflatedView)
                            inflatedView.setOnClickListener {
                                it.context.startActivity(it.context.intentFor<PdfActivity>("fileUrl" to content.contentDocument.documentPdfLink, "fileName" to content.contentDocument.documentName + ".pdf").singleTop())

                            }
                        }
                        content.contentable == "video" -> {
                            downloadBtn.setImageDrawable(context.getDrawable(R.drawable.ic_youtube_video))
                            downloadBtn.background = null
                            if (content.progress == "DONE") {
                                downloadBtn.setImageDrawable(context.getDrawable(R.drawable.ic_status_done))
                            }
                            ll.addView(inflatedView)
                            inflatedView.setOnClickListener {
                                it.context.startActivity(it.context.intentFor<YoutubePlayerActivity>("videoUrl" to content.contentVideo.videoUrl).singleTop())

                            }
                        }
                    }

                    itemView.setOnClickListener {
                        showOrHide(ll, it)
                    }

                    itemView.arrow.setOnClickListener {
                        showOrHide(ll, itemView)
                    }
                }
            })
        }
    }

    fun showOrHide(ll: View, itemView: View) {
        if (ll.visibility == View.GONE) {
            ll.visibility = View.VISIBLE
            arrowAnimation = RotateAnimation(0f, 180f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                    0.5f)
            arrowAnimation.fillAfter = true
            arrowAnimation.duration = 350
            itemView.arrow.startAnimation(arrowAnimation)
        } else {
            ll.visibility = View.GONE
            arrowAnimation = RotateAnimation(180f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                    0.5f)
            arrowAnimation.fillAfter = true
            arrowAnimation.duration = 350
            itemView.arrow.startAnimation(arrowAnimation)
        }
    }

    private fun writeResponseBodyToDisk(body: ResponseBody, videoUrl: String?, fileName: String): Boolean {
        try {

            val file = context.getExternalFilesDir(Environment.getDataDirectory().absolutePath)
            val folderFile = File(file, "/$videoUrl")
            val dataFile = File(file, "/$videoUrl/$fileName")
            if (!folderFile.exists()) {
                folderFile.mkdir()
            }
            // todo change the file location/name according to your needs

            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null

            try {
                val fileReader = ByteArray(4096)

                val fileSize = body.contentLength()
                var fileSizeDownloaded: Long = 0


                inputStream = body.byteStream()
                outputStream = FileOutputStream(dataFile)

                while (true) {
                    val read = inputStream!!.read(fileReader)

                    if (read == -1) {
                        break
                    }

                    outputStream.write(fileReader, 0, read)

                    fileSizeDownloaded += read.toLong()

                }

                outputStream.flush()

                return true
            } catch (e: IOException) {
                return false
            } finally {
                if (inputStream != null) {
                    inputStream.close()
                }

                if (outputStream != null) {
                    outputStream.close()
                }
            }
        } catch (e: IOException) {
            return false
        }

    }
}
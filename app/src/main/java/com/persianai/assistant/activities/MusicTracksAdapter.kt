package com.persianai.assistant.activities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.R
import com.persianai.assistant.music.SimpleMusicPlayer
import java.text.SimpleDateFormat
import java.util.*

/**
 * آداپتور برای نمایش لیست آهنگ‌ها در موزیک پلیر
 */
class MusicTracksAdapter(
    private val tracks: List<SimpleMusicPlayer.MusicTrack>,
    private val onTrackClick: (SimpleMusicPlayer.MusicTrack, Int) -> Unit
) : RecyclerView.Adapter<MusicTracksAdapter.TrackViewHolder>() {
    
    private var currentTrackIndex = -1
    
    fun setCurrentTrackIndex(index: Int) {
        val oldIndex = currentTrackIndex
        currentTrackIndex = index
        
        if (oldIndex != -1) {
            notifyItemChanged(oldIndex)
        }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music_track, parent, false)
        return TrackViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(tracks[position], position == currentTrackIndex)
    }
    
    override fun getItemCount(): Int = tracks.size
    
    inner class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val trackNumberText: TextView = itemView.findViewById(R.id.trackNumberText)
        private val trackTitleText: TextView = itemView.findViewById(R.id.trackTitleText)
        private val trackArtistText: TextView = itemView.findViewById(R.id.trackArtistText)
        private val trackDurationText: TextView = itemView.findViewById(R.id.trackDurationText)
        private val playingIndicator: ImageView = itemView.findViewById(R.id.playingIndicator)
        private val trackCard: View = itemView.findViewById(R.id.trackCard)
        
        fun bind(track: SimpleMusicPlayer.MusicTrack, isCurrentlyPlaying: Boolean) {
            trackNumberText.text = (adapterPosition + 1).toString()
            trackTitleText.text = track.title
            trackArtistText.text = track.artist
            trackDurationText.text = formatDuration(track.duration)
            
            // نمایش وضعیت پخش
            if (isCurrentlyPlaying) {
                playingIndicator.visibility = View.VISIBLE
                trackCard.setBackgroundColor(
                    trackCard.context.getColor(R.color.purple_100)
                )
            } else {
                playingIndicator.visibility = View.GONE
                trackCard.setBackgroundColor(
                    trackCard.context.getColor(android.R.color.white)
                )
            }
            
            // کلیک روی آهنگ
            trackCard.setOnClickListener {
                onTrackClick(track, adapterPosition)
            }
        }
        
        private fun formatDuration(durationMs: Long): String {
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / (1000 * 60)) % 60
            return String.format("%d:%02d", minutes, seconds)
        }
    }
}

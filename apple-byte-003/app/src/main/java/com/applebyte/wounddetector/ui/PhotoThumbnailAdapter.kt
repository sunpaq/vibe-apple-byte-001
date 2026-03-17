package com.applebyte.wounddetector.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.applebyte.wounddetector.databinding.ItemPhotoThumbnailBinding

class PhotoThumbnailAdapter : ListAdapter<Bitmap, PhotoThumbnailAdapter.PhotoViewHolder>(PhotoDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoThumbnailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class PhotoViewHolder(
        private val binding: ItemPhotoThumbnailBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(bitmap: Bitmap) {
            binding.ivThumbnail.setImageBitmap(bitmap)
        }
    }
    
    class PhotoDiffCallback : DiffUtil.ItemCallback<Bitmap>() {
        override fun areItemsTheSame(oldItem: Bitmap, newItem: Bitmap): Boolean {
            return oldItem === newItem
        }
        
        override fun areContentsTheSame(oldItem: Bitmap, newItem: Bitmap): Boolean {
            return oldItem.sameAs(newItem)
        }
    }
}

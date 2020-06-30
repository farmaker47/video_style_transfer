package com.george.lite.examples.video_style_transfer.adapters

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.george.lite.examples.video_style_transfer.databinding.SearchFragmentAdapterBinding
import java.util.*

class SearchFragmentNavigationAdapter(
    val mContext: Context,
    private var hitsList: ArrayList<String>?,
    private val mSearchClickItemListener: SearchClickItemListener
) :
    RecyclerView.Adapter<SearchFragmentNavigationAdapter.NavigationAdapterViewHolder>() {

    interface SearchClickItemListener {
        fun onListItemClick(
            itemIndex: Int,
            sharedImage: ImageView?,
            type: String
        )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        i: Int
    ): NavigationAdapterViewHolder {

        return from(
            this,
            parent
        )
    }

    override fun onBindViewHolder(
        holder: NavigationAdapterViewHolder,
        position: Int
    ) {
        //setting the name at the textView
        holder.bind(position)
    }

    override fun getItemCount(): Int {
        return if (hitsList != null && hitsList!!.size > 0) {
            hitsList!!.size
        } else {
            0
        }
    }

    inner class NavigationAdapterViewHolder(val binding: SearchFragmentAdapterBinding) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        override fun onClick(view: View) {
            val clickedPosition = adapterPosition
            mSearchClickItemListener.onListItemClick(
                clickedPosition,
                binding.imageFragmentAdapter,
                hitsList!![clickedPosition]
            )
        }

        init {
            itemView.setOnClickListener{
                //ViewCompat.setTransitionName(binding.imageFragmentAdapter, mContext.getString(R.string.transition_photo))
                onClick(binding.imageFragmentAdapter)
            }
        }

        fun bind(
            position: Int
        ) {

            val imagePath = hitsList!![position]

            Glide.with(mContext)
                .load(Uri.parse("file:///android_asset/thumbnails/$imagePath"))
                .centerInside()
                .into(binding.imageFragmentAdapter)
            //binding.textViewFragmentAdapter.text = hitsList!![position]
            //binding.imageFragmentAdapter.setImageResource(R.drawable.medicine)
        }
    }

    fun setHitsData(list: ArrayList<String>?) {
        hitsList = list
/*
        notifyDataSetChanged();
*/
    }

    companion object {
        private fun from(
            searchFragmentNavigationAdapter: SearchFragmentNavigationAdapter,
            parent: ViewGroup
        ): NavigationAdapterViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = SearchFragmentAdapterBinding.inflate(inflater, parent, false)

            /*return NavigationAdapterViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.search_fragment_adapter, parent, false)
            )*/
            return searchFragmentNavigationAdapter.NavigationAdapterViewHolder(binding)
        }
    }

}

class HitListDiffCallBack : DiffUtil.ItemCallback<ArrayList<String>?>() {

    override fun areContentsTheSame(
        oldItem: ArrayList<String>,
        newItem: ArrayList<String>
    ): Boolean {
        return oldItem.equals(newItem)
    }

    override fun areItemsTheSame(oldItem: ArrayList<String>, newItem: ArrayList<String>): Boolean {
        TODO("Not yet implemented")
    }

}
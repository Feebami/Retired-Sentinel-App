package com.feebami.retiredsentinel

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.feebami.retiredsentinel.databinding.ItemEnrolledPersonBinding

data class EnrolledPerson(val name: String, val vectorCount: Int)

class EnrolledPersonAdapter(
    private val onDelete: (String) -> Unit,
    private val onAdd: (String) -> Unit
) : RecyclerView.Adapter<EnrolledPersonAdapter.ViewHolder>() {

    private val items = mutableListOf<EnrolledPerson>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<EnrolledPerson>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemEnrolledPersonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(person: EnrolledPerson) {
            binding.tvName.text = person.name
            binding.tvVectorCount.text = "${person.vectorCount} image(s) enrolled"
            binding.btnDelete.setOnClickListener { onDelete(person.name) }
            binding.btnAddPhoto.setOnClickListener { onAdd(person.name) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemEnrolledPersonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size
}

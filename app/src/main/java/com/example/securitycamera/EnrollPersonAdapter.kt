package com.example.securitycamera

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.securitycamera.databinding.ItemEnrolledPersonBinding

data class EnrolledPerson(val name: String, val vectorCount: Int)

class EnrolledPersonAdapter(
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<EnrolledPersonAdapter.ViewHolder>() {

    private val items = mutableListOf<EnrolledPerson>()

    fun submitList(newItems: List<EnrolledPerson>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemEnrolledPersonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(person: EnrolledPerson) {
            binding.tvName.text = person.name
            binding.tvVectorCount.text = "${person.vectorCount} image(s) enrolled"
            binding.btnDelete.setOnClickListener { onDelete(person.name) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemEnrolledPersonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size
}

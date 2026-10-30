package com.example.ui.showcase

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ItemShowcaseExperienceBinding
import com.example.model.WorkExperienceItem

class ExperienceTimelineAdapter(
    private val experiences: List<WorkExperienceItem>
) : RecyclerView.Adapter<ExperienceTimelineAdapter.ExperienceViewHolder>() {

    inner class ExperienceViewHolder(private val binding: ItemShowcaseExperienceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WorkExperienceItem) {
            val titleText = when {
                item.roleTitle.isNotBlank() && item.companyName.isNotBlank() ->
                    "${item.roleTitle} · ${item.companyName}"
                item.roleTitle.isNotBlank() -> item.roleTitle
                item.companyName.isNotBlank() -> item.companyName
                else -> "Experience Position"
            }
            binding.tvRoleAndCompany.text = titleText

            val durationText = when {
                item.startDate.isNotBlank() && item.endDate.isNotBlank() ->
                    "${item.startDate} — ${item.endDate}"
                item.startDate.isNotBlank() -> item.startDate
                item.endDate.isNotBlank() -> item.endDate
                else -> ""
            }
            binding.tvExperienceDuration.text = durationText
            binding.tvExperienceDuration.visibility = if (durationText.isNotBlank()) View.VISIBLE else View.GONE

            val bulletPoints = item.bulletPoints.filter { it.isNotBlank() }
            if (bulletPoints.isNotEmpty()) {
                val formattedBullets = bulletPoints.joinToString("\n\n") { "•  $it" }
                binding.tvExperienceBullets.text = formattedBullets
                binding.tvExperienceBullets.visibility = View.VISIBLE
            } else {
                binding.tvExperienceBullets.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExperienceViewHolder {
        val binding = ItemShowcaseExperienceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ExperienceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExperienceViewHolder, position: Int) {
        holder.bind(experiences[position])
    }

    override fun getItemCount(): Int = experiences.size
}

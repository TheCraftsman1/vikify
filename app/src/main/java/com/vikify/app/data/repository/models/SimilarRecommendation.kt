package com.vikify.app.data.repository.models

import com.vikify.app.data.repository.db.entities.entities.LocalItem
import com.zionhuang.innertube.models.YTItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)

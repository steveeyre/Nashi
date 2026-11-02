package com.nashi.feature.fields

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashi.core.data.NashiDao
import com.nashi.core.model.Category
import com.nashi.core.model.CategoryFieldDef
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CategoryFieldsViewModel @Inject constructor(
    private val dao: NashiDao,
) : ViewModel() {

    val categories: StateFlow<List<Category>> =
        dao.categoriesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val categoryId = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = categoryId

    @OptIn(ExperimentalCoroutinesApi::class)
    val fields: StateFlow<List<CategoryFieldDef>> = categoryId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else dao.fieldDefs(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(id: String) {
        categoryId.value = id
    }

    fun save(def: CategoryFieldDef) {
        viewModelScope.launch {
            dao.upsertCategoryFields(listOf(def))
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            dao.deleteCategoryField(id)
        }
    }
}

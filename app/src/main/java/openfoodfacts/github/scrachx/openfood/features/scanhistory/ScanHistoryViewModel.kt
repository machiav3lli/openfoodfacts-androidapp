package openfoodfacts.github.scrachx.openfood.features.scanhistory

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import openfoodfacts.github.scrachx.openfood.R
import openfoodfacts.github.scrachx.openfood.models.DaoSession
import openfoodfacts.github.scrachx.openfood.models.HistoryProduct
import openfoodfacts.github.scrachx.openfood.models.HistoryProductDao
import openfoodfacts.github.scrachx.openfood.repositories.ProductRepository
import openfoodfacts.github.scrachx.openfood.utils.LocaleManager
import openfoodfacts.github.scrachx.openfood.utils.SortType
import openfoodfacts.github.scrachx.openfood.utils.list
import javax.inject.Inject

@HiltViewModel
@SuppressLint("StaticFieldLeak")
class ScanHistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val daoSession: DaoSession,
    private val client: ProductRepository,
    private val localeManager: LocaleManager
) : ViewModel() {

    private val unorderedProductState = MutableLiveData<FetchProductsState>(FetchProductsState.Loading)
    val productsState = unorderedProductState.switchMap { products ->
        liveData {
            when (products) {
                is FetchProductsState.Loading, is FetchProductsState.Error -> emit(products)
                is FetchProductsState.Data -> {
                    try {
                        (products as? FetchProductsState.Data)?.items
                            ?.customSort(sortType.value)
                            ?.let { emit(FetchProductsState.Data(it)) }
                    } catch (err: Exception) {
                        emit(FetchProductsState.Error)
                    }
                }
            }
        }
    }

    private val sortType = MutableLiveData(SortType.TIME)

    suspend fun refreshItems() {
        unorderedProductState.postValue(FetchProductsState.Loading)

        withContext(Dispatchers.IO) {
            val barcodes = daoSession.historyProductDao.list().map { it.barcode }
            if (barcodes.isNotEmpty()) {
                try {
                    client.getProductsByBarcode(barcodes)
                        .forEach { product ->
                            val historyProduct = daoSession.historyProductDao.queryBuilder()
                                .where(HistoryProductDao.Properties.Barcode.eq(product.code))
                                .build()
                                .unique()

                            product.productName?.let { historyProduct.title = it }
                            product.brands?.let { historyProduct.brands = it }
                            product.getImageSmallUrl(localeManager.getLanguage())?.let { historyProduct.url = it }
                            product.quantity?.let { historyProduct.quantity = it }
                            product.nutritionGradeFr?.let { historyProduct.nutritionGrade = it }
                            product.ecoscore?.let { historyProduct.ecoscore = it }
                            product.novaGroups?.let { historyProduct.novaGroup = it }

                            daoSession.historyProductDao.update(historyProduct)
                        }
                } catch (err: Exception) {
                    unorderedProductState.postValue(FetchProductsState.Error)
                }
            }

            val updatedProducts = daoSession.historyProductDao.list()
            unorderedProductState.postValue(FetchProductsState.Data(updatedProducts))
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        unorderedProductState.postValue(FetchProductsState.Loading)

        try {
            daoSession.historyProductDao.deleteAll()
        } catch (err: Exception) {
            unorderedProductState.postValue(FetchProductsState.Error)
        }

        unorderedProductState.postValue(FetchProductsState.Data(emptyList()))
    }

    suspend fun removeProductFromHistory(product: HistoryProduct) = withContext(Dispatchers.IO) {
        try {
            daoSession.historyProductDao.delete(product)
        } catch (err: Exception) {
            unorderedProductState.postValue(FetchProductsState.Error)
        }

        val products = daoSession.historyProductDao.list()
        unorderedProductState.postValue(FetchProductsState.Data(products))
    }

    fun updateSortType(type: SortType) {
        sortType.postValue(type)

        // refresh
        unorderedProductState.postValue(productsState.value)
    }

    /**
     * Function to compare history items based on title, brand, barcode, time and nutrition grade
     */
    private fun List<HistoryProduct>.customSort(sortType: SortType?) = when (sortType) {
        SortType.TITLE -> sortedWith { item1, item2 ->
            if (item1.title.isNullOrEmpty()) item1.title = context.getString(R.string.no_title)
            if (item2.title.isNullOrEmpty()) item2.title = context.getString(R.string.no_title)
            item1.title.compareTo(item2.title, true)
        }

        SortType.BRAND -> sortedWith { item1, item2 ->
            if (item1.brands.isNullOrEmpty()) item1.brands = context.getString(R.string.no_brand)
            if (item2.brands.isNullOrEmpty()) item2.brands = context.getString(R.string.no_brand)
            item1.brands!!.compareTo(item2.brands!!, true)
        }
        SortType.BARCODE -> sortedBy { it.barcode }
        SortType.GRADE -> sortedBy { it.nutritionGrade }
        SortType.TIME -> sortedByDescending { it.lastSeen }
        SortType.NONE, null -> this
    }

    sealed class FetchProductsState {
        data class Data(val items: List<HistoryProduct>) : FetchProductsState()
        object Loading : FetchProductsState()
        object Error : FetchProductsState()
    }
}

package openfoodfacts.github.scrachx.openfood.features.simplescan

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class SimpleScanActivityContract : ActivityResultContract<Unit, String?>() {

    companion object {
        const val KEY_SCANNED_BARCODE = "scanned_barcode"
    }

    override fun createIntent(context: Context, input: Unit?): Intent {
        return Intent(context, SimpleScanActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        val bundle = intent?.extras ?: return null
        return if (resultCode == Activity.RESULT_OK && bundle.containsKey(KEY_SCANNED_BARCODE)) {
            bundle.getString(KEY_SCANNED_BARCODE, null)
        } else {
            null
        }
    }
}

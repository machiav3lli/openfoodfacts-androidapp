package openfoodfacts.github.scrachx.openfood.features.product.view.contributors

import android.os.Bundle
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.buildSpannedString
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import openfoodfacts.github.scrachx.openfood.R
import openfoodfacts.github.scrachx.openfood.databinding.FragmentContributorsBinding
import openfoodfacts.github.scrachx.openfood.features.product.edit.ProductEditActivity.Companion.KEY_STATE
import openfoodfacts.github.scrachx.openfood.features.search.ProductSearchActivity.Companion.start
import openfoodfacts.github.scrachx.openfood.features.shared.BaseFragment
import openfoodfacts.github.scrachx.openfood.models.ProductState
import openfoodfacts.github.scrachx.openfood.models.entities.states.StatesName
import openfoodfacts.github.scrachx.openfood.network.ApiFields
import openfoodfacts.github.scrachx.openfood.repositories.TaxonomiesRepository
import openfoodfacts.github.scrachx.openfood.utils.LocaleManager
import openfoodfacts.github.scrachx.openfood.utils.SearchType
import openfoodfacts.github.scrachx.openfood.utils.requireProductState
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * @see R.layout.fragment_contributors
 */

@AndroidEntryPoint
class ContributorsFragment : BaseFragment() {

    private var _binding: FragmentContributorsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContributorsViewModel by viewModels()

    @Inject
    lateinit var taxonomiesRepository: TaxonomiesRepository

    @Inject
    lateinit var localeManager: LocaleManager

    private lateinit var productState: ProductState

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentContributorsBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.incompleteStates.setOnClickListener { toggleIncompleteStatesVisibility() }
        binding.completeStates.setOnClickListener { toggleCompleteStatesVisibility() }

        binding.incompleteStates.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_keyboard_arrow_down_grey_24dp, 0)
        binding.completeStates.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_keyboard_arrow_down_grey_24dp, 0)

        refreshView(this.requireProductState())
    }

    override fun refreshView(productState: ProductState) {
        super.refreshView(productState)
        this.productState = productState
        val product = this.productState.product!!

        if (!product.creator.isNullOrBlank()) {
            val createdDate = getDateTime(product.createdDateTime!!)
            binding.creatorTxt.movementMethod = LinkMovementMethod.getInstance()
            binding.creatorTxt.text = getString(R.string.creator_history, createdDate.first, createdDate.second, product.creator)
        } else {
            binding.creatorTxt.visibility = View.INVISIBLE
        }

        if (!product.lastModifiedBy.isNullOrBlank()) {
            val lastEditDate = getDateTime(product.lastModifiedTime!!)
            binding.lastEditorTxt.movementMethod = LinkMovementMethod.getInstance()
            binding.lastEditorTxt.text = getString(R.string.last_editor_history, lastEditDate.first, lastEditDate.second, product.lastModifiedBy)
        } else {
            binding.lastEditorTxt.visibility = View.INVISIBLE
        }

        if (product.editors.isNotEmpty()) {
            binding.otherEditorsTxt.movementMethod = LinkMovementMethod.getInstance()
            binding.otherEditorsTxt.text = buildSpannedString {
                append(getString(R.string.other_editors))
                append(" ")
                product.editors.map { getContributorsTag(it).subSequence(0, it.length) }
                    .forEachIndexed { i, el ->
                        if (i > 0) append(", ")
                        append(el)
                    }
                append(getContributorsTag(product.editors.last()))
            }
        } else {
            binding.otherEditorsTxt.visibility = View.INVISIBLE
        }

        // function to show states tags
        viewModel.product.value = product
        viewModel.states.observe(viewLifecycleOwner) { showStatesTags(it) }
    }

    /**
     * Get date and time in MMMM dd, yyyy and HH:mm:ss a format
     *
     * @param dateTime date and time in miliseconds
     */
    private fun getDateTime(dateTime: String): Pair<String, String> {
        val unixSeconds = dateTime.toLong()
        val date = Date(unixSeconds * 1000L)
        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("CET")
        }
        val sdf2 = SimpleDateFormat("HH:mm:ss a", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("CET")
        }
        return sdf.format(date) to sdf2.format(date)
    }

    private fun getContributorsTag(contributor: String): CharSequence {
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(view: View) = start(requireContext(), SearchType.CONTRIBUTOR, contributor)
        }
        return buildSpannedString {
            append(contributor)
            setSpan(clickableSpan, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" ")
        }
    }

    private fun getStatesTag(stateName: String, stateTag: String): CharSequence {
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(view: View) = start(requireContext(), SearchType.STATE, stateTag)
        }
        return buildSpannedString {
            append(stateName)
            setSpan(clickableSpan, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun showStatesTags(states: List<StatesName>?) {
        if (states.isNullOrEmpty()) {
            binding.statesTagsCv.visibility = View.GONE
        } else {
            binding.incompleteStatesTxt.movementMethod = LinkMovementMethod.getInstance()
            binding.incompleteStatesTxt.text = ""

            binding.completeStatesTxt.movementMethod = LinkMovementMethod.getInstance()
            binding.completeStatesTxt.text = ""

            states.forEach { state ->
                if (isIncompleteState(state.statesTag)) {
                    binding.incompleteStatesTxt.append(getStatesTag(state.name, state.statesTag.split(":").component2()))
                    binding.incompleteStatesTxt.append("\n")
                } else {
                    binding.completeStatesTxt.append(getStatesTag(state.name, state.statesTag.split(":").component2()))
                    binding.completeStatesTxt.append("\n")
                }
            }
        }

    }


    private fun toggleIncompleteStatesVisibility() {
        if (binding.incompleteStatesTxt.visibility == View.VISIBLE) {
            binding.incompleteStatesTxt.visibility = View.GONE
            binding.incompleteStates.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_keyboard_arrow_down_grey_24dp, 0)
        } else {
            binding.incompleteStatesTxt.visibility = View.VISIBLE
            binding.incompleteStates.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_keyboard_arrow_up_grey_24dp, 0)
        }
    }

    private fun toggleCompleteStatesVisibility() {
        if (binding.completeStatesTxt.visibility == View.VISIBLE) {
            binding.completeStatesTxt.visibility = View.GONE
            binding.completeStates.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_keyboard_arrow_down_grey_24dp, 0)
        } else {
            binding.completeStatesTxt.visibility = View.VISIBLE
            binding.completeStates.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_keyboard_arrow_up_grey_24dp, 0)
        }
    }

    companion object {
        fun newInstance(productState: ProductState) = ContributorsFragment().apply {
            arguments = Bundle().apply {
                putSerializable(KEY_STATE, productState)
            }
        }

        internal fun isIncompleteState(stateTag: String) =
            ApiFields.StateTags.INCOMPLETE_TAGS.any { it in stateTag }
    }
}

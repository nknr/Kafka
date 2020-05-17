package com.kafka.search.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.kafka.ui.actions.ItemClickAction
import com.kafka.ui_common.BaseFragment
import com.kafka.ui_common.EventObserver
import com.kafka.ui_common.itemDetailDeepLinkUri
import javax.inject.Inject

/**
 * @author Vipul Kumar; dated 02/02/19.
 */
class SearchFragment : BaseFragment() {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private val searchViewModel: SearchViewModel by viewModels(factoryProducer = { viewModelFactory })
    private val navController by lazy { findNavController() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchViewModel.navigateToContentDetailAction.observe(viewLifecycleOwner, EventObserver {
            (it as? ItemClickAction)?.let { navController.navigate(itemDetailDeepLinkUri(it.item.itemId)) }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
//            composeMainScreen(viewLifecycleOwner, searchViewModel.viewState, searchViewModel::submitAction)
        }
    }
}

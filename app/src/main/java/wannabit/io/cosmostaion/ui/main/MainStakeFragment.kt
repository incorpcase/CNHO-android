package wannabit.io.cosmostaion.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wannabit.io.cosmostaion.R
import wannabit.io.cosmostaion.chain.FetchState
import wannabit.io.cosmostaion.chain.cosmosClass.ChainCnho
import wannabit.io.cosmostaion.common.BaseData
import wannabit.io.cosmostaion.common.formatAssetValue
import wannabit.io.cosmostaion.data.viewmodel.ApplicationViewModel
import wannabit.io.cosmostaion.databinding.FragmentMainStakeBinding
import wannabit.io.cosmostaion.ui.tx.genTx.StakingFragment
import wannabit.io.cosmostaion.ui.tx.info.StakingInfoFragment
import wannabit.io.cosmostaion.ui.tx.info.UnStakingInfoFragment
import java.math.BigDecimal

class MainStakeFragment : Fragment() {

    private var _binding: FragmentMainStakeBinding? = null
    private val binding get() = _binding!!

    private val cnhoChain = ChainCnho()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainStakeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        setUpClickAction()
        setUpObservers()
    }

    private fun initView() {
        binding.apply {
            stakedValueTitle.text = getString(R.string.str_total_staked)

            val stakingPagerAdapter = StakingPagerAdapter(this@MainStakeFragment)
            viewPager.adapter = stakingPagerAdapter
            viewPager.offscreenPageLimit = 1
            viewPager.isUserInputEnabled = false
            tabLayout.bringToFront()

            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> "Staking"
                    else -> "Unstaking"
                }
            }.attach()

            updateStakeValue()
        }
    }

    private fun updateStakeValue() {
        val account = BaseData.baseAccount
        val cnho = account?.allChains?.firstOrNull { it.tag == cnhoChain.tag } ?: cnhoChain

        if (cnho.fetchState == FetchState.IDLE || cnho.fetchState == FetchState.FAIL) {
            ApplicationViewModel.shared.loadChainData(cnho, account?.id ?: -1L, isRefresh = true)
        }

        val delegationValue = cnho.cosmosFetcher?.delegationValueSum() ?: BigDecimal.ZERO
        val unbondingValue = cnho.cosmosFetcher?.unbondingValueSum() ?: BigDecimal.ZERO
        val totalStakedValue = delegationValue.add(unbondingValue)

        val delegations = cnho.cosmosFetcher?.cosmosDelegations ?: mutableListOf()
        val unbondings = cnho.cosmosFetcher?.cosmosUnbondings ?: mutableListOf()

        binding.stakedValue.text = formatAssetValue(totalStakedValue)
        binding.swipeRefreshLayout.isRefreshing = false
        if (delegations.isEmpty() && unbondings.isEmpty()) {
            binding.emptyStake.visibility = View.VISIBLE
            binding.stakingDataView.visibility = View.GONE
        } else {
            binding.emptyStake.visibility = View.GONE
            binding.stakingDataView.visibility = View.VISIBLE
        }
    }

    private fun setUpClickAction() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            val account = BaseData.baseAccount
            val cnho = account?.allChains?.firstOrNull { it.tag == cnhoChain.tag } ?: cnhoChain
            cnho.fetchState = FetchState.IDLE
            ApplicationViewModel.shared.loadChainData(cnho, account?.id ?: -1L, isRefresh = true)
        }

        binding.btnStake.setOnClickListener {
            val account = BaseData.baseAccount
            val cnho = account?.allChains?.firstOrNull { it.tag == cnhoChain.tag } ?: cnhoChain

            StakingFragment.newInstance(cnho).show(
                requireActivity().supportFragmentManager, StakingFragment::class.java.name
            )
        }
    }

    private fun setUpObservers() {
        ApplicationViewModel.shared.currentAccountResult.observe(viewLifecycleOwner) {
            updateStakeValue()
        }

        ApplicationViewModel.shared.fetchedStakeResult.observe(viewLifecycleOwner) { tag ->
            if (cnhoChain.tag == tag) {
                updateStakeValue()
            }
        }

        ApplicationViewModel.shared.txFetchedResult.observe(viewLifecycleOwner) { tag ->
            if (cnhoChain.tag == tag) {
                updateStakeValue()
            }
        }

        ApplicationViewModel.shared.refreshStakingInfoFetchedResult.observe(viewLifecycleOwner) { tag ->
            if (cnhoChain.tag == tag) {
                updateStakeValue()
            }
        }
    }

    inner class StakingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            val account = BaseData.baseAccount
            val cnho = account?.allChains?.firstOrNull { it.tag == cnhoChain.tag } ?: cnhoChain

            return if (position == 0) {
                StakingInfoFragment.newInstance(cnho)
            } else {
                UnStakingInfoFragment.newInstance(cnho)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

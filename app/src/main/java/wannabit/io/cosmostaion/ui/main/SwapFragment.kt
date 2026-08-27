package wannabit.io.cosmostaion.ui.main

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wannabit.io.cosmostaion.R
import wannabit.io.cosmostaion.chain.cosmosClass.ChainCnho
import wannabit.io.cosmostaion.common.BaseData
import wannabit.io.cosmostaion.common.formatAmount
import wannabit.io.cosmostaion.common.formatAssetValue
import wannabit.io.cosmostaion.common.setTokenImg
import wannabit.io.cosmostaion.common.updateButtonView
import wannabit.io.cosmostaion.data.model.res.Asset
import wannabit.io.cosmostaion.data.model.res.NetworkResult
import wannabit.io.cosmostaion.data.viewmodel.ApplicationViewModel
import wannabit.io.cosmostaion.databinding.FragmentSwapBinding
import wannabit.io.cosmostaion.ui.tx.genTx.TargetAsset
import wannabit.io.cosmostaion.ui.tx.genTx.TargetAssetType
import wannabit.io.cosmostaion.ui.tx.option.swap.AssetListener
import wannabit.io.cosmostaion.ui.tx.option.swap.AssetSelectFragment
import wannabit.io.cosmostaion.ui.tx.option.swap.AssetSelectType
import wannabit.io.cosmostaion.ui.tx.option.swap.SlippageFragment
import wannabit.io.cosmostaion.ui.tx.option.swap.SlippageListener
import java.math.BigDecimal
import java.math.RoundingMode

class SwapFragment : Fragment() {

    private var _binding: FragmentSwapBinding? = null
    private val binding get() = _binding!!

    private val applicationViewModel: ApplicationViewModel by activityViewModels()

    private val cnhoChain = ChainCnho()
    private var inputAsset: Asset? = null
    private var outputAsset: Asset? = null
    private var inputAmount: String = ""
    private var swapOutputAmount: String = ""
    private var swapSlippage = "1"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSwapBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initData()
        initView()
        setUpClickAction()
    }

    private fun initData() {
        inputAsset = BaseData.getAsset(cnhoChain.apiName, "ucnho")
        outputAsset = BaseData.getAsset(cnhoChain.apiName, "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo")
            ?: BaseData.assets?.firstOrNull { it.chain == cnhoChain.apiName && it.denom != "ucnho" }
    }

    private fun initView() {
        binding.apply {
            loading.visibility = View.GONE
            swapLayout.visibility = View.VISIBLE

            inputChainImg.setImageResource(R.drawable.chain_default)
            inputChainName.text = cnhoChain.name.uppercase()
            outputChainImg.setImageResource(R.drawable.chain_default)
            outputChainName.text = cnhoChain.name.uppercase()

            fromAddress.text = cnhoChain.address
            toAddress.text = cnhoChain.address

            slippage.text = "$swapSlippage%"

            updateAssetsView()
        }

        binding.inputAmountTxt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                inputAmount = s.toString().trim()
                if (inputAmount.isEmpty() || inputAmount.toBigDecimalOrNull() == null || inputAmount.toBigDecimal() == BigDecimal.ZERO) {
                    swapOutputAmount = ""
                    updateSwapView()
                    return
                }
                simulateSwap()
            }
        })
    }

    private fun updateAssetsView() {
        binding.apply {
            inputAsset?.let {
                inputTokenImg.setTokenImg(it.image ?: "")
                inputToken.text = it.symbol
                val balance = cnhoChain.cosmosFetcher?.availableAmount(it.denom ?: "") ?: BigDecimal.ZERO
                inputAvailable.text = formatAmount(balance.movePointLeft(it.decimals ?: 6).toPlainString(), it.decimals ?: 6)
            }

            outputAsset?.let {
                outputTokenImg.setTokenImg(it.image ?: "")
                outputToken.text = it.symbol
                val balance = cnhoChain.cosmosFetcher?.availableAmount(it.denom ?: "") ?: BigDecimal.ZERO
                outputAvailable.text = formatAmount(balance.movePointLeft(it.decimals ?: 6).toPlainString(), it.decimals ?: 6)
            }
        }
        simulateSwap()
    }

    private fun updateSwapView() {
        binding.apply {
            if (swapOutputAmount.isEmpty()) {
                outputAmount.text = ""
                outputAmountValue.text = ""
                feeView.visibility = View.GONE
                btnSwap.updateButtonView(false)
            } else {
                outputAmount.text = formatAmount(swapOutputAmount, outputAsset?.decimals ?: 6)
                
                val price = BaseData.getPrice(outputAsset?.coinGeckoId)
                val value = swapOutputAmount.toBigDecimal().multiply(price)
                outputAmountValue.text = formatAssetValue(value)
                
                feeView.visibility = View.VISIBLE
                btnSwap.updateButtonView(true)
                
                // Exchange rate
                inputRateAmount.text = "1"
                inputRateDenom.text = inputAsset?.symbol
                
                val rate = swapOutputAmount.toBigDecimal().divide(inputAmount.toBigDecimal(), 6, RoundingMode.HALF_DOWN)
                outputRateAmount.text = formatAmount(rate.toPlainString(), 6)
                outputRateDenom.text = outputAsset?.symbol
                
                txFeeAmount.text = formatAmount("0.01", 6)
                txFeeDenom.text = "CNHO"
                swapVenue.text = "CNHO Swap"
            }
            
            val inputPrice = BaseData.getPrice(inputAsset?.coinGeckoId)
            val inputValue = if (inputAmount.isEmpty()) BigDecimal.ZERO else inputAmount.toBigDecimal().multiply(inputPrice)
            inputAmountValue.text = formatAssetValue(inputValue)
        }
    }

    private fun simulateSwap() {
        if (inputAmount.isEmpty() || inputAmount.toBigDecimalOrNull() == null || inputAmount.toBigDecimal() == BigDecimal.ZERO || inputAsset == null || outputAsset == null) {
            swapOutputAmount = ""
            updateSwapView()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val amount = inputAmount.toBigDecimal().movePointRight(inputAsset?.decimals ?: 6).toLong().toString()
            val result = if (inputAsset?.denom == "ucnho") {
                 if (outputAsset?.denom == "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo") {
                     applicationViewModel.walletRepository.simulateVndoPrice(null, cnhoChain, ChainCnho.DEX_PAIR, amount, outputAsset?.denom!!)
                 } else {
                     applicationViewModel.walletRepository.simulateSwap(null, cnhoChain, ChainCnho.DEX_ROUTER, amount, outputAsset?.denom!!, "ucnho")
                 }
            } else {
                applicationViewModel.walletRepository.simulateSwap(null, cnhoChain, ChainCnho.DEX_ROUTER, amount, inputAsset?.denom!!, "ucnho")
            }

            withContext(Dispatchers.Main) {
                if (result is NetworkResult.Success) {
                    swapOutputAmount = result.data?.toBigDecimal()?.movePointLeft(outputAsset?.decimals ?: 6)?.toPlainString() ?: ""
                    updateSwapView()
                } else {
                    swapOutputAmount = ""
                    updateSwapView()
                }
            }
        }
    }

    private fun setUpClickAction() {
        binding.apply {
            inputTokenLayout.setOnClickListener {
                val swapAssets = BaseData.assets?.filter { it.chain == cnhoChain.apiName }?.map { 
                    it.toTargetAsset()
                }?.toMutableList() ?: mutableListOf()
                
                if (parentFragmentManager.findFragmentByTag(AssetSelectFragment::class.java.name) != null) return@setOnClickListener
                
                val selectFragment = AssetSelectFragment.newInstance(cnhoChain, inputAsset?.toTargetAsset(), swapAssets, cnhoChain.cosmosFetcher?.cosmosBalances, AssetSelectType.SWAP_INPUT, object : AssetListener {
                    override fun select(denom: String) {
                        if (outputAsset?.denom == denom) {
                            outputAsset = inputAsset
                        }
                        inputAsset = BaseData.getAsset(cnhoChain.apiName, denom)
                        updateAssetsView()
                    }
                })
                selectFragment.show(parentFragmentManager, AssetSelectFragment::class.java.name)
            }

            outputTokenLayout.setOnClickListener {
                val swapAssets = BaseData.assets?.filter { it.chain == cnhoChain.apiName }?.map { 
                    it.toTargetAsset()
                }?.toMutableList() ?: mutableListOf()

                if (parentFragmentManager.findFragmentByTag(AssetSelectFragment::class.java.name) != null) return@setOnClickListener

                val selectFragment = AssetSelectFragment.newInstance(cnhoChain, outputAsset?.toTargetAsset(), swapAssets, cnhoChain.cosmosFetcher?.cosmosBalances, AssetSelectType.SWAP_OUTPUT, object : AssetListener {
                    override fun select(denom: String) {
                        if (inputAsset?.denom == denom) {
                            inputAsset = outputAsset
                        }
                        outputAsset = BaseData.getAsset(cnhoChain.apiName, denom)
                        updateAssetsView()
                    }
                })
                selectFragment.show(parentFragmentManager, AssetSelectFragment::class.java.name)
            }

            btnToggle.setOnClickListener {
                val temp = inputAsset
                inputAsset = outputAsset
                outputAsset = temp
                updateAssetsView()
            }

            btnSlippage.setOnClickListener {
                val slippageFragment = SlippageFragment.newInstance(swapSlippage, object : SlippageListener {
                    override fun slippage(position: Int) {
                        swapSlippage = position.toString()
                        binding.slippage.text = "$swapSlippage%"
                    }
                })
                slippageFragment.show(parentFragmentManager, SlippageFragment::class.java.name)
            }
            
            btnHalf.setOnClickListener {
                val balance = cnhoChain.cosmosFetcher?.availableAmount(inputAsset?.denom ?: "") ?: BigDecimal.ZERO
                val halfAmount = balance.divide(BigDecimal("2"), inputAsset?.decimals ?: 6, RoundingMode.DOWN)
                inputAmountTxt.setText(halfAmount.movePointLeft(inputAsset?.decimals ?: 6).toPlainString())
            }
            
            btnMax.setOnClickListener {
                val balance = cnhoChain.cosmosFetcher?.availableAmount(inputAsset?.denom ?: "") ?: BigDecimal.ZERO
                inputAmountTxt.setText(balance.movePointLeft(inputAsset?.decimals ?: 6).toPlainString())
            }
        }
    }

    private fun Asset.toTargetAsset(): TargetAsset {
        return TargetAsset(image, symbol, denom ?: "", TargetAssetType.NATIVE, description, decimals)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package wannabit.io.cosmostaion.ui.main

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wannabit.io.cosmostaion.R
import wannabit.io.cosmostaion.chain.FetchState
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
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.cosmos.base.v1beta1.CoinProto.Coin
import com.cosmwasm.wasm.v1.TxProto.MsgExecuteContract
import com.google.gson.Gson
import com.google.protobuf.ByteString
import com.cosmos.tx.v1beta1.TxProto
import com.google.protobuf.Any
import wannabit.io.cosmostaion.common.makeToast
import wannabit.io.cosmostaion.data.repository.tx.TxRepositoryImpl
import wannabit.io.cosmostaion.data.viewmodel.tx.TxViewModel
import wannabit.io.cosmostaion.data.viewmodel.tx.TxViewModelProviderFactory
import wannabit.io.cosmostaion.sign.Signer
import wannabit.io.cosmostaion.ui.password.PasswordCheckActivity
import wannabit.io.cosmostaion.ui.tx.TxResultActivity
import wannabit.io.cosmostaion.ui.tx.TxResultType
import wannabit.io.cosmostaion.ui.tx.option.swap.SlippageFragment
import wannabit.io.cosmostaion.ui.tx.option.swap.SlippageListener
import java.math.BigDecimal
import java.math.RoundingMode

class SwapFragment : Fragment() {

    private var _binding: FragmentSwapBinding? = null
    private val binding get() = _binding!!

    private val applicationViewModel = ApplicationViewModel.shared
    private lateinit var txViewModel: TxViewModel

    private var cnhoChain = ChainCnho()
    private var txFee: TxProto.Fee? = null
    private var txMemo = ""
    private var inputAsset: Asset? = null
    private var outputAsset: Asset? = null
    private var inputAmount: String = ""
    private var swapOutputAmount: String = ""
    private var swapSlippage = "0.1"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSwapBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewModel()

        setUpObservers()
        initData()
        initView()
        setUpClickAction()
        setUpBroadcast()
    }

    private val swapResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && isAdded) {
                binding.loading.visibility = View.VISIBLE
                txViewModel.broadcast(
                    cnhoChain.cosmosFetcher()?.getChannel(),
                    onBindSwapMsg(),
                    txFee,
                    txMemo,
                    cnhoChain
                )
            }
        }

    override fun onResume() {
        super.onResume()
        updateAssetsView()
    }

    private fun initViewModel() {
        val txRepository = TxRepositoryImpl()
        val txViewModelProviderFactory = TxViewModelProviderFactory(txRepository)
        txViewModel = ViewModelProvider(
            this, txViewModelProviderFactory
        )[TxViewModel::class.java]
    }

    private fun initData() {
        BaseData.baseAccount?.allChains?.firstOrNull { it.tag == cnhoChain.tag }?.let {
            cnhoChain = it as ChainCnho
        }

        if (cnhoChain.fetchState == FetchState.IDLE || cnhoChain.fetchState == FetchState.FAIL) {
            applicationViewModel.loadChainData(cnhoChain, BaseData.baseAccount?.id ?: -1L, isRefresh = true)
        }

        initFee()

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
                val amount = inputAmount.toBigDecimalOrNull()
                if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
                    swapOutputAmount = ""
                    updateSwapView()
                    return
                }
                simulateSwap()
            }
        })
    }

    private fun updateAssetsView() {
        inputAsset?.let { inputAsset = BaseData.getAsset(cnhoChain.apiName, it.denom ?: "") }
        outputAsset?.let { outputAsset = BaseData.getAsset(cnhoChain.apiName, it.denom ?: "") }

        if (inputAsset == null || outputAsset == null) {
            inputAsset = BaseData.getAsset(cnhoChain.apiName, "ucnho")
            outputAsset = BaseData.getAsset(cnhoChain.apiName, "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo")
                ?: BaseData.assets?.firstOrNull { it.chain == cnhoChain.apiName && it.denom != "ucnho" }
        }

        binding.apply {
            inputAsset?.let {
                inputTokenImg.setTokenImg(it.image ?: "")
                inputToken.text = it.symbol
                val balance = cnhoChain.cosmosFetcher()?.balanceAmount(it.denom ?: "") ?: BigDecimal.ZERO
                inputAvailable.text = formatAmount(balance.movePointLeft(it.decimals ?: 6).toPlainString(), it.decimals ?: 6)
            }

            outputAsset?.let {
                outputTokenImg.setTokenImg(it.image ?: "")
                outputToken.text = it.symbol
                val balance = cnhoChain.cosmosFetcher()?.balanceAmount(it.denom ?: "") ?: BigDecimal.ZERO
                outputAvailable.text = formatAmount(balance.movePointLeft(it.decimals ?: 6).toPlainString(), it.decimals ?: 6)
            }

            fromAddress.text = cnhoChain.address
            toAddress.text = cnhoChain.address
        }
        updateSwapView()
        simulateSwap()
    }

    private fun updateSwapView() {
        binding.apply {
            if (swapOutputAmount.isEmpty()) {
                outputAmount.text = "0"
                outputAmountValue.text = formatAssetValue(BigDecimal.ZERO, coinGeckoId = "cnho")
                feeView.visibility = View.GONE
                btnSwap.updateButtonView(false)
            } else {
                outputAmount.text = formatAmount(swapOutputAmount, outputAsset?.decimals ?: 6)
                
                val price = BaseData.getPrice(outputAsset?.coinGeckoId)
                val outputAmountDecimal = swapOutputAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val value = outputAmountDecimal.multiply(price)
                outputAmountValue.text = formatAssetValue(value, coinGeckoId = outputAsset?.coinGeckoId)
                
                feeView.visibility = View.VISIBLE
                
                // Exchange rate
                inputRateAmount.text = "1"
                inputRateDenom.text = inputAsset?.symbol
                
                val inputAmountDecimal = inputAmount.toBigDecimalOrNull()
                if (inputAmountDecimal != null && inputAmountDecimal.compareTo(BigDecimal.ZERO) != 0) {
                    val rate = outputAmountDecimal.divide(inputAmountDecimal, 6, RoundingMode.HALF_DOWN)
                    outputRateAmount.text = formatAmount(rate.toPlainString(), 6)
                } else {
                    outputRateAmount.text = "0"
                }
                outputRateDenom.text = outputAsset?.symbol
                
                txFeeAmount.text = formatAmount("0.01", 6)
                txFeeDenom.text = "CNHO"
                swapVenue.text = "CNHO Swap"

                val available = cnhoChain.cosmosFetcher()?.balanceAmount(inputAsset?.denom ?: "") ?: BigDecimal.ZERO
                val inputAmountBigDecimal = inputAmountDecimal?.movePointRight(inputAsset?.decimals ?: 6) ?: BigDecimal.ZERO
                if (inputAmountBigDecimal > available) {
                    btnSwap.updateButtonView(false)
                    invalidMsg.visibility = View.VISIBLE
                } else {
                    btnSwap.updateButtonView(true)
                    invalidMsg.visibility = View.INVISIBLE
                }
            }
            
            val inputPrice = BaseData.getPrice(inputAsset?.coinGeckoId)
            val inputAmountDecimal = inputAmount.toBigDecimalOrNull()
            val inputValue = if (inputAmountDecimal == null) BigDecimal.ZERO else inputAmountDecimal.multiply(inputPrice)
            inputAmountValue.text = formatAssetValue(inputValue, coinGeckoId = "cnho")
        }
    }

    private fun setUpObservers() {
        applicationViewModel.currentAccountResult.observe(viewLifecycleOwner) {
            initData()
            initView()
        }

        applicationViewModel.fetchedResult.observe(viewLifecycleOwner) { tag ->
            if (tag == cnhoChain.tag) {
                updateAssetsView()
            }
        }
        applicationViewModel.refreshStakingInfoFetchedResult.observe(viewLifecycleOwner) { tag ->
            if (tag == cnhoChain.tag) {
                updateAssetsView()
            }
        }
        applicationViewModel.txFetchedResult.observe(viewLifecycleOwner) { tag ->
            if (tag == cnhoChain.tag) {
                updateAssetsView()
            }
        }
        applicationViewModel.updatePriceResult.observe(viewLifecycleOwner) {
            updateAssetsView()
        }

        setUpBroadcast()
    }

    private fun simulateSwap() {
        val inputAmountDecimal = inputAmount.toBigDecimalOrNull()
        if (inputAmountDecimal == null || inputAmountDecimal.compareTo(BigDecimal.ZERO) == 0 || inputAsset == null || outputAsset == null) {
            swapOutputAmount = ""
            updateSwapView()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val amount = inputAmountDecimal.movePointRight(inputAsset?.decimals ?: 6).setScale(0, RoundingMode.DOWN).toPlainString()
            val result = if ((inputAsset?.denom == "ucnho" && outputAsset?.denom == "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo") ||
                (inputAsset?.denom == "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo" && outputAsset?.denom == "ucnho")) {
                applicationViewModel.walletRepository.simulateVndoPrice(null, cnhoChain, ChainCnho.DEX_PAIR, amount, inputAsset?.denom!!)
            } else {
                applicationViewModel.walletRepository.simulateSwap(null, cnhoChain, ChainCnho.DEX_ROUTER, amount, inputAsset?.denom!!, outputAsset?.denom!!)
            }

            withContext(Dispatchers.Main) {
                if (result is NetworkResult.Success) {
                    swapOutputAmount = result.data?.toBigDecimalOrNull()?.movePointLeft(outputAsset?.decimals ?: 6)?.stripTrailingZeros()?.toPlainString() ?: ""
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

                if (swapAssets.none { it.denom == "ucnho" }) {
                    swapAssets.add(0, TargetAsset("https://raw.githubusercontent.com/cosmostation/chainlist/main/chain/cnho/asset/cnho.png", "CNHO", "ucnho", TargetAssetType.NATIVE, "CNHO Stables", 6))
                }
                if (swapAssets.none { it.denom == "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo" }) {
                    swapAssets.add(TargetAsset("https://raw.githubusercontent.com/cosmostation/chainlist/main/chain/cnho/asset/vndo.png", "VNDO", "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo", TargetAssetType.NATIVE, "VNDO Stablecoin", 6))
                }

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

                if (swapAssets.none { it.denom == "ucnho" }) {
                    swapAssets.add(0, TargetAsset("https://raw.githubusercontent.com/cosmostation/chainlist/main/chain/cnho/asset/cnho.png", "CNHO", "ucnho", TargetAssetType.NATIVE, "CNHO Stables", 6))
                }
                if (swapAssets.none { it.denom == "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo" }) {
                    swapAssets.add(TargetAsset("https://raw.githubusercontent.com/cosmostation/chainlist/main/chain/cnho/asset/vndo.png", "VNDO", "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo", TargetAssetType.NATIVE, "VNDO Stablecoin", 6))
                }

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
                    override fun slippage(slippage: String) {
                        swapSlippage = slippage
                        binding.slippage.text = "$swapSlippage%"
                    }
                })
                slippageFragment.show(parentFragmentManager, SlippageFragment::class.java.name)
            }
            
            btnHalf.setOnClickListener {
                inputAsset?.let { asset ->
                    val balance = cnhoChain.cosmosFetcher()?.balanceAmount(asset.denom ?: "") ?: BigDecimal.ZERO
                    val halfAmount = balance.divide(BigDecimal("2"), 0, RoundingMode.DOWN)
                    inputAmountTxt.setText(halfAmount.movePointLeft(asset.decimals ?: 6).toPlainString())
                }
            }

            btnMax.setOnClickListener {
                inputAsset?.let { asset ->
                    val balance = cnhoChain.cosmosFetcher()?.balanceAmount(asset.denom ?: "") ?: BigDecimal.ZERO
                    inputAmountTxt.setText(balance.movePointLeft(asset.decimals ?: 6).toPlainString())
                }
            }

            btnSwap.setOnClickListener {
                val amount = inputAmount.toBigDecimalOrNull()
                if (inputAmount.isEmpty() || amount == null || amount == BigDecimal.ZERO) {
                    return@setOnClickListener
                }

                Intent(requireContext(), PasswordCheckActivity::class.java).apply {
                    swapResultLauncher.launch(this)
                    if (Build.VERSION.SDK_INT >= 34) {
                        requireActivity().overrideActivityTransition(
                            Activity.OVERRIDE_TRANSITION_OPEN,
                            R.anim.anim_slide_in_bottom,
                            R.anim.anim_fade_out
                        )
                    } else {
                        requireActivity().overridePendingTransition(
                            R.anim.anim_slide_in_bottom, R.anim.anim_fade_out
                        )
                    }
                }
            }
        }
    }

    private fun initFee() {
        val feeInfos = cnhoChain.getFeeInfos(requireContext())
        txFee = cnhoChain.getBaseFee(requireContext(), 0, feeInfos[0].feeDatas[0].denom)
    }

    private fun onBindSwapMsg(): MutableList<Any> {
        val inputAsset = inputAsset ?: return mutableListOf()
        val outputAsset = outputAsset ?: return mutableListOf()
        val amount = inputAmount.toBigDecimalOrNull()?.movePointRight(inputAsset.decimals ?: 6)?.setScale(0, RoundingMode.DOWN)?.toPlainString() ?: "0"
        val offerAssetDenom = inputAsset.denom ?: ""

        val msgJson = if ((inputAsset.denom == "ucnho" && outputAsset.denom == "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo") ||
            (inputAsset.denom == "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo" && outputAsset.denom == "ucnho")) {
            val offerAsset = mapOf(
                "info" to mapOf("native_token" to mapOf("denom" to offerAssetDenom)),
                "amount" to amount
            )
            mapOf("swap" to mapOf("offer_asset" to offerAsset))
        } else {
            val operation = mapOf(
                "astroport" to mapOf(
                    "native_swap" to mapOf(
                        "offer_denom" to offerAssetDenom,
                        "ask_denom" to (outputAsset.denom ?: "")
                    )
                )
            )
            mapOf(
                "execute_swap_operations" to mapOf(
                    "offer_amount" to amount,
                    "operations" to listOf(operation)
                )
            )
        }

        val contractAddress = if ((inputAsset.denom == "ucnho" && outputAsset.denom == "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo") ||
            (inputAsset.denom == "factory/cnho18x42dnqv4z2mxdw6pq5p4h5aj49vnqytq6k0h4/vndo" && outputAsset.denom == "ucnho")) {
            ChainCnho.DEX_PAIR
        } else {
            ChainCnho.DEX_ROUTER
        }

        val wasmMsg = MsgExecuteContract.newBuilder()
            .setSender(cnhoChain.address)
            .setContract(contractAddress)
            .setMsg(ByteString.copyFromUtf8(Gson().toJson(msgJson)))
            .addFunds(Coin.newBuilder().setDenom(offerAssetDenom).setAmount(amount).build())
            .build()

        return Signer.wasmMsg(mutableListOf(wasmMsg))
    }

    private fun setUpBroadcast() {
        txViewModel.broadcast.observe(viewLifecycleOwner) { response ->
            binding.loading.visibility = View.GONE
            response?.let { txResponse ->
                Intent(requireContext(), TxResultActivity::class.java).apply {
                    if (txResponse.code > 0) {
                        putExtra("isSuccess", false)
                    } else {
                        putExtra("isSuccess", true)
                        binding.inputAmountTxt.setText("")
                        applicationViewModel.loadChainData(cnhoChain, BaseData.baseAccount?.id ?: -1L, isRefresh = true)
                    }
                    putExtra("errorMsg", txResponse.rawLog)
                    putExtra("selectedChain", cnhoChain.tag)
                    val hash = txResponse.txhash
                    if (!TextUtils.isEmpty(hash)) putExtra("txHash", hash)
                    putExtra("txResultType", TxResultType.COSMOS.toString())
                    startActivity(this)
                }
            }
        }

        txViewModel.errorMessage.observe(viewLifecycleOwner) {
            binding.loading.visibility = View.GONE
            it?.let { requireContext().makeToast(it) }
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

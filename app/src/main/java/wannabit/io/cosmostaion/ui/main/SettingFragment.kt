package wannabit.io.cosmostaion.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.hardware.fingerprint.FingerprintManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wannabit.io.cosmostaion.BuildConfig
import wannabit.io.cosmostaion.R
import wannabit.io.cosmostaion.chain.allChains
import wannabit.io.cosmostaion.common.BaseData
import wannabit.io.cosmostaion.common.BaseUtils
import wannabit.io.cosmostaion.common.CosmostationConstants
import wannabit.io.cosmostaion.common.makeToast
import wannabit.io.cosmostaion.common.toMoveAnimation
import wannabit.io.cosmostaion.data.viewmodel.ApplicationViewModel
import wannabit.io.cosmostaion.data.viewmodel.intro.WalletViewModel
import wannabit.io.cosmostaion.database.AppDatabase
import wannabit.io.cosmostaion.database.Prefs
import wannabit.io.cosmostaion.databinding.FragmentSettingBinding
import wannabit.io.cosmostaion.ui.main.dapp.DappActivity
import wannabit.io.cosmostaion.ui.main.setting.NoticeActivity
import wannabit.io.cosmostaion.ui.main.setting.SettingBottomFragment
import wannabit.io.cosmostaion.ui.main.setting.ThemeFragment
import wannabit.io.cosmostaion.ui.main.setting.general.DevDialogActivity
import wannabit.io.cosmostaion.ui.main.setting.general.PushManager
import wannabit.io.cosmostaion.ui.main.setting.wallet.account.AccountActivity
import wannabit.io.cosmostaion.ui.main.setting.wallet.book.AddressBookListActivity
import wannabit.io.cosmostaion.ui.main.setting.wallet.chain.ChainActivity
import wannabit.io.cosmostaion.ui.main.setting.wallet.chain.ChainNoticeActivity
import wannabit.io.cosmostaion.ui.password.PasswordCheckActivity
import wannabit.io.cosmostaion.ui.qr.WaitingDialog
import java.util.Locale

class SettingFragment : Fragment() {

    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!

    private val walletViewModel: WalletViewModel by activityViewModels()

    private var isClickable = true

    private var waitingDialog: WaitingDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
        setUpClickAction()
        setUpSwitchAction()
        checkAccountStatus()
        checkChangeNameData()
    }

    private fun initView() {
        Prefs.language = BaseUtils.LANGUAGE_ENGLISH
        Prefs.currency = 4
        binding.apply {
            listOf(
                accountView,
                addressBookView,
                languageView,
                currencyView,
                alarmView,
                appLockView,
                bioView,
                helpView,
                noticeView,
                termView,
                privacyView,
                versionView
            ).forEach { it.setBackgroundResource(R.drawable.item_bg) }
            noticeView.visibility = View.GONE

            updateWalletView()
            updateDefaultView()

            val fingerprintManagerCompat = FingerprintManagerCompat.from(requireContext())
            if (fingerprintManagerCompat.isHardwareDetected && fingerprintManagerCompat.hasEnrolledFingerprints()) {
                bioTxt.text = getString(R.string.title_using_bio)
            } else {
                bioTxt.text = ""
            }
            val versionName = BuildConfig.VERSION_NAME
            version.text = "v " + versionName

            if (BaseData.pushRefreshIfNeed()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    PushManager.updateStatus(requireContext(), Prefs.alarmEnable) { _, _ -> }
                }
            }

            waitingDialog = WaitingDialog.newInstance()
        }
    }

    override fun onResume() {
        super.onResume()
        onUpdateSwitch()
    }

    private fun updateWalletView() {
        binding.apply {
            BaseData.baseAccount?.let { account ->
                accountName.text = account.name
            }
        }
    }

    private fun checkAccountStatus() {
        ApplicationViewModel.shared.currentAccountResult.observe(viewLifecycleOwner) {
            updateWalletView()
        }
    }

    private fun checkChangeNameData() {
        ApplicationViewModel.shared.changeNameResult.observe(viewLifecycleOwner) { account ->
            if (BaseData.baseAccount?.id == account?.id) {
                binding.accountName.text = account?.name
            }
        }
    }

    private fun updateDefaultView() {
        binding.apply {
            when (Prefs.language) {
                BaseUtils.LANGUAGE_ENGLISH -> {
                    language.text = getString(R.string.title_language_en)
                }

                BaseUtils.LANGUAGE_KOREAN -> {
                    language.text = getString(R.string.title_language_kr)
                }

                BaseUtils.LANGUAGE_JAPANESE -> {
                    language.text = getString(R.string.title_language_ja)
                }

                else -> {
                    language.text = getString(R.string.str_system)
                }
            }

            currency.text = BaseData.currencyName()
        }
    }

    private fun setUpClickAction() {
        binding.apply {
            accountView.setOnClickListener {
                Intent(requireContext(), AccountActivity::class.java).apply {
                    startActivity(this)
                    requireActivity().toMoveAnimation()
                }
            }

            addressBookView.setOnClickListener {
                Intent(requireContext(), AddressBookListActivity::class.java).apply {
                    startActivity(this)
                    requireActivity().toMoveAnimation()
                }
            }

            languageView.setOnClickListener {
//                handleOneClickWithDelay(
//                    SettingBottomFragment.newInstance(null, SettingType.LANGUAGE)
//                )
            }

            currencyView.setOnClickListener {
//                handleOneClickWithDelay(
//                    SettingBottomFragment.newInstance(null, SettingType.CURRENCY)
//                )
//                parentFragmentManager.setFragmentResultListener(
//                    "currency", this@SettingFragment
//                ) { _, _ ->
//                    currency.text = BaseData.currencyName()
//                    walletViewModel.price(BaseData.currencyName(), true)
//                }
            }

            helpView.setOnClickListener {
                val url = Uri.parse("https://chat.cnho.io")
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW, url
                    )
                )
            }

            noticeView.setOnClickListener {
                Intent(requireContext(), NoticeActivity::class.java).apply {
                    startActivity(this)
                    requireActivity().toMoveAnimation()
                }
            }

            termView.setOnClickListener {
                if (Prefs.language == BaseUtils.LANGUAGE_KOREAN || Locale.getDefault().language == "ko") {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(CosmostationConstants.COSMOSTATION_TERM_KR)
                        )
                    )
                } else {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(CosmostationConstants.COSMOSTATION_TERM_EN)
                        )
                    )
                }
            }

            privacyView.setOnClickListener {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(CosmostationConstants.COSMOSTATION_PRIVACY_POLICY)
                    )
                )
            }

            versionView.setOnClickListener {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + requireActivity().packageName)
                    )
                )
            }
        }
    }

    private fun handleOneClickWithDelay(bottomSheetDialogFragment: BottomSheetDialogFragment) {
        if (isClickable) {
            isClickable = false

            bottomSheetDialogFragment.show(
                parentFragmentManager, bottomSheetDialogFragment::class.java.name
            )

            Handler(Looper.getMainLooper()).postDelayed({
                isClickable = true
            }, 300)
        }
    }

    private fun onUpdateSwitch() {
        binding.apply {
            alarmSwitch.isChecked = Prefs.alarmEnable
            alarmSwitch.setSwitchView()

            appLockSwitch.isChecked = Prefs.appLock
            appLockSwitch.setSwitchView()

            bioSwitch.isChecked = Prefs.usingBio
            bioSwitch.setSwitchView()

            lifecycleScope.launch(Dispatchers.IO) {
                val addressCnt =
                    AppDatabase.getInstance().addressBookDao().selectAll().size.toString()
                withContext(Dispatchers.Main) {
                    favoriteAddressCnt.text = addressCnt
                }
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun setUpSwitchAction() {
        binding.apply {
            onUpdateSwitch()

            alarmSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    alarmSwitch.thumbDrawable =
                        ContextCompat.getDrawable(requireContext(), R.drawable.switch_thumb_on)
                } else {
                    alarmSwitch.thumbDrawable =
                        ContextCompat.getDrawable(requireContext(), R.drawable.switch_thumb_off)
                }
                setVibrate()
                waitingDialog?.show(requireActivity().supportFragmentManager, "dialog")
                PushManager.updateStatus(requireContext(), isChecked) { _, msg ->
                    requireActivity().makeToast(msg)
                    if (waitingDialog?.isVisible == true) {
                        waitingDialog?.dismissAllowingStateLoss()
                    }
                }
            }

            appLockSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    appLockSwitch.thumbDrawable =
                        ContextCompat.getDrawable(requireContext(), R.drawable.switch_thumb_on)
                    Prefs.appLock = true

                } else {
                    val intent = Intent(requireContext(), PasswordCheckActivity::class.java)
                    appLockCheckResultLauncher.launch(intent)
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
                setVibrate()
            }

            bioSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    bioSwitch.thumbDrawable =
                        ContextCompat.getDrawable(requireContext(), R.drawable.switch_thumb_on)
                    Prefs.usingBio = true
                } else {
                    bioSwitch.thumbDrawable =
                        ContextCompat.getDrawable(requireContext(), R.drawable.switch_thumb_off)
                    Prefs.usingBio = false
                }
                setVibrate()
            }
        }
    }

    private fun SwitchCompat.setSwitchView() {
        thumbDrawable = if (isChecked) {
            ContextCompat.getDrawable(requireContext(), R.drawable.switch_thumb_on)
        } else {
            ContextCompat.getDrawable(requireContext(), R.drawable.switch_thumb_off)
        }
    }

    private fun setVibrate() {
        val vibrator = requireActivity().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(100, 50))
    }

    private val appLockCheckResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                binding.appLockSwitch.thumbDrawable =
                    ContextCompat.getDrawable(requireContext(), R.drawable.switch_thumb_off)
                Prefs.appLock = false
            }
        }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

enum class SettingType { LANGUAGE, CURRENCY, PRICE_STATUS, BUY_CRYPTO, DAPP_SORT_OPTION }

package wannabit.io.cosmostaion.ui.main.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import wannabit.io.cosmostaion.chain.cosmosClass.ChainCnho
import wannabit.io.cosmostaion.database.Prefs
import wannabit.io.cosmostaion.databinding.FragmentCnhoEndpointBinding

class CnhoEndpointFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCnhoEndpointBinding? = null
    private val binding get() = _binding!!

    companion object {
        @JvmStatic
        fun newInstance(): CnhoEndpointFragment {
            return CnhoEndpointFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCnhoEndpointBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
        setUpClickAction()
    }

    private fun initView() {
        binding.apply {
            val chain = ChainCnho()
            val lcd = Prefs.getLcdEndpoint(chain)
            val rpc = Prefs.getGrpcEndpoint(chain)

            if (lcd.isNotEmpty()) {
                lcdEndpoint.setText(lcd)
            } else {
                lcdEndpoint.setText(chain.lcdUrl)
            }

            if (rpc.isNotEmpty()) {
                rpcEndpoint.setText(rpc)
            } else {
                rpcEndpoint.setText(chain.mainUrl)
            }
        }
    }

    private fun setUpClickAction() {
        binding.apply {
            btnReset.setOnClickListener {
                val chain = ChainCnho()
                Prefs.removeLcdEndpoint(chain)
                Prefs.removeGrpcEndpoint(chain)
                dismiss()
            }

            btnConfirm.setOnClickListener {
                val lcd = lcdEndpoint.text.toString().trim()
                val rpc = rpcEndpoint.text.toString().trim()
                val chain = ChainCnho()

                if (lcd.isNotEmpty()) {
                    Prefs.setLcdEndpoint(chain, lcd)
                } else {
                    Prefs.removeLcdEndpoint(chain)
                }

                if (rpc.isNotEmpty()) {
                    Prefs.setGrpcEndpoint(chain, rpc)
                } else {
                    Prefs.removeGrpcEndpoint(chain)
                }

                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
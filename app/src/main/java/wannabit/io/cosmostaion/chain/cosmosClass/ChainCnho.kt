package wannabit.io.cosmostaion.chain.cosmosClass

import android.os.Parcelable
import com.google.common.collect.ImmutableList
import kotlinx.parcelize.Parcelize
import org.bitcoinj.crypto.ChildNumber
import wannabit.io.cosmostaion.chain.AccountKeyType
import wannabit.io.cosmostaion.chain.BaseChain
import wannabit.io.cosmostaion.chain.CosmosEndPointType
import wannabit.io.cosmostaion.chain.PubKeyType
import wannabit.io.cosmostaion.data.model.res.FeeInfo
import wannabit.io.cosmostaion.R

/** CNHO mainnet configuration. Network-critical values are kept local so they do not
 * depend on the upstream Cosmostation chain list service.
 *
 * Note: [apiName] ("cnho") must match the 'chain' field in the [wannabit.io.cosmostaion.data.model.res.Asset]
 * objects stored in [wannabit.io.cosmostaion.common.BaseData.assets] for the wallet to correctly
 * resolve and display balances, symbols, and names. */
@Parcelize
class ChainCnho : BaseChain(), Parcelable {

    override var name: String = "CNHO Stables"
    override var tag: String = "cnho118"
    override var apiName: String = "cnho"
    override var chainIdCosmos: String = "cnho_stables-1"

    override var accountKeyType = AccountKeyType(PubKeyType.COSMOS_SECP256K1, "m/44'/118'/0'/0/X")
    override var setParentPath: List<ChildNumber> = ImmutableList.of(
        ChildNumber(44, true), ChildNumber(118, true), ChildNumber.ZERO_HARDENED, ChildNumber.ZERO
    )

    // The provided public endpoint is REST/LCD. A gRPC endpoint can be added later
    // without changing addresses or signing behavior.
    override var cosmosEndPointType: CosmosEndPointType? = CosmosEndPointType.USE_LCD
    override var stakeDenom: String = "ucnho"
    override var coinSymbol: String = "CNHO"
    override var accountPrefix: String = "cnho"
    override var lcdUrl: String = "https://api.cnho.io/"
    override var mainUrl: String = "https://rpc.cnho.io/"

    override fun getFeeInfos(c: android.content.Context): MutableList<FeeInfo> {
        val result: MutableList<FeeInfo> = mutableListOf()
        val feeInfo = FeeInfo("0.01ucnho")
        feeInfo.title = c.getString(R.string.str_fixed)
        feeInfo.msg = c.getString(R.string.str_fee_speed_title_fixed)
        result.add(feeInfo)
        return result
    }

    companion object {
        const val DEX_FACTORY = "cnho1suhgf5svhu4usrurvxzlgn54ksxmn8gljarjtxqnapv8kjnp4nrsdx5lfr"
        const val DEX_PAIR = "cnho1xr3rq8yvd7qplsw5yx90ftsr2zdhg4e9z60h5duusgxpv72hud3sjpj5dw"
        const val DEX_ROUTER = "cnho1xt4ahzz2x8hpkc0tk6ekte9x6crw4w6u0r67cyt3kz9syh24pd7sfkgg2z"
        const val DISPLAY_DECIMALS = 6
    }
}
